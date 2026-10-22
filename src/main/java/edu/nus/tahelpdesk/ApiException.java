package edu.nus.tahelpdesk;

/** Deliberately small error vocabulary; callers never receive protected existence details. */
public final class ApiException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  public final int status;
  public final String code;
  private ApiException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
  public static ApiException validation(String message) { return new ApiException(422, "validation_error", message); }
  public static ApiException unauthenticated() { return new ApiException(401, "authentication_required", "Sign in is required."); }
  public static ApiException forbidden() { return new ApiException(403, "permission_denied", "This action is unavailable."); }
  public static ApiException hidden() { return new ApiException(404, "not_found", "The requested resource is unavailable."); }
  public static ApiException conflict(String message) { return new ApiException(409, "conflict", message); }
  public static ApiException requestTooLarge() { return new ApiException(413, "request_too_large", "The request is too large."); }
  public static ApiException rateLimited(String message) { return new ApiException(429, "retry_limited", message); }
  public static ApiException unavailable(String message) { return new ApiException(503, "service_unavailable", message); }
}
