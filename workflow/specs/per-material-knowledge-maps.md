# Per-material Knowledge Maps and non-destructive regeneration

Status: Approved for implementation

Date: 2026-10-21

## Decision summary and critique

The existing course-wide Knowledge Map should be replaced by **one independently
generated Knowledge Map per text-ready PDF**. Staff may generate a map for one
PDF or start a bounded bulk operation that queues a separate job for every
eligible PDF in the course.

This is the recommended direction. The current design constructs one request
from every active PDF, has a 120,000-character combined-input ceiling, and
replaces the sole stored map with `generating` before the provider returns.
Consequently, a provider failure can remove a staff member's reviewable map
from the only current slot, and changing one PDF needlessly regenerates every
other PDF.

Per-material generation has clear benefits:

- It sends only one PDF's page-aware extracted text per request, reducing
  request size, timeout/502 risk, and blast radius of malformed output.
- A changed, deleted, or newly uploaded PDF affects only its own map.
- Staff can review provenance and approve/reject output in the context of the
  source document that produced it.
- Bulk generation can report partial success rather than failing one large
  course request.

It is not a free equivalent of a course-wide map. The model cannot connect a
topic in one PDF with material in another PDF, so it will not produce a single
course-level information architecture. It can also duplicate topics across
slides/notes. This specification therefore treats the results as **per-material
navigation aids**. A future cross-material index or synthesis layer would be a
separate feature, generated from approved per-material outputs or a deliberately
bounded retrieval set; it must not be smuggled back into the bulk action.

## Goals

1. Never discard a completed pending-review map merely because a later
   generation attempt starts or fails.
2. Generate only the PDF a staff member selected, or independent jobs for the
   eligible PDFs included in a bulk request.
3. Keep an approved, current map usable while a newer candidate is being
   generated or reviewed for the same unchanged PDF.
4. Retain current PDF/page provenance and the existing staff-only review and
   student-use authorization boundary.
5. Give staff a clear per-file status, review action, and safe failure detail.

## Non-goals

- Reconstructing one cross-PDF course outline from individual maps.
- Automatically generating after upload, deletion, retry, or source change.
- Sending raw PDF binaries directly to the LLM, exposing extracted text through
  a public API, or relaxing page-provenance validation.
- Keeping maps after their source PDF is deleted; source deletion continues to
  remove derived content immediately under the existing material-deletion
  policy.
- Adding student-facing UI in this feature. Future student AI may consume only
  approved, current per-material maps after server-side authorization.

## Roles and authorization

| Action | Authorized course staff | Student/non-member/unassigned staff |
| --- | --- | --- |
| List maps/jobs and see generated content | Allowed for the same course only | Denied without file/map/job existence leakage |
| Generate one eligible PDF or bulk-generate | Allowed for the same course only | Denied |
| Approve or reject a candidate | Allowed for the candidate's PDF/course only | Denied |
| Use a map for future student AI navigation | Not a direct UI action | Allowed only through a later student-AI service after it verifies course membership, active source, current digest, and approved status |

The browser never supplies authoritative course, material, approval, source
digest, or citation claims. Every route resolves the material under the target
course and confirms staff membership on the server.

## Proposed lifecycle and retention rule

Each active, text-ready material has two independent slots:

| Slot | Meaning | Replacement/removal rule |
| --- | --- | --- |
| Approved map | The latest explicitly approved, source-current map. It is the only map eligible for future student-AI navigation. | Replaced only when staff approve a newer verified candidate for the same unchanged PDF; invalidated for student use when source provenance is no longer current. |
| Candidate map | The latest completed returned output awaiting staff review, including an unverified result that is staff-only. | Preserved while another job runs and after that job fails. Atomically replaced only when a newer job returns usable content. Removed only when staff explicitly reject it, when it is promoted on approval, or when the source is deleted. |

A separate persisted generation-job record reports `queued`, `generating`,
`failed`, `cancelled_due_to_source_change`, or `completed`. A job is **not** a
map and cannot overwrite either slot merely by starting or failing.

