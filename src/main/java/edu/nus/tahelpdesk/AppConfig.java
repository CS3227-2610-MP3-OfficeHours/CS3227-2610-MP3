package edu.nus.tahelpdesk;

/** Environment-only configuration; secrets are deliberately never logged. */
public record AppConfig(int port, boolean production, String databaseUrl, String databaseUser, String databasePassword, String staticDirectory) {
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
    String databaseUrl = trimToNull(System.getenv("TA_HELP_DESK_DATABASE_URL"));
    String databaseUser = trimToNull(System.getenv("TA_HELP_DESK_DATABASE_USER"));
    String databasePassword = System.getenv("TA_HELP_DESK_DATABASE_PASSWORD");
    if (databaseUrl != null) {
      if (!databaseUrl.startsWith("jdbc:postgresql://"))
        throw new IllegalArgumentException("TA_HELP_DESK_DATABASE_URL must be a PostgreSQL JDBC URL.");
      if (databaseUser == null || databasePassword == null || databasePassword.isBlank())
        throw new IllegalArgumentException("TA_HELP_DESK_DATABASE_USER and TA_HELP_DESK_DATABASE_PASSWORD are required with TA_HELP_DESK_DATABASE_URL.");
    } else if (databaseUser != null || databasePassword != null || production) {
      throw new IllegalArgumentException("TA_HELP_DESK_DATABASE_URL is required in production and whenever database credentials are supplied.");
    }
    // Optional: the directory holding the built client. Absent means API only.
    String staticDirectory = trimToNull(System.getenv("TA_HELP_DESK_STATIC_DIR"));
    return new AppConfig(port, production, databaseUrl, databaseUser, databasePassword, staticDirectory);
  }
  public boolean usesPostgres() { return databaseUrl != null; }
  private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
