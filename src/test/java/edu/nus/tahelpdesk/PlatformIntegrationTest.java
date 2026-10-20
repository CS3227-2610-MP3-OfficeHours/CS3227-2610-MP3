package edu.nus.tahelpdesk;

import static edu.nus.tahelpdesk.Domain.*;
import java.util.UUID;

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
    s.deleteCourse(staffA.id(),first.id());
    expect(404,()->s.getCourse(staffA.id(),first.id()),"deleted course inaccessible to staff");
    expect(404,()->s.getPost(staffA.id(),first.id(),staffPost.id()),"deleted course forum inaccessible");
    check(s.listPosts(staffA.id(),second.id()).isEmpty(),"other course remains isolated");
    check(removedPost.id()!=null,"test created post before removal");
  }
  private static void expect(int status, Runnable action, String name) { try { action.run(); throw new AssertionError(name+": request unexpectedly succeeded"); } catch(ApiException e) { if(e.status!=status) throw new AssertionError(name+": expected "+status+", got "+e.status); } }
  private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
}