This resolves the reported loss case: pressing Generate leaves the current
candidate visible; a 502/timeout/rate-limit result becomes a job failure next
to that PDF and preserves the prior candidate and approved map. A valid
returned candidate replaces the prior candidate atomically. Rejecting a
candidate removes its stored generated content; an audit event may retain the
decision metadata but not the generated body.

If an unchanged PDF has an approved map and staff generate a new candidate,
the approved map remains eligible while the candidate is pending. If the
source's active text/digest/page provenance changes, all maps for that source
become stale and are excluded from student use. Because this application has
delete-then-upload replacement, deletion removes both derived maps and jobs
with the PDF; a newly uploaded replacement starts with no map.

## Generation and citation contract

For one eligible `active` material, the server snapshots:

- course ID, material ID, material digest, filename, and extracted current
  pages/text digests;
- requestor and request time; and
- the generator/prompt version needed for safe operational diagnostics.

It sends only that material's server-owned page text to the guarded SoC LLM
service, framed as untrusted source data. The prompt must say that PDF text,
filenames, and any embedded instructions are data rather than instructions.
It must ask for concise topical navigation for that one PDF and require the
canonical citation `(filename.pdf, p. N)` on every factual claim.

Validation is scoped to the selected PDF, not to every active PDF in the
course. Each citation must use the snapshotted filename and an extracted,
non-blank, current page number for that material. Returned content with missing
or invalid citations is retained only as an unverified candidate for the same
authorized staff; it cannot be approved. Provider failure, malformed provider
response, no returned content, source deletion, or source-digest mismatch
creates a failed/cancelled job and leaves both map slots intact.

## API and persistence plan

Replace the one-row-per-course map contract with material-scoped read/mutation
contracts. Exact route names may follow the existing API conventions, but the
authorization and response semantics are required:

| Operation | Proposed contract |
| --- | --- |
| List per-file state | `GET /api/courses/{courseId}/knowledge-maps` returns authorized staff a list keyed by active material ID, each containing safe filename/status metadata, its approved map (if any), candidate (if any), and latest job status/failure. |
| Generate one | `POST /api/courses/{courseId}/materials/{materialId}/knowledge-map/generate` snapshots one active text-ready PDF and creates a job without mutating a stored map. Returns `202` and that job/state. |
| Generate all eligible | `POST /api/courses/{courseId}/knowledge-maps/generate-all` queues independent jobs for all active text-ready PDFs that do not already have an active job. Returns `202` with queued/skipped material counts and per-job identifiers; it does not fail the whole request because one source is resource-only or already generating. |
| Approve candidate | Material-scoped POST validates that candidate's source snapshot/citations and atomically promotes it to the approved slot. |
| Reject candidate | Material-scoped POST deletes only that candidate's generated body after recording the decision; an approved map, if any, remains unchanged. |

Use migrations for a `material_knowledge_maps` representation that supports a
current approved and candidate version per material, and a
`material_knowledge_map_jobs` representation for generation attempts. Store
material ID, course ID, source digest/page snapshot, state, content,
verification/failure code, request/review actors, timestamps, and an immutable
job-to-output association. Apply uniqueness/foreign keys so maps/jobs cannot
cross course/material boundaries. Retire the current unique course-wide map
table/API only after a migration path exists.

Existing course-wide rows cannot be safely decomposed into individual maps.
This project remains in development, so the migration may remove the obsolete
course-wide map records and API contract. No legacy-map retention or display is
required.

## Staff UI plan

Revise **Manage materials** rather than adding another course tab:

1. Replace the single Course Knowledge Map panel with **Knowledge Maps by
   PDF** directly beneath or expandable within each eligible source-PDF row.
   The region names the PDF and keeps generated output visibly distinct from
   the source resource.
2. At the section heading, provide **Generate all eligible maps**. Its
   confirmation reports the number of text-ready PDFs, explains that separate
   background requests will be queued, and says existing maps stay visible if
   a request fails. Resource-only/processing/failed PDFs are reported as
   skipped with a reason.
3. Each text-ready PDF has **Generate map** when no candidate/job exists and
   **Update map** otherwise. The action starts only that source's job. An
   in-progress row disables only its own generation action; bulk progress does
   not make unrelated staff controls disappear.
