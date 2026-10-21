package edu.nus.tahelpdesk;

import static edu.nus.tahelpdesk.Domain.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Dependency-free integration tests for the server authorization boundary. */
public final class PlatformIntegrationTest {
  private final PlatformService s = new PlatformService();
  private final User staffA = s.signup("staff.a", "correct horse battery", "staff");
  private final User staffB = s.signup("staff.b", "correct horse battery", "staff");
  private final User alice = s.signup("alice", "correct horse battery", "student");
  private final User bob = s.signup("bob", "correct horse battery", "student");
  private final Course first = s.createCourse(staffA.id(), "CS1010", "Programming Methodology");
  private final Course second = s.createCourse(staffA.id(), "CS2040", "Data Structures");
  public static void main(String[] args) { PlatformIntegrationTest t=new PlatformIntegrationTest(); t.run(); System.out.println("Platform integration tests: PASS"); }
  private void run() {
    check(s.membershipRole(first.id(),staffA.id())==Role.staff,"creator is course staff");
    expect(409,()->s.signup("alice","another password","student"),"duplicate username");
    expect(422,()->s.signup("ab","password","student"),"invalid username");
    expect(422,()->s.signup("newuser","short","student"),"invalid password");
    expect(401,()->s.authenticate("alice","wrong password"),"wrong password denied");
    expect(403,()->s.createCourse(alice.id(),"NOPE","Denied"),"student cannot create courses");
    s.addMember(staffA.id(),first.id(),alice.username()); s.addMember(staffA.id(),first.id(),bob.username()); s.addMember(staffA.id(),first.id(),staffB.username());
    check(s.addMember(staffA.id(),first.id(),alice.username()).userId().equals(alice.id()),"membership add is idempotent");
    expect(422,()->s.addMember(staffA.id(),first.id(),"nobody"),"unknown member denied");
    expect(404,()->s.addMember(staffB.id(),second.id(),alice.username()),"non-course staff cannot modify cross-course membership");
    expect(404,()->s.listPosts(alice.id(),second.id()),"student cannot read other course");
    expect(404,()->s.listPosts(staffB.id(),second.id()),"unassigned staff cannot read other course");
    Post alicePost=s.createPost(alice.id(),first.id(),"Question","**Can someone help?** <script>alert(1)</script>");
    check(Markdown.render(alicePost.description()).contains("&lt;script&gt;"),"markdown escapes supplied html");
    expect(422,()->s.createPost(alice.id(),first.id()," ",null),"missing title denied");
    expect(422,()->s.createPost(alice.id(),first.id(),"x".repeat(121),null),"long title denied");
    expect(422,()->s.createPost(alice.id(),first.id(),"ok","x".repeat(2001)),"long description denied");
    expect(404,()->s.getPost(alice.id(),second.id(),alicePost.id()),"cross-course post ID denial");
    expect(403,()->s.editPost(staffA.id(),first.id(),alicePost.id(),"staff edit",null),"staff cannot edit student post");
    expect(403,()->s.deletePost(bob.id(),first.id(),alicePost.id()),"student cannot delete another student post");
    Post staffPost=s.createPost(staffB.id(),first.id(),"Staff note",null);
    expect(403,()->s.deletePost(staffA.id(),first.id(),staffPost.id()),"staff cannot delete another staff post");
    Comment bobComment=s.createComment(bob.id(),first.id(),alicePost.id(),"I have the same question");
    expect(403,()->s.editComment(alice.id(),first.id(),alicePost.id(),bobComment.id(),"changed"),"non-author cannot edit comment");
    s.deleteComment(staffA.id(),first.id(),alicePost.id(),bobComment.id());
    expect(404,()->s.editComment(bob.id(),first.id(),alicePost.id(),bobComment.id(),"gone"),"hard-deleted comment hidden");
    Comment dependent=s.createComment(alice.id(),first.id(),alicePost.id(),"follow-up"); s.deletePost(alice.id(),first.id(),alicePost.id());
    expect(404,()->s.getPost(alice.id(),first.id(),alicePost.id()),"hard-deleted post hidden");
    expect(404,()->s.editComment(alice.id(),first.id(),alicePost.id(),dependent.id(),"gone"),"post deletion cascades comments");
    Post removedPost=s.createPost(bob.id(),first.id(),"Before removal",null); s.removeStudent(staffA.id(),first.id(),bob.id());
    expect(404,()->s.listPosts(bob.id(),first.id()),"removed student loses reads immediately");
    expect(404,()->s.createComment(bob.id(),first.id(),staffPost.id(),"stale request"),"removed student loses writes immediately");
    expect(422,()->s.removeStudent(staffA.id(),first.id(),staffB.id()),"staff membership cannot be removed");
    materialLifecycle();
    knowledgeMapLifecycle();
    knowledgeMapQueueLimit();
    s.deleteCourse(staffA.id(),first.id());
    expect(404,()->s.getCourse(staffA.id(),first.id()),"deleted course inaccessible to staff");
    expect(404,()->s.getPost(staffA.id(),first.id(),staffPost.id()),"deleted course forum inaccessible");
    check(s.listPosts(staffA.id(),second.id()).isEmpty(),"other course remains isolated");
    check(removedPost.id()!=null,"test created post before removal");
  }
  private void materialLifecycle() {
    byte[] validPdf = pdf(true);
    expect(404, () -> s.uploadMaterial(alice.id(), first.id(), "notes.pdf", "application/pdf", validPdf), "student cannot upload");
    expect(422, () -> s.uploadMaterial(staffA.id(), first.id(), "notes.pdf", "text/plain", validPdf), "declared non-PDF denied");
    expect(422, () -> s.uploadMaterial(staffA.id(), first.id(), "notes.pdf", "application/pdf", "not a PDF".getBytes(StandardCharsets.ISO_8859_1)), "renamed non-PDF denied");
    expect(422, () -> s.uploadMaterial(staffA.id(), first.id(), "fake.pdf", "application/pdf", "%PDF-1.4\n/Type /Page\ntrailer <</Root 1 0 R>>\n%%EOF".getBytes(StandardCharsets.ISO_8859_1)), "PDF-like malformed bytes denied");
    expect(422, () -> s.uploadMaterial(staffA.id(), first.id(), "missing-xref.pdf", "application/pdf", pdfWithoutXref()), "missing xref and startxref denied");
    expect(422, () -> s.uploadMaterial(staffA.id(), first.id(), "bad-xref-stream.pdf", "application/pdf", xrefStreamPdf(true, true)), "xref stream with incorrect object offset denied");
    expect(422, () -> s.uploadMaterial(staffA.id(), first.id(), "dangling-pages.pdf", "application/pdf", danglingPagesPdf()), "catalog page-tree reference must resolve before processing");
    expect(422, () -> s.uploadMaterial(staffA.id(), first.id(), "secret.pdf", "application/pdf", "%PDF-1.4 /Encrypt %%EOF".getBytes(StandardCharsets.ISO_8859_1)), "encrypted PDF denied");
    Material material = s.uploadMaterial(staffA.id(), first.id(), "Week 1.pdf", "application/pdf", validPdf);
    check(material.status() == MaterialStatus.processing, "accepted material initially processes");
    waitFor(material.id(), MaterialStatus.active);
    check(s.listResources(alice.id(), first.id()).size() == 1, "enrolled student sees active resource");
    check(s.downloadResource(alice.id(), first.id(), material.id()).content().length == validPdf.length, "enrolled student downloads bytes");
    expect(404, () -> s.listMaterials(alice.id(), first.id()), "student cannot read staff material metadata");
    expect(404, () -> s.downloadResource(staffB.id(), second.id(), material.id()), "unassigned staff cannot download resource");
    expect(404, () -> s.deleteMaterial(staffB.id(), second.id(), material.id()), "unassigned staff cannot delete material");
    s.deleteMaterial(staffA.id(), first.id(), material.id());
    check(s.listResources(alice.id(), first.id()).isEmpty(), "deleted PDF immediately leaves resources");
    expect(404, () -> s.downloadResource(alice.id(), first.id(), material.id()), "deleted PDF is not downloadable");
    Material failed = s.uploadMaterial(staffA.id(), first.id(), "scanned.pdf", "application/pdf", pdf(false));
    waitFor(failed.id(), MaterialStatus.resource_only);
    check(s.listResources(alice.id(), first.id()).size() == 1, "image-only PDF remains downloadable as a resource");
    check(s.downloadResource(alice.id(), first.id(), failed.id()).content().length > 0, "image-only PDF download remains authorized");
  }
  private void knowledgeMapLifecycle() {
    Material source = s.uploadMaterial(staffA.id(), first.id(), "Week 2.pdf", "application/pdf", twoPagePdf());
    waitFor(source.id(), MaterialStatus.active);
    s.setKnowledgeMapGenerator(packet -> {
      check(packet.contains("Week 2.pdf page=1") && packet.contains("Hello") && packet.contains("Week 2.pdf page=2") && packet.contains("Second page"), "map input preserves active PDF page text");
      return SoCLaaSClient.Result.success("Topic: introduction (Week 2.pdf, p. 1)");
    }, true);
    check(s.knowledgeMapFallbackConfigured(staffA.id(), first.id()), "authorized course staff sees fallback capability");
    expect(404, () -> s.knowledgeMapFallbackConfigured(alice.id(), first.id()), "student cannot query fallback capability");
    expect(404, () -> s.generateKnowledgeMap(alice.id(), first.id(), source.id()), "student cannot generate a Knowledge Map");
    expect(404, () -> s.generateKnowledgeMap(staffB.id(), second.id(), source.id()), "staff cannot generate a cross-course Knowledge Map");
    s.generateKnowledgeMap(staffA.id(), first.id(), source.id()); waitForMap(source.id(), KnowledgeMapJobStatus.completed);
    KnowledgeMap pending = candidate(source.id()); check(pending.materialId().equals(source.id()) && pending.verificationMessage() == null, "one-PDF request produces verified candidate");
    s.setKnowledgeMapGenerator(packet -> SoCLaaSClient.Result.failure(SoCLaaSClient.Failure.temporarily_unavailable));
    s.generateKnowledgeMap(staffA.id(), first.id(), source.id()); waitForMap(source.id(), KnowledgeMapJobStatus.failed);
    check(candidate(source.id()) != null && candidate(source.id()).content().contains("Topic"), "failed update retains candidate map");
    check(s.approveKnowledgeMap(staffB.id(), first.id(), source.id()).status() == KnowledgeMapStatus.approved, "course staff can approve candidate");
    s.generateKnowledgeMap(staffA.id(), first.id(), source.id()); waitForMap(source.id(), KnowledgeMapJobStatus.failed);
    check(approved(source.id()) != null && approved(source.id()).content().contains("Topic"), "failed update retains approved map");
    s.setKnowledgeMapGenerator(packet -> SoCLaaSClient.Result.success("A map without source citations"));
    s.generateKnowledgeMap(staffA.id(), first.id(), source.id()); waitForMap(source.id(), KnowledgeMapJobStatus.completed);
    KnowledgeMap unverified = candidate(source.id()); check(unverified.verificationMessage().contains("Unverified"), "uncited map remains staff-only candidate");
    expect(422, () -> s.approveKnowledgeMap(staffA.id(), first.id(), source.id()), "unverified candidate cannot be approved");
    s.setKnowledgeMapGenerator(packet -> SoCLaaSClient.Result.success("Topic (Other.pdf, p. 1)"));
    s.generateKnowledgeMap(staffA.id(), first.id(), source.id()); waitForMap(source.id(), KnowledgeMapJobStatus.completed);
    check(candidate(source.id()).verificationMessage().contains("Unverified"), "foreign citation remains unverified");
    expect(422, () -> s.approveKnowledgeMap(staffA.id(), first.id(), source.id()), "foreign citation cannot be approved");
    s.setKnowledgeMapGenerator(packet -> SoCLaaSClient.Result.success("Topic (Week 2.pdf, p. 99)"));
    s.generateKnowledgeMap(staffA.id(), first.id(), source.id()); waitForMap(source.id(), KnowledgeMapJobStatus.completed);
    check(candidate(source.id()).verificationMessage().contains("Unverified"), "stale page citation remains unverified");
    expect(422, () -> s.approveKnowledgeMap(staffA.id(), first.id(), source.id()), "stale page citation cannot be approved");
    s.rejectKnowledgeMap(staffA.id(), first.id(), source.id()); check(candidate(source.id()) == null && approved(source.id()) != null, "rejection removes only candidate");
  }
  private void knowledgeMapQueueLimit() {
    CountDownLatch providerStarted = new CountDownLatch(1), releaseProvider = new CountDownLatch(1);
    s.setKnowledgeMapGenerator(packet -> { providerStarted.countDown(); try { releaseProvider.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } return SoCLaaSClient.Result.success("queue.pdf, p. 1"); });
    java.util.List<Course> queueCourses = new java.util.ArrayList<>();
    for (int i = 0; i < 5; i++) { Course course = s.createCourse(staffA.id(), "Q" + (1000 + i), "Queue " + i); queueCourses.add(course); Material material = s.uploadMaterial(staffA.id(), course.id(), "queue.pdf", "application/pdf", pdf(true)); waitFor(course.id(), material.id(), MaterialStatus.active); }
    for (int i = 0; i < 4; i++) { Material m=s.listMaterials(staffA.id(), queueCourses.get(i).id()).getFirst(); s.generateKnowledgeMap(staffA.id(), queueCourses.get(i).id(), m.id()); }
    try { check(providerStarted.await(2, TimeUnit.SECONDS), "queue test gateway call started"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    Material fifth=s.listMaterials(staffA.id(), queueCourses.get(4).id()).getFirst(); check(s.generateKnowledgeMap(staffA.id(), queueCourses.get(4).id(), fifth.id()).status() == KnowledgeMapJobStatus.queued, "fifth job remains visible as queued under concurrency pressure");
    releaseProvider.countDown();
  }
  private void waitFor(UUID materialId, MaterialStatus expected) { long deadline = System.nanoTime() + 2_000_000_000L; while (System.nanoTime() < deadline) { Material actual = s.listMaterials(staffA.id(), first.id()).stream().filter(m -> m.id().equals(materialId)).findFirst().orElseThrow(); if (actual.status() == expected) return; try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); } } throw new AssertionError("material did not reach " + expected); }
  private void waitFor(UUID courseId, UUID materialId, MaterialStatus expected) { long deadline = System.nanoTime() + 2_000_000_000L; while (System.nanoTime() < deadline) { Material actual = s.listMaterials(staffA.id(), courseId).stream().filter(m -> m.id().equals(materialId)).findFirst().orElseThrow(); if (actual.status() == expected) return; try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); } } throw new AssertionError("material did not reach " + expected); }
  @SuppressWarnings("unchecked") private KnowledgeMap candidate(UUID materialId) { return (KnowledgeMap) s.listKnowledgeMaps(staffA.id(), first.id()).stream().filter(row -> ((Material)row.get("material")).id().equals(materialId)).findFirst().orElseThrow().get("candidate"); }
  @SuppressWarnings("unchecked") private KnowledgeMap approved(UUID materialId) { return (KnowledgeMap) s.listKnowledgeMaps(staffA.id(), first.id()).stream().filter(row -> ((Material)row.get("material")).id().equals(materialId)).findFirst().orElseThrow().get("approved"); }
  private void waitForMap(UUID materialId, KnowledgeMapJobStatus expected) { long deadline = System.nanoTime() + 2_000_000_000L; while (System.nanoTime() < deadline) { Object job=s.listKnowledgeMaps(staffA.id(), first.id()).stream().filter(row -> ((Material)row.get("material")).id().equals(materialId)).findFirst().orElseThrow().get("job"); if (job instanceof KnowledgeMapJob actual && actual.status() == expected) return; try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); } } throw new AssertionError("Knowledge Map job did not reach " + expected); }
  private static byte[] pdf(boolean text) { return generatedPdf(text ? "Hello" : ""); }
  private static byte[] twoPagePdf() { return generatedPdf("Hello", "Second page"); }
  private static byte[] generatedPdf(String... pages) { try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) { for (String text : pages) { PDPage page = new PDPage(); document.addPage(page); if (!text.isEmpty()) try (PDPageContentStream stream = new PDPageContentStream(document, page)) { stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12); stream.newLineAtOffset(72, 720); stream.showText(text); stream.endText(); } } document.save(output); return output.toByteArray(); } catch (Exception e) { throw new AssertionError(e); } }
  private static byte[] danglingPagesPdf() { return classicPdf(new String[] {"<</Type /Catalog /Pages 99 0 R>>", "<</Type /Page /Parent 99 0 R>>"}); }
  private static byte[] xrefStreamPdf(boolean flate, boolean corruptRootOffset) { return xrefStreamPdf(flate, corruptRootOffset, false); }
  private static byte[] xrefStreamPdf(boolean flate, boolean corruptRootOffset, boolean omitXrefSelf) { String[] bodies = {"<</Type /Catalog /Pages 2 0 R>>", "<</Type /Pages /Kids [3 0 R] /Count 1>>", "<</Type /Page /Parent 2 0 R>>", "<< /Length 0 >>\nstream\nBT (Hello) Tj ET\nendstream"}; StringBuilder prefix = new StringBuilder("%PDF-1.5\n"); int[] offsets = new int[6]; for (int i=0;i<bodies.length;i++){offsets[i+1]=prefix.length();prefix.append(i+1).append(" 0 obj\n").append(bodies[i]).append("\nendobj\n");} offsets[5]=prefix.length(); byte[] table=new byte[6*7]; for(int i=1;i<6;i++){int offset=offsets[i]+(corruptRootOffset&&i==1?1:0);int at=i*7;if (omitXrefSelf && i==5) continue;table[at]=1;table[at+1]=(byte)(offset>>>24);table[at+2]=(byte)(offset>>>16);table[at+3]=(byte)(offset>>>8);table[at+4]=(byte)offset;} byte[] data=flate?deflate(table):table; prefix.append("5 0 obj\n<</Type /XRef /Size 6 /Root 1 0 R /W [1 4 2] /Length ").append(data.length).append(flate?" /Filter /FlateDecode>>\nstream\n":" >>\nstream\n").append(new String(data,StandardCharsets.ISO_8859_1)).append("\nendstream\nendobj\nstartxref\n").append(offsets[5]).append("\n%%EOF\n");return prefix.toString().getBytes(StandardCharsets.ISO_8859_1); }
  private static byte[] hybridXrefPdf() { String direct = new String(xrefStreamPdf(true, false, true), StandardCharsets.ISO_8859_1); int marker = direct.lastIndexOf("startxref\n") + "startxref\n".length(); int streamOffset = Integer.parseInt(direct.substring(marker, direct.indexOf('\n', marker))); String prefix = direct.substring(0, direct.lastIndexOf("startxref\n")); int xrefOffset = prefix.length(); return (prefix + "xref\n0 0\ntrailer\n<</Size 6 /Root 1 0 R /XRefStm " + streamOffset + ">>\nstartxref\n" + xrefOffset + "\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1); }
  private static byte[] deflate(byte[] value) { try { ByteArrayOutputStream out=new ByteArrayOutputStream(); try(DeflaterOutputStream zip=new DeflaterOutputStream(out)){zip.write(value);} return out.toByteArray(); } catch(Exception e){throw new AssertionError(e);} }
  private static byte[] pdfWithoutXref() { return "%PDF-1.4\n1 0 obj\n<</Type /Catalog /Pages 2 0 R>>\nendobj\n2 0 obj\n<</Type /Pages /Kids [3 0 R] /Count 1>>\nendobj\n3 0 obj\n<</Type /Page /Parent 2 0 R>>\nendobj\ntrailer\n<</Root 1 0 R>>\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1); }
  private static byte[] classicPdf(String[] bodies) { StringBuilder pdf = new StringBuilder("%PDF-1.4\n"); int[] offsets = new int[bodies.length + 1]; for (int i = 0; i < bodies.length; i++) { String body = withStreamLength(bodies[i]); offsets[i + 1] = pdf.length(); pdf.append(i + 1).append(" 0 obj\n").append(body).append("\nendobj\n"); } int xref = pdf.length(); pdf.append("xref\n0 ").append(bodies.length + 1).append("\n0000000000 65535 f \n"); for (int i = 1; i <= bodies.length; i++) pdf.append(String.format("%010d 00000 n \n", offsets[i])); pdf.append("trailer\n<</Size ").append(bodies.length + 1).append(" /Root 1 0 R>>\nstartxref\n").append(xref).append("\n%%EOF\n"); return pdf.toString().getBytes(StandardCharsets.ISO_8859_1); }
  private static String withStreamLength(String body) { int start = body.indexOf("stream\n"); int end = body.indexOf("\nendstream", start); if (start < 0 || end < 0) return body; int length = body.substring(start + 7, end).getBytes(StandardCharsets.ISO_8859_1).length; return body.replace("/Length 0", "/Length " + length); }
  private static void expect(int status, Runnable action, String name) { try { action.run(); throw new AssertionError(name+": request unexpectedly succeeded"); } catch(ApiException e) { if(e.status!=status) throw new AssertionError(name+": expected "+status+", got "+e.status); } }
  private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
}
