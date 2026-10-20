# Staff Material Upload, Processing, Review, and Consolidation

Status: Approved for Stage 4A non-AI implementation; AI implementation remains blocked on SoC operating-envelope confirmation
Approval: Human product-owner approval recorded in chat, 2026-10-20 — approved the non-AI material-management scope only.
Last handoff: Human product owner → Engineer, 2026-10-20 — implement Stage 4A upload, lifecycle, Resources, authorization, and non-AI failure behavior. AI-dependent work remains blocked on SoC details.

## Purpose and outcome

Allow authorized staff to maintain course lecture materials and use the staff
AI feature to create reviewable supporting knowledge artefacts. The feature
accepts bounded PDF source material, safely processes it, keeps its origin
visible, and makes active staff-uploaded source PDFs eligible for student AI
retrieval. Explicitly approved consolidations may add supporting context but
may not become the sole authority for student help.

This is a staff-facing feature owned by **@p-xp**. It depends on the shared
course authorization contract and later consumes the approved shared AI/RAG
service; it must not implement a separate LLM client or bypass the shared
course boundary.

## Confirmed requirements

1. Only staff may upload or delete course material.
2. Source material is PDF only, limited to 20 MB and 100 pages per PDF, with a
   maximum of 20 active source PDFs per course.
3. Password-protected or malformed PDFs are rejected with a clear error.
4. A staff-uploaded active source PDF is eligible for student AI retrieval only
   after course authorization is enforced. A generated consolidation may
   influence student-AI retrieval only after an authorized staff member for
   that course explicitly approves it. It is supporting material and cannot
   replace active source PDFs as the sole source of truth; student-facing
   claims must still be grounded in and cite original PDF filename/page sources.
5. Deleting a material item immediately removes it from new retrieval requests.
   Retention/recovery after deletion is out of scope for this test project.
6. Staff use an AI material-consolidation flow for uploaded lecture PDFs. It
   produces reviewable knowledge artefacts, such as knowledge maps, learning
   objectives, and candidate FAQs, with source PDF/page links.
7. The product uses the SoC LLM service. Its model/embedding capabilities,
   quotas, rate limits, and detailed retry/backoff behavior are not yet known.
   Until confirmed, the product must not fabricate results or use an unapproved
   substitute provider; it shows an explicit unavailable/retry-later state.
8. PDFs, filenames, generated artefacts, and all AI-related text are untrusted
   input and may contain indirect prompt-injection attempts.
9. “Authorized course staff” means a staff-role account that created the course
   or was added to that course by existing course staff. A staff account not
   tied to a course has no access to its material or consolidation operations.
10. Replacing a source PDF is delete-then-upload; there is no in-place replace
    operation or revision-history promise.
11. A failed processing or consolidation job shows authorized course staff a
    safe error message and a manual retry action. Until official SoC limits are
    known, the interim product guardrail is at most three manual retry-job
    submissions per minute per staff member per course. This is not a claimed
    SoC quota and must be reconciled with the official operating envelope.
12. Enrolled students may browse and download active course PDFs through a
    dedicated Resources section in that course forum. Every list, metadata,
    download, and citation target remains server-authorized for current course
    membership.

## Scope

- Authorized staff upload of bounded course source PDFs, validation, storage,
  processing state, active-state visibility, and deletion.
- Safe background processing sufficient to extract material provenance for
  future authorized retrieval and consolidation, with clear processing/failure
  states for staff.
- Staff-requested consolidation of eligible course source material into
  reviewable generated artefacts with source PDF/page provenance.
- Authorized course-staff approval/rejection of generated artefacts before any
  artefact may influence student-AI retrieval.
- Server-enforced course/role authorization and material lifecycle checks on
  uploads, reads, deletes, processing, consolidations, and retrieval eligibility.
- Clear validation, capacity, processing-failure, AI-unavailable, and
  permission-denied states.
- An enrolled-student Resources section that lists and serves only active PDFs
  for the requested course after server-side membership authorization.

## Non-goals

- Uploading non-PDF files, student material uploads/deletion, public material
  libraries, cross-course material sharing, or material revision recovery.
- Backups, retention, restoration, or a promise to recover deleted PDFs.
- Treating an AI consolidation as the only source for student AI answers.
- Publishing an AI artefact automatically as a forum answer or substituting an
  unapproved AI provider when SoC service access fails.
- Final SoC model, embedding, quota, concurrency, retry, or cost design; these
  remain blocked until official operating details are supplied.
- Defining the student AI user flow; that is covered by its own specification.

## Roles and user stories

