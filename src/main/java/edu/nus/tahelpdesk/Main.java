package edu.nus.tahelpdesk;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public final class Main {
  public static void main(String[] args) throws Exception {
    AppConfig config = AppConfig.load();
    int port = config.port();
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    PlatformService platform = config.usesPostgres()
        ? PostgresPlatform.open(config.databaseUrl(), config.databaseUser(), config.databasePassword())
        : SeedData.create();
    if (config.usesPostgres()) SeedData.seedIfEmpty(platform);
    SoCLaaSClient staffLlm = SoCLaaSClient.staffFromEnvironment();
    if (staffLlm != null) platform.setKnowledgeMapGenerator(staffLlm::generateKnowledgeMap, staffLlm.fallbackConfigured());
    SoCLaaSClient studentLlm = SoCLaaSClient.studentFromEnvironment();
    if (studentLlm != null) platform.setStudentAiProvider(studentLlm.studentProvider());
    StaticSite site = StaticSite.openOrNull(config.staticDirectory());
    server.createContext("/", new HttpApi(platform, config.production(), site)); server.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); server.start();
    System.out.println("TA Help Desk listening on port " + port + (site == null ? " (API only)" : " (API and client)"));
  }
}
