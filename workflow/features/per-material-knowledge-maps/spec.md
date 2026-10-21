# Per-material Knowledge Maps

Status: Approved for implementation

Approval: Human product-owner approval recorded in chat, 2026-10-21.

The detailed decision record, API/persistence plan, security constraints, and
required evidence are in
`workflow/specs/per-material-knowledge-maps.md`. This feature specification
adopts that document as normative.

## Product contract

- Replace the one course-wide Knowledge Map with independent maps for each
  active, text-ready course PDF.
- Staff can start generation for one PDF or bulk-queue one independent job for
  every eligible PDF. Generation is always manual.
- Each PDF has an approved slot and a candidate slot. Starting or failing a job
  never removes either slot. A completed usable result atomically replaces only
  the candidate slot for its PDF. Rejecting deletes only that candidate.
- An approved map stays eligible for future student-AI navigation while a newer
  candidate for the same unchanged PDF is pending. Only verified, explicitly
  approved, source-current maps may be used by that future service.
- Every claim in a candidate must cite a valid page of its own source PDF;
  unverified returned content is staff-only and cannot be approved.
- PDF deletion removes its derived maps/jobs. A changed source snapshot makes
  derived output ineligible and prevents an in-flight job from restoring it.
- The project is in development: delete the obsolete course-wide map persistence
  and endpoints. No legacy-map migration or retention is required.

## Roles, scope, and non-goals

Only authorized staff for the same course can list, read, generate, approve,
or reject map data. Students, non-members, and unrelated-course staff are
denied without existence disclosure. This does not add a student-facing UI or
cross-PDF synthesis; those remain later features.

## Acceptance gate

Implementation must meet every required acceptance-evidence item in the
normative planning specification. It must receive an independent passing test
report and then an acceptance-auditor review before it is represented as
complete.
