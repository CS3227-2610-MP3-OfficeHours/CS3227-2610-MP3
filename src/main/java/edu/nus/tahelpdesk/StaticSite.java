package edu.nus.tahelpdesk;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Serves the built client from one directory so the API and the browser share a
 * single origin in production. Request paths are resolved inside the configured
 * root and anything escaping it is refused, so a crafted path cannot read the
 * image's own files. Unknown paths return the client shell rather than a 404 so
 * client-side routing keeps working.
 */
public final class StaticSite {
  private static final Map<String,String> TYPES = Map.ofEntries(
      Map.entry("html","text/html; charset=utf-8"), Map.entry("js","text/javascript; charset=utf-8"),
      Map.entry("css","text/css; charset=utf-8"), Map.entry("json","application/json; charset=utf-8"),
      Map.entry("svg","image/svg+xml"), Map.entry("png","image/png"), Map.entry("jpg","image/jpeg"),
      Map.entry("jpeg","image/jpeg"), Map.entry("gif","image/gif"), Map.entry("webp","image/webp"),
      Map.entry("ico","image/x-icon"), Map.entry("woff","font/woff"), Map.entry("woff2","font/woff2"),
      Map.entry("map","application/json; charset=utf-8"), Map.entry("txt","text/plain; charset=utf-8"));

  private final Path root; private final Path shell;

  private StaticSite(Path root, Path shell) { this.root = root; this.shell = shell; }

  /** Returns null when no client build is configured, which keeps the API-only runtime unchanged. */
  public static StaticSite openOrNull(String directory) {
    if (directory == null || directory.isBlank()) return null;
    Path root = Path.of(directory).toAbsolutePath().normalize();
    Path shell = root.resolve("index.html");
    if (!Files.isDirectory(root) || !Files.isRegularFile(shell)) {
      System.out.println("Client build not found at " + root + "; serving the API only.");
      return null;
    }
    return new StaticSite(root, shell);
  }

  /** True when the request was answered from the client build. */
  public boolean serve(HttpExchange x, String method, String path) throws IOException {
    if (!"GET".equals(method) && !"HEAD".equals(method)) return false;
    Path file = resolve(path);
    boolean shellFallback = file == null;
    if (shellFallback) file = shell;
    byte[] bytes = Files.readAllBytes(file);
    // The shell carries the session-dependent app, so it must never be cached;
    // Vite's asset filenames are content-hashed, so those are safe to cache.
    boolean hashedAsset = !shellFallback && path.startsWith("/assets/");
    x.getResponseHeaders().set("Content-Type", contentType(file));
    x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    x.getResponseHeaders().set("Cache-Control", hashedAsset ? "public, max-age=31536000, immutable" : "no-store");
    if ("HEAD".equals(method)) { x.sendResponseHeaders(200, -1); x.close(); return true; }
    x.sendResponseHeaders(200, bytes.length);
    try (OutputStream out = x.getResponseBody()) { out.write(bytes); }
    return true;
  }

  /** Returns the requested file, or null when the client shell should answer instead. */
  private Path resolve(String path) {
    if (path == null || path.isEmpty() || "/".equals(path)) return shell;
    String decoded;
    try { decoded = java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8); }
    catch (IllegalArgumentException e) { return null; }
    if (decoded.indexOf('\0') >= 0) return null;
    Path candidate = root.resolve(decoded.replace('\\', '/').replaceFirst("^/+", "")).normalize();
    if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) return null;
    return candidate;
  }

  private static String contentType(Path file) {
    String name = file.getFileName().toString(); int dot = name.lastIndexOf('.');
    String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    return TYPES.getOrDefault(extension, "application/octet-stream");
  }
}
