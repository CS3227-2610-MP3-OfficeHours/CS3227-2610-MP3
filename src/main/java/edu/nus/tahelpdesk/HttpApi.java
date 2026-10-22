package edu.nus.tahelpdesk;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.*;
import java.util.stream.Collectors;
import static edu.nus.tahelpdesk.Domain.*;

public final class HttpApi implements HttpHandler {
  static final int MAX_JSON_BODY_BYTES = 1_048_576;
  static final int MAX_MATERIAL_UPLOAD_BODY_BYTES = 29_360_128;
  private final PlatformService service; private final SessionManager sessions; private final boolean secureCookies;
  public HttpApi(PlatformService service) { this(service, false); }
  public HttpApi(PlatformService service, boolean secureCookies) { this.service = service; this.sessions = new SessionManager(); this.secureCookies = secureCookies; }
  @Override public void handle(HttpExchange exchange) throws IOException {
    try { dispatch(exchange); } catch (ApiException e) { respond(exchange, e.status, Map.of("error", Map.of("code", e.code, "message", e.getMessage()))); }
      catch (IllegalArgumentException e) { respond(exchange, 404, Map.of("error", Map.of("code", "not_found", "message", "The requested resource is unavailable."))); }
      catch (Exception e) { respond(exchange, 500, Map.of("error", Map.of("code", "service_failure", "message", "The request could not be completed."))); }
  }
  private void dispatch(HttpExchange x) throws IOException {
    String method=x.getRequestMethod(), path=x.getRequestURI().getPath(); String[] part=path.split("/");
    if ("GET".equals(method) && "/health".equals(path)) { respond(x, 200, Map.of("status", "ok")); return; }
    if ("POST".equals(method) && "/api/auth/signup".equals(path)) { Map<String,Object>b=body(x); User u=service.signup(Json.string(b,"username"),Json.string(b,"password"),Json.string(b,"role")); signedIn(x,u,201); return; }
    if ("POST".equals(method) && "/api/auth/signin".equals(path)) { Map<String,Object>b=body(x); signedIn(x,service.authenticate(Json.string(b,"username"),Json.string(b,"password")),200); return; }
    if ("POST".equals(method) && "/api/auth/signout".equals(path)) { sessions.revoke(token(x)); respond(x,204,null); return; }
    UUID actor = sessions.require(token(x));
    if ("GET".equals(method) && "/api/me".equals(path)) { respond(x,200, Map.of("user", user(service.current(actor)))); return; }
    if ("GET".equals(method) && "/api/courses".equals(path)) { respond(x,200, Map.of("courses", service.listCourses(actor).stream().map(this::course).toList())); return; }
    if ("POST".equals(method) && "/api/courses".equals(path)) { Map<String,Object>b=body(x); respond(x,201,Map.of("course",course(service.createCourse(actor,Json.string(b,"code"),Json.string(b,"name")))));return; }
    if (part.length >= 4 && "api".equals(part[1]) && "courses".equals(part[2])) { courseRoutes(x, actor, part, method); return; }
    throw ApiException.hidden();
  }
  private void courseRoutes(HttpExchange x, UUID actor, String[] p, String method) throws IOException {
    UUID courseId=id(p[3]);
    if(p.length==4 && "GET".equals(method)){respond(x,200,Map.of("course",course(service.getCourse(actor,courseId))));return;}
    if(p.length==4 && "PATCH".equals(method)){respond(x,200,Map.of("course",course(service.renameCourse(actor,courseId,Json.string(body(x),"name")))));return;}
    if(p.length==4 && "DELETE".equals(method)){service.deleteCourse(actor,courseId);respond(x,204,null);return;}
    if(p.length==5 && "forum-tags".equals(p[4]) && "GET".equals(method)){respond(x,200,Map.of("tags",service.listForumTags(actor,courseId).stream().map(this::forumTag).toList()));return;}
    if(p.length==5 && "forum-tags".equals(p[4]) && "POST".equals(method)){Map<String,Object>b=body(x);respond(x,201,Map.of("tag",forumTag(service.createForumTag(actor,courseId,Json.string(b,"name"),Json.string(b,"color")))));return;}
    if(p.length==6 && "forum-tags".equals(p[4])){UUID tagId=id(p[5]);if("PATCH".equals(method)){Map<String,Object>b=body(x);respond(x,200,Map.of("tag",forumTag(service.editForumTag(actor,courseId,tagId,Json.string(b,"name"),Json.string(b,"color")))));return;}if("DELETE".equals(method)){service.deleteForumTag(actor,courseId,tagId);respond(x,204,null);return;}}
    if(p.length==5 && "members".equals(p[4]) && "GET".equals(method)){respond(x,200,Map.of("members",service.listMembers(actor,courseId).stream().map(m->member(m)).toList()));return;}
    if(p.length==5 && "members".equals(p[4]) && "POST".equals(method)){respond(x,201,Map.of("membership",member(service.addMember(actor,courseId,Json.string(body(x),"username")))));return;}
    if(p.length==6 && "members".equals(p[4]) && "DELETE".equals(method)){service.removeStudent(actor,courseId,id(p[5]));respond(x,204,null);return;}
    if(p.length==5 && "posts".equals(p[4]) && "GET".equals(method)){UUID tagId=queryTag(x.getRequestURI().getRawQuery());respond(x,200,Map.of("posts",service.listPosts(actor,courseId,tagId).stream().map(this::post).toList()));return;}
    if(p.length==5 && "posts".equals(p[4]) && "POST".equals(method)){Map<String,Object>b=body(x);respond(x,201,Map.of("post",post(service.createPost(actor,courseId,Json.string(b,"title"),Json.string(b,"description"),requiredUuid(b,"tagId")))));return;}
    if(p.length>=6 && "posts".equals(p[4])) { postRoutes(x,actor,courseId,id(p[5]),p,method);return; }
    if(p.length==5 && "materials".equals(p[4]) && "GET".equals(method)){respond(x,200,Map.of("materials",service.listMaterials(actor,courseId).stream().map(this::material).toList()));return;}
    if(p.length==5 && "materials".equals(p[4]) && "POST".equals(method)){Map<String,Object>b=body(x,MAX_MATERIAL_UPLOAD_BODY_BYTES); Material m=service.uploadMaterial(actor,courseId,Json.string(b,"filename"),Json.string(b,"contentType"),base64(Json.string(b,"contentBase64")));respond(x,201,Map.of("material",material(m)));return;}
    if(p.length==7 && "materials".equals(p[4]) && "retry".equals(p[6]) && "POST".equals(method)){respond(x,200,Map.of("material",material(service.retryMaterial(actor,courseId,id(p[5])))));return;}
    if(p.length==6 && "materials".equals(p[4]) && "DELETE".equals(method)){service.deleteMaterial(actor,courseId,id(p[5]));respond(x,204,null);return;}
    if(p.length==5 && "resources".equals(p[4]) && "GET".equals(method)){respond(x,200,Map.of("resources",service.listResources(actor,courseId).stream().map(this::resource).toList()));return;}
    if(p.length==7 && "resources".equals(p[4]) && "download".equals(p[6]) && "GET".equals(method)){download(x,service.downloadResource(actor,courseId,id(p[5])));return;}
    if(p.length==5 && "knowledge-maps".equals(p[4]) && "GET".equals(method)){respond(x,200,Map.of("maps",service.listKnowledgeMaps(actor,courseId).stream().map(this::knowledgeMapState).toList(),"fallbackConfigured",service.knowledgeMapFallbackConfigured(actor,courseId)));return;}
    if(p.length==6 && "knowledge-maps".equals(p[4]) && "generate-all".equals(p[5]) && "POST".equals(method)){respond(x,202,Map.of("jobs",service.generateAllKnowledgeMaps(actor,courseId).stream().map(this::knowledgeMapJob).toList()));return;}
    if(p.length==8 && "materials".equals(p[4]) && "knowledge-map".equals(p[6]) && "generate".equals(p[7]) && "POST".equals(method)){respond(x,202,Map.of("job",knowledgeMapJob(service.generateKnowledgeMap(actor,courseId,id(p[5])))));return;}
    if(p.length==8 && "materials".equals(p[4]) && "knowledge-map".equals(p[6]) && "approve".equals(p[7]) && "POST".equals(method)){respond(x,200,Map.of("knowledgeMap",knowledgeMap(service.approveKnowledgeMap(actor,courseId,id(p[5])))));return;}
    if(p.length==8 && "materials".equals(p[4]) && "knowledge-map".equals(p[6]) && "reject".equals(p[7]) && "POST".equals(method)){service.rejectKnowledgeMap(actor,courseId,id(p[5]));respond(x,204,null);return;}
    throw ApiException.hidden();
  }
  private void postRoutes(HttpExchange x, UUID actor, UUID courseId, UUID postId, String[] p, String method) throws IOException {
    if(p.length==6 && "GET".equals(method)){respond(x,200,Map.of("post",post(service.getPost(actor,courseId,postId)),"comments",service.listComments(actor,courseId,postId).stream().map(this::comment).toList()));return;}
    if(p.length==6 && "PATCH".equals(method)){Map<String,Object>b=body(x);respond(x,200,Map.of("post",post(service.editPost(actor,courseId,postId,Json.string(b,"title"),Json.string(b,"description"),requiredUuid(b,"tagId")))));return;}
    if(p.length==6 && "DELETE".equals(method)){service.deletePost(actor,courseId,postId);respond(x,204,null);return;}
    if(p.length==7 && "comments".equals(p[6]) && "POST".equals(method)){respond(x,201,Map.of("comment",comment(service.createComment(actor,courseId,postId,Json.string(body(x),"body")))));return;}
    if(p.length==8 && "comments".equals(p[6])) { UUID commentId=id(p[7]); if("PATCH".equals(method)){respond(x,200,Map.of("comment",comment(service.editComment(actor,courseId,postId,commentId,Json.string(body(x),"body")))));return;} if("DELETE".equals(method)){service.deleteComment(actor,courseId,postId,commentId);respond(x,204,null);return;} }
    throw ApiException.hidden();
  }
  private void signedIn(HttpExchange x, User u, int status) throws IOException { String token=sessions.create(u.id()); x.getResponseHeaders().add("Set-Cookie","ta_session="+token+"; Path=/; HttpOnly; SameSite=Strict" + (secureCookies ? "; Secure" : "")); respond(x,status,Map.of("user",user(u))); }
  private Map<String,Object> user(User u){return Map.of("id",u.id().toString(),"username",u.username(),"role",u.role().name());}
  private Map<String,Object> course(Course c){return Map.of("id",c.id().toString(),"code",c.code(),"name",c.name());}
  private Map<String,Object> member(Membership m){User u=service.author(m.userId());return Map.of("user",user(u),"membershipRole",m.role().name());}
  private Map<String,Object> forumTag(ForumTag t){return Map.of("id",t.id().toString(),"courseId",t.courseId().toString(),"name",t.name(),"color",t.color(),"isDefault",t.isDefault());}
  private Map<String,Object> post(Post p){User u=service.author(p.authorId()); Map<String,Object> out=new LinkedHashMap<>();out.put("id",p.id().toString());out.put("courseId",p.courseId().toString());out.put("title",p.title());out.put("description",p.description());out.put("tag",forumTag(service.forumTag(p.tagId())));out.put("renderedDescription",Markdown.render(p.description()));out.put("author",user(u));out.put("createdAt",p.createdAt().toString());out.put("updatedAt",p.updatedAt().toString());return out;}
  private Map<String,Object> comment(Comment c){User u=service.author(c.authorId());Map<String,Object>out=new LinkedHashMap<>();out.put("id",c.id().toString());out.put("postId",c.postId().toString());out.put("body",c.body());out.put("renderedBody",Markdown.render(c.body()));out.put("author",user(u));out.put("createdAt",c.createdAt().toString());out.put("updatedAt",c.updatedAt().toString());return out;}
  private Map<String,Object> material(Material m){Map<String,Object> out=new LinkedHashMap<>();out.put("id",m.id().toString());out.put("courseId",m.courseId().toString());out.put("filename",m.filename());out.put("status",m.status().name());out.put("pageCount",m.pageCount());out.put("failureMessage",m.failureMessage());out.put("createdAt",m.createdAt().toString());out.put("updatedAt",m.updatedAt().toString());return out;}
  private Map<String,Object> resource(Material m){return Map.of("id",m.id().toString(),"filename",m.filename(),"downloadPath","/api/courses/"+m.courseId()+"/resources/"+m.id()+"/download");}
  private Map<String,Object> knowledgeMap(KnowledgeMap map){Map<String,Object> out=new LinkedHashMap<>();out.put("id",map.id().toString());out.put("courseId",map.courseId().toString());out.put("materialId",map.materialId().toString());out.put("status",map.status().name());out.put("content",map.content());out.put("verificationMessage",map.verificationMessage());out.put("requestedBy",map.requestedBy().toString());out.put("reviewedBy",map.reviewedBy()==null?null:map.reviewedBy().toString());out.put("reviewedAt",map.reviewedAt()==null?null:map.reviewedAt().toString());out.put("createdAt",map.createdAt().toString());out.put("updatedAt",map.updatedAt().toString());return out;}
  private Map<String,Object> knowledgeMapJob(KnowledgeMapJob job){Map<String,Object> out=new LinkedHashMap<>();out.put("id",job.id().toString());out.put("materialId",job.materialId().toString());out.put("status",job.status().name());out.put("failureMessage",job.failureMessage());out.put("createdAt",job.createdAt().toString());out.put("updatedAt",job.updatedAt().toString());return out;}
  @SuppressWarnings("unchecked") private Map<String,Object> knowledgeMapState(Map<String,Object> state){Map<String,Object> out=new LinkedHashMap<>();out.put("material",material((Material)state.get("material"))); Object candidate=state.get("candidate"), approved=state.get("approved"), job=state.get("job");out.put("candidate",candidate==null?null:knowledgeMap((KnowledgeMap)candidate));out.put("approved",approved==null?null:knowledgeMap((KnowledgeMap)approved));out.put("job",job==null?null:knowledgeMapJob((KnowledgeMapJob)job));return out;}
  private static UUID requiredUuid(Map<String,Object> body,String key){try{Object value=body.get(key);if(!(value instanceof String s))throw new Exception();return UUID.fromString(s);}catch(Exception e){throw ApiException.validation("Choose a current tag for this course.");}}
  private static UUID queryTag(String raw){if(raw==null)return null;for(String pair:raw.split("&")){String[] kv=pair.split("=",2);if(kv[0].equals("tagId")){try{return UUID.fromString(URLDecoder.decode(kv.length==2?kv[1]:"",StandardCharsets.UTF_8));}catch(Exception e){throw ApiException.validation("Choose a current tag for this course.");}}}return null;}
  private static UUID id(String text){try{return UUID.fromString(text);}catch(Exception e){throw ApiException.hidden();}}
  private static Map<String,Object> body(HttpExchange x)throws IOException{return body(x,MAX_JSON_BODY_BYTES);}
  private static Map<String,Object> body(HttpExchange x,int limit)throws IOException{return Json.object(new String(readBody(x.getRequestBody(),x.getRequestHeaders().getFirst("Content-Length"),limit),StandardCharsets.UTF_8));}
  static byte[] readBody(InputStream input, String contentLength, int limit) throws IOException {
    if (limit < 0) throw new IllegalArgumentException("limit must be non-negative");
    if (contentLength != null) try { if (Long.parseLong(contentLength) > limit) throw ApiException.requestTooLarge(); } catch (NumberFormatException ignored) { }
    ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
    byte[] buffer = new byte[8192]; int remaining = limit + 1;
    while (remaining > 0) { int count = input.read(buffer, 0, Math.min(buffer.length, remaining)); if (count < 0) break; output.write(buffer, 0, count); remaining -= count; }
    if (output.size() > limit) throw ApiException.requestTooLarge();
    return output.toByteArray();
  }
  private static byte[] base64(String text) { try { return text == null ? null : Base64.getDecoder().decode(text); } catch (IllegalArgumentException e) { throw ApiException.validation("This file cannot be used. Upload an unprotected, valid PDF."); } }
  private static String token(HttpExchange x){String auth=x.getRequestHeaders().getFirst("Authorization");if(auth!=null&&auth.startsWith("Bearer "))return auth.substring(7);String cookie=x.getRequestHeaders().getFirst("Cookie");if(cookie!=null)for(String piece:cookie.split(";")){String trimmed=piece.trim();if(trimmed.startsWith("ta_session="))return trimmed.substring(11);}return null;}
  private static void respond(HttpExchange x,int status,Object value)throws IOException{byte[]bytes=value==null?new byte[0]:Json.write(value).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.getResponseHeaders().set("X-Content-Type-Options","nosniff");x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(status,bytes.length);try(OutputStream out=x.getResponseBody()){out.write(bytes);}}
  private static void download(HttpExchange x, Material material) throws IOException { byte[] bytes=material.content(); x.getResponseHeaders().set("Content-Type","application/pdf");x.getResponseHeaders().set("Content-Disposition","attachment; filename=\""+material.filename().replace("\"", "'")+"\"");x.getResponseHeaders().set("X-Content-Type-Options","nosniff");x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(200,bytes.length);try(OutputStream out=x.getResponseBody()){out.write(bytes);} }
}
