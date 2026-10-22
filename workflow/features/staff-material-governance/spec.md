# Staff Material Upload, Processing, Review, and Consolidation

Status: Approved for Stage 4A and the Stage 4B Knowledge Map scope; Stage 4B awaits Stage 3 completion
Approval: Human product-owner approval recorded in chat, 2026-10-20 through 2026-10-22 — approved non-AI material management, normal SoCLaaS use without published numeric limits, one manually updated Knowledge Map per course, authorized-staff display of unverified generated maps, the approved staff dashboard refinements, and approval/status-control behavior (2026-10-22).
Last handoff: Human product owner → UI designer, 2026-10-22 — update the Knowledge Map review/status controls for the approved interaction changes in the latest addendum; next owner is engineer after design alignment.

## Purpose and outcome

Allow authorized staff to maintain course lecture materials and use the staff
AI feature to create reviewable supporting knowledge artefacts. The feature
accepts bounded PDF source material, safely processes it, keeps its origin
visible, and makes active staff-uploaded source PDFs eligible for student AI
retrieval. One explicitly approved, current Knowledge Map may guide student
retrieval but may not become the sole authority for student help.

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
   after course authorization is enforced. The current approved Knowledge Map
   may guide student-AI retrieval only after an authorized staff member for that
   course explicitly approves it. It is supporting navigation material and
   cannot replace active source PDFs as the sole source of truth; student-facing
   claims must still be grounded in and cite original PDF filename/page sources.
5. Deleting a material item immediately removes it from new retrieval requests.
   Retention/recovery after deletion is out of scope for this test project.
6. Each course has one AI-generated Knowledge Map covering every active course
   PDF. It organizes topics and subtopics, gives short navigation descriptions,
   and links each area to its supporting source PDF/pages. Only authorized
   course staff can manually generate or update it; learning objectives and
   candidate FAQs are out of scope.
7. The product uses the SoC LLM service through the shared guarded service.
   The verified staff key can access chat models and `bge-m3` embeddings. The
   product owner accepts normal use without published numeric provider limits;
   the service must use bounded local controls, never fabricate results or use
   an unapproved substitute provider, and show safe rate-limited/retry-later
   states when the gateway rejects or cannot complete a request.
8. PDFs, filenames, generated artefacts, and all AI-related text are untrusted
   input and may contain indirect prompt-injection attempts.
9. “Authorized course staff” means a staff-role account that created the course
   or was added to that course by existing course staff. A staff account not
   tied to a course has no access to its material or consolidation operations.
10. Replacing a source PDF is delete-then-upload; there is no in-place replace
    operation or revision-history promise.
11. A failed processing or consolidation job shows authorized course staff a
    safe error message and a manual retry action. The application guardrail is
    at most three manual retry-job submissions per minute per staff member per
    course; it is an application control, not a claimed provider quota.
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
- Staff-requested generation/update of one course Knowledge Map from every
  active source PDF, with source PDF/page provenance.
- Authorized course-staff approval/rejection of a generated Knowledge Map
  before it may guide student-AI retrieval.
- Server-enforced course/role authorization and material lifecycle checks on
  uploads, reads, deletes, processing, Knowledge Map operations, and retrieval eligibility.
- Clear validation, capacity, processing-failure, AI-unavailable, and
  permission-denied states.
- An enrolled-student Resources section that lists and serves only active PDFs
  for the requested course after server-side membership authorization.

## Non-goals

- Uploading non-PDF files, student material uploads/deletion, public material
  libraries, cross-course material sharing, or material revision recovery.
- Backups, retention, restoration, or a promise to recover deleted PDFs.
- Treating a Knowledge Map as the only source for student AI answers; generating
  learning objectives, candidate FAQs, multiple concurrent course maps, or
  automatic map regeneration after a material change.
- Publishing an AI artefact automatically as a forum answer or substituting an
  unapproved AI provider when SoC service access fails.
- A promise of numeric SoCLaaS quota, budget, concurrency, or availability.
  Selected model IDs and bounded local timeout/concurrency/retry behavior are
  implementation decisions of the approved shared service.
- Defining the student AI user flow; that is covered by its own specification.

## Roles and user stories

