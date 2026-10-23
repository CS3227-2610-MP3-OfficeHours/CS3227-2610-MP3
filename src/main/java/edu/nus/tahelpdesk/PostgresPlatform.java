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
      service.setMaterialContentLoader(store::loadMaterialContent);
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
    for (String migration : List.of("V1__shared_course_forum.sql", "V2__course_materials.sql", "V3__course_knowledge_maps.sql", "V4__material_pdf_pages.sql", "V5__material_knowledge_maps.sql", "V6__forum_tags.sql", "V7__material_resource_only_status.sql", "V8__expand_forum_tag_palette.sql")) {
      try (PreparedStatement seen = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?")) { seen.setString(1, migration); try (ResultSet result = seen.executeQuery()) { if (result.next()) continue; } }
      String sql = Files.readString(Path.of("db/migrations/" + migration)); connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) { for (String part : migrationStatements(sql)) if (!part.isBlank()) statement.execute(part); try (PreparedStatement applied = connection.prepareStatement("INSERT INTO schema_migrations(version) VALUES (?)")) { applied.setString(1, migration); applied.executeUpdate(); } connection.commit(); }
      catch (SQLException e) { connection.rollback(); throw e; } finally { connection.setAutoCommit(true); }
    }
  }

  static List<String> migrationStatements(String sql) {
    String source = sql.replaceAll("(?m)^--.*$", ""); List<String> statements = new ArrayList<>(); StringBuilder current = new StringBuilder(); boolean single = false, quoted = false; String dollar = null;
    for (int i=0;i<source.length();i++) {
      char c=source.charAt(i);
      if (dollar != null) { if (source.startsWith(dollar,i)) { current.append(dollar); i+=dollar.length()-1; dollar=null; } else current.append(c); continue; }
      if (!single && !quoted && c=='$') { int end=source.indexOf('$',i+1); if(end>=0&&source.substring(i+1,end).matches("[A-Za-z_][A-Za-z0-9_]*|")) { dollar=source.substring(i,end+1);current.append(dollar);i=end;continue; } }
      if(c=='\''&&!quoted) { if(single&&i+1<source.length()&&source.charAt(i+1)=='\''){current.append("''");i++;continue;}single=!single;current.append(c);continue; }
      if(c=='"'&&!single) { quoted=!quoted;current.append(c);continue; }
      if(c==';'&&!single&&!quoted) { statements.add(current.toString());current.setLength(0); } else current.append(c);
    }
    if(!current.toString().isBlank())statements.add(current.toString()); return statements;
  }

  private static final class Store {
    private final Connection connection;
    Store(Connection connection) { this.connection = connection; }
    synchronized void loadInto(PlatformService service) throws SQLException {
      List<User> users = new ArrayList<>(); List<Course> courses = new ArrayList<>(); List<Membership> memberships = new ArrayList<>(); List<ForumTag> tags = new ArrayList<>(); List<Post> posts = new ArrayList<>(); List<Comment> comments = new ArrayList<>(); List<Material> materials = new ArrayList<>(); List<PdfPage> pdfPages = new ArrayList<>(); List<KnowledgeMap> candidates = new ArrayList<>(); List<KnowledgeMap> approved = new ArrayList<>(); List<KnowledgeMapJob> jobs = new ArrayList<>();
      try (Statement st = connection.createStatement()) {
        try (ResultSet rs = st.executeQuery("SELECT id, username, role, password_hash, created_at FROM users")) { while (rs.next()) users.add(new User(uuid(rs,1), rs.getString(2), Role.valueOf(rs.getString(3)), rs.getString(4), instant(rs,5))); }
        try (ResultSet rs = st.executeQuery("SELECT id, code, name, created_by, created_at FROM courses")) { while (rs.next()) courses.add(new Course(uuid(rs,1), rs.getString(2), rs.getString(3), uuid(rs,4), instant(rs,5))); }
        try (ResultSet rs = st.executeQuery("SELECT course_id, user_id, membership_role FROM course_memberships")) { while (rs.next()) memberships.add(new Membership(uuid(rs,1), uuid(rs,2), Role.valueOf(rs.getString(3)))); }
        try (ResultSet rs = st.executeQuery("SELECT id, course_id, name, color, is_default, created_at FROM course_forum_tags")) { while (rs.next()) tags.add(new ForumTag(uuid(rs,1),uuid(rs,2),rs.getString(3),rs.getString(4),rs.getBoolean(5),instant(rs,6))); }
        try (ResultSet rs = st.executeQuery("SELECT id, course_id, author_id, title, description, tag_id, created_at, updated_at FROM posts")) { while (rs.next()) posts.add(new Post(uuid(rs,1), uuid(rs,2), uuid(rs,3), rs.getString(4), rs.getString(5), uuid(rs,6), instant(rs,7), instant(rs,8))); }
        try (ResultSet rs = st.executeQuery("SELECT id, post_id, author_id, body, created_at, updated_at FROM comments")) { while (rs.next()) comments.add(new Comment(uuid(rs,1), uuid(rs,2), uuid(rs,3), rs.getString(4), instant(rs,5), instant(rs,6))); }
        try (ResultSet rs = st.executeQuery("SELECT id, course_id, uploader_id, filename, status, failure_message, page_count, created_at, updated_at FROM course_materials")) { while (rs.next()) materials.add(new Material(uuid(rs,1),uuid(rs,2),uuid(rs,3),rs.getString(4),MaterialStatus.valueOf(rs.getString(5)),rs.getString(6),null,rs.getInt(7),instant(rs,8),instant(rs,9))); }
        try (ResultSet rs = st.executeQuery("SELECT material_id, course_id, material_digest, page_number, text, text_digest, parser_version, state, safe_failure_code, created_at, invalidated_at FROM course_material_pdf_pages")) { while (rs.next()) pdfPages.add(new PdfPage(uuid(rs,1),uuid(rs,2),rs.getString(3),rs.getInt(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),instant(rs,10),nullableInstant(rs,11))); }
        try (ResultSet rs = st.executeQuery("SELECT id, course_id, material_id, material_digest, requested_by, slot, content, verification_message, reviewed_by, reviewed_at, created_at, updated_at FROM material_knowledge_maps")) { while (rs.next()) { String slot=rs.getString(6); KnowledgeMap map=new KnowledgeMap(uuid(rs,1),uuid(rs,2),uuid(rs,3),rs.getString(4),uuid(rs,5),"candidate".equals(slot)?KnowledgeMapStatus.pending_review:KnowledgeMapStatus.approved,rs.getString(7),rs.getString(8),nullableUuid(rs,9),nullableInstant(rs,10),instant(rs,11),instant(rs,12)); if("candidate".equals(slot)) candidates.add(map); else approved.add(map); } }
        try (ResultSet rs = st.executeQuery("SELECT id, course_id, material_id, material_digest, requested_by, status, failure_message, output_map_id, created_at, updated_at FROM material_knowledge_map_jobs")) { while (rs.next()) jobs.add(new KnowledgeMapJob(uuid(rs,1),uuid(rs,2),uuid(rs,3),rs.getString(4),uuid(rs,5),KnowledgeMapJobStatus.valueOf(rs.getString(6)),rs.getString(7),nullableUuid(rs,8),instant(rs,9),instant(rs,10))); }
      }
      service.restore(users, courses, memberships, tags, posts, comments, materials, pdfPages, candidates, approved, jobs);
    }
    synchronized byte[] loadMaterialContent(UUID id) { try(PreparedStatement q=connection.prepareStatement("SELECT content FROM course_materials WHERE id=?")){q.setObject(1,id);try(ResultSet rs=q.executeQuery()){return rs.next()?rs.getBytes(1):null;}}catch(SQLException e){throw new IllegalStateException("PostgreSQL material retrieval failed.",e);} }
    synchronized void save(PlatformService service) {
      try {
        PlatformService.Snapshot state = service.snapshot(); connection.setAutoCommit(false);
        try (Statement st = connection.createStatement()) { st.executeUpdate("CREATE TEMPORARY TABLE retained_material_content ON COMMIT DROP AS SELECT id, content FROM course_materials"); st.executeUpdate("DELETE FROM material_knowledge_map_jobs"); st.executeUpdate("DELETE FROM material_knowledge_maps"); st.executeUpdate("DELETE FROM course_material_pdf_pages"); st.executeUpdate("DELETE FROM course_materials"); st.executeUpdate("DELETE FROM comments"); st.executeUpdate("DELETE FROM posts"); st.executeUpdate("DELETE FROM course_forum_tags"); st.executeUpdate("DELETE FROM course_memberships"); st.executeUpdate("DELETE FROM courses"); st.executeUpdate("DELETE FROM users"); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO users(id,username,role,password_hash,created_at) VALUES(?,?,?,?,?)")) { for(User x:state.users()){q.setObject(1,x.id());q.setString(2,x.username());q.setString(3,x.role().name());q.setString(4,x.passwordHash());q.setTimestamp(5,Timestamp.from(x.createdAt()));q.addBatch();}q.executeBatch(); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO courses(id,code,name,created_by,created_at) VALUES(?,?,?,?,?)")) { for(Course x:state.courses()){q.setObject(1,x.id());q.setString(2,x.code());q.setString(3,x.name());q.setObject(4,x.createdBy());q.setTimestamp(5,Timestamp.from(x.createdAt()));q.addBatch();}q.executeBatch(); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO course_forum_tags(id,course_id,name,normalized_name,color,is_default,created_at) VALUES(?,?,?,?,?,?,?)")) { for(ForumTag x:state.tags().stream().sorted(Comparator.comparing(ForumTag::isDefault).reversed().thenComparing(ForumTag::createdAt)).toList()){q.setObject(1,x.id());q.setObject(2,x.courseId());q.setString(3,x.name());q.setString(4,PlatformService.normalizedTagName(x.name()));q.setString(5,x.color());q.setBoolean(6,x.isDefault());q.setTimestamp(7,Timestamp.from(x.createdAt()));q.addBatch();}q.executeBatch(); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO course_memberships(course_id,user_id,membership_role) VALUES(?,?,?)")) { for(Membership x:state.memberships()){q.setObject(1,x.courseId());q.setObject(2,x.userId());q.setString(3,x.role().name());q.addBatch();}q.executeBatch(); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO posts(id,course_id,author_id,title,description,tag_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)")) { for(Post x:state.posts()){q.setObject(1,x.id());q.setObject(2,x.courseId());q.setObject(3,x.authorId());q.setString(4,x.title());q.setString(5,x.description());q.setObject(6,x.tagId());q.setTimestamp(7,Timestamp.from(x.createdAt()));q.setTimestamp(8,Timestamp.from(x.updatedAt()));q.addBatch();}q.executeBatch(); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO comments(id,post_id,author_id,body,created_at,updated_at) VALUES(?,?,?,?,?,?)")) { for(Comment x:state.comments()){q.setObject(1,x.id());q.setObject(2,x.postId());q.setObject(3,x.authorId());q.setString(4,x.body());q.setTimestamp(5,Timestamp.from(x.createdAt()));q.setTimestamp(6,Timestamp.from(x.updatedAt()));q.addBatch();}q.executeBatch(); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO course_materials(id,course_id,uploader_id,filename,status,failure_message,content,page_count,created_at,updated_at) VALUES(?,?,?,?,?,?,COALESCE(?,(SELECT content FROM retained_material_content WHERE id=?)),?,?,?)")) { for(Material x:state.materials()){q.setObject(1,x.id());q.setObject(2,x.courseId());q.setObject(3,x.uploaderId());q.setString(4,x.filename());q.setString(5,x.status().name());q.setString(6,x.failureMessage());q.setBytes(7,x.content());q.setObject(8,x.id());q.setInt(9,x.pageCount());q.setTimestamp(10,Timestamp.from(x.createdAt()));q.setTimestamp(11,Timestamp.from(x.updatedAt()));q.addBatch();}q.executeBatch(); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO course_material_pdf_pages(material_id,course_id,material_digest,page_number,text,text_digest,parser_version,state,safe_failure_code,created_at,invalidated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)")) { for(PdfPage x:state.pdfPages()){q.setObject(1,x.materialId());q.setObject(2,x.courseId());q.setString(3,x.materialDigest());q.setInt(4,x.pageNumber());q.setString(5,x.text());q.setString(6,x.textDigest());q.setString(7,x.parserVersion());q.setString(8,x.state());q.setString(9,x.safeFailureCode());q.setTimestamp(10,Timestamp.from(x.createdAt()));if(x.invalidatedAt()==null)q.setNull(11,Types.TIMESTAMP_WITH_TIMEZONE);else q.setTimestamp(11,Timestamp.from(x.invalidatedAt()));q.addBatch();}q.executeBatch(); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO material_knowledge_maps(id,course_id,material_id,material_digest,requested_by,slot,content,verification_message,reviewed_by,reviewed_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) { for(KnowledgeMap x:state.candidates()){insertMap(q,x,"candidate");} for(KnowledgeMap x:state.approvedMaps()){insertMap(q,x,"approved");} q.executeBatch(); }
        try (PreparedStatement q = connection.prepareStatement("INSERT INTO material_knowledge_map_jobs(id,course_id,material_id,material_digest,requested_by,status,failure_message,output_map_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)")) { for(KnowledgeMapJob x:state.knowledgeMapJobs()){q.setObject(1,x.id());q.setObject(2,x.courseId());q.setObject(3,x.materialId());q.setString(4,x.materialDigest());q.setObject(5,x.requestedBy());q.setString(6,x.status().name());q.setString(7,x.failureMessage());q.setObject(8,x.outputMapId());q.setTimestamp(9,Timestamp.from(x.createdAt()));q.setTimestamp(10,Timestamp.from(x.updatedAt()));q.addBatch();}q.executeBatch(); }
        connection.commit();
      } catch (SQLException e) { try { connection.rollback(); } catch (SQLException ignored) {} throw new IllegalStateException("PostgreSQL persistence failed.", e); }
      finally { try { connection.setAutoCommit(true); } catch (SQLException ignored) {} }
    }
    private static UUID uuid(ResultSet rs,int index) throws SQLException { return rs.getObject(index, UUID.class); }
    private static UUID nullableUuid(ResultSet rs,int index) throws SQLException { return rs.getObject(index, UUID.class); }
    private static Instant instant(ResultSet rs,int index) throws SQLException { return rs.getTimestamp(index).toInstant(); }
    private static Instant nullableInstant(ResultSet rs,int index) throws SQLException { Timestamp value = rs.getTimestamp(index); return value == null ? null : value.toInstant(); }
    private static List<UUID> uuidList(Array value) throws SQLException { if (value == null) return List.of(); Object[] raw = (Object[]) value.getArray(); List<UUID> ids = new ArrayList<>(); for (Object item : raw) ids.add(item instanceof UUID id ? id : UUID.fromString(item.toString())); return List.copyOf(ids); }
    private static void insertMap(PreparedStatement q, KnowledgeMap x, String slot) throws SQLException { q.setObject(1,x.id());q.setObject(2,x.courseId());q.setObject(3,x.materialId());q.setString(4,x.materialDigest());q.setObject(5,x.requestedBy());q.setString(6,slot);q.setString(7,x.content());q.setString(8,x.verificationMessage());q.setObject(9,x.reviewedBy());if(x.reviewedAt()==null)q.setNull(10,Types.TIMESTAMP_WITH_TIMEZONE);else q.setTimestamp(10,Timestamp.from(x.reviewedAt()));q.setTimestamp(11,Timestamp.from(x.createdAt()));q.setTimestamp(12,Timestamp.from(x.updatedAt()));q.addBatch(); }
  }
}
