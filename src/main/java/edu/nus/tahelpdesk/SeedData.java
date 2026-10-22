package edu.nus.tahelpdesk;

/**
 * Deterministic starter identities and two isolated courses.
 *
 * <p>The password is supplied by the caller rather than written here, because
 * this repository is public: a literal password in this file would be a
 * published credential for every deployment that seeds. {@link Main} uses the
 * development default only outside production, and requires an operator-set
 * value before creating these accounts in a deployed environment.
 */
public final class SeedData {
  /** Used only for local development and tests, never for a deployed environment. */
  static final String DEVELOPMENT_PASSWORD = "local-development-password";

  private SeedData() {}

  public static PlatformService create() {
    PlatformService s = new PlatformService();
    seedIfEmpty(s, DEVELOPMENT_PASSWORD);
    return s;
  }

  public static void seedIfEmpty(PlatformService s, String password) {
    if (!s.isEmpty()) return;
    var staff = s.signup("seed.staff", password, "staff");
    var student = s.signup("seed.student", password, "student");
    var other = s.signup("seed.other", password, "student");
    var first = s.createCourse(staff.id(), "CS3227", "Introduction to Internet Architecture");
    var second = s.createCourse(staff.id(), "CS2103", "Software Engineering");
    s.addMember(staff.id(), first.id(), student.username()); s.addMember(staff.id(), second.id(), other.username());
  }
}
