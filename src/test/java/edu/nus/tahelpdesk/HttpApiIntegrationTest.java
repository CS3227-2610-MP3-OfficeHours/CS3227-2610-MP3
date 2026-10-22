package edu.nus.tahelpdesk;

import com.sun.net.httpserver.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/** Loopback coverage for JSON request-size enforcement before API parsing. */
public final class HttpApiIntegrationTest {
  private final PlatformService service = new PlatformService();
  private final HttpServer server;
  private final HttpClient client = HttpClient.newHttpClient();
  private final URI base;

  private HttpApiIntegrationTest() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", new HttpApi(service));
    server.start();
    base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  public static void main(String[] args) throws Exception {
    HttpApiIntegrationTest test = new HttpApiIntegrationTest();
    try { test.run(); System.out.println("HTTP API integration tests: PASS"); }
    finally { test.server.stop(0); }
  }

  private void run() throws Exception {
    String normalPrefix = "{\"username\":\"", normalSuffix = "\"}";
    String normalAtLimit = normalPrefix + "a".repeat(HttpApi.MAX_JSON_BODY_BYTES - normalPrefix.length() - normalSuffix.length()) + normalSuffix;
    check(normalAtLimit.getBytes(StandardCharsets.UTF_8).length == HttpApi.MAX_JSON_BODY_BYTES, "normal fixture is exactly one MiB");
    check(send("POST", "/api/auth/signup", normalAtLimit, null).statusCode() == 422, "normal JSON body at limit reaches validation");
    check(send("POST", "/api/auth/signup", "{" + "x".repeat(HttpApi.MAX_JSON_BODY_BYTES) + "}", null).statusCode() == 413, "declared normal JSON body over limit is rejected");

    HttpResponse<String> signup = send("POST", "/api/auth/signup", "{\"username\":\"api.staff\",\"password\":\"correct horse battery\",\"role\":\"staff\"}", null);
    check(signup.statusCode() == 201, "staff signup succeeds");
    String cookie = signup.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    Object user = Json.object(signup.body()).get("user");
    UUID staffId = UUID.fromString(String.valueOf(((java.util.Map<?, ?>) user).get("id")));
    Domain.Course course = service.createCourse(staffId, "BOUNDARY", "Body boundary checks");
    handlerSizeBoundary();

    String uploadAtLimit = uploadBodyExactlyAtLimit();
    check(send("POST", "/api/courses/" + course.id() + "/materials", uploadAtLimit, cookie).statusCode() == 201, "upload body at limit reaches existing upload processing");
  }

  private void handlerSizeBoundary() throws Exception {
    HttpApi handler = new HttpApi(service);
    TestExchange signedUp = new TestExchange("POST", "/api/auth/signup", "{\"username\":\"handler.staff\",\"password\":\"correct horse battery\",\"role\":\"staff\"}".getBytes(StandardCharsets.UTF_8), null, null);
    handler.handle(signedUp);
    check(signedUp.status == 201, "handler test staff signup succeeds");
    String cookie = signedUp.responseHeaders.getFirst("Set-Cookie").split(";", 2)[0];
    Object user = Json.object(signedUp.response()).get("user");
    UUID staffId = UUID.fromString(String.valueOf(((java.util.Map<?, ?>) user).get("id")));
    Domain.Course course = service.createCourse(staffId, "HANDLER", "Handler body checks");
    byte[] oversizedSignup = oversizedSignupBody();
    assertHandler413(handler, "/api/auth/signup", null, null, oversizedSignup, "normal JSON without Content-Length");
    assertHandler413(handler, "/api/auth/signup", "0", null, oversizedSignup, "normal JSON with understated Content-Length");
    expect(401, () -> service.authenticate("oversized.normal", "correct horse battery"), "handler-level oversized signup does not create a user");
    String materialPath = "/api/courses/" + course.id() + "/materials";
    assertHandler413(handler, materialPath, Integer.toString(HttpApi.MAX_MATERIAL_UPLOAD_BODY_BYTES + 1), cookie,
        new FailOnReadInputStream(), "declared material upload body over limit");
    byte[] oversizedUpload = new byte[HttpApi.MAX_MATERIAL_UPLOAD_BODY_BYTES + 1];
    assertHandler413(handler, materialPath, null, cookie, oversizedUpload, "material upload without Content-Length");
    assertHandler413(handler, materialPath, "0", cookie, oversizedUpload, "material upload with understated Content-Length");
    check(service.listMaterials(staffId, course.id()).isEmpty(), "handler-level over-limit uploads do not mutate materials");
  }