| Role | User goal | Required behavior |
| --- | --- | --- |
| Authorized course staff | Add an eligible lecture PDF to my course. | Upload a valid PDF within the stated limits, see whether it is accepted/processing/active/failed, and receive clear validation or capacity errors. |
| Authorized course staff | Remove a source I no longer want used. | Delete a material item and have it excluded immediately from new student-AI retrieval requests. |
| Authorized course staff | Organize course source materials using AI. | Manually generate/update the one Knowledge Map from all active PDFs, receive a reviewable map or an explicit safe failure, and inspect source PDF/page provenance. |
| Authorized course staff | Control whether the map is used. | Explicitly approve or reject the generated map; only an approved, current map may guide student-AI retrieval. |
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
- An upload, deletion, or replacement of an active source PDF immediately
  marks the course Knowledge Map stale. The system never starts regeneration
  automatically; authorized staff choose **Update Knowledge Map** when ready.
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
- A failed processing or Knowledge Map job exposes a safe, non-sensitive error
  message and a manual retry control to authorized course staff. The server
  permits no more than three such retry-job submissions per minute for the
  same staff member and course. This interim application guardrail applies
  until the official SoC envelope supplies stricter limits; it is not an
  asserted provider quota. Automatic retry/backoff behavior remains pending.

### Knowledge Map generation and review

- Each course has at most one current Knowledge Map. An authorized course staff
  member may manually request generation or update only from every active PDF
  in that course at request time. The request must use the shared guarded SoC
  service once available; it never runs automatically after a source change.
- The map is clearly labelled as generated supporting navigation material, not
  authoritative source material. It contains topics, subtopics, short
  navigation descriptions, and links/references to supporting source PDF pages
  for staff review. Learning objectives and candidate FAQs are not produced.
- A source upload, deletion, or replacement marks the existing map stale
  immediately. Stale maps are excluded from student-AI navigation and must be
  manually regenerated and approved before use.
- If the SoC service returns content but the grounding/provenance check cannot
  verify every active PDF, the system preserves that content only for
  authorized course-staff review. It labels it unverified, identifies the
  source-support gaps where determinable, and blocks approval, student-AI
  navigation, and publication. Service, quota, or processing failures with no
  usable returned content remain unavailable, rate-limited, failed, or
  retry-later states without a provider-capacity promise.
- Generated Knowledge Map content must be treated as untrusted output. It must not issue
  executable actions, change authorization, alter source-material lifecycle,
  or be automatically published to the forum.
- A generated Knowledge Map starts pending review. Only an explicit approval by
  authorized staff for the same course makes it navigation-eligible; rejection,
  lack of approval, or staleness keeps it ineligible. It remains derived
  supporting material linked to every active source PDF, never a replacement
  for active source PDFs or the sole basis for student AI output. Student-facing
  claims using it must still be grounded in and cite original PDF filename/page
  sources.

## Authorization and retrieval rules

All checks are server-side and occur before protected metadata/content is
returned or any job is started.

| Action | Student | Authorized course staff | Required server check |
| --- | --- | --- | --- |
| Browse/download active course PDFs in Resources | Allowed when enrolled | Allowed | Actor is a current student member or course staff for the course; source is active/current/not deleted; server authorizes every list/download. |
| List/read material metadata or source for staff operations | Denied | Allowed | Actor is course staff for the course. |
| Upload source PDF | Denied | Allowed | Actor is authorized course staff; course is in scope; file and capacity validation pass. |
| Delete source PDF | Denied | Allowed | Actor is authorized course staff; material belongs to course. |
| View processing/Knowledge Map status | Denied | Allowed | Actor is authorized course staff; job/material belongs to course. |
| Manually retry failed job | Denied | Allowed | Actor is authorized course staff; job belongs to course and is failed; server applies interim three submissions/minute/staff/course guardrail. |
| Generate/update Knowledge Map | Denied | Allowed | Actor is authorized course staff; every active PDF in the same course is included; service is available; regeneration is manual only. |
| Review/approve/reject generated Knowledge Map | Denied | Allowed | Actor is authorized course staff for the map's course; server records the explicit decision and reviewer. |
| Student AI retrieval of a source PDF | Only through later student-AI feature when enrolled | N/A | Requester is course-authorized before retrieval; source is active/current/not deleted; result retains source provenance. |
| Student AI navigation using a Knowledge Map | Only through later student-AI feature when enrolled | N/A | Requester is enrolled in the course; the one map is explicitly approved, current, and sourced from every active PDF; result still cites original PDF filename/page sources. |

