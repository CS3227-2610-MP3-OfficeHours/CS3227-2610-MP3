package edu.nus.tahelpdesk;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class SessionManager {
  private record Session(UUID userId, Instant expiresAt) {}
  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();
  String create(UUID userId) { byte[] bytes = new byte[32]; random.nextBytes(bytes); String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); sessions.put(token, new Session(userId, Instant.now().plus(Duration.ofHours(12)))); return token; }
  UUID require(String token) { Session session = token == null ? null : sessions.get(token); if (session == null || session.expiresAt().isBefore(Instant.now())) { if (token != null) sessions.remove(token); throw ApiException.unauthenticated(); } return session.userId(); }
  void revoke(String token) { if (token != null) sessions.remove(token); }
}
