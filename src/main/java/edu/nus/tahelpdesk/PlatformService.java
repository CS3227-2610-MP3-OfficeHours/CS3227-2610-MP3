package edu.nus.tahelpdesk;

import static edu.nus.tahelpdesk.Domain.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  private final Map<UUID, List<PdfPage>> materialPages = new ConcurrentHashMap<>();
  /** Candidate and approved slots are keyed by material, never by course. */
  private final Map<UUID, KnowledgeMap> candidates = new ConcurrentHashMap<>();
  private final Map<UUID, KnowledgeMap> approvedMaps = new ConcurrentHashMap<>();
  private final Map<UUID, KnowledgeMapJob> knowledgeMapJobs = new ConcurrentHashMap<>();
  private final Map<String, Deque<Instant>> materialRetries = new ConcurrentHashMap<>();
  private final Map<String, Deque<Instant>> studentAiRequests = new ConcurrentHashMap<>();
  private static final int MAX_MATERIAL_BYTES = 20 * 1024 * 1024;
  private static final int MAX_ACTIVE_MATERIALS = 20;
  private static final int MAX_STUDENT_AI_REQUESTS_PER_MINUTE = 5;
  private final ScheduledExecutorService materialProcessor = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "material-processor"); thread.setDaemon(true); return thread;
  });
  /** Bounded local queue: callers are never parked waiting on provider capacity. */
  private final ThreadPoolExecutor knowledgeMapProcessor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<>(16), r -> { Thread thread = new Thread(r, "knowledge-map-processor"); thread.setDaemon(true); return thread; }, new ThreadPoolExecutor.AbortPolicy());
  private final PasswordHasher passwords = new PasswordHasher();
  private final Runnable afterMutation;
  private volatile KnowledgeMapGenerator knowledgeMapGenerator;
  private volatile boolean knowledgeMapFallbackConfigured;
  private volatile StudentAiService.Provider studentAiProvider;

  @FunctionalInterface interface KnowledgeMapGenerator { SoCLaaSClient.Result generate(String sourcePacket); }

  public PlatformService() { this(() -> {}); }
  /** Package-private so the PostgreSQL adapter can load state before serving requests. */
  PlatformService(Runnable afterMutation) { this.afterMutation = afterMutation; }
  synchronized boolean isEmpty() { return users.isEmpty(); }
  synchronized void restore(Collection<User> loadedUsers, Collection<Course> loadedCourses,
      Collection<Membership> loadedMemberships, Collection<Post> loadedPosts, Collection<Comment> loadedComments, Collection<Material> loadedMaterials, Collection<PdfPage> loadedPages, Collection<KnowledgeMap> loadedCandidates, Collection<KnowledgeMap> loadedApproved, Collection<KnowledgeMapJob> loadedJobs) {
    for (User user : loadedUsers) { users.put(user.id(), user); userByName.put(normalize(user.username()), user.id()); }
    for (Course course : loadedCourses) { courses.put(course.id(), course); courseByCode.put(course.code(), course.id()); memberships.put(course.id(), new ConcurrentHashMap<>()); }
    for (Membership membership : loadedMemberships) memberships.computeIfAbsent(membership.courseId(), ignored -> new ConcurrentHashMap<>()).put(membership.userId(), membership);
    for (Post post : loadedPosts) posts.put(post.id(), post);
    for (Comment comment : loadedComments) comments.put(comment.id(), comment);
    for (Material material : loadedMaterials) materials.put(material.id(), copyMaterial(material));
    for (PdfPage page : loadedPages) materialPages.computeIfAbsent(page.materialId(), ignored -> new ArrayList<>()).add(page);
    for (KnowledgeMap map : loadedCandidates) candidates.put(map.materialId(), copyKnowledgeMap(map));
    for (KnowledgeMap map : loadedApproved) approvedMaps.put(map.materialId(), copyKnowledgeMap(map));
    for (KnowledgeMapJob job : loadedJobs) knowledgeMapJobs.put(job.materialId(), copyKnowledgeMapJob(job));
  }
  synchronized Snapshot snapshot() { return new Snapshot(List.copyOf(users.values()), List.copyOf(courses.values()), memberships.values().stream().flatMap(m -> m.values().stream()).toList(), List.copyOf(posts.values()), List.copyOf(comments.values()), materials.values().stream().map(PlatformService::copyMaterial).toList(), materialPages.values().stream().flatMap(Collection::stream).toList(), candidates.values().stream().map(PlatformService::copyKnowledgeMap).toList(), approvedMaps.values().stream().map(PlatformService::copyKnowledgeMap).toList(), knowledgeMapJobs.values().stream().map(PlatformService::copyKnowledgeMapJob).toList()); }
  record Snapshot(List<User> users, List<Course> courses, List<Membership> memberships, List<Post> posts, List<Comment> comments, List<Material> materials, List<PdfPage> pdfPages, List<KnowledgeMap> candidates, List<KnowledgeMap> approvedMaps, List<KnowledgeMapJob> knowledgeMapJobs) {}
  private void changed() { afterMutation.run(); }

  /** The gateway client is installed only by the application bootstrap, never by a browser request. */
  void setKnowledgeMapGenerator(KnowledgeMapGenerator generator) { setKnowledgeMapGenerator(generator, false); }
  void setKnowledgeMapGenerator(KnowledgeMapGenerator generator, boolean fallbackConfigured) { knowledgeMapGenerator = generator; knowledgeMapFallbackConfigured = generator != null && fallbackConfigured; }
  /** The student gateway path is installed only by the application bootstrap, never by a browser request. */
  void setStudentAiProvider(StudentAiService.Provider provider) { studentAiProvider = provider; }
  boolean knowledgeMapFallbackConfigured(UUID actor, UUID courseId) { requireCourseStaff(actor, courseId); return knowledgeMapGenerator != null && knowledgeMapFallbackConfigured; }

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
    materialPages.entrySet().removeIf(entry -> entry.getValue().stream().anyMatch(page -> page.courseId().equals(courseId)));
    candidates.entrySet().removeIf(entry -> entry.getValue().courseId().equals(courseId)); approvedMaps.entrySet().removeIf(entry -> entry.getValue().courseId().equals(courseId)); knowledgeMapJobs.entrySet().removeIf(entry -> entry.getValue().courseId().equals(courseId));
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
    return materials.values().stream().filter(m -> m.courseId().equals(courseId) && (m.status() == MaterialStatus.active || m.status() == MaterialStatus.resource_only))
        .sorted(Comparator.comparing(Material::createdAt).reversed()).map(PlatformService::copyMaterial).toList();
  }
  public synchronized Material uploadMaterial(UUID actor, UUID courseId, String filename, String contentType, byte[] content) {
    requireCourseStaff(actor, courseId); PdfPageExtractor.Result inspected = validateMaterial(filename, contentType, content);
    long existing = materials.values().stream().filter(m -> m.courseId().equals(courseId) &&
        (m.status() == MaterialStatus.active || m.status() == MaterialStatus.resource_only || m.status() == MaterialStatus.processing)).count();
    if (existing >= MAX_ACTIVE_MATERIALS) throw ApiException.conflict("This course already has 20 active PDFs. Delete one before uploading another.");
    int pages = inspected.pageCount();
    Instant now = Instant.now();
    Material material = new Material(UUID.randomUUID(), courseId, actor, safeFilename(filename), MaterialStatus.processing,
        null, content.clone(), pages, now, now);
    materials.put(material.id(), material); changed(); scheduleProcessing(material.id()); return copyMaterial(material);
  }
  public synchronized void deleteMaterial(UUID actor, UUID courseId, UUID materialId) {
    requireCourseStaff(actor, courseId); Material old = requireMaterialInCourse(courseId, materialId);
    if (old.status() == MaterialStatus.deleted) throw ApiException.hidden();
    materialPages.remove(old.id());
    materials.put(old.id(), new Material(old.id(), old.courseId(), old.uploaderId(), old.filename(), MaterialStatus.deleted,
        null, old.content(), old.pageCount(), old.createdAt(), Instant.now())); removeMaterialMaps(old.id()); changed();
  }
  public synchronized Material retryMaterial(UUID actor, UUID courseId, UUID materialId) {
    requireCourseStaff(actor, courseId); Material old = requireMaterialInCourse(courseId, materialId);
    if (old.status() != MaterialStatus.failed) throw ApiException.validation("Only failed PDF processing can be retried.");
    enforceRetryLimit(actor, courseId);
    materialPages.remove(old.id());
    Material retried = new Material(old.id(), old.courseId(), old.uploaderId(), old.filename(), MaterialStatus.processing,
        null, old.content(), old.pageCount(), old.createdAt(), Instant.now()); materials.put(old.id(), retried); removeMaterialMaps(old.id()); changed(); scheduleProcessing(retried.id()); return copyMaterial(retried);
  }
  /** Staff-only per-material state. Students deliberately have no equivalent endpoint yet. */
  public synchronized List<Map<String, Object>> listKnowledgeMaps(UUID actor, UUID courseId) {
    requireCourseStaff(actor, courseId);
    return listMaterials(actor, courseId).stream().map(material -> { Map<String,Object> row = new LinkedHashMap<>(); row.put("material", copyMaterial(material)); row.put("candidate", nullableMap(candidates.get(material.id()))); row.put("approved", nullableMap(approvedMaps.get(material.id()))); row.put("job", nullableJob(knowledgeMapJobs.get(material.id()))); return row; }).toList();
  }
  private static Object nullableMap(KnowledgeMap map) { return map == null ? null : copyKnowledgeMap(map); }
  private static Object nullableJob(KnowledgeMapJob job) { return job == null ? null : copyKnowledgeMapJob(job); }
  public synchronized KnowledgeMapJob generateKnowledgeMap(UUID actor, UUID courseId, UUID materialId) {
    requireCourseStaff(actor, courseId); if (knowledgeMapGenerator == null) throw ApiException.validation("Staff LLM generation is not configured on this server.");
    Material source = requireMaterialInCourse(courseId, materialId);
    if (source.status() != MaterialStatus.active) throw ApiException.validation("This PDF is not text-ready for Knowledge Map generation.");
    KnowledgeMapJob prior = knowledgeMapJobs.get(materialId); if (prior != null && (prior.status() == KnowledgeMapJobStatus.queued || prior.status() == KnowledgeMapJobStatus.generating)) throw ApiException.conflict("A Knowledge Map update is already running for this PDF.");
    String digest = PdfPageExtractor.digest(source.content()); String packet = sourcePacket(source);
    Instant now = Instant.now(); KnowledgeMapJob job = new KnowledgeMapJob(UUID.randomUUID(), courseId, materialId, digest, actor, KnowledgeMapJobStatus.queued, null, null, now, now);
    knowledgeMapJobs.put(materialId, job); changed();
    try { knowledgeMapProcessor.execute(() -> completeKnowledgeMap(job.id(), courseId, materialId, digest, packet)); }
    catch (RuntimeException e) { knowledgeMapJobs.put(materialId, failedJob(job, "The local Knowledge Map queue is full. This PDF was not queued; try again shortly.")); changed(); }
    return copyKnowledgeMapJob(job);
  }
  public synchronized List<KnowledgeMapJob> generateAllKnowledgeMaps(UUID actor, UUID courseId) {
    requireCourseStaff(actor, courseId); List<KnowledgeMapJob> jobs = new ArrayList<>();
    for (Material material : activeMaterials(courseId)) { KnowledgeMapJob prior = knowledgeMapJobs.get(material.id()); if (prior != null && (prior.status() == KnowledgeMapJobStatus.queued || prior.status() == KnowledgeMapJobStatus.generating)) jobs.add(copyKnowledgeMapJob(prior)); else jobs.add(generateKnowledgeMap(actor, courseId, material.id())); }
    return List.copyOf(jobs);
  }
  public synchronized KnowledgeMap approveKnowledgeMap(UUID actor, UUID courseId, UUID materialId) {
    requireCourseStaff(actor, courseId); KnowledgeMap candidate = requireReviewableKnowledgeMap(courseId, materialId);
    if (candidate.verificationMessage() != null || !sourceCurrent(candidate)) throw ApiException.validation("This Knowledge Map is unverified or its source changed and cannot be approved.");
    Instant now = Instant.now(); KnowledgeMap approved = new KnowledgeMap(candidate.id(), candidate.courseId(), candidate.materialId(), candidate.materialDigest(), candidate.requestedBy(), KnowledgeMapStatus.approved, candidate.content(), null, actor, now, candidate.createdAt(), now);
    approvedMaps.put(materialId, approved); candidates.remove(materialId); changed(); return copyKnowledgeMap(approved);
  }
  public synchronized void rejectKnowledgeMap(UUID actor, UUID courseId, UUID materialId) { requireCourseStaff(actor, courseId); requireReviewableKnowledgeMap(courseId, materialId); candidates.remove(materialId); changed(); }
  public Material downloadResource(UUID actor, UUID courseId, UUID materialId) {
    requireCourseMember(actor, courseId); Material material = requireMaterialInCourse(courseId, materialId);
    if (material.status() != MaterialStatus.active && material.status() != MaterialStatus.resource_only) throw ApiException.hidden();
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
  /**
   * Student AI help for one course-scoped question.
   *
   * <p>Authorization, question validation, and the local rate limit all run
   * before any gateway call. Only active, text-ready PDFs of this course are
   * eligible, and an approved Knowledge Map is attached only while its stored
   * digest still matches the current PDF bytes, so a pending, rejected,
   * unverified, or stale map cannot influence retrieval.
   */
  public StudentAiService.Answer studentAiHelp(UUID actor, UUID courseId, String question) {
    requireEnrolledStudent(actor, courseId);
    String cleaned = requireQuestion(question);
    enforceStudentAiLimit(actor, courseId);
    StudentAiService.Provider provider = studentAiProvider;
    if (provider == null) throw ApiException.unavailable(
        "AI help is unavailable right now. The forum and course resources still work - try again later.");
    try { return StudentAiService.answer(cleaned, studentAiCandidates(courseId), provider); }
    catch (StudentAiService.Unanswerable e) { throw e.api; }
  }

  /** Lets the student UI hide the tab when the server has no student gateway configured. */
  public boolean studentAiConfigured(UUID actor, UUID courseId) {
    requireEnrolledStudent(actor, courseId); return studentAiProvider != null;
  }

  /** Staff are deliberately refused with the same response as a non-member. */
  private void requireEnrolledStudent(UUID actor, UUID courseId) {
    if (requireUser(actor).role() != Role.student) throw ApiException.hidden();
    if (membershipRole(courseId, actor) != Role.student) throw ApiException.hidden();
    requireCourse(courseId);
  }

  private static String requireQuestion(String question) {
    String cleaned = question == null ? "" : question.trim();
    if (cleaned.isEmpty()) throw ApiException.validation("Enter a question first.");
    if (cleaned.length() > StudentAiService.MAX_QUESTION_CHARS)
      throw ApiException.validation("Shorten your question to " + StudentAiService.MAX_QUESTION_CHARS + " characters or fewer.");
    return cleaned;
  }

  private void enforceStudentAiLimit(UUID actor, UUID courseId) {
    String key = actor + ":" + courseId; Instant cutoff = Instant.now().minusSeconds(60);
    Deque<Instant> attempts = studentAiRequests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
    synchronized (attempts) {
      while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) attempts.removeFirst();
      if (attempts.size() >= MAX_STUDENT_AI_REQUESTS_PER_MINUTE) throw ApiException.rateLimited(
          "You have asked a few questions in quick succession. Wait a moment and try again.");
      attempts.addLast(Instant.now());
    }
  }

  /** Snapshot of eligible evidence, read at request time so revoked sources drop out immediately. */
  private synchronized List<StudentAiService.Candidate> studentAiCandidates(UUID courseId) {
    List<StudentAiService.Candidate> candidates = new ArrayList<>();
    for (Material material : activeMaterials(courseId)) {
      List<StudentAiService.SourcePage> pages = materialPages.getOrDefault(material.id(), List.of()).stream()
          .filter(page -> page.courseId().equals(courseId) && page.invalidatedAt() == null && !page.text().isBlank())
          .sorted(Comparator.comparingInt(PdfPage::pageNumber))
          .map(page -> new StudentAiService.SourcePage(material.id(), material.filename(), page.pageNumber(), page.text()))
          .toList();
      if (pages.isEmpty()) continue;
      KnowledgeMap approved = approvedMaps.get(material.id());
      String mapText = approved != null && approved.status() == KnowledgeMapStatus.approved
          && approved.verificationMessage() == null && sourceCurrent(approved) ? approved.content() : null;
      candidates.add(new StudentAiService.Candidate(material.id(), material.filename(), mapText, pages));
    }
    return List.copyOf(candidates);
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
  private static PdfPageExtractor.Result validateMaterial(String filename, String contentType, byte[] content) {
    if (filename == null || filename.isBlank() || filename.length() > 255 || filename.chars().anyMatch(Character::isISOControl)) throw ApiException.validation("Choose a valid PDF filename.");
    if (!"application/pdf".equalsIgnoreCase(contentType == null ? "" : contentType.trim())) throw ApiException.validation("This file cannot be used. Upload an unprotected, valid PDF.");
    if (content == null || content.length == 0 || content.length > MAX_MATERIAL_BYTES) throw ApiException.validation(content != null && content.length > MAX_MATERIAL_BYTES ? "This PDF is larger than the 20 MB limit." : "This file cannot be used. Upload an unprotected, valid PDF.");
    String header = new String(content, 0, Math.min(content.length, 4096), StandardCharsets.ISO_8859_1);
    String trailer = new String(content, Math.max(0, content.length - 4096), Math.min(content.length, 4096), StandardCharsets.ISO_8859_1);
    if (!header.startsWith("%PDF-") || header.contains("/Encrypt") || trailer.contains("/Encrypt") || !trailer.contains("startxref") || !trailer.contains("%%EOF")) throw ApiException.validation("This file cannot be used. Upload an unprotected, valid PDF.");
    PdfPageExtractor.Result inspected = PdfPageExtractor.inspect(content);
    if (!inspected.succeeded()) {
      if ("page_limit".equals(inspected.failureCode())) throw ApiException.validation("This PDF has more than the 100-page limit.");
      if ("encrypted".equals(inspected.failureCode())) throw ApiException.validation("This PDF is encrypted or password-protected. Upload an unprotected PDF.");
      throw ApiException.validation("This file cannot be used. Upload an unprotected, valid PDF.");
    }
    return inspected;
  }
  private static String safeFilename(String filename) { return filename.trim().replaceAll("[\\r\\n]", " "); }
  private static Material copyMaterial(Material material) { return new Material(material.id(), material.courseId(), material.uploaderId(), material.filename(), material.status(), material.failureMessage(), material.content().clone(), material.pageCount(), material.createdAt(), material.updatedAt()); }
  private static KnowledgeMap copyKnowledgeMap(KnowledgeMap map) { return new KnowledgeMap(map.id(), map.courseId(), map.materialId(), map.materialDigest(), map.requestedBy(), map.status(), map.content(), map.verificationMessage(), map.reviewedBy(), map.reviewedAt(), map.createdAt(), map.updatedAt()); }
  private static KnowledgeMapJob copyKnowledgeMapJob(KnowledgeMapJob job) { return new KnowledgeMapJob(job.id(), job.courseId(), job.materialId(), job.materialDigest(), job.requestedBy(), job.status(), job.failureMessage(), job.outputMapId(), job.createdAt(), job.updatedAt()); }
  private List<Material> activeMaterials(UUID courseId) { return materials.values().stream().filter(material -> material.courseId().equals(courseId) && material.status() == MaterialStatus.active).sorted(Comparator.comparing(Material::createdAt)).map(PlatformService::copyMaterial).toList(); }
  private KnowledgeMap requireReviewableKnowledgeMap(UUID courseId, UUID materialId) { KnowledgeMap map = candidates.get(materialId); if (map == null || !map.courseId().equals(courseId)) throw ApiException.validation("Only a completed Knowledge Map awaiting review can be approved or rejected."); return map; }
  private void removeMaterialMaps(UUID materialId) { candidates.remove(materialId); approvedMaps.remove(materialId); knowledgeMapJobs.remove(materialId); }
  private boolean sourceCurrent(KnowledgeMap map) { Material material = materials.get(map.materialId()); return material != null && material.courseId().equals(map.courseId()) && material.status() == MaterialStatus.active && PdfPageExtractor.digest(material.content()).equals(map.materialDigest()); }
  private String sourcePacket(Material source) {
    StringBuilder packet = new StringBuilder();
    List<PdfPage> pages = materialPages.getOrDefault(source.id(), List.of());
    if (pages.isEmpty()) throw ApiException.validation("This PDF is not ready for page-aware text extraction. Wait for processing and try again.");
    for (PdfPage page : pages) packet.append("[SOURCE filename=").append(source.filename()).append(" page=").append(page.pageNumber()).append("]\n").append(page.text()).append("\n[/SOURCE]\n");
    if (packet.length() > 120_000) throw ApiException.validation("This PDF is too large to map safely. Export a smaller PDF and try again.");
    return packet.toString();
  }
  private void completeKnowledgeMap(UUID jobId, UUID courseId, UUID materialId, String digest, String sourcePacket) {
    KnowledgeMapGenerator generator;
    synchronized (this) {
      KnowledgeMapJob current = knowledgeMapJobs.get(materialId); if (current == null || !current.id().equals(jobId)) return;
      if (!sourceCurrent(current)) { knowledgeMapJobs.put(materialId, cancelledJob(current)); changed(); return; }
      knowledgeMapJobs.put(materialId, new KnowledgeMapJob(current.id(), current.courseId(), current.materialId(), current.materialDigest(), current.requestedBy(), KnowledgeMapJobStatus.generating, null, null, current.createdAt(), Instant.now()));
      generator = knowledgeMapGenerator;
    }
    SoCLaaSClient.Result result = generator.generate(sourcePacket);
    synchronized (this) {
      KnowledgeMapJob current = knowledgeMapJobs.get(materialId); if (current == null || !current.id().equals(jobId)) return;
      if (!sourceCurrent(current)) { knowledgeMapJobs.put(materialId, cancelledJob(current)); changed(); return; }
      Instant now = Instant.now();
      if (result.succeeded()) {
        Material source = materials.get(materialId); String verification = hasValidCitation(result.text(), source) ? null : "Unverified: missing valid support from " + source.filename() + ". This returned map is staff-only and cannot be approved for student AI navigation.";
        KnowledgeMap candidate = new KnowledgeMap(UUID.randomUUID(), courseId, materialId, digest, current.requestedBy(), KnowledgeMapStatus.pending_review, result.text(), verification, null, null, now, now);
        candidates.put(materialId, candidate); knowledgeMapJobs.put(materialId, new KnowledgeMapJob(current.id(), courseId, materialId, digest, current.requestedBy(), KnowledgeMapJobStatus.completed, null, candidate.id(), current.createdAt(), now));
      } else {
        knowledgeMapJobs.put(materialId, failedJob(current, knowledgeMapFailure(result.failure())));
      }
      changed();
    }
  }
  private boolean sourceCurrent(KnowledgeMapJob job) { Material material = materials.get(job.materialId()); return material != null && material.courseId().equals(job.courseId()) && material.status() == MaterialStatus.active && PdfPageExtractor.digest(material.content()).equals(job.materialDigest()); }
  private static KnowledgeMapJob failedJob(KnowledgeMapJob current, String failure) { return new KnowledgeMapJob(current.id(), current.courseId(), current.materialId(), current.materialDigest(), current.requestedBy(), KnowledgeMapJobStatus.failed, failure, null, current.createdAt(), Instant.now()); }
  private static KnowledgeMapJob cancelledJob(KnowledgeMapJob current) { return new KnowledgeMapJob(current.id(), current.courseId(), current.materialId(), current.materialDigest(), current.requestedBy(), KnowledgeMapJobStatus.cancelled_due_to_source_change, "This PDF changed or was removed before generation completed.", null, current.createdAt(), Instant.now()); }
  private boolean hasValidCitation(String content, Material source) {
    if (content == null || content.isBlank() || source == null) return false;
    Set<Integer> pages = materialPages.getOrDefault(source.id(), List.of()).stream().filter(page -> !page.text().isBlank() && page.invalidatedAt() == null).map(PdfPage::pageNumber).collect(java.util.stream.Collectors.toSet());
    Pattern canonical = Pattern.compile("\\(" + Pattern.quote(source.filename()) + ", p\\. (\\d+)\\)", Pattern.CASE_INSENSITIVE);
    Pattern anyPdf = Pattern.compile("[A-Za-z0-9_. -]+\\.pdf\\s*,?\\s*p(?:age)?\\.?\\s*\\d+", Pattern.CASE_INSENSITIVE);
    for (String line : content.split("\\R")) { String trimmed=line.trim(); if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
      Matcher cited=canonical.matcher(trimmed); int end=-1; while(cited.find()) { if(!pages.contains(Integer.parseInt(cited.group(1)))) return false; end=cited.end(); }
      if(end < 0 || end != trimmed.length() || anyPdf.matcher(trimmed).find() && !trimmed.contains(source.filename())) return false;
    }
    return true;
  }
  private static String knowledgeMapFailure(SoCLaaSClient.Failure failure) {
    return switch (failure) {
      case configuration -> "The staff LLM service is not configured correctly. Check the server configuration.";
      case rate_limited -> "The staff LLM service is busy. Try the manual update again shortly.";
      case temporarily_unavailable -> "The staff LLM service is temporarily unavailable. Try again later.";
      case rejected -> "The staff LLM service could not create a Knowledge Map. Review the PDFs and try again.";
    };
  }
  private void enforceRetryLimit(UUID actor, UUID courseId) { String key = actor + ":" + courseId; Instant cutoff = Instant.now().minusSeconds(60); Deque<Instant> attempts = materialRetries.computeIfAbsent(key, ignored -> new ArrayDeque<>()); while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) attempts.removeFirst(); if (attempts.size() >= 3) throw ApiException.rateLimited("You can submit up to 3 retries per minute for this course. Try again shortly."); attempts.addLast(Instant.now()); }
  private void scheduleProcessing(UUID materialId) { materialProcessor.schedule(() -> completeProcessing(materialId), 25, TimeUnit.MILLISECONDS); }
  private synchronized void completeProcessing(UUID materialId) {
    Material old = materials.get(materialId); if (old == null || old.status() != MaterialStatus.processing) return;
    PdfPageExtractor.Result extraction = PdfPageExtractor.extract(old.content()); Instant now = Instant.now();
    if (!extraction.succeeded()) {
      materials.put(old.id(), new Material(old.id(), old.courseId(), old.uploaderId(), old.filename(), MaterialStatus.failed,
          safeExtractionFailure(extraction.failureCode()), old.content(), old.pageCount(), old.createdAt(), now));
      materialPages.remove(old.id()); changed(); return;
    }
    String materialDigest = PdfPageExtractor.digest(old.content()); List<PdfPage> pages = new ArrayList<>();
    for (int index = 0; index < extraction.pages().size(); index++) { String text = extraction.pages().get(index); pages.add(new PdfPage(old.id(), old.courseId(), materialDigest, index + 1, text, PdfPageExtractor.digestText(text), PdfPageExtractor.PARSER_VERSION, "ready", null, now, null)); }
    materialPages.put(old.id(), List.copyOf(pages));
    MaterialStatus status = extraction.hasText() ? MaterialStatus.active : MaterialStatus.resource_only;
    materials.put(old.id(), new Material(old.id(), old.courseId(), old.uploaderId(), old.filename(), status, null, old.content(), extraction.pageCount(), old.createdAt(), now)); changed();
  }
  private static String safeExtractionFailure(String code) {
    return switch (code) {
      case "encrypted" -> "This PDF is encrypted or password-protected. Upload an unprotected PDF.";
      case "page_limit" -> "This PDF has more than the 100-page limit.";
      case "text_limit" -> "This PDF contains too much selectable text to process safely.";
      case "timeout" -> "This PDF took too long to process safely. Try exporting it again.";
      default -> "This PDF could not be processed. It may be malformed or too complex to extract.";
    };
  }
}
