package edu.nus.tahelpdesk;

import java.time.Instant;
import java.util.UUID;

public final class Domain {
  private Domain() {}
  public enum Role { student, staff }
  public record User(UUID id, String username, Role role, String passwordHash, Instant createdAt) {}
  public record Course(UUID id, String code, String name, UUID createdBy, Instant createdAt) {}
  public record Membership(UUID courseId, UUID userId, Role role) {}
  public record ForumTag(UUID id, UUID courseId, String name, String color, boolean isDefault, Instant createdAt) {}
  public record Post(UUID id, UUID courseId, UUID authorId, String title, String description,
                     UUID tagId, Instant createdAt, Instant updatedAt) {}
  public record Comment(UUID id, UUID postId, UUID authorId, String body,
                        Instant createdAt, Instant updatedAt) {}
  /** Source PDFs are deliberately distinct from future generated AI artefacts. */
  /** active is text-ready for AI; resource_only remains downloadable but has no selectable text. */
  public enum MaterialStatus { processing, active, resource_only, failed, deleted }
  public record Material(UUID id, UUID courseId, UUID uploaderId, String filename,
                         MaterialStatus status, String failureMessage, byte[] content,
                         int pageCount, Instant createdAt, Instant updatedAt) {}
  /** Server-owned extracted text provenance. It is never exposed as a raw-text API. */
  public record PdfPage(UUID materialId, UUID courseId, String materialDigest, int pageNumber,
                        String text, String textDigest, String parserVersion, String state,
                        String safeFailureCode, Instant createdAt, Instant invalidatedAt) {}
  /** A map is bound to exactly one source PDF and is never a student-facing answer. */
  public enum KnowledgeMapStatus { pending_review, approved }
  public record KnowledgeMap(UUID id, UUID courseId, UUID materialId, String materialDigest,
                             UUID requestedBy, KnowledgeMapStatus status, String content,
                             String verificationMessage, UUID reviewedBy, Instant reviewedAt,
                             Instant createdAt, Instant updatedAt) {}
  public enum KnowledgeMapJobStatus { queued, generating, completed, failed, cancelled_due_to_source_change }
  /** A job reports an attempt and never takes the place of a stored map slot. */
  public record KnowledgeMapJob(UUID id, UUID courseId, UUID materialId, String materialDigest,
                                UUID requestedBy, KnowledgeMapJobStatus status, String failureMessage,
                                UUID outputMapId, Instant createdAt, Instant updatedAt) {}
}