Course staff are defined by the shared course-membership contract: the creator
is automatically course staff, and course staff may add another staff account
by username. No client-provided course, filename, material ID, status, or
reviewer claim is trusted without server verification.

## Data and API implications

The shared platform must define documented contracts for, at minimum:

- source material identity, course, uploader, safe display name, lifecycle
  status, validation failure code where relevant, and active/deleted state;
- processing/Knowledge Map job identity, course, requestor, status, safe
  failure information, and source-material version/set reference;
- provenance connecting an extracted/retrieved or generated statement to a
  current source PDF and page;
- the single course Knowledge Map identity, source snapshot/version set, topic
  entries, `generating`/`pending_review`/`approved`/`rejected`/`stale`/`failed`
  status, approval decision time, and reviewer identity; and
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
  Knowledge Map endpoints directly: deny without leaking material existence,
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
  same-course scope server-side, including the all-active-source set for a map.
- A deleted or failed material is referenced by a stale browser/job/index:
  exclude it from all new retrieval/Knowledge Map results and mark the map stale.
- A removed student or unrelated-course actor requests a Resources list,
  download, or guessed citation URL: deny without disclosing material
  existence or storage location.
- A staff actor repeatedly submits retries for failed jobs: enforce the interim
  three-per-minute-per-staff-per-course guardrail and return a safe rate-limit
  state; do not represent it as a provider quota.
- A malicious or hallucinated Knowledge Map claims sources it lacks: label it
  generated and unverified, restrict it to authorized course-staff review, and
  block approval/student use until traceable source PDF/page support validates.
- An unapproved, rejected, stale, cross-course, or source-invalidated Knowledge
  Map is offered to student retrieval: exclude it server-side. An approval claim or
  reviewer identity supplied by a client or generated content cannot make it
  eligible.
- SoC service quota/outage/error: show a safe explicit failure; do not invent
  Knowledge Map output, use a replacement provider, or disclose source content
  beyond the requester’s authorization.

## Dependencies and assumptions

- Depends on `workflow/features/product-foundation/spec.md` and the future
  approved shared course/membership contract.
- Depends on Stage 2 authorization/storage foundation and Stage 3 shared,
  guarded SoC/RAG service. Published numeric SoCLaaS operating limits are not
  a prerequisite; the shared service must keep its bounded local controls.
- Requires a staff-facing `design.md` after the behavioral decisions below are
  approved and before implementation.
- The user-approved Railway topology provides private object storage and a
  private worker, but this spec does not select storage APIs or queue design.

## Recorded operating decision

On 2026-10-21, the human product owner accepted normal SoCLaaS use without
published numeric quota, budget, or concurrency values. This removes those
values as an implementation blocker. The feature still depends on Stage 3,
which selects permitted model IDs from `GET /v1/models`, bounds local work,
and handles 429, 5xx/503, and timeouts safely without an availability promise
or substitute provider.

## Acceptance criteria

1. A student is denied, including by direct request, when attempting to upload,
   delete, list staff-only material metadata, view processing status, generate
   or review a Knowledge Map; the response does not disclose
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
   staff member and course as an application guardrail.
9. An authorized staff deletion immediately excludes the material and its
   derived data from all new student-AI retrieval and consolidation requests;
   stale identifiers, queued work, and cached/indexed data cannot restore it to
   a new result.
10. An authorized staff actor can manually generate or update the one Knowledge
   Map only from every active, same-course source PDF. The result is labelled
   generated and displays traceable source PDF/page references; it is never
   automatically posted to the forum or automatically regenerated after a
   material change.
11. A Knowledge Map does not become the sole authority for student AI. It may
   guide student-AI retrieval only after an authorized staff member for that
   course explicitly approves a current map; any use retains active source-PDF/
   page grounding and student-facing citations to original PDF filename/page sources.