4. A row shows, in order: latest job progress/failure; approved-map status and
   review time where present; then any candidate labelled **AI-generated —
   staff review required**, its source-support links, and its review controls.
   An unverified candidate gets the existing staff-only warning and no approve
   control. A failed attempt shows **Map generation unavailable** alongside the
   retained map, never in place of it.
5. Candidate approval remains a deliberate confirmation. Candidate rejection
   uses a destructive confirmation explaining that only this unapproved
   generated output will be removed and that an already approved map is kept.
6. Accessible status text, keyboard-operable expand/collapse controls,
   focus restoration after confirmation, responsive stacked rows, and
   text-only rendering for generated content/filenames are required. Do not
   display raw extracted page text, provider diagnostics, or public storage
   URLs.

## Security and operational controls

- Keep course-staff authorization before every map/job list, generation,
  approval, rejection, and content read; future student use must independently
  re-check current membership and source status.
- Treat filenames, PDF text, candidate content, and provider errors as
  untrusted. Render as text and preserve source framing; no generated text can
  alter workflow, authorization, or prompt policy.
- Enforce the existing local queue/concurrency and retry limits per staff and
  course. Bulk creation must be bounded, deduplicate an in-flight job per
  material, and surface partial queue/rate-limit results without retry storms.
- Before completing a job, re-check that material/course identity, active
  state, digest, and page provenance still match its immutable snapshot.
  Discard mismatched results without replacing a map.
- Continue to give generic denial responses to unauthorized/cross-course
  requests and safe, non-provider-diagnostic failure messages to authorized
  staff.

## Implementation sequence after approval

1. Update the staff-material governance specification/design and security
   review to replace the one-course-map policy and resolve any listed decision.
2. Add migrations and domain/repository support for material-scoped approved
   maps, candidates, and persisted jobs; remove the obsolete course-wide map
   persistence/API contract.
3. Replace the all-active source-packet builder with one-material snapshot,
   prompt, validation, completion, cancellation, and queue logic.
4. Replace course-map HTTP routes with material/list/bulk/review routes while
   preserving staff authorization and a defined legacy-read transition.
5. Implement the per-PDF staff interface and its loading, partial-progress,
   empty, source-not-eligible, unverified, failed-but-retained, and
   permission-denied states.
6. Add migration, integration, provider-client, and frontend tests; obtain an
   independent security/test review before marking the feature complete.

## Required acceptance evidence

1. Starting a new job leaves a completed pending candidate readable; provider
   429/5xx/timeout/malformed/no-content failure leaves it and any approved map
   unchanged.
2. A successful newer candidate replaces only that material's prior candidate;
   explicit rejection removes only that candidate; approval promotes only that
   candidate and replaces only the same material's approved map.
3. One-PDF generation sends no text/provenance from another PDF. Bulk creates
   one bounded job per eligible PDF and demonstrates partial success/failure
   without overwriting unrelated maps.
4. Citation validation accepts only the selected material's current,
   extracted pages and rejects wrong filename, wrong page, deleted, changed,
   cross-course, and prompt-injected output for approval.
5. Direct student, non-member, cross-course, forged material ID, forged review,
   and stale-job endpoint tests disclose no protected map/job/PDF metadata.
6. Material deletion and source change remove or stale derived data before it
   can guide student AI; an in-flight completion cannot restore it.
7. UI tests cover retained candidate during a failing update, individual and
   bulk actions, skipped PDFs, partial bulk results, review confirmations,
   accessible labels/focus, and text-only untrusted rendering.
8. Migration testing demonstrates obsolete course-wide records/routes are
   removed cleanly in this development-stage schema transition.

## Recorded approval

Human product-owner approval recorded in chat, 2026-10-21:

1. Per-material maps replace the single cross-document course map; cross-PDF
   synthesis is deferred.
2. An existing approved map remains active while a newer candidate for the
   *same unchanged PDF* is pending.
3. Development-stage course-wide map records need not be preserved. The new
   migration replaces that obsolete representation.