| Role | User goal | Required behavior |
| --- | --- | --- |
| Authorized course staff | Add an eligible lecture PDF to my course. | Upload a valid PDF within the stated limits, see whether it is accepted/processing/active/failed, and receive clear validation or capacity errors. |
| Authorized course staff | Remove a source I no longer want used. | Delete a material item and have it excluded immediately from new student-AI retrieval requests. |
| Authorized course staff | Organize course source materials using AI. | Request a consolidation, receive a reviewable artefact or an explicit safe failure, and inspect source PDF/page provenance. |
| Authorized course staff | Control whether generated work is used. | Explicitly approve or reject an artefact; only an approved artefact may influence student-AI retrieval. |
| Enrolled student | Receive grounded help without private material leakage. | Access only the later student-AI behavior permitted by that feature; never upload/delete materials or bypass course authorization. |
| Enrolled student | Read course resources. | Browse and download active PDFs only through the Resources section of a course where the server confirms current enrollment. |

## Functional behavior and states

### Upload and lifecycle

- The server accepts only a PDF upload made by authorized course staff. It
  validates declared file type, actual PDF parseability, password protection,
  size (20 MB maximum), page count (100 maximum), and the course's 20-active-
  source-PDF capacity before marking material active.
- The service treats filenames and PDF contents as untrusted. It must not use a
  filename as a storage path or execute/obey text inside a PDF as an instruction.
- An accepted source material record has a visible lifecycle sufficient for
  staff to distinguish at least validation rejection, accepted/processing,
  active, failed processing, and deleted/unavailable. Exact internal job states
  are an engineering detail.
- “Active” means a staff-uploaded source PDF eligible for future student-AI
  retrieval only when the later student request also passes course
  authorization. Processing failure must not silently make unprocessed or
  partial material retrieval-eligible.
- At the active-PDF limit, the next upload is rejected with an explicit
  capacity error. Deleting an active source frees capacity once the deletion
  takes effect.
- A staff-initiated deletion removes the material from ordinary staff views and
  from all new retrieval/consolidation requests immediately. In-flight jobs and
  cached/indexed outputs must not cause deleted material to appear in a new
  result; exact cancellation mechanics are an implementation concern.
- To replace a PDF, course staff delete the existing material and upload a new
  PDF. The product offers no in-place replacement, retained revision, or
  restoration behavior.
- Enrolled students see a dedicated Resources section in their authorized
  course forum. It lists and downloads only active course PDFs. A deleted,
  failed, still-processing, or cross-course PDF must not be listed or served.

### Processing and provenance

- Processing is asynchronous/background work where needed. Staff can see that
  processing has not completed rather than being shown a fabricated active
  result.
- Processed source data preserves enough provenance to associate future
  retrieval/consolidation excerpts with the original course, source PDF, and
  page number. The system must not expose one course's source/provenance to an
  actor not authorized for that course.
- A malformed/unsupported result, extraction failure, or service failure moves
  the material/job to a visible failed state with safe, non-sensitive feedback.
- A failed processing or consolidation job exposes a safe, non-sensitive error
  message and a manual retry control to authorized course staff. The server
  permits no more than three such retry-job submissions per minute for the
  same staff member and course. This interim application guardrail applies
  until the official SoC envelope supplies stricter limits; it is not an
  asserted provider quota. Automatic retry/backoff behavior remains pending.

### Consolidation and review

- An authorized course staff member can request a consolidation only for
  source material in that course that is active at request time. The request
  must use the shared guarded SoC service once available.
- Generated knowledge maps, learning objectives, and candidate FAQs are
  clearly labelled as generated supporting artefacts, not authoritative source
  material. They include links/references to supporting source PDF pages for
  staff review.
- The system must never claim a consolidation completed if the SoC service,
  quota, processing, or grounding check fails. It returns a clear unavailable,
  rate-limited, failed, or retry-later state as appropriate to the approved
  operating envelope.
- Generated artefacts must be treated as untrusted output. They must not issue
  executable actions, change authorization, alter source-material lifecycle,
  or be automatically published to the forum.
- A generated artefact starts pending review. Only an explicit approval by an
  authorized staff member for the same course makes it retrieval-eligible;
  rejection or lack of approval keeps it ineligible. It remains derived
  supporting material linked to source PDFs, never a replacement for active
  source PDFs or the sole basis for student AI output. Student-facing claims
  using it must still be grounded in and cite original PDF filename/page sources.

## Authorization and retrieval rules

All checks are server-side and occur before protected metadata/content is
returned or any job is started.