12. For an indirect prompt-injection payload in a filename/PDF or hostile
    generated text, the system treats it as untrusted data: it cannot alter
    authorization, storage path, source lifecycle, review decisions, model
    policy, or cause an AI output to be automatically published.
13. When the SoC service is unavailable, quota-limited, malformed, or returns
    no usable content, no fabricated or substitute-provider result is shown;
    staff see an explicit safe unavailable/failed/rate-limited/retry-later
    state. A returned but unverified map is instead visible only to authorized
    staff, clearly labelled and not approval-eligible or student-usable.
14. The final implementation enforces course-staff authorization and the
    Knowledge Map approval/currentness policy with automated positive and
    negative authorization tests, including pending, rejected, stale,
    forged-approval, and cross-course cases.

## Verification evidence expected

Engineering must record test evidence for PDF type/content/size/page/capacity
validation; direct endpoint denials; cross-course material/job isolation;
deleted/stale material exclusion; Resources list/download authorization and
revocation; processing failure/manual-retry rate limiting; Knowledge Map
provenance, labelling, approval, manual-only regeneration, and stale-map
exclusion; SoC failure; and indirect prompt-injection fixtures.
An independent tester must verify the final staff-authority and approval policy,
the visible lifecycle states, and that deleted material cannot appear in a new
AI request.

## Handoff

**Feature:** `staff-material-governance` — Stage 4A complete; AI-dependent
scope pending shared Stage 3 completion and human approval, not numeric SoCLaaS
operating-limit confirmation.

**Input artefacts:** `AGENTS.md`, `Instructions_and_Planning.md`,
`workflow/project-overview.md`, `workflow/development-process.md`,
`workflow/implementation_plan.md`, and
`workflow/features/product-foundation/spec.md` (Phase 0 decisions).

**Deliverable:** This specification only; no UI design, source code, storage
design, schema, AI-provider call, or tests were created.

**Next owner:** Human product owner approves the AI-dependent specification and
design after the shared AI/RAG contract is approved. @p-xp implements
AI-dependent work only against the later approved guarded AI/RAG contract.

## Approved staff-dashboard and map-validation addendum (2026-10-21)

This addendum is approved by the human product owner in the request dated
2026-10-21. It extends the approved staff-facing behavior above. Where an older
specification or design calls the staff material workspace **Manage materials**,
this addendum changes its visible name to **Knowledge Map**. Server-side role,
course, source-currentness, and approval rules remain unchanged.

### Confirmed behavior

- In an authorized staff member's **Resources** tab, show a top-right **Upload
  file** button. Students and other non-staff users never see this control.
  Clicking it opens a centered overlay containing the existing PDF upload form
  and its validation/capacity guidance. The overlay is dismissible by its close
  control and Escape; it must not weaken the existing server-side authorization
  or PDF validation rules.
- Rename the existing **Manage materials** staff tab/section to **Knowledge
  Map**. Preserve its existing material lifecycle, map generation, candidate
  review, approval/rejection, and failure/retry features, subject to this
  addendum's explicit display changes. Existing material list and lifecycle
  rows remain in this workspace. Students continue to see only Resources and
  Forum.
- Do not render the separate clickable **Source support** download links below
  a returned map. Keep the map's own textual source citations and verification
  outcome visible; removing those extra links does not permit dropping source
  provenance from generated content or from staff review.
- Constrain the returned-map content viewport to at most 70% of the viewport
  height (`70vh`) and provide an internal vertical scrollbar when its content
  exceeds that height. The surrounding workspace and review actions remain
  usable and the map content remains keyboard-readable.
- When a map fails source validation, show the reason(s) that the validator can
  establish, in safe staff-facing language, instead of always returning only
  the current generic “missing valid support” message. At minimum, identify:
  missing citations; non-canonical citation syntax or formatting; a filename
  that does not match the source PDF; a cited page number outside the PDF or
  without extracted text; and a claim line whose citation is not at the end of
  the line. If several checks fail, provide a concise set of applicable
  reasons. Use the existing general unverified-map message only when no more
  specific safe reason can be determined. All unverified content remains
  staff-only and approval-ineligible.
