package edu.nus.tahelpdesk;
/** Development-only deterministic starter identities and two isolated courses. */
public final class SeedData {
  private SeedData() {}
  public static PlatformService create() {
    PlatformService s = new PlatformService();
    seedIfEmpty(s);
    return s;
  }
  public static void seedIfEmpty(PlatformService s) {
    if (!s.isEmpty()) return;
    var staff = s.signup("seed.staff", "local-development-password", "staff");
    var student = s.signup("seed.student", "local-development-password", "student");
    var other = s.signup("seed.other", "local-development-password", "student");
    var first = s.createCourse(staff.id(), "CS3227", "Introduction to Internet Architecture");
    var second = s.createCourse(staff.id(), "CS2103", "Software Engineering");
    s.addMember(staff.id(), first.id(), student.username()); s.addMember(staff.id(), second.id(), other.username());
  }
}