| Action | Student | Authorized course staff | Required server check |
| --- | --- | --- | --- |
| Browse/download active course PDFs in Resources | Allowed when enrolled | Allowed | Actor is a current student member or course staff for the course; source is active/current/not deleted; server authorizes every list/download. |
| List/read material metadata or source for staff operations | Denied | Allowed | Actor is course staff for the course. |
| Upload source PDF | Denied | Allowed | Actor is authorized course staff; course is in scope; file and capacity validation pass. |
| Delete source PDF | Denied | Allowed | Actor is authorized course staff; material belongs to course. |
| View processing/consolidation status | Denied | Allowed | Actor is authorized course staff; job/material belongs to course. |
| Manually retry failed job | Denied | Allowed | Actor is authorized course staff; job belongs to course and is failed; server applies interim three submissions/minute/staff/course guardrail. |
| Request consolidation | Denied | Allowed | Actor is authorized course staff; source set is current, active, same-course, and service is available. |
| Review/approve/reject generated artefact | Denied | Allowed | Actor is authorized course staff for the artefact's course; server records the explicit decision and reviewer. |
| Student AI retrieval of a source PDF | Only through later student-AI feature when enrolled | N/A | Requester is course-authorized before retrieval; source is active/current/not deleted; result retains source provenance. |
| Student AI retrieval of a consolidation | Only through later student-AI feature when enrolled | N/A | Requester is enrolled in the course; artefact is explicitly approved by authorized course staff; its supporting sources are active/current/not deleted; result still cites original PDF filename/page sources. |

Course staff are defined by the shared course-membership contract: the creator
is automatically course staff, and course staff may add another staff account
by username. No client-provided course, filename, material ID, status, or
reviewer claim is trusted without server verification.

## Data and API implications

The shared platform must define documented contracts for, at minimum:

- source material identity, course, uploader, safe display name, lifecycle
  status, validation failure code where relevant, and active/deleted state;
- processing/consolidation job identity, course, requestor, status, safe
  failure information, and source-material version/set reference;
- provenance connecting an extracted/retrieved or generated statement to a
  current source PDF and page;
- generated artefact identity, course, source provenance, pending/approved/
  rejected status, approval decision time, and reviewer identity; and
- authorization-filtered staff material/job reads and mutations.

The material-resource contract must separately support an authorization-filtered
Resources listing and download for current members. It must not expose private
storage locations; citation and download targets are re-authorized by the
server rather than treated as public URLs. Job state must support a safe
staff-facing failure message and retry eligibility/rate-limit outcome.

The implementation must preserve enough material version/lifecycle information
to exclude deleted or inactive source material from new retrieval. It must not
expose raw private storage locations, credentials, or cross-course metadata in
API responses. The exact database/storage schema, queue, vector/embedding
implementation, and endpoints are engineering decisions after the SoC gate.

## Abuse cases and required safeguards

- A student or an unrelated-course actor calls upload, material, job, or
  consolidation endpoints directly: deny without leaking material existence,
  filename, status, or content.
- A user submits a renamed non-PDF, oversized, over-page-limit, encrypted, or
  malformed file: reject before active/retrieval eligibility and give safe
  validation feedback.
- A course already has 20 active PDFs: reject a further active upload without
  exceeding the limit.
- A filename attempts path traversal or a PDF includes text such as “ignore
  prior instructions”: store/process it as untrusted content, never executable
  control instructions, and prevent it from changing AI/system policy.
- An actor changes a material/job/course identifier: authorize ownership and
  same-course scope server-side, including source sets for consolidation.
- A deleted or failed material is referenced by a stale browser/job/index:
  exclude it from all new retrieval/consolidation results.
- A removed student or unrelated-course actor requests a Resources list,
  download, or guessed citation URL: deny without disclosing material
  existence or storage location.
- A staff actor repeatedly submits retries for failed jobs: enforce the interim
  three-per-minute-per-staff-per-course guardrail and return a safe rate-limit
  state; do not represent it as a provider quota.
- A malicious or hallucinated generated artefact claims sources it lacks:
  label it generated, require traceable source PDF/page support, and fail or
  withhold it if the approved grounding/provenance validation cannot be met.
- An unapproved, rejected, cross-course, or source-invalidated artefact is
  offered to student retrieval: exclude it server-side. An approval claim or
  reviewer identity supplied by a client or generated content cannot make it
  eligible.
- SoC service quota/outage/error: show a safe explicit failure; do not invent
  consolidation output, use a replacement provider, or disclose source content
  beyond the requester’s authorization.

## Dependencies and assumptions

- Depends on `workflow/features/product-foundation/spec.md` and the future
  approved shared course/membership contract.
- Depends on Stage 2 authorization/storage foundation and Stage 3 shared,
  guarded SoC/RAG service. It cannot be approved for AI implementation until
  the SoC operating envelope is confirmed.
- Requires a staff-facing `design.md` after the behavioral decisions below are
  approved and before implementation.
- The user-approved Railway topology provides private object storage and a
  private worker, but this spec does not select storage APIs or queue design.

## Open decisions requiring human review