  private static byte[] oversizedSignupBody() {
    String prefix = "{\"username\":\"oversized.normal\",\"password\":\"correct horse battery\",\"role\":\"staff\",\"padding\":\"", suffix = "\"}";
    return (prefix + "x".repeat(HttpApi.MAX_JSON_BODY_BYTES + 1 - prefix.length() - suffix.length()) + suffix).getBytes(StandardCharsets.UTF_8);
  }

  private static void assertHandler413(HttpApi handler, String path, String contentLength, String cookie, byte[] body, String name) throws Exception {
    assertHandler413(handler, path, contentLength, cookie, new ByteArrayInputStream(body), name);
  }

  private static void assertHandler413(HttpApi handler, String path, String contentLength, String cookie, InputStream body, String name) throws Exception {
    TestExchange exchange = new TestExchange("POST", path, body, contentLength, cookie);
    handler.handle(exchange);
    check(exchange.status == 413, name + " returns HTTP 413");
    Object error = Json.object(exchange.response()).get("error");
    java.util.Map<?, ?> safeError = (java.util.Map<?, ?>) error;
    check(safeError.size() == 2, name + " returns only the safe error fields");
    check("request_too_large".equals(safeError.get("code")), name + " returns the safe error code");
    check("The request is too large.".equals(safeError.get("message")), name + " returns the safe error message");
  }

  private String uploadBodyExactlyAtLimit() throws Exception {
    String encodedPdf = Base64.getEncoder().encodeToString(imageOnlyPdf());
    String prefix = "{\"filename\":\"scan.pdf\",\"contentType\":\"application/pdf\",\"contentBase64\":\"" + encodedPdf + "\",\"padding\":\"";
    String suffix = "\"}";
    String body = prefix + "x".repeat(HttpApi.MAX_MATERIAL_UPLOAD_BODY_BYTES - prefix.getBytes(StandardCharsets.UTF_8).length - suffix.length()) + suffix;
    check(body.getBytes(StandardCharsets.UTF_8).length == HttpApi.MAX_MATERIAL_UPLOAD_BODY_BYTES, "upload fixture is exactly 28 MiB");
    return body;
  }

  private HttpResponse<String> send(String method, String path, String body, String cookie) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(20)).method(method, HttpRequest.BodyPublishers.ofString(body));
    if (cookie != null) request.header("Cookie", cookie);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static byte[] imageOnlyPdf() throws Exception {
    try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) { document.addPage(new PDPage()); document.save(output); return output.toByteArray(); }
  }
  private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
  private static void expect(int status, ThrowingRunnable action, String name) throws Exception { try { action.run(); throw new AssertionError(name + " unexpectedly succeeded"); } catch (ApiException error) { if (error.status != status) throw new AssertionError(name + " returned " + error.status); } }
  @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
  private static final class FailOnReadInputStream extends InputStream {
    @Override public int read() { throw new AssertionError("declared oversized request body must not be read"); }
  }
  private static final class TestExchange extends HttpExchange {
    private final Headers requestHeaders = new Headers(), responseHeaders = new Headers();
    private final String method; private final URI uri; private final InputStream request;
    private final ByteArrayOutputStream response = new ByteArrayOutputStream(); private int status = -1;
    TestExchange(String method, String path, byte[] body, String contentLength, String cookie) { this(method,path,new ByteArrayInputStream(body),contentLength,cookie); }
    TestExchange(String method, String path, InputStream body, String contentLength, String cookie) { this.method=method;uri=URI.create(path);request=body;if(contentLength!=null)requestHeaders.set("Content-Length",contentLength);if(cookie!=null)requestHeaders.set("Cookie",cookie); }
    String response() { return response.toString(StandardCharsets.UTF_8); }
    @Override public Headers getRequestHeaders() { return requestHeaders; }
    @Override public Headers getResponseHeaders() { return responseHeaders; }
    @Override public URI getRequestURI() { return uri; }
    @Override public String getRequestMethod() { return method; }
    @Override public HttpContext getHttpContext() { return null; }
    @Override public void close() { }
    @Override public InputStream getRequestBody() { return request; }
    @Override public ByteArrayOutputStream getResponseBody() { return response; }
    @Override public void sendResponseHeaders(int code, long length) { status=code; }
    @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 1); }
    @Override public int getResponseCode() { return status; }
    @Override public InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 2); }
    @Override public String getProtocol() { return "HTTP/1.1"; }
    @Override public Object getAttribute(String name) { return null; }
    @Override public void setAttribute(String name, Object value) { }
    @Override public void setStreams(java.io.InputStream input, java.io.OutputStream output) { }
    @Override public HttpPrincipal getPrincipal() { return null; }
  }
}
