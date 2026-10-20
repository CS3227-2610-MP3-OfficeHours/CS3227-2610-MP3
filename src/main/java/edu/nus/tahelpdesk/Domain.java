package edu.nus.tahelpdesk;

import java.time.Instant;
import java.util.UUID;

public final class Domain {
  private Domain() {}
  public enum Role { student, staff }
  public record User(UUID id, String username, Role role, String passwordHash, Instant createdAt) {}
  public record Course(UUID id, String code, String name, UUID createdBy, Instant createdAt) {}
  public record Membership(UUID courseId, UUID userId, Role role) {}
  public record Post(UUID id, UUID courseId, UUID authorId, String title, String description,
                     Instant createdAt, Instant updatedAt) {}
  public record Comment(UUID id, UUID postId, UUID authorId, String body,
                        Instant createdAt, Instant updatedAt) {}
}