1. **SoC operating envelope (external blocker):** Confirm sanctioned models,
   embedding availability, call/concurrency quotas, cost/budget behavior, and
   allowed retry/backoff/fallback behavior. No provider-specific model or quota
   is assumed in this draft. The interim manual retry guardrail is subject to
   reconciliation with this decision.

## Acceptance criteria

1. A student is denied, including by direct request, when attempting to upload,
   delete, list staff-only material metadata, view processing status, request
   consolidation, or review an artefact; the response does not disclose
   protected material details.
2. An authorized course staff actor can upload a valid, parseable, unprotected
   PDF of at most 20 MB and 100 pages to their authorized course and sees an
   accepted/processing state followed by active or a safe failed state.
3. The server rejects non-PDF, renamed non-PDF, malformed, password-protected,
   oversized, and over-100-page uploads before treating them as active or
   retrieval-eligible, with an explicit safe validation error.
4. The server allows no more than 20 active source PDFs in a course and returns
   a clear capacity error for the next active upload. Deleting one active item
   frees capacity only after deletion takes effect.
5. An active source PDF is associated with its course and uploader and retains
   source PDF/page provenance after processing. An actor outside the approved
   course-staff scope cannot access its metadata, contents, job state, or
   provenance.
6. A failed or still-processing source PDF is never silently treated as active
   retrieval material. Staff can distinguish processing, active, failed, and
   deleted/unavailable states without exposed internal secrets/paths.
7. An enrolled student can browse and download only active PDFs through the
   dedicated Resources section of a course where they are currently enrolled.
   A non-member, removed student, unrelated-course actor, or guessed direct
   URL is denied without protected metadata, content, or storage locations.
8. A staff actor replaces material only by deleting it then uploading a new
   PDF; no in-place replace or old revision remains available. An authorized
   staff member sees a safe error and can manually retry a failed job, while
   the server enforces at most three retry-job submissions per minute for that
   staff member and course until official SoC limits supersede it.
9. An authorized staff deletion immediately excludes the material and its
   derived data from all new student-AI retrieval and consolidation requests;
   stale identifiers, queued work, and cached/indexed data cannot restore it to
   a new result.
10. An authorized staff actor can request consolidation only for active,
   same-course source material permitted by the final policy. The result is
   labelled generated and displays traceable source PDF/page references; it is
   never automatically posted to the forum.
11. A consolidation does not become the sole authority for student AI. It may
   influence student-AI retrieval only after an authorized staff member for
   that course explicitly approves it; any use retains active source-PDF/page
   grounding and student-facing citations to original PDF filename/page sources.
12. For an indirect prompt-injection payload in a filename/PDF or hostile
    generated text, the system treats it as untrusted data: it cannot alter
    authorization, storage path, source lifecycle, review decisions, model
    policy, or cause an AI output to be automatically published.
13. When the SoC service is unavailable, quota-limited, malformed, or fails a
    required grounding/provenance check, no fabricated or substitute-provider
    result is shown; staff see an explicit safe unavailable/failed/retry-later
    state. The final rate-limit wording/behavior follows the approved SoC
    operating envelope.
14. The final implementation enforces course-staff authorization and the
    generated-artefact approval policy with automated positive and negative
    authorization tests, including pending, rejected, forged-approval, and
    cross-course cases.

## Verification evidence expected

Engineering must record test evidence for PDF type/content/size/page/capacity
validation; direct endpoint denials; cross-course material/job isolation;
deleted/stale material exclusion; Resources list/download authorization and
revocation; processing failure/manual-retry rate limiting; provenance; generated
artefact labelling/approval; retrieval exclusion for pending/rejected/invalidated
artefacts; SoC failure; and indirect prompt-injection fixtures.
An independent tester must verify the final staff-authority and approval policy,
the visible lifecycle states, and that deleted material cannot appear in a new
AI request.

## Handoff

**Feature:** `staff-material-governance` — Draft, with all non-AI product
decisions recorded; pending human approval and SoC operating-envelope
confirmation for AI-dependent implementation.

**Input artefacts:** `AGENTS.md`, `Instructions_and_Planning.md`,
`workflow/project-overview.md`, `workflow/development-process.md`,
`workflow/implementation_plan.md`, and
`workflow/features/product-foundation/spec.md` (Phase 0 decisions).

**Deliverable:** This specification only; no UI design, source code, storage
design, schema, AI-provider call, or tests were created.

**Next owner:** Human product owner approves the documented non-AI material
behavior. The SoC service owner/human product owner supplies the operating-
envelope decision before AI-dependent behavior is implementation-approved. The
UI designer can design the Resources, upload, lifecycle, failure, and retry
states against this approved behavior; @p-xp implements AI-dependent work only
against the later approved guarded AI/RAG contract.
