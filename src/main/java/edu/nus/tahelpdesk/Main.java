package edu.nus.tahelpdesk;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public final class Main {
  /** A deployed demo account is useless to a peer tester below this length and unsafe above none. */
  private static final int MIN_SEED_PASSWORD = 12;

  public static void main(String[] args) throws Exception {
    AppConfig config = AppConfig.load();
    int port = config.port();
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    PlatformService platform = config.usesPostgres()
        ? PostgresPlatform.open(config.databaseUrl(), config.databaseUser(), config.databasePassword())
        : SeedData.create();
    if (config.usesPostgres()) seedDeployedData(platform, config);
    SoCLaaSClient staffLlm = SoCLaaSClient.staffFromEnvironment();
    if (staffLlm != null) platform.setKnowledgeMapGenerator(staffLlm::generateKnowledgeMap, staffLlm.fallbackConfigured());
    SoCLaaSClient studentLlm = SoCLaaSClient.studentFromEnvironment();
    if (studentLlm != null) platform.setStudentAiProvider(studentLlm.studentProvider());
    System.out.println("Staff Knowledge Map AI: " + llmStatus("SOCLAAS_STAFF_API_KEY", staffLlm));
    System.out.println("Student AI help: " + llmStatus("SOCLAAS_STUDENT_API_KEY", studentLlm));
    StaticSite site = StaticSite.openOrNull(config.staticDirectory());
    server.createContext("/", new HttpApi(platform, config.production(), site)); server.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); server.start();
    System.out.println("TA Help Desk listening on port " + port + (site == null ? " (API only)" : " (API and client)"));
  }

  /**
   * Creates the demo accounts a peer tester needs, without ever using a
   * password that is published in this repository. Outside production the
   * development default is fine; in production an operator must choose one,
   * and the accounts are simply not created until they do.
   */
  private static void seedDeployedData(PlatformService platform, AppConfig config) {
    String chosen = System.getenv("TA_HELP_DESK_SEED_PASSWORD");
    boolean supplied = chosen != null && chosen.length() >= MIN_SEED_PASSWORD;
    if (!config.production()) { SeedData.seedIfEmpty(platform, supplied ? chosen : SeedData.DEVELOPMENT_PASSWORD); return; }
    if (supplied) { SeedData.seedIfEmpty(platform, chosen); System.out.println("Demo accounts: created or already present, using the configured seed password."); return; }
    System.out.println("Demo accounts: not created. Set TA_HELP_DESK_SEED_PASSWORD to at least "
        + MIN_SEED_PASSWORD + " characters to create seed.staff, seed.student and seed.other for peer testing.");
  }

  /**
   * A safe, secret-free reason the gateway client does or does not exist. The
   * factory returns null for several distinct causes, which is invisible in a
   * deployment log without this.
   */
  private static String llmStatus(String keyVariable, SoCLaaSClient client) {
    String key = System.getenv(keyVariable);
    if (key == null || key.isBlank()) return "not configured, because " + keyVariable + " is unset or empty";
    if (client == null) return "not configured: " + keyVariable + " is set, but the model catalog could not be validated. "
        + "The gateway may be unreachable from this host, or the selected model may not be in the catalog.";
    return "configured" + (client.fallbackConfigured() ? ", with a fallback model" : ", with no fallback model");
  }
}
