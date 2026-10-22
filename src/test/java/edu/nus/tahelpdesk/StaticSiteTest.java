package edu.nus.tahelpdesk;

import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * Static-serving tests for the single-origin production deployment. Raw sockets
 * are used for the traversal cases so an HTTP client cannot normalize the path
 * away before the server sees it.
 */
public final class StaticSiteTest {
  public static void main(String[] args) throws Exception {
    Path temp = Files.createTempDirectory("ta-static-test");
    Path root = Files.createDirectories(temp.resolve("dist"));
    Files.createDirectories(root.resolve("assets"));
    Files.writeString(root.resolve("index.html"), "<!doctype html><div id=\"root\"></div>");
    Files.writeString(root.resolve("assets/index-abc123.js"), "export const client = 1;");
    Files.writeString(root.resolve("assets/index-abc123.css"), ".app{color:#000}");
    // A file beside the client build that a traversal attempt would try to read.
    Files.writeString(temp.resolve("secret.txt"), "PRODUCTION-SECRET-MUST-NOT-LEAK");

    StaticSite site = StaticSite.openOrNull(root.toString());
    check(site != null, "a built client directory opens");
    check(StaticSite.openOrNull(null) == null, "no configured directory serves the API only");
    check(StaticSite.openOrNull(temp.resolve("missing").toString()) == null, "a missing directory serves the API only");

    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.createContext("/", new HttpApi(SeedData.create(), false, site));
    server.start();
    int port = server.getAddress().getPort();
    try {
      Response shell = request(port, "GET / HTTP/1.1");
      check(shell.status == 200 && shell.body.contains("id=\"root\""), "the root path serves the client shell");
      check(shell.headers.contains("content-type: text/html"), "the shell is served as HTML");
      check(shell.headers.contains("cache-control: no-store"), "the shell is never cached");
      check(shell.headers.contains("x-content-type-options: nosniff"), "the shell sets nosniff");

      Response script = request(port, "GET /assets/index-abc123.js HTTP/1.1");
      check(script.status == 200 && script.body.contains("export const client"), "a hashed asset is served");
      check(script.headers.contains("content-type: text/javascript"), "a script keeps its media type");
      check(script.headers.contains("immutable"), "a content-hashed asset is cacheable");

      Response style = request(port, "GET /assets/index-abc123.css HTTP/1.1");
      check(style.status == 200 && style.headers.contains("content-type: text/css"), "a stylesheet keeps its media type");

      Response route = request(port, "GET /courses/some-client-route HTTP/1.1");
      check(route.status == 200 && route.body.contains("id=\"root\""), "an unknown client route serves the shell");

      // Traversal: none of these may reach the file sitting next to the build
      // directory. A safe answer is either the client shell or a client error
      // from the HTTP layer, never the escaped file's bytes.
      for (String attack : new String[] {
          "GET /../secret.txt HTTP/1.1",
          "GET /assets/../../secret.txt HTTP/1.1",
          "GET /%2e%2e%2fsecret.txt HTTP/1.1",
          "GET /..%2f..%2fsecret.txt HTTP/1.1",
          "GET /assets/..\\..\\secret.txt HTTP/1.1",
          "GET /assets/%5c..%5c..%5csecret.txt HTTP/1.1",
          "GET //etc/passwd HTTP/1.1" }) {
        Response denied = request(port, attack);
        check(!denied.body.contains("PRODUCTION-SECRET-MUST-NOT-LEAK"), "traversal must not leak a file outside the build: " + attack);
        boolean servedShell = denied.status == 200 && denied.body.contains("id=\"root\"");
        boolean refused = denied.status == 400 || denied.status == 404;
        check(servedShell || refused, "traversal is answered safely (shell or client error), got " + denied.status + ": " + attack);
      }

      Response head = request(port, "HEAD /assets/index-abc123.js HTTP/1.1");
      check(head.status == 200 && head.body.isEmpty(), "HEAD returns headers without a body");

      // The client build must not shadow the API or its session check.
      Response health = request(port, "GET /health HTTP/1.1");
      check(health.status == 200 && health.body.contains("\"ok\""), "health stays a JSON endpoint");
      check(request(port, "GET /api/me HTTP/1.1").status == 401, "an API path still requires a session");
      check(request(port, "GET /api/courses HTTP/1.1").status == 401, "an API collection still requires a session");
      check(request(port, "GET /api HTTP/1.1").status == 401, "the bare API prefix is not served as the shell");
      check(request(port, "POST /api/courses HTTP/1.1").status == 401, "a write still requires a session");
    } finally { server.stop(0); }

    // Without a client build the server stays API-only and unknown paths are hidden.
    HttpServer apiOnly = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    apiOnly.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    apiOnly.createContext("/", new HttpApi(SeedData.create(), false, null));
    apiOnly.start();
    try {
      check(request(apiOnly.getAddress().getPort(), "GET /health HTTP/1.1").status == 200, "API-only health works");
      check(request(apiOnly.getAddress().getPort(), "GET / HTTP/1.1").status == 401, "API-only serves no client shell");
    } finally { apiOnly.stop(0); }

    System.out.println("Static site tests: PASS");
  }

  private record Response(int status, String headers, String body) {}

  /** Sends one raw request line so the path reaches the server exactly as written. */
  private static Response request(int port, String requestLine) throws Exception {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.setSoTimeout(10_000);
      OutputStream out = socket.getOutputStream();
      out.write((requestLine + "\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
      out.flush();
      InputStream in = socket.getInputStream();
      String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      int split = raw.indexOf("\r\n\r\n");
      String head = split < 0 ? raw : raw.substring(0, split);
      String body = split < 0 ? "" : raw.substring(split + 4);
      int status = Integer.parseInt(head.split("\r\n")[0].split(" ")[1]);
      return new Response(status, head.toLowerCase(java.util.Locale.ROOT), body);
    }
  }

  private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
