package edu.nus.tahelpdesk;

import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;

final class PasswordHasher {
  private static final int ITERATIONS = 210_000, BITS = 256;
  private final SecureRandom random = new SecureRandom();
  String hash(String password) {
    byte[] salt = new byte[16]; random.nextBytes(salt);
    return "pbkdf2-sha256$" + ITERATIONS + "$" + b64(salt) + "$" + b64(derive(password, salt, ITERATIONS));
  }
  boolean matches(String password, String stored) {
    try {
      String[] parts = stored.split("\\$");
      if (parts.length != 4 || !"pbkdf2-sha256".equals(parts[0])) return false;
      byte[] actual = Base64.getUrlDecoder().decode(parts[3]);
      byte[] expected = derive(password, Base64.getUrlDecoder().decode(parts[2]), Integer.parseInt(parts[1]));
      return MessageDigest.isEqual(actual, expected);
    } catch (RuntimeException ignored) { return false; }
  }
  private static byte[] derive(String value, byte[] salt, int iterations) {
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
          .generateSecret(new PBEKeySpec(value.toCharArray(), salt, iterations, BITS)).getEncoded();
    } catch (GeneralSecurityException e) { throw new IllegalStateException("Password hashing unavailable", e); }
  }
  private static String b64(byte[] input) { return Base64.getUrlEncoder().withoutPadding().encodeToString(input); }
}