- Update the generation prompt to request unformatted plain-text topic headings
  and one factual claim per line, with one or more exact canonical citations as
  the final characters of each claim line and no trailing punctuation after
  citations. The prompt must continue to prohibit invented citations and
  require every factual claim to be cited to supplied filename/page pairs.
  Keep the server verifier authoritative: a prompt change does not itself make
  a map verified.

### Finding for the submitted example

The current server verifier in `PlatformService.hasValidCitation` checks each
nonblank, non-`#` line. It requires a canonical citation for the source
filename, a page with extracted text, and that citation's closing `)` be the
last character on the line. The submitted map bundles claims and contains
Markdown headings such as `**Course Overview**`, which are not recognized as
headings and have no citation; its claim lines place a period after citations,
for example `(l0.pdf, p. 1).`, so the citation is not the line's final
character. Either pattern is sufficient to fail current verification. The
current prompt requires canonical citation syntax and that every supplied PDF
be cited, but does not explicitly require one claim per line, prohibit Markdown,
or prohibit punctuation after a citation. Therefore prompt clarification is
part of the remedy. The current verifier does not semantically check whether a
line bundles multiple factual claims or whether each claim is factually
supported; the prompt should request one claim per line, and source support
still requires human review. This finding explains the reported failure; it
does not establish that every cited statement is factually supported by the
PDF.

### Assumptions and open decision

- The upload overlay contains the existing upload form and its related
  validation/capacity feedback. The earlier assumption that material
  list/lifecycle rows remained in the Knowledge Map workspace was superseded by
  the approved layout follow-up below.
- The 70vh maximum applies to returned map content, not the entire Knowledge
  Map workspace.

### Addendum acceptance criteria

15. In Resources, an authorized staff member sees **Upload file** at the top
    right; a student does not. Activating the button opens a centered overlay
    with the existing PDF upload controls and validation/capacity guidance.
    Dismissal works with the close control and Escape, and unauthorized direct
    upload attempts remain rejected server-side.
16. The prior **Manage materials** tab/section is visibly named **Knowledge
    Map**. Existing material list/lifecycle controls and Knowledge Map
    generation, review, approval/rejection, and retry behavior remain available
    to staff; student navigation remains limited to Forum and Resources.
17. Returned map content does not show the separate clickable Source support
    download links. Its generated citations, unverified notice/reasons, and
    existing staff review actions remain visible as applicable.
18. The returned map content area is no taller than 70vh and scrolls vertically
    when content exceeds the available height. Keyboard users can reach and
    read the entire content without losing access to surrounding review
    actions.
19. For fixtures with missing citations, malformed/non-canonical citation
    syntax, wrong source filename, out-of-range or textless page, and trailing
    content after a citation, staff receive the matching actionable validation
    reason(s). For an
    unclassified validation failure they receive the existing general message.
    Every such map remains staff-only and cannot be approved.
20. The generation prompt requests plain unformatted headings, one factual
    claim per line, and citations as each claim line's final characters with no
    trailing punctuation. A fixture shaped like the submitted example is either
    corrected by generation to satisfy the canonical verifier or remains
    unverified with a specific explanation; it is never approved solely due to
    prompt instructions.

### Verification evidence and handoff

Engineering records focused UI checks for staff/student Resources controls,
overlay open/dismiss/upload and validation states, renamed tab behavior, hidden
support links, 70vh scrolling, plus server/UI checks for each named citation
failure reason and the general fallback. Prompt tests inspect the submitted
example's heading, bundled-claim, and trailing-period cases and confirm that
accepted maps cite valid pages from their own source PDF. Independent testing
covers new criteria 15–20 alongside existing authorization and approval
criteria.

**Next owner:** UI designer updates `design.md` to align Resources, the upload
overlay, the renamed Knowledge Map workspace, and bounded map scrolling with
this approved addendum. The engineer may implement after that handoff and
Stage 3's existing dependency is satisfied. The ambiguity about moving the
entire management section into the overlay is recorded above for design review.

## Approved Resources and Knowledge Map layout follow-up (2026-10-22)

This follow-up is approved by the human product owner in the request dated
2026-10-22 and supersedes the earlier display decision in criterion 16 that
kept material lifecycle rows in the Knowledge Map workspace. Server-side
authorization, deletion semantics, and Knowledge Map verification/approval
rules remain unchanged.

