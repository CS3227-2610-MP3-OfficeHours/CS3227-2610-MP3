package edu.nus.tahelpdesk;

import static edu.nus.tahelpdesk.Domain.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PostgreSQL runtime adapter. Authorization stays in PlatformService; this class
 * durably stores its domain state in the migration-defined relational tables.
 * It uses JDBC only, so the PostgreSQL driver is supplied at run time rather
 * than being copied into source control.
 */
public final class PostgresPlatform {
  private PostgresPlatform() {}

  public static PlatformService open(String url, String user, String password) {
    try {
      Connection connection = DriverManager.getConnection(url, user, password);
      migrate(connection);
      AtomicReference<PlatformService> ref = new AtomicReference<>();
      Store store = new Store(connection);
      PlatformService service = new PlatformService(() -> store.save(ref.get()));
      ref.set(service);
      store.loadInto(service);
      return service;
    } catch (SQLException | IOException e) {
      throw new IllegalStateException("PostgreSQL could not be initialized. Check the JDBC driver, database settings, and migration path.", e);
    }
  }

  private static void migrate(Connection connection) throws SQLException, IOException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations (version VARCHAR(100) PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }
    try (PreparedStatement seen = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?")) {
      seen.setString(1, "V1__shared_course_forum.sql");
      try (ResultSet result = seen.executeQuery()) { if (result.next()) return; }
    }
    String sql = Files.readString(Path.of("db/migrations/V1__shared_course_forum.sql"));
    connection.setAutoCommit(false);
    try (Statement statement = connection.createStatement()) {
      for (String part : sql.replaceAll("(?m)^--.*$", "").split(";")) if (!part.isBlank()) statement.execute(part);
      try (PreparedStatement applied = connection.prepareStatement("INSERT INTO schema_migrations(version) VALUES (?)")) { applied.setString(1, "V1__shared_course_forum.sql"); applied.executeUpdate(); }
      connection.commit();
    } catch (SQLException e) { connection.rollback(); throw e; } finally { connection.setAutoCommit(true); }
  }

  private static final class Store {
    private final Connection connection;
    Store(Connection connection) { this.connection = connection; }
    synchronized void loadInto(PlatformService service) throws SQLException {
      List<User> users = new ArrayList<>(); List<Course> courses = new ArrayList<>(); List<Membership> memberships = new ArrayList<>(); List<Post> posts = new ArrayList<>(); List<Comment> comments = new ArrayList<>();
      try (Statement st = connection.createStatement()) {
        try (ResultSet rs = st.executeQuery("SELECT id, username, role, password_hash, created_at FROM users")) { while (rs.next()) users.add(new User(uuid(rs,1), rs.getString(2), Role.valueOf(rs.getString(3)), rs.getString(4), instant(rs,5))); }
        try (ResultSet rs = st.executeQuery("SELECT id, code, name, created_by, created_at FROM courses")) { while (rs.next()) courses.add(new Course(uuid(rs,1), rs.getString(2), rs.getString(3), uuid(rs,4), instant(rs,5))); }
        try (ResultSet rs = st.executeQuery("SELECT course_id, user_id, membership_role FROM course_memberships")) { while (rs.next()) memberships.add(new Membership(uuid(rs,1), uuid(rs,2), Role.valueOf(rs.getString(3)))); }
        try (ResultSet rs = st.executeQuery("SELECT id, course_id, author_id, title, description, created_at, updated_at FROM posts")) { while (rs.next()) posts.add(new Post(uuid(rs,1), uuid(rs,2), uuid(rs,3), rs.getString(4), rs.getString(5), instant(rs,6), instant(rs,7))); }
        try (ResultSet rs = st.executeQuery("SELECT id, post_id, author_id, body, created_at, updated_at FROM comments")) { while (rs.next()) comments.add(new Comment(uuid(rs,1), uuid(rs,2), uuid(rs,3), rs.getString(4), instant(rs,5), instant(rs,6))); }
      }
      service.restore(users, courses, memberships, posts, comments);
    }
    synchronized void save(PlatformService service) {
      try {
        PlatformService.Snapshot state = service.snapshot(); connection.setAutoCommit(false);
        try (Statement st = connection.createStatement()) { st.executeUpdate("DELETE FROM comments"); st.executeUpdate("DELETE FROM posts"); st.executeUpdate("DELETE FROM course_memberships"); st.executeUpdate("DELETE FROM courses"); st.executeUpdate("DELETE FROM users"); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO users(id,username,role,password_hash,created_at) VALUES(?,?,?,?,?)")) { for(User x:state.users()){q.setObject(1,x.id());q.setString(2,x.username());q.setString(3,x.role().name());q.setString(4,x.passwordHash());q.setTimestamp(5,Timestamp.from(x.createdAt()));q.addBatch();}q.executeBatch(); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO courses(id,code,name,created_by,created_at) VALUES(?,?,?,?,?)")) { for(Course x:state.courses()){q.setObject(1,x.id());q.setString(2,x.code());q.setString(3,x.name());q.setObject(4,x.createdBy());q.setTimestamp(5,Timestamp.from(x.createdAt()));q.addBatch();}q.executeBatch(); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO course_memberships(course_id,user_id,membership_role) VALUES(?,?,?)")) { for(Membership x:state.memberships()){q.setObject(1,x.courseId());q.setObject(2,x.userId());q.setString(3,x.role().name());q.addBatch();}q.executeBatch(); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO posts(id,course_id,author_id,title,description,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")) { for(Post x:state.posts()){q.setObject(1,x.id());q.setObject(2,x.courseId());q.setObject(3,x.authorId());q.setString(4,x.title());q.setString(5,x.description());q.setTimestamp(6,Timestamp.from(x.createdAt()));q.setTimestamp(7,Timestamp.from(x.updatedAt()));q.addBatch();}q.executeBatch(); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO comments(id,post_id,author_id,body,created_at,updated_at) VALUES(?,?,?,?,?,?)")) { for(Comment x:state.comments()){q.setObject(1,x.id());q.setObject(2,x.postId());q.setObject(3,x.authorId());q.setString(4,x.body());q.setTimestamp(5,Timestamp.from(x.createdAt()));q.setTimestamp(6,Timestamp.from(x.updatedAt()));q.addBatch();}q.executeBatch(); }
        connection.commit();
      } catch (SQLException e) { try { connection.rollback(); } catch (SQLException ignored) {} throw new IllegalStateException("PostgreSQL persistence failed.", e); }
      finally { try { connection.setAutoCommit(true); } catch (SQLException ignored) {} }
    }
    private static UUID uuid(ResultSet rs,int index) throws SQLException { return rs.getObject(index, UUID.class); }
    private static Instant instant(ResultSet rs,int index) throws SQLException { return rs.getTimestamp(index).toInstant(); }
  }
}
