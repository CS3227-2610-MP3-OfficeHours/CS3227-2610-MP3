package edu.nus.tahelpdesk;

import java.util.UUID;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static edu.nus.tahelpdesk.Domain.*;

/** Run by scripts/test-postgres.sh against an isolated local PostgreSQL database. */
public final class PostgresPlatformIntegrationTest {
  public static void main(String[] args) throws Exception {
    String url = required("TA_HELP_DESK_DATABASE_URL");
    String user = required("TA_HELP_DESK_DATABASE_USER");
    String password = required("TA_HELP_DESK_DATABASE_PASSWORD");
    PlatformService first = PostgresPlatform.open(url, user, password);
    String suffix = Long.toUnsignedString(System.nanoTime());
    User staff = first.signup("pgstaff" + suffix, "correct horse battery", "staff");
    User student = first.signup("pgstudent" + suffix, "correct horse battery", "student");
    Course course = first.createCourse(staff.id(), "PG" + suffix.substring(0, Math.min(12, suffix.length())), "Persistent test course");
    first.addMember(staff.id(), course.id(), student.username());
    UUID general = first.listForumTags(student.id(), course.id()).get(0).id();
    ForumTag custom = first.createForumTag(staff.id(), course.id(), "Persisted", "#1D4ED8");
    Post post = first.createPost(student.id(), course.id(), "Persisted post", "body", custom.id());
    first.editForumTag(staff.id(), course.id(), custom.id(), "Renamed", "#166534");
    for(int i=0;i<5;i++) first.createForumTag(staff.id(),course.id(),"Custom"+i,"#1D4ED8");
    check(first.listForumTags(staff.id(),course.id()).size()==7,"database enforces seven total tags including General");

    PlatformService reloaded = PostgresPlatform.open(url, user, password);
    check(reloaded.authenticate(student.username(), "correct horse battery").id().equals(student.id()), "user survives restart");
    check(reloaded.getPost(student.id(), course.id(), post.id()).title().equals("Persisted post"), "forum content survives restart");
    check(reloaded.listForumTags(student.id(),course.id()).stream().anyMatch(t->t.name().equals("Renamed")&&t.color().equals("#166534")),"tag edit persists across reload");
    check(reloaded.getPost(student.id(),course.id(),post.id()).tagId().equals(custom.id()),"post tag association persists across reload");
    reloaded.deleteForumTag(staff.id(),course.id(),custom.id());
    check(reloaded.getPost(staff.id(),course.id(),post.id()).tagId().equals(general),"tag deletion and General reassignment persist");
    reloaded.removeStudent(staff.id(), course.id(), student.id());
    expect(404, () -> reloaded.listPosts(student.id(), course.id()), "persisted membership revocation applies");
    reloaded.deleteCourse(staff.id(),course.id());
    expect(404, () -> reloaded.getCourse(staff.id(),course.id()), "course deletion persists after tagged data");
    migrationBackfill(url,user,password);
    System.out.println("PostgreSQL platform integration tests: PASS");
  }
  private static void migrationBackfill(String url,String user,String password) throws Exception {
    String schema="forum_tags_backfill_"+UUID.randomUUID().toString().replace("-","");
    try(Connection admin=DriverManager.getConnection(url,user,password);Statement statement=admin.createStatement()) { statement.execute("CREATE SCHEMA "+schema); }
    String scopedUrl=url+(url.contains("?")?"&":"?")+"currentSchema="+schema;
    try {
      UUID staffId=UUID.randomUUID(),studentId=UUID.randomUUID(),courseId=UUID.randomUUID(),postId=UUID.randomUUID(),commentId=UUID.randomUUID();
      Instant postCreated=Instant.parse("2022-03-04T05:06:07.123456Z"),postUpdated=Instant.parse("2022-03-05T06:07:08.234567Z");
      Instant commentCreated=Instant.parse("2022-03-06T07:08:09.345678Z"),commentUpdated=Instant.parse("2022-03-07T08:09:10.456789Z");
      try(Connection connection=DriverManager.getConnection(scopedUrl,user,password);Statement statement=connection.createStatement()) {
        statement.execute("CREATE TABLE schema_migrations (version VARCHAR(100) PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        List<String> priorMigrations=List.of("V1__shared_course_forum.sql","V2__course_materials.sql","V3__course_knowledge_maps.sql","V4__material_pdf_pages.sql","V5__material_knowledge_maps.sql");
        for(String migration:priorMigrations) {
          String sql=Files.readString(Path.of("db/migrations/"+migration)).replaceAll("(?m)^--.*$","");
          for(String part:sql.split(";"))if(!part.isBlank())statement.execute(part);
          try(PreparedStatement inserted=connection.prepareStatement("INSERT INTO schema_migrations(version) VALUES(?)")){inserted.setString(1,migration);inserted.executeUpdate();}
        }
        try(PreparedStatement q=connection.prepareStatement("INSERT INTO users(id,username,role,password_hash,created_at) VALUES(?,?,?,?,?)")) {
          q.setObject(1,staffId);q.setString(2,"migration.staff");q.setString(3,"staff");q.setString(4,"not-used");q.setTimestamp(5,Timestamp.from(postCreated));q.executeUpdate();
          q.setObject(1,studentId);q.setString(2,"migration.student");q.setString(3,"student");q.setString(4,"not-used");q.setTimestamp(5,Timestamp.from(postCreated));q.executeUpdate();
        }
        try(PreparedStatement q=connection.prepareStatement("INSERT INTO courses(id,code,name,created_by,created_at) VALUES(?,?,?,?,?)")){q.setObject(1,courseId);q.setString(2,"MIGRATION");q.setString(3,"Pre-existing course");q.setObject(4,staffId);q.setTimestamp(5,Timestamp.from(postCreated));q.executeUpdate();}
        try(PreparedStatement q=connection.prepareStatement("INSERT INTO course_memberships(course_id,user_id,membership_role) VALUES(?,?,?)")){q.setObject(1,courseId);q.setObject(2,staffId);q.setString(3,"staff");q.executeUpdate();q.setObject(2,studentId);q.setString(3,"student");q.executeUpdate();}
        try(PreparedStatement q=connection.prepareStatement("INSERT INTO posts(id,course_id,author_id,title,description,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")){q.setObject(1,postId);q.setObject(2,courseId);q.setObject(3,studentId);q.setString(4,"Pre-existing title");q.setString(5,"Pre-existing body");q.setTimestamp(6,Timestamp.from(postCreated));q.setTimestamp(7,Timestamp.from(postUpdated));q.executeUpdate();}
        try(PreparedStatement q=connection.prepareStatement("INSERT INTO comments(id,post_id,author_id,body,created_at,updated_at) VALUES(?,?,?,?,?,?)")){q.setObject(1,commentId);q.setObject(2,postId);q.setObject(3,studentId);q.setString(4,"Pre-existing reply");q.setTimestamp(5,Timestamp.from(commentCreated));q.setTimestamp(6,Timestamp.from(commentUpdated));q.executeUpdate();}
      }
      PlatformService migrated=PostgresPlatform.open(scopedUrl,user,password);
      ForumTag general=migrated.listForumTags(studentId,courseId).get(0);
      Post post=migrated.getPost(studentId,courseId,postId);
      Comment comment=migrated.listComments(studentId,courseId,postId).get(0);
      check(migrated.listForumTags(studentId,courseId).size()==1&&general.isDefault()&&general.name().equals("General"),"V6 backfills one General tag for an existing course");
      check(post.tagId().equals(general.id()),"V6 assigns the existing post to General");
      check(post.id().equals(postId)&&post.courseId().equals(courseId)&&post.authorId().equals(studentId)&&post.title().equals("Pre-existing title")&&post.description().equals("Pre-existing body")&&post.createdAt().equals(postCreated)&&post.updatedAt().equals(postUpdated),"V6 preserves existing post identifiers, content, authorship, and timestamps");
      check(comment.id().equals(commentId)&&comment.postId().equals(postId)&&comment.authorId().equals(studentId)&&comment.body().equals("Pre-existing reply")&&comment.createdAt().equals(commentCreated)&&comment.updatedAt().equals(commentUpdated),"V6 preserves existing replies and timestamps");
      PlatformService restarted=PostgresPlatform.open(scopedUrl,user,password);
      check(restarted.listForumTags(studentId,courseId).size()==1&&restarted.listForumTags(studentId,courseId).get(0).id().equals(general.id()),"repeated startup does not duplicate or replace General");
      check(restarted.listComments(studentId,courseId,postId).size()==1&&restarted.getPost(studentId,courseId,postId).tagId().equals(general.id()),"repeated startup preserves migrated post and reply associations");
    } finally {
      try(Connection admin=DriverManager.getConnection(url,user,password);Statement statement=admin.createStatement()) { statement.execute("DROP SCHEMA "+schema+" CASCADE"); }
    }
  }

  private static String required(String name) { String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required"); return value; }
  private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
  private static void expect(int status, Runnable action, String name) { try { action.run(); throw new AssertionError(name + ": unexpectedly succeeded"); } catch (ApiException error) { if (error.status != status) throw new AssertionError(name + ": expected " + status + ", got " + error.status); } }
}
