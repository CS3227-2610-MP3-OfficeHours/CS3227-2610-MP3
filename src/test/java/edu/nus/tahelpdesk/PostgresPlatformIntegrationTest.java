package edu.nus.tahelpdesk;

import static edu.nus.tahelpdesk.Domain.*;

/** Run by scripts/test-postgres.sh against an isolated local PostgreSQL database. */
public final class PostgresPlatformIntegrationTest {
  public static void main(String[] args) {
    String url = required("TA_HELP_DESK_DATABASE_URL");
    String user = required("TA_HELP_DESK_DATABASE_USER");
    String password = required("TA_HELP_DESK_DATABASE_PASSWORD");
    PlatformService first = PostgresPlatform.open(url, user, password);
    String suffix = Long.toUnsignedString(System.nanoTime());
    User staff = first.signup("pgstaff" + suffix, "correct horse battery", "staff");
    User student = first.signup("pgstudent" + suffix, "correct horse battery", "student");
    Course course = first.createCourse(staff.id(), "PG" + suffix.substring(0, Math.min(12, suffix.length())), "Persistent test course");
    first.addMember(staff.id(), course.id(), student.username());
    Post post = first.createPost(student.id(), course.id(), "Persisted post", "body");

    PlatformService reloaded = PostgresPlatform.open(url, user, password);
    check(reloaded.authenticate(student.username(), "correct horse battery").id().equals(student.id()), "user survives restart");
    check(reloaded.getPost(student.id(), course.id(), post.id()).title().equals("Persisted post"), "forum content survives restart");
    reloaded.removeStudent(staff.id(), course.id(), student.id());
    expect(404, () -> reloaded.listPosts(student.id(), course.id()), "persisted membership revocation applies");
    System.out.println("PostgreSQL platform integration tests: PASS");
  }
  private static String required(String name) { String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required"); return value; }
  private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
  private static void expect(int status, Runnable action, String name) { try { action.run(); throw new AssertionError(name + ": unexpectedly succeeded"); } catch (ApiException error) { if (error.status != status) throw new AssertionError(name + ": expected " + status + ", got " + error.status); } }
}
