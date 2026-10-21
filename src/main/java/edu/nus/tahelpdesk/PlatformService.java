package edu.nus.tahelpdesk;

import static edu.nus.tahelpdesk.Domain.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

/** In-memory local adapter. All access methods re-check current memberships. */
public class PlatformService {
  private final Map<UUID, User> users = new ConcurrentHashMap<>();
  private final Map<String, UUID> userByName = new ConcurrentHashMap<>();
  private final Map<UUID, Course> courses = new ConcurrentHashMap<>();
  private final Map<String, UUID> courseByCode = new ConcurrentHashMap<>();
  private final Map<UUID, Map<UUID, Membership>> memberships = new ConcurrentHashMap<>();
  private final Map<UUID, Post> posts = new ConcurrentHashMap<>();
  private final Map<UUID, Comment> comments = new ConcurrentHashMap<>();
  private final Map<UUID, Material> materials = new ConcurrentHashMap<>();
  private final Map<UUID, KnowledgeMap> knowledgeMaps = new ConcurrentHashMap<>();
  private final Map<String, Deque<Instant>> materialRetries = new ConcurrentHashMap<>();
  private static final int MAX_MATERIAL_BYTES = 20 * 1024 * 1024;
  private static final int MAX_MATERIAL_PAGES = 100;
  private static final int MAX_ACTIVE_MATERIALS = 20;
  private static final Pattern PDF_OBJECT = Pattern.compile("(?s)(\\d+)\\s+(\\d+)\\s+obj\\b(.*?)\\bendobj\\b");
  private static final Pattern PDF_ROOT = Pattern.compile("/Root\\s+(\\d+)\\s+(\\d+)\\s+R\\b");
  private static final Pattern PDF_PAGES = Pattern.compile("/Pages\\s+(\\d+)\\s+(\\d+)\\s+R\\b");
  private static final Pattern PDF_KIDS = Pattern.compile("(?s)/Kids\\s*\\[(.*?)]");
  private static final Pattern PDF_REFERENCE = Pattern.compile("(\\d+)\\s+(\\d+)\\s+R\\b");
  private static final Pattern PDF_START_XREF = Pattern.compile("(?s)startxref\\s*(\\d+)\\s*%%EOF\\s*$");
  private static final Pattern PDF_XREF_ENTRY = Pattern.compile("(\\d{10})\\s+(\\d{5})\\s+([nf])\\s*");
  private static final Pattern PDF_W = Pattern.compile("/W\\s*\\[\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*]");
  private static final Pattern PDF_SIZE = Pattern.compile("/Size\\s+(\\d+)\\b");
  private static final Pattern PDF_LENGTH = Pattern.compile("/Length\\s+(\\d+)\\b");
  private static final Pattern PDF_TEXT = Pattern.compile("\\((?:\\\\.|[^\\\\)])*\\)\\s*(?:Tj|TJ)");
  private static final Pattern PDF_PAGE = Pattern.compile("/Type\\s*/Page(?!s)\\b");
  private static final Pattern PDF_CONTENTS = Pattern.compile("(?s)/Contents\\s*(?:\\[(.*?)]|(\\d+)\\s+(\\d+)\\s+R)");
  private final ScheduledExecutorService materialProcessor = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "material-processor"); thread.setDaemon(true); return thread;
  });
  private final ScheduledExecutorService knowledgeMapProcessor = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "knowledge-map-processor"); thread.setDaemon(true); return thread;
  });
  private final PasswordHasher passwords = new PasswordHasher();
  private final Runnable afterMutation;
  private volatile KnowledgeMapGenerator knowledgeMapGenerator;
  /** Bounds queued and in-flight gateway work when provider capacity is unknown. */
  private final Semaphore knowledgeMapSlots = new Semaphore(4);

  @FunctionalInterface interface KnowledgeMapGenerator { SoCLaaSClient.Result generate(String sourcePacket); }

  public PlatformService() { this(() -> {}); }
  /** Package-private so the PostgreSQL adapter can load state before serving requests. */
  PlatformService(Runnable afterMutation) { this.afterMutation = afterMutation; }
  synchronized boolean isEmpty() { return users.isEmpty(); }
  synchronized void restore(Collection<User> loadedUsers, Collection<Course> loadedCourses,
      Collection<Membership> loadedMemberships, Collection<Post> loadedPosts, Collection<Comment> loadedComments, Collection<Material> loadedMaterials, Collection<KnowledgeMap> loadedKnowledgeMaps) {
    for (User user : loadedUsers) { users.put(user.id(), user); userByName.put(normalize(user.username()), user.id()); }
    for (Course course : loadedCourses) { courses.put(course.id(), course); courseByCode.put(course.code(), course.id()); memberships.put(course.id(), new ConcurrentHashMap<>()); }
    for (Membership membership : loadedMemberships) memberships.computeIfAbsent(membership.courseId(), ignored -> new ConcurrentHashMap<>()).put(membership.userId(), membership);
    for (Post post : loadedPosts) posts.put(post.id(), post);
    for (Comment comment : loadedComments) comments.put(comment.id(), comment);
    for (Material material : loadedMaterials) materials.put(material.id(), copyMaterial(material));
    for (KnowledgeMap map : loadedKnowledgeMaps) knowledgeMaps.put(map.courseId(), copyKnowledgeMap(map));
  }
  synchronized Snapshot snapshot() { return new Snapshot(List.copyOf(users.values()), List.copyOf(courses.values()), memberships.values().stream().flatMap(m -> m.values().stream()).toList(), List.copyOf(posts.values()), List.copyOf(comments.values()), materials.values().stream().map(PlatformService::copyMaterial).toList(), knowledgeMaps.values().stream().map(PlatformService::copyKnowledgeMap).toList()); }
  record Snapshot(List<User> users, List<Course> courses, List<Membership> memberships, List<Post> posts, List<Comment> comments, List<Material> materials, List<KnowledgeMap> knowledgeMaps) {}
  private void changed() { afterMutation.run(); }

  /** The gateway client is installed only by the application bootstrap, never by a browser request. */
  void setKnowledgeMapGenerator(KnowledgeMapGenerator generator) { knowledgeMapGenerator = generator; }

  public synchronized User signup(String username, String password, String selectedRole) {
    requireUsername(username); requirePassword(password);
    Role role = parseRole(selectedRole);
    String normalized = username.trim().toLowerCase(Locale.ROOT);
    if (userByName.containsKey(normalized)) throw ApiException.conflict("Username is already in use.");
    User user = new User(UUID.randomUUID(), username.trim(), role, passwords.hash(password), Instant.now());
    users.put(user.id(), user); userByName.put(normalized, user.id()); changed(); return user;
  }
  public synchronized User authenticate(String username, String password) {
    UUID id = userByName.get(normalize(username)); User user = id == null ? null : users.get(id);
    if (user == null || password == null || !passwords.matches(password, user.passwordHash())) throw ApiException.unauthenticated();
    return user;
  }
  public User current(UUID actor) { return requireUser(actor); }
  public synchronized Course createCourse(UUID actor, String code, String name) {
    requireRole(actor, Role.staff); requireCourseCode(code); requireCourseName(name);
    String normalized = code.trim().toUpperCase(Locale.ROOT);
    if (courseByCode.containsKey(normalized)) throw ApiException.conflict("Course code is already in use.");
    Course course = new Course(UUID.randomUUID(), normalized, name.trim(), actor, Instant.now());
    courses.put(course.id(), course); courseByCode.put(normalized, course.id());
    memberships.put(course.id(), new ConcurrentHashMap<>());
    memberships.get(course.id()).put(actor, new Membership(course.id(), actor, Role.staff));
    changed(); return course;
  }
  public synchronized Course renameCourse(UUID actor, UUID courseId, String name) {
    Course old = requireCourseStaff(actor, courseId); requireCourseName(name);
    Course updated = new Course(old.id(), old.code(), name.trim(), old.createdBy(), old.createdAt()); courses.put(courseId, updated); changed(); return updated;
  }
  public synchronized void deleteCourse(UUID actor, UUID courseId) {
    Course course = requireCourseStaff(actor, courseId);
    posts.values().removeIf(p -> p.courseId().equals(courseId));
    comments.values().removeIf(c -> !posts.containsKey(c.postId()));
    materials.entrySet().removeIf(entry -> entry.getValue().courseId().equals(courseId));
    knowledgeMaps.remove(courseId);
    memberships.remove(courseId); courses.remove(courseId); courseByCode.remove(course.code()); changed();
  }
  /** Staff-only view includes non-active states; deleted records are intentionally hidden. */
  public List<Material> listMaterials(UUID actor, UUID courseId) {
    requireCourseStaff(actor, courseId);
    return materials.values().stream().filter(m -> m.courseId().equals(courseId) && m.status() != MaterialStatus.deleted)
        .sorted(Comparator.comparing(Material::createdAt).reversed()).map(PlatformService::copyMaterial).toList();
  }
  /** Resource list deliberately exposes only active source PDFs to current course members. */
  public List<Material> listResources(UUID actor, UUID courseId) {
    requireCourseMember(actor, courseId);
    return materials.values().stream().filter(m -> m.courseId().equals(courseId) && m.status() == MaterialStatus.active)
        .sorted(Comparator.comparing(Material::createdAt).reversed()).map(PlatformService::copyMaterial).toList();
  }
  public synchronized Material uploadMaterial(UUID actor, UUID courseId, String filename, String contentType, byte[] content) {
    requireCourseStaff(actor, courseId); validateMaterial(filename, contentType, content);
    long existing = materials.values().stream().filter(m -> m.courseId().equals(courseId) &&
        (m.status() == MaterialStatus.active || m.status() == MaterialStatus.processing)).count();
    if (existing >= MAX_ACTIVE_MATERIALS) throw ApiException.conflict("This course already has 20 active PDFs. Delete one before uploading another.");
    int pages = pageCount(content);
    Instant now = Instant.now();
    Material material = new Material(UUID.randomUUID(), courseId, actor, safeFilename(filename), MaterialStatus.processing,
        null, content.clone(), pages, now, now);
    materials.put(material.id(), material); markKnowledgeMapStale(courseId); changed(); scheduleProcessing(material.id()); return copyMaterial(material);
  }
  public synchronized void deleteMaterial(UUID actor, UUID courseId, UUID materialId) {
    requireCourseStaff(actor, courseId); Material old = requireMaterialInCourse(courseId, materialId);
    if (old.status() == MaterialStatus.deleted) throw ApiException.hidden();
    materials.put(old.id(), new Material(old.id(), old.courseId(), old.uploaderId(), old.filename(), MaterialStatus.deleted,
        null, old.content(), old.pageCount(), old.createdAt(), Instant.now())); markKnowledgeMapStale(courseId); changed();
  }
  public synchronized Material retryMaterial(UUID actor, UUID courseId, UUID materialId) {
    requireCourseStaff(actor, courseId); Material old = requireMaterialInCourse(courseId, materialId);
    if (old.status() != MaterialStatus.failed) throw ApiException.validation("Only failed PDF processing can be retried.");
    enforceRetryLimit(actor, courseId);
    Material retried = new Material(old.id(), old.courseId(), old.uploaderId(), old.filename(), MaterialStatus.processing,
        null, old.content(), old.pageCount(), old.createdAt(), Instant.now()); materials.put(old.id(), retried); markKnowledgeMapStale(courseId); changed(); scheduleProcessing(retried.id()); return copyMaterial(retried);
  }
  /** Staff-only map status/content. Students deliberately have no equivalent endpoint yet. */
  public synchronized KnowledgeMap getKnowledgeMap(UUID actor, UUID courseId) {
    requireCourseStaff(actor, courseId); KnowledgeMap map = knowledgeMaps.get(courseId);
    if (map == null) throw ApiException.hidden(); return copyKnowledgeMap(map);
  }
  /** Starts a manual refresh using every active course PDF as one immutable source snapshot. */
  public synchronized KnowledgeMap generateKnowledgeMap(UUID actor, UUID courseId) {
    requireCourseStaff(actor, courseId);
    if (knowledgeMapGenerator == null) throw ApiException.validation("Staff LLM generation is not configured on this server.");
    KnowledgeMap prior = knowledgeMaps.get(courseId);
    if (prior != null && prior.status() == KnowledgeMapStatus.generating) throw ApiException.conflict("A Knowledge Map update is already running for this course.");
    List<Material> sources = activeMaterials(courseId);
    if (sources.isEmpty()) throw ApiException.validation("Upload and process at least one PDF before generating a Knowledge Map.");
    String sourcePacket = sourcePacket(sources);
    if (!knowledgeMapSlots.tryAcquire()) throw ApiException.rateLimited("The staff LLM queue is busy. Try the manual update again shortly.");
    Instant now = Instant.now();
    KnowledgeMap map = new KnowledgeMap(prior == null ? UUID.randomUUID() : prior.id(), courseId, actor, KnowledgeMapStatus.generating,
        prior == null ? null : prior.content(), null, sources.stream().map(Material::id).toList(), null, null,
        prior == null ? now : prior.createdAt(), now);
    knowledgeMaps.put(courseId, map); changed();
    try { knowledgeMapProcessor.execute(() -> { try { completeKnowledgeMap(map.id(), courseId, map.sourceMaterialIds(), sourcePacket); } finally { knowledgeMapSlots.release(); } }); }
    catch (RuntimeException e) { knowledgeMapSlots.release(); knowledgeMaps.put(courseId, failedKnowledgeMap(map, "The staff LLM service is temporarily unavailable. Try the manual update again later.")); changed(); throw e; }
    return copyKnowledgeMap(map);
  }
  public synchronized KnowledgeMap approveKnowledgeMap(UUID actor, UUID courseId) {
    requireCourseStaff(actor, courseId); KnowledgeMap old = requireReviewableKnowledgeMap(courseId);
    KnowledgeMap approved = new KnowledgeMap(old.id(), old.courseId(), old.requestedBy(), KnowledgeMapStatus.approved, old.content(), null,
        old.sourceMaterialIds(), actor, Instant.now(), old.createdAt(), Instant.now()); knowledgeMaps.put(courseId, approved); changed(); return copyKnowledgeMap(approved);
  }
  public synchronized KnowledgeMap rejectKnowledgeMap(UUID actor, UUID courseId) {
    requireCourseStaff(actor, courseId); KnowledgeMap old = requireReviewableKnowledgeMap(courseId);
    KnowledgeMap rejected = new KnowledgeMap(old.id(), old.courseId(), old.requestedBy(), KnowledgeMapStatus.rejected, old.content(), null,
        old.sourceMaterialIds(), actor, Instant.now(), old.createdAt(), Instant.now()); knowledgeMaps.put(courseId, rejected); changed(); return copyKnowledgeMap(rejected);
  }
  public Material downloadResource(UUID actor, UUID courseId, UUID materialId) {
    requireCourseMember(actor, courseId); Material material = requireMaterialInCourse(courseId, materialId);
    if (material.status() != MaterialStatus.active) throw ApiException.hidden();
    return copyMaterial(material);
  }
  public List<Course> listCourses(UUID actor) {
    requireUser(actor); return courses.values().stream().filter(c -> membership(c.id(), actor) != null)
        .sorted(Comparator.comparing(Course::code)).toList();
  }
  public Course getCourse(UUID actor, UUID courseId) { requireCourseMember(actor, courseId); return requireCourse(courseId); }
  public synchronized Membership addMember(UUID actor, UUID courseId, String username) {
    requireCourseStaff(actor, courseId); UUID targetId = userByName.get(normalize(username));
    if (targetId == null) throw ApiException.validation("No account exists for that username.");
    User target = users.get(targetId); Map<UUID, Membership> members = memberships.get(courseId);
    Membership prior = members.get(targetId); if (prior != null) return prior;
    Membership membership = new Membership(courseId, targetId, target.role()); members.put(targetId, membership); changed(); return membership;
  }
  public synchronized void removeStudent(UUID actor, UUID courseId, UUID userId) {
    requireCourseStaff(actor, courseId); Membership target = membership(courseId, userId);
    if (target == null || target.role() != Role.student) throw ApiException.validation("That student is not enrolled in this course.");
    memberships.get(courseId).remove(userId); changed();
  }
  public List<Membership> listMembers(UUID actor, UUID courseId) { requireCourseStaff(actor, courseId); return List.copyOf(memberships.get(courseId).values()); }
  public synchronized Post createPost(UUID actor, UUID courseId, String title, String description) {
    requireCourseMember(actor, courseId); validatePost(title, description); Instant now = Instant.now();
    Post post = new Post(UUID.randomUUID(), courseId, actor, title.trim(), nullable(description), now, now); posts.put(post.id(), post); changed(); return post;
  }
  public List<Post> listPosts(UUID actor, UUID courseId) { requireCourseMember(actor, courseId); return posts.values().stream().filter(p -> p.courseId().equals(courseId)).sorted(Comparator.comparing(Post::createdAt).reversed()).toList(); }
  public Post getPost(UUID actor, UUID courseId, UUID postId) { requireCourseMember(actor, courseId); return requirePostInCourse(courseId, postId); }
  public synchronized Post editPost(UUID actor, UUID courseId, UUID postId, String title, String description) {
    requireCourseMember(actor, courseId); Post old = requirePostInCourse(courseId, postId); requireAuthor(actor, old.authorId()); validatePost(title, description);
    Post updated = new Post(old.id(), old.courseId(), old.authorId(), title.trim(), nullable(description), old.createdAt(), Instant.now()); posts.put(postId, updated); changed(); return updated;
  }
  public synchronized void deletePost(UUID actor, UUID courseId, UUID postId) {
    requireCourseMember(actor, courseId); Post post = requirePostInCourse(courseId, postId); requireDeletion(actor, courseId, post.authorId());
    posts.remove(postId); comments.values().removeIf(c -> c.postId().equals(postId)); changed();
  }
  public synchronized Comment createComment(UUID actor, UUID courseId, UUID postId, String body) {
    requireCourseMember(actor, courseId); requirePostInCourse(courseId, postId); validateComment(body); Instant now = Instant.now();
    Comment comment = new Comment(UUID.randomUUID(), postId, actor, body, now, now); comments.put(comment.id(), comment); changed(); return comment;
  }
  public List<Comment> listComments(UUID actor, UUID courseId, UUID postId) { requireCourseMember(actor, courseId); requirePostInCourse(courseId, postId); return comments.values().stream().filter(c -> c.postId().equals(postId)).sorted(Comparator.comparing(Comment::createdAt)).toList(); }
  public synchronized Comment editComment(UUID actor, UUID courseId, UUID postId, UUID commentId, String body) {
    requireCourseMember(actor, courseId); requirePostInCourse(courseId, postId); Comment old = requireCommentForPost(postId, commentId); requireAuthor(actor, old.authorId()); validateComment(body);
    Comment updated = new Comment(old.id(), old.postId(), old.authorId(), body, old.createdAt(), Instant.now()); comments.put(commentId, updated); changed(); return updated;
  }
  public synchronized void deleteComment(UUID actor, UUID courseId, UUID postId, UUID commentId) {
    requireCourseMember(actor, courseId); requirePostInCourse(courseId, postId); Comment comment = requireCommentForPost(postId, commentId); requireDeletion(actor, courseId, comment.authorId()); comments.remove(commentId); changed();
  }
  public User author(UUID id) { return requireUser(id); }
  public Role membershipRole(UUID courseId, UUID userId) { Membership value = membership(courseId, userId); return value == null ? null : value.role(); }
  private void requireDeletion(UUID actor, UUID courseId, UUID authorId) {
    if (actor.equals(authorId)) return;
    User author = requireUser(authorId); if (author.role() == Role.student && membershipRole(courseId, actor) == Role.staff) return;
    throw ApiException.forbidden();
  }
  private void requireAuthor(UUID actor, UUID authorId) { if (!actor.equals(authorId)) throw ApiException.forbidden(); }
  private Course requireCourseStaff(UUID actor, UUID courseId) { requireUser(actor); if (membershipRole(courseId, actor) != Role.staff) throw ApiException.hidden(); return requireCourse(courseId); }
  private void requireCourseMember(UUID actor, UUID courseId) { requireUser(actor); if (membership(courseId, actor) == null) throw ApiException.hidden(); }
  private void requireRole(UUID actor, Role role) { if (requireUser(actor).role() != role) throw ApiException.forbidden(); }
  private User requireUser(UUID id) { User user = users.get(id); if (user == null) throw ApiException.unauthenticated(); return user; }
  private Course requireCourse(UUID id) { Course course = courses.get(id); if (course == null) throw ApiException.hidden(); return course; }
  private Post requirePostInCourse(UUID courseId, UUID postId) { Post post = posts.get(postId); if (post == null || !post.courseId().equals(courseId)) throw ApiException.hidden(); return post; }
  private Comment requireCommentForPost(UUID postId, UUID commentId) { Comment comment = comments.get(commentId); if (comment == null || !comment.postId().equals(postId)) throw ApiException.hidden(); return comment; }
  private Material requireMaterialInCourse(UUID courseId, UUID materialId) { Material material = materials.get(materialId); if (material == null || !material.courseId().equals(courseId)) throw ApiException.hidden(); return material; }
  private Membership membership(UUID courseId, UUID userId) { Map<UUID, Membership> group = memberships.get(courseId); return group == null ? null : group.get(userId); }
  private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
  private static String nullable(String value) { return value == null || value.isBlank() ? null : value; }
  private static Role parseRole(String value) { try { return Role.valueOf(value); } catch (Exception e) { throw ApiException.validation("Select student or staff."); } }
  private static void requireUsername(String value) { if (value == null || !value.matches("[A-Za-z0-9_.-]{3,40}")) throw ApiException.validation("Username must be 3-40 letters, numbers, dot, dash, or underscore."); }
  private static void requirePassword(String value) { if (value == null || value.length() < 8 || value.length() > 128) throw ApiException.validation("Password must be 8-128 characters."); }
  private static void requireCourseCode(String value) { if (value == null || value.trim().isEmpty() || value.trim().length() > 32) throw ApiException.validation("Course code is required and must be at most 32 characters."); }
  private static void requireCourseName(String value) { if (value == null || value.trim().isEmpty() || value.trim().length() > 160) throw ApiException.validation("Course name is required and must be at most 160 characters."); }
  private static void validatePost(String title, String description) { if (title == null || title.trim().isEmpty() || title.length() > 120) throw ApiException.validation("Post title is required and must be at most 120 characters."); if (description != null && description.length() > 2000) throw ApiException.validation("Description must be at most 2,000 characters."); }
  private static void validateComment(String value) { if (value == null || value.trim().isEmpty()) throw ApiException.validation("Comment text is required."); }
  private static void validateMaterial(String filename, String contentType, byte[] content) {
    if (filename == null || filename.isBlank() || filename.length() > 255 || filename.chars().anyMatch(Character::isISOControl)) throw ApiException.validation("Choose a valid PDF filename.");
    if (!"application/pdf".equalsIgnoreCase(contentType == null ? "" : contentType.trim())) throw ApiException.validation("This file cannot be used. Upload an unprotected, valid PDF.");
    if (content == null || content.length == 0 || content.length > MAX_MATERIAL_BYTES) throw ApiException.validation(content != null && content.length > MAX_MATERIAL_BYTES ? "This PDF is larger than the 20 MB limit." : "This file cannot be used. Upload an unprotected, valid PDF.");
    String inspection = new String(content, StandardCharsets.ISO_8859_1);
    if (!isStructurallyValidPdf(inspection)) throw ApiException.validation("This file cannot be used. Upload an unprotected, valid PDF.");
    if (pageCount(content) > MAX_MATERIAL_PAGES) throw ApiException.validation("This PDF has more than the 100-page limit.");
  }
  private static int pageCount(byte[] content) { String pdf = new String(content, StandardCharsets.ISO_8859_1); return (int) java.util.regex.Pattern.compile("/Type\\s*/Page(?!s)\\b").matcher(pdf).results().count(); }
  /**
   * Bounded, dependency-free structural gate. It intentionally accepts only a
   * conservative PDF subset: an object graph must contain a declared catalog
   * root, at least one page object, balanced object delimiters, and a terminal
   * trailer/EOF. This rejects PDF-looking arbitrary bytes before they enter the
   * asynchronous processor; it is not an attempt to execute PDF content.
   */
  private static boolean isStructurallyValidPdf(String pdf) {
    if (!pdf.startsWith("%PDF-") || pdf.contains("/Encrypt")) return false;
    Matcher startXref = PDF_START_XREF.matcher(pdf); if (!startXref.find()) return false;
    int eof = pdf.lastIndexOf("%%EOF");
    Map<String, PdfObject> objects = new HashMap<>(); int end = 0;
    Matcher object = PDF_OBJECT.matcher(pdf); while (object.find()) { if (object.start() < end) return false; end = object.end(); String key = object.group(1) + " " + object.group(2); if (objects.putIfAbsent(key, new PdfObject(object.start(), object.group(3))) != null) return false; }
    int xrefOffset; try { xrefOffset = Integer.parseInt(startXref.group(1)); } catch (NumberFormatException e) { return false; }
    XrefValidation xref = validateXref(pdf, xrefOffset, objects); if (xref == null) return false;
    Matcher root = PDF_ROOT.matcher(xref.dictionary()); if (!root.find()) return false;
    String rootKey = root.group(1) + " " + root.group(2);
    PdfObject catalogObject = objects.get(rootKey); String catalog = catalogObject == null ? null : catalogObject.body(); if (catalog == null || !catalog.contains("/Type /Catalog")) return false;
    Matcher pagesReference = PDF_PAGES.matcher(catalog); if (!pagesReference.find()) return false;
    String pagesKey = pagesReference.group(1) + " " + pagesReference.group(2); PdfObject pagesObject = objects.get(pagesKey); String pages = pagesObject == null ? null : pagesObject.body();
    if (pages == null || !pages.contains("/Type /Pages")) return false;
    Matcher kids = PDF_KIDS.matcher(pages); if (!kids.find()) return false;
    Matcher childReference = PDF_REFERENCE.matcher(kids.group(1)); boolean hasConsistentPage = false;
    while (childReference.find()) { String childKey = childReference.group(1) + " " + childReference.group(2); PdfObject childObject = objects.get(childKey); String child = childObject == null ? null : childObject.body(); if (child == null) return false; if (child.contains("/Type /Page") && !child.contains("/Type /Pages") && child.contains("/Parent " + pagesKey + " R")) hasConsistentPage = true; }
    return hasConsistentPage && pageCount(pdf.getBytes(StandardCharsets.ISO_8859_1)) > 0;
  }
  private static XrefValidation validateXref(String pdf, int offset, Map<String, PdfObject> objects) {
    if (offset < 0 || offset >= pdf.length()) return null;
    if (pdf.startsWith("xref", offset)) { if (!hasValidClassicXref(pdf, offset, objects)) return null; int trailer = pdf.indexOf("trailer", offset); if (trailer < 0) return null; return new XrefValidation(pdf.substring(trailer)); }
    PdfObject xrefObject = objects.values().stream().filter(o -> o.offset() == offset).findFirst().orElse(null);
    return xrefObject != null && hasValidXrefStream(xrefObject, objects) ? new XrefValidation(xrefObject.body()) : null;
  }
  /** Supports bounded classic xref tables only; xref streams are intentionally not Stage 4A input. */
  private static boolean hasValidClassicXref(String pdf, int offset, Map<String, PdfObject> objects) {
    if (offset < 0 || offset >= pdf.length() || !pdf.startsWith("xref", offset)) return false;
    int cursor = skipPdfWhitespace(pdf, offset + 4); Set<String> indexed = new HashSet<>();
    while (!pdf.startsWith("trailer", cursor)) {
      int firstStart = cursor; while (cursor < pdf.length() && Character.isDigit(pdf.charAt(cursor))) cursor++; if (firstStart == cursor) return false;
      int first; try { first = Integer.parseInt(pdf.substring(firstStart, cursor)); } catch (NumberFormatException e) { return false; }
      cursor = skipPdfWhitespace(pdf, cursor); int countStart = cursor; while (cursor < pdf.length() && Character.isDigit(pdf.charAt(cursor))) cursor++; if (countStart == cursor) return false;
      int count; try { count = Integer.parseInt(pdf.substring(countStart, cursor)); } catch (NumberFormatException e) { return false; }
      cursor = skipPdfLineEnd(pdf, cursor); if (cursor < 0 || count < 0 || count > objects.size() + 1) return false;
      for (int i = 0; i < count; i++) { int lineEnd = pdf.indexOf('\n', cursor); if (lineEnd < 0) return false; String line = pdf.substring(cursor, lineEnd).replace("\r", ""); Matcher entry = PDF_XREF_ENTRY.matcher(line); if (!entry.matches()) return false; if (entry.group(3).equals("n")) { int generation = Integer.parseInt(entry.group(2)); String key = (first + i) + " " + generation; PdfObject object = objects.get(key); int entryOffset; try { entryOffset = Integer.parseInt(entry.group(1)); } catch (NumberFormatException e) { return false; } if (object == null || object.offset() != entryOffset || !pdf.startsWith((first + i) + " " + generation + " obj", entryOffset) || !indexed.add(key)) return false; } cursor = lineEnd + 1; }
      cursor = skipPdfWhitespace(pdf, cursor);
    }
    return indexed.containsAll(objects.keySet());
  }
  /** Bounded support for direct classic xref streams; object-stream entries fail closed. */
  private static boolean hasValidXrefStream(PdfObject xrefObject, Map<String, PdfObject> objects) {
    String body = xrefObject.body(); if (!body.contains("/Type /XRef")) return false;
    Matcher width = PDF_W.matcher(body), sizeMatcher = PDF_SIZE.matcher(body), lengthMatcher = PDF_LENGTH.matcher(body);
    if (!width.find() || !sizeMatcher.find() || !lengthMatcher.find()) return false;
    int w0, w1, w2, size, length; try { w0=Integer.parseInt(width.group(1)); w1=Integer.parseInt(width.group(2)); w2=Integer.parseInt(width.group(3)); size=Integer.parseInt(sizeMatcher.group(1)); length=Integer.parseInt(lengthMatcher.group(1)); } catch (NumberFormatException e) { return false; }
    if (w0 != 1 || w1 < 1 || w1 > 4 || w2 < 0 || w2 > 2 || size < 1 || size > objects.size() + 1 || length < 0 || length > MAX_MATERIAL_BYTES) return false;
    Matcher index = Pattern.compile("/Index\\s*\\[\\s*0\\s+" + size + "\\s*]").matcher(body); if (body.contains("/Index") && !index.find()) return false;
    int stream = body.indexOf("stream"); if (stream < 0) return false; int dataStart = stream + 6; if (dataStart < body.length() && body.charAt(dataStart) == '\r') dataStart++; if (dataStart < body.length() && body.charAt(dataStart) == '\n') dataStart++; if (dataStart + length > body.length()) return false;
    byte[] bytes = body.substring(dataStart, dataStart + length).getBytes(StandardCharsets.ISO_8859_1); if (body.contains("/FlateDecode")) bytes = inflateXref(bytes); else if (body.contains("/Filter")) return false; if (bytes == null) return false;
    int entryWidth = w0 + w1 + w2; if (entryWidth == 0 || bytes.length != size * entryWidth) return false; Set<String> indexed = new HashSet<>();
    for (int objectNumber = 0, cursor = 0; objectNumber < size; objectNumber++) { long type = readXrefField(bytes, cursor, w0); cursor += w0; long objectOffset = readXrefField(bytes, cursor, w1); cursor += w1; long generation = readXrefField(bytes, cursor, w2); cursor += w2; if (type == 1) { if (objectOffset > Integer.MAX_VALUE || generation > Integer.MAX_VALUE) return false; String key = objectNumber + " " + generation; PdfObject object = objects.get(key); if (object == null || object.offset() != (int)objectOffset || !indexed.add(key)) return false; } else if (type != 0) return false; }
    return indexed.containsAll(objects.keySet());
  }
  private static long readXrefField(byte[] bytes, int offset, int width) { long value = 0; for (int i = 0; i < width; i++) value = (value << 8) | (bytes[offset + i] & 0xffL); return value; }
  private static byte[] inflateXref(byte[] bytes) { try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(bytes)); ByteArrayOutputStream output = new ByteArrayOutputStream()) { byte[] buffer = new byte[512]; int read; while ((read = input.read(buffer)) != -1) { if (output.size() + read > 1_048_576) return null; output.write(buffer, 0, read); } return output.toByteArray(); } catch (Exception e) { return null; } }
  private static int skipPdfWhitespace(String value, int cursor) { while (cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) cursor++; return cursor; }
  private static int skipPdfLineEnd(String value, int cursor) { while (cursor < value.length() && (value.charAt(cursor) == ' ' || value.charAt(cursor) == '\t')) cursor++; if (cursor < value.length() && value.charAt(cursor) == '\r') cursor++; if (cursor < value.length() && value.charAt(cursor) == '\n') return cursor + 1; return -1; }
  private record PdfObject(int offset, String body) {}
  private record XrefValidation(String dictionary) {}
  private static String safeFilename(String filename) { return filename.trim().replaceAll("[\\r\\n]", " "); }
  private static Material copyMaterial(Material m) { return new Material(m.id(), m.courseId(), m.uploaderId(), m.filename(), m.status(), m.failureMessage(), m.content().clone(), m.pageCount(), m.createdAt(), m.updatedAt()); }
  private static KnowledgeMap copyKnowledgeMap(KnowledgeMap map) { return new KnowledgeMap(map.id(), map.courseId(), map.requestedBy(), map.status(), map.content(), map.failureMessage(), List.copyOf(map.sourceMaterialIds()), map.reviewedBy(), map.reviewedAt(), map.createdAt(), map.updatedAt()); }
  private List<Material> activeMaterials(UUID courseId) { return materials.values().stream().filter(m -> m.courseId().equals(courseId) && m.status() == MaterialStatus.active).sorted(Comparator.comparing(Material::createdAt)).map(PlatformService::copyMaterial).toList(); }
  private KnowledgeMap requireReviewableKnowledgeMap(UUID courseId) { KnowledgeMap map = knowledgeMaps.get(courseId); if (map == null || map.status() != KnowledgeMapStatus.pending_review) throw ApiException.validation("Only a completed Knowledge Map awaiting review can be approved or rejected."); return map; }
  private void markKnowledgeMapStale(UUID courseId) {
    KnowledgeMap old = knowledgeMaps.get(courseId);
    if (old == null || old.status() == KnowledgeMapStatus.stale) return;
    knowledgeMaps.put(courseId, new KnowledgeMap(old.id(), old.courseId(), old.requestedBy(), KnowledgeMapStatus.stale, old.content(), null,
        old.sourceMaterialIds(), old.reviewedBy(), old.reviewedAt(), old.createdAt(), Instant.now()));
  }
  private static String sourcePacket(List<Material> sources) {
    StringBuilder packet = new StringBuilder();
    for (Material source : sources) {
      List<ExtractedPage> pages = extractPdfPages(source.content());
      for (ExtractedPage page : pages) packet.append("[SOURCE filename=").append(source.filename()).append(" page=").append(page.number()).append("]\n")
          .append(page.text().isBlank() ? "(No machine-readable text was extracted from this page.)" : page.text()).append("\n[/SOURCE]\n");
      if (packet.length() > 120_000) throw ApiException.validation("The active PDFs are too large to map together. Reduce the course material set and try again.");
    }
    return packet.toString();
  }
  /** Extracts bounded literal text by PDF page; PDF bytes are never executed or treated as instructions. */
  private static List<ExtractedPage> extractPdfPages(byte[] content) {
    String pdf = new String(content, StandardCharsets.ISO_8859_1); Map<String, String> objects = new LinkedHashMap<>(); Matcher object = PDF_OBJECT.matcher(pdf);
    while (object.find()) objects.put(object.group(1) + " " + object.group(2), object.group(3));
    List<ExtractedPage> pages = new ArrayList<>(); int number = 1;
    for (String body : objects.values()) {
      if (!PDF_PAGE.matcher(body).find()) continue;
      StringBuilder pageText = new StringBuilder(); Matcher contents = PDF_CONTENTS.matcher(body);
      if (contents.find()) {
        String references = contents.group(1) == null ? contents.group(2) + " " + contents.group(3) + " R" : contents.group(1);
        Matcher reference = PDF_REFERENCE.matcher(references);
        while (reference.find()) pageText.append(extractPdfText(objects.getOrDefault(reference.group(1) + " " + reference.group(2), ""))).append(' ');
      }
      pages.add(new ExtractedPage(number++, pageText.toString().trim()));
    }
    if (pages.isEmpty()) return List.of(new ExtractedPage(1, extractPdfText(pdf)));
    if (pages.size() == 1 && pages.getFirst().text().isBlank()) return List.of(new ExtractedPage(1, extractPdfText(pdf)));
    return List.copyOf(pages);
  }
  private static String extractPdfText(String pdf) {
    StringBuilder text = new StringBuilder(); Matcher matcher = PDF_TEXT.matcher(pdf);
    while (matcher.find() && text.length() < 24_000) {
      String literal = matcher.group(); literal = literal.substring(1, literal.lastIndexOf(')')).replaceAll("\\\\([()\\\\])", "$1");
      text.append(literal).append(' ');
    }
    return text.toString().replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").trim();
  }
  private record ExtractedPage(int number, String text) {}
  private void completeKnowledgeMap(UUID mapId, UUID courseId, List<UUID> sourceIds, String sourcePacket) {
    KnowledgeMapGenerator generator;
    synchronized (this) {
      KnowledgeMap current = knowledgeMaps.get(courseId);
      if (current == null || !current.id().equals(mapId) || current.status() != KnowledgeMapStatus.generating) return;
      if (!activeMaterials(courseId).stream().map(Material::id).toList().equals(sourceIds)) { markKnowledgeMapStale(courseId); changed(); return; }
      generator = knowledgeMapGenerator;
    }
    SoCLaaSClient.Result result = generator.generate(sourcePacket);
    synchronized (this) {
      KnowledgeMap current = knowledgeMaps.get(courseId);
      if (current == null || !current.id().equals(mapId) || current.status() != KnowledgeMapStatus.generating) return;
      List<Material> currentSources = activeMaterials(courseId);
      if (!currentSources.stream().map(Material::id).toList().equals(sourceIds)) { markKnowledgeMapStale(courseId); changed(); return; }
      Instant now = Instant.now();
      if (result.succeeded() && hasValidMapCitations(result.text(), currentSources)) {
        knowledgeMaps.put(courseId, new KnowledgeMap(current.id(), current.courseId(), current.requestedBy(), KnowledgeMapStatus.pending_review,
            result.text(), null, current.sourceMaterialIds(), null, null, current.createdAt(), now));
      } else {
        knowledgeMaps.put(courseId, failedKnowledgeMap(current, result.succeeded() ? "The staff LLM response did not contain valid citations for every active PDF." : knowledgeMapFailure(result.failure())));
      }
      changed();
    }
  }
  private static KnowledgeMap failedKnowledgeMap(KnowledgeMap current, String failure) { return new KnowledgeMap(current.id(), current.courseId(), current.requestedBy(), KnowledgeMapStatus.failed, current.content(), failure, current.sourceMaterialIds(), null, null, current.createdAt(), Instant.now()); }
  private static boolean hasValidMapCitations(String content, List<Material> sources) {
    if (content == null || content.isBlank()) return false;
    for (Material source : sources) {
      Set<Integer> extractedPages = extractPdfPages(source.content()).stream().filter(page -> !page.text().isBlank()).map(ExtractedPage::number).collect(java.util.stream.Collectors.toSet());
      Pattern citation = Pattern.compile(Pattern.quote(source.filename()) + "\\s*,\\s*p(?:age)?\\.?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
      Matcher match = citation.matcher(content); boolean valid = false;
      while (match.find()) { int page = Integer.parseInt(match.group(1)); if (extractedPages.contains(page)) { valid = true; break; } }
      if (!valid) return false;
    }
    return true;
  }
  private static String knowledgeMapFailure(SoCLaaSClient.Failure failure) {
    return switch (failure) {
      case configuration -> "The staff LLM service is not configured correctly. Check the server configuration.";
      case rate_limited -> "The staff LLM service is busy. Try the manual update again shortly.";
      case temporarily_unavailable -> "The staff LLM service is temporarily unavailable. Try the manual update again later.";
      case rejected -> "The staff LLM service could not create a Knowledge Map. Review the PDFs and try again.";
    };
  }
  private void enforceRetryLimit(UUID actor, UUID courseId) { String key = actor + ":" + courseId; Instant cutoff = Instant.now().minusSeconds(60); Deque<Instant> attempts = materialRetries.computeIfAbsent(key, ignored -> new ArrayDeque<>()); while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) attempts.removeFirst(); if (attempts.size() >= 3) throw ApiException.rateLimited("You can submit up to 3 retries per minute for this course. Try again shortly."); attempts.addLast(Instant.now()); }
  private void scheduleProcessing(UUID materialId) { materialProcessor.schedule(() -> completeProcessing(materialId), 25, TimeUnit.MILLISECONDS); }
  private synchronized void completeProcessing(UUID materialId) { Material old = materials.get(materialId); if (old == null || old.status() != MaterialStatus.processing) return; String pdf = new String(old.content(), StandardCharsets.ISO_8859_1); boolean extractable = pdf.contains("BT") && pdf.contains("ET"); MaterialStatus status = extractable ? MaterialStatus.active : MaterialStatus.failed; String failure = extractable ? null : "This PDF could not be processed. Retry later."; materials.put(old.id(), new Material(old.id(), old.courseId(), old.uploaderId(), old.filename(), status, failure, old.content(), old.pageCount(), old.createdAt(), Instant.now())); changed(); }
}
