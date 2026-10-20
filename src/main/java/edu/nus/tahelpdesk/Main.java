package edu.nus.tahelpdesk;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public final class Main {
  public static void main(String[] args) throws Exception {
    AppConfig config = AppConfig.load();
    int port = config.port();
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/", new HttpApi(SeedData.create(), config.production())); server.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); server.start();
    System.out.println("TA Help Desk listening on http://localhost:" + port);
  }
}
