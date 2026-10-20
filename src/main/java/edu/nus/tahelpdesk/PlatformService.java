package edu.nus.tahelpdesk;

import static edu.nus.tahelpdesk.Domain.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory local adapter. All access methods re-check current memberships. */
public class PlatformService {
  private final Map<UUID, User> users = new ConcurrentHashMap<>();
  private final Map<String, UUID> userByName = new ConcurrentHashMap<>();
  private final Map<UUID, Course> courses = new ConcurrentHashMap<>();
  private final Map<String, UUID> courseByCode = new ConcurrentHashMap<>();
  private final Map<UUID, Map<UUID, Membership>> memberships = new ConcurrentHashMap<>();
  private final Map<UUID, Post> posts = new ConcurrentHashMap<>();
  private final Map<UUID, Comment> comments = new ConcurrentHashMap<>();
  private final PasswordHasher passwords = new PasswordHasher();
  private final Runnable afterMutation;

  public PlatformService() { this(() -> {}); }
  /** Package-private so the PostgreSQL adapter can load state before serving requests. */
  PlatformService(Runnable afterMutation) { this.afterMutation = afterMutation; }
  synchronized boolean isEmpty() { return users.isEmpty(); }
  synchronized void restore(Collection<User> loadedUsers, Collection<Course> loadedCourses,
      Collection<Membership> loadedMemberships, Collection<Post> loadedPosts, Collection<Comment> loadedComments) {
    for (User user : loadedUsers) { users.put(user.id(), user); userByName.put(normalize(user.username()), user.id()); }
    for (Course course : loadedCourses) { courses.put(course.id(), course); courseByCode.put(course.code(), course.id()); memberships.put(course.id(), new ConcurrentHashMap<>()); }
    for (Membership membership : loadedMemberships) memberships.computeIfAbsent(membership.courseId(), ignored -> new ConcurrentHashMap<>()).put(membership.userId(), membership);
    for (Post post : loadedPosts) posts.put(post.id(), post);
    for (Comment comment : loadedComments) comments.put(comment.id(), comment);
  }
  synchronized Snapshot snapshot() { return new Snapshot(List.copyOf(users.values()), List.copyOf(courses.values()), memberships.values().stream().flatMap(m -> m.values().stream()).toList(), List.copyOf(posts.values()), List.copyOf(comments.values())); }
  record Snapshot(List<User> users, List<Course> courses, List<Membership> memberships, List<Post> posts, List<Comment> comments) {}
  private void changed() { afterMutation.run(); }

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
    memberships.remove(courseId); courses.remove(courseId); courseByCode.remove(course.code()); changed();
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
}
