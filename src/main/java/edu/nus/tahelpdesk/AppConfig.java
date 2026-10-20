package edu.nus.tahelpdesk;

/** Environment-only configuration; secrets are deliberately never logged. */
public record AppConfig(int port, boolean production) {
  public static AppConfig load() {
    String environment = System.getenv().getOrDefault("TA_HELP_DESK_ENV", "development");
    boolean production = "production".equals(environment);
    if (!production && !"development".equals(environment) && !"test".equals(environment))
      throw new IllegalArgumentException("TA_HELP_DESK_ENV must be development, test, or production.");
    int port;
    try { port = Integer.parseInt(System.getenv().getOrDefault("TA_HELP_DESK_PORT", "8080")); }
    catch (NumberFormatException e) { throw new IllegalArgumentException("TA_HELP_DESK_PORT must be a valid TCP port."); }
    if (port < 1 || port > 65535) throw new IllegalArgumentException("TA_HELP_DESK_PORT must be a valid TCP port.");
    String secret = System.getenv("TA_HELP_DESK_SESSION_SECRET");
    if (production && (secret == null || secret.length() < 32))
      throw new IllegalArgumentException("TA_HELP_DESK_SESSION_SECRET must be at least 32 characters in production.");
    return new AppConfig(port, production);
  }
}