- In Resources, assigned staff see a red **Delete file** button immediately to
  the right of **Download PDF** on each available resource row. Students see
  the download action only. Deletion uses the existing course-authorized
  endpoint and confirmation; successful deletion removes the file from
  Resources and new retrieval/generation inputs.
- The staff **Knowledge Map** tab contains only the existing **Course Knowledge
  Map** section and its map generation, refresh, status, and review features.
  Remove the separate material list/lifecycle section from this tab. Upload
  remains in Resources, and file deletion is available there.
- Remove the “AI-generated supporting navigation material” tagline. Beneath
  **Course Knowledge Map**, explain that the map organizes course topics and
  helps student AI search for relevant course material and supporting PDF
  pages; it guides retrieval, does not replace source PDFs, requires staff
  review/approval before use, and student answers still cite original sources.

### Additional acceptance criteria

21. Each active resource row gives assigned staff **Download PDF** followed
    immediately by a red **Delete file** action. Students see no delete
    control. Deletion retains the existing confirmation and server-side
    authorization and removes the source from Resources and new AI use.
22. The staff Knowledge Map tab shows only **Course Knowledge Map** and its
    existing map controls/content; it has no separate material lifecycle list.
    The tagline is absent, and explanatory text accurately describes topic
    search/retrieval, staff approval, and original-PDF citations.

The engineer records focused role-based Resources checks, delete behavior,
Knowledge Map section/content checks, and the existing map verification and
authorization test results.

## Approved Knowledge Map review-control follow-up (2026-10-22)

The human product owner approved the following interaction changes in the
request dated 2026-10-22. They change only staff-facing controls and do not
change map eligibility, server authorization, source currentness, verification,
or student citation requirements.

- Activating **Approve for student AI navigation** on an eligible pending-review
  map submits the approval immediately. Remove the approval confirmation
  message/dialog.
- Remove the **Refresh Map status** button. Load current map/status data on
  normal page load and refresh it after completed actions, including approval,
  rejection, and generation/update, so the page does not require a separate
  manual status-refresh control.
- Keep the rejection confirmation. Preserve visible busy/in-progress,
  success, and safe error feedback for actions. Do not show success or update
  the displayed decision as accepted until the server confirms it; on failure,
  retain the prior state and show the safe error.
- Keep **Approve for student AI navigation** unavailable for unverified maps.
  The server must continue to enforce authorized course-staff identity,
  same-course scope, map currentness/source snapshot, and verified source/page
  support before accepting approval. Client controls do not establish
  eligibility.
- Approval enables student AI navigation only under the existing rules:
  student requests remain course-authorized and answers still cite original
  PDF filenames and pages; the map remains supporting navigation material.

### Additional acceptance criteria

23. For an eligible verified pending-review map, one activation of
    **Approve for student AI navigation** sends the approval action immediately
    without an approval confirmation message/dialog. A busy state prevents
    duplicate submission; success is shown only after server confirmation,
    followed by refreshed map/status data and the recorded decision summary.
24. The **Refresh Map status** button is absent. Initial page load obtains
    current map/status data, and completion of generation/update, approval, or
    rejection refreshes the displayed data without that control.
25. Rejection still requires its confirmation. Approval remains hidden or
    disabled for an unverified map. Direct or manipulated requests from an
    unauthorized actor, for another course, for a stale/non-current map, or
    for a map with unverified source/page support are rejected server-side;
    no client state can make such a map eligible. Failures retain the prior
    decision/status and display safe error feedback.
26. Student AI navigation continues to require current course authorization
    and an approved, current, verified map; student-facing answers using the map
    cite original PDF filenames and pages.

**Verification evidence and handoff:** Engineering records focused UI checks
for immediate approval (including busy, success, and error states), continued
rejection confirmation, absence of the status-refresh button, data refresh on
page load and after actions, and disabled approval for unverified maps. Server
tests verify authorization, course scope, currentness, and source/page
verification remain enforced, and student-facing citation behavior remains
unchanged. Independent testing covers criteria 23–26 with the existing map
approval and authorization criteria.

**Next owner:** UI designer updates `design.md` to align the approval and status
controls with this approved follow-up; engineer implements after design
alignment.
