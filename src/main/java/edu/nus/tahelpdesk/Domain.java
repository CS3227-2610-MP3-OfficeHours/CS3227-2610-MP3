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
  /** Source PDFs are deliberately distinct from future generated AI artefacts. */
  public enum MaterialStatus { processing, active, failed, deleted }
  public record Material(UUID id, UUID courseId, UUID uploaderId, String filename,
                         MaterialStatus status, String failureMessage, byte[] content,
                         int pageCount, Instant createdAt, Instant updatedAt) {}
  /** One staff-reviewed navigation aid per course; it is never a student-facing answer. */
  public enum KnowledgeMapStatus { generating, pending_review, approved, rejected, stale, failed }
  public record KnowledgeMap(UUID id, UUID courseId, UUID requestedBy, KnowledgeMapStatus status,
                             String content, String failureMessage, java.util.List<UUID> sourceMaterialIds,
                             UUID reviewedBy, Instant reviewedAt, Instant createdAt, Instant updatedAt) {}
}
