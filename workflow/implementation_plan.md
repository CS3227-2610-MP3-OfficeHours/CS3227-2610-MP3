# Implementation Plan — University TA Help Desk

## Plan status and purpose

**Status: Active — revised 2026-10-21.** The SoCLaaS integration reference is
now recorded: it is an OpenAI-compatible, school-hosted gateway at
`https://soclaas-api.comp.nus.edu.sg/v1`, authenticated by a server-held API
key. The product owner has accepted normal-use operation without published
numeric SoCLaaS quotas; the implementation still uses bounded concurrency and
safe 429/5xx handling. This plan still requires feature-level approval before
work that has not already been approved begins.

This is the broad delivery order for the two-role University TA Help Desk. It
preserves the existing work division and adds the approval gates and handoffs
needed by the repository workflow. It is not a substitute for approved,
feature-specific requirements in `workflow/features/<feature-name>/`.

## Delivery status snapshot (evidence-based, 2026-10-21)

**Legend:** **Complete** means the planned scope has independent verification
evidence. **Partial** means only the stated subset is complete; it must not be
represented as completing the whole stage. **Blocked** means a documented
external approval/dependency prevents the stage. **Not started** means no
implementation/verification evidence is recorded for that stage.

| Stage | Status | What is complete / remaining evidence |
| --- | --- | --- |
| 0 — scope and guardrails | **Partial** | SoCLaaS gateway/API behavior is documented and the staff key can access chat models plus `bge-m3` embeddings. The product owner accepted normal-use operation without published numeric quota/budget/concurrency values; bounded local controls and safe provider-failure handling remain required. |
| 1 — specifications and role flows | **Partial** | The shared-course-forum, shared Stage 3 staff AI/RAG foundation, and staff Knowledge Map specifications/designs are approved. The student-AI specification, including academic-integrity behavior, remains deferred to its owner. |
| 2 — shared secure platform | **Partial** | The server-authoritative account, course, membership, forum, PostgreSQL migration, authorization, and API-contract foundation is independently verified in `workflow/features/shared-course-forum/test-report.md`. Role-facing student forum UI remains unfinished. |
| 3 — shared AI and material-processing services | **Not started** | The staff key is securely configured, its model catalog is verified, and the staff AI/RAG foundation is approved. Student AI behavior is expressly out of this stage’s current implementation scope. |
| 4A — non-AI staff material governance | **Complete** | Independently verified as passing in `workflow/features/staff-material-governance/test-report.md`; this excludes every AI/RAG/consolidation capability. |
| 4B — AI-dependent staff material governance | **Blocked — not started** | Depends on completed Stage 3; the Knowledge Map specification/design are approved. |
| 5A — shared student/staff forum experience | **Complete** | Independently verified as passing in `workflow/features/shared-course-forum/test-report.md`: two-role authentication, course/member management, forum post/comment CRUD, moderation boundaries, and safe failure/retry states are implemented. Live multi-session browser verification remains an environment-limited follow-up because this sandbox cannot bind the local Java HTTP listener. |
| 5B — student AI-help experience | **Blocked — not started** | Depends on Stage 3, suitable approved material fixtures, and the student-AI specification/design approval. |
| 6 — shared production deployment | **Not started** | Railway topology is approved, but no live deployment, migration, private-storage, or multi-user evidence is recorded. |
| 7 — release verification and submission evidence | **Not started** | Requires the completed release/deployment stages and final regression/documentation evidence. |

## Work division (preserved)

| Accountable owner | Area of responsibility |
| --- | --- |
| **@ziyang** | Student-facing development only: student dashboard, student forum flows, student AI help feature, and their tests. |
| **@p-xp** | Initial setup, shared platform infrastructure, server-side security foundations, deployment, documentation/process artefacts, and every staff-facing feature. |
| **Human product owner** | Approves consequential product, academic-integrity, access-control, security, and design decisions before implementation; accepts the release. |
| **Specialist workflow agents** | Product manager creates feature specifications; UI designer creates needed UI designs; engineer implements approved work and records `tasks.md`; tester independently verifies and records `test-report.md`. These roles may be performed by the accountable developer, but the required artefacts and approval gates remain. |

One task has one accountable owner. Shared work is owned by **@p-xp**; @ziyang
consumes documented shared APIs/contracts and reports integration defects rather
than changing shared ownership unilaterally.

## Delivery stages and dependency order

### Stage 0 — Confirm scope and guardrails

**Owner:** Human product owner, with @p-xp coordinating.

**Build/decide:** Confirm the product boundary: staff create and manage courses
and course PDFs; enrolled students use a course-scoped forum and AI help; staff
use AI material consolidation. Confirm the project constraints from
`Instructions_and_Planning.md`: SoC LLM use, explicit AI security, separate
development/production environments, team-managed deployment, required
documentation, logs, public repository, and GitHub Pages site.

**Handoff/gate:** Product owner records the decisions in the feature
specifications. No substantial feature implementation begins until its
consequential decisions are approved.

### Stage 1 — Specify the features and role flows

**Owners:** Product manager first; UI designer for each user-facing feature;
human product owner approves. @p-xp is accountable for starting/maintaining the
workflow artefacts.

**Produce:** Separate, testable specifications at minimum for:

- shared identity, course membership, and course-scoped forum;
- staff material upload, processing, review, and consolidation;
- student AI help; and
- shared AI/RAG security and deployment/release behavior.

Write a UI design after each relevant specification for the staff and student
flows. The specifications must establish authorization, data scope, failure
states, academic-integrity behavior, AI grounding/citations, and testable
acceptance criteria. The AI/RAG specification must treat PDFs, filenames,
forum posts, summaries, and prompts as untrusted input.

**Handoff/gate:** Each feature directory receives the required `spec.md`, and
where applicable `design.md`, with the status/approval block required by
`workflow/development-process.md`. Human approval is required before an
engineer begins that feature. Approved interfaces and acceptance criteria are
then handed to the accountable implementation owner.

### Stage 2 — Establish the shared, secure platform foundation

**Owner:** @p-xp.

**Build:** Create the application/repository structure and local developer
environment; configuration and secret handling; database migrations; automated
quality checks; seed data; authentication; and server-enforced role and
course-scoped authorization. Establish the shared persistence/API contracts for
courses, membership, posts, replies, materials, processing jobs, generated
artefacts, citations, reviews, and audit events.

Also establish the common security primitives: input validation and safe
rendering, private material storage, structured audit logging without secrets,
and baseline cross-course/access-denial tests.

**Handoff/gate:** @p-xp documents stable API/data contracts and test evidence in
the relevant `tasks.md`. The student work may implement against these contracts
only after they are available. Any contract change affecting student work is
returned to the relevant specification/design for review before implementation.

### Stage 3 — Build the shared AI and material-processing services

**Owner:** @p-xp.

**Build:** Implement the guarded SoCLaaS integration once for both role
features, using its OpenAI-compatible `POST /v1/chat/completions` and
`POST /v1/embeddings` endpoints. Keep `SOCLAAS_STAFF_API_KEY` and
`SOCLAAS_STUDENT_API_KEY` only in server/worker secret configuration; set the base URL to
`https://soclaas-api.comp.nus.edu.sg/v1`; and never expose the key or direct
gateway access to browsers. At startup/controlled administration time, call
`GET /v1/models` with the project key and record the returned permitted public
model IDs, context limits, and which ID has embedding capability. Select model
IDs from that response rather than hard-coding examples from documentation.

Build a provider adapter with explicit request timeouts, bounded concurrency,
and structured outcomes. Treat 400 as a non-retryable request/configuration
failure, 401/403 as a secret/model-policy configuration failure, 429 as either
rate or budget exhaustion, and 5xx/503 as a transient service failure. Retry
only idempotent/safely repeatable background jobs with bounded exponential
backoff and jitter; never automatically re-run an end-user consolidation in a
way that can create duplicate artefacts. Log correlation ID, operation type,
model alias, status class, latency, and token usage when supplied, while
redacting API keys, prompts, PDF text, vectors, and protected course metadata.
Do not rely on `/v1/responses` conversation state for application workflows:
its `previous_response_id` cache is process-local, expires after 15 minutes,
and does not survive restarts.

Implement course-authorized ingestion and
retrieval: PDF validation/storage, isolated extraction, page-aware chunking,
provenance, embeddings, versioning/invalidation, and retrieval filtered by the
requester's authorized course *before* similarity search.

Add versioned prompt templates and central policy enforcement for prompt
injection resistance, citation/grounding checks, restricted-assessment behavior,
and safe malformed/unsupported output handling. Create the security test corpus
and automated negative tests for direct and indirect prompt injection,
cross-course requests, unapproved/stale material, hostile forum text, and bad
uploads.

**Handoff/gate:** @p-xp publishes the approved service/API contract, fixtures,
known limits, and security-test evidence to the feature artefacts. Both
role-specific AI features depend on this gate; they must not implement separate
LLM clients or bypass server-side authorization/retrieval controls.

**SoCLaaS readiness checklist (required before this stage's implementation
gate):**

1. Issue a dedicated project API key through the SoCLaaS portal or
   `soclaas-portal issue`; store it only in the local/server secret store and
   confirm it is excluded from Git, browser bundles, logs, and test fixtures.
2. Use that key to query `GET /v1/models`; record the exact permitted chat and
   embedding public IDs, context limits, and selection rationale in the Stage 3
   feature artefact. A chat-only model cannot be used for embeddings.
3. Use small, non-sensitive test prompts to verify chat and embedding calls,
   request timeout behaviour, and the configured deployment secret injection.
4. The product owner has approved normal-use operation without fixed published
   numeric quota, budget, or concurrency values. Apply bounded local
   concurrency, neutral quota/rate-limit wording for 429, and the gateway's
   safe failure states; do not make an availability or capacity guarantee.
5. Send the minimum authorized PDF excerpt necessary for an operation and do
   not send forum content by default. Do not claim an external provider
   retention policy that has not been documented.

### Stage 4A — Deliver non-AI staff material governance (approved before Stage 3)

**Owner:** @p-xp.

**Sequencing decision (human-approved, 2026-10-20):** The staff feature is
split so the non-AI material experience can proceed while the SoC operating
envelope is unavailable. The non-LLM implementation paths are Stage 2 → Stage
4A and Stage 2 → Stage 5A; they may proceed independently when their own
approved feature artefacts are ready. The LLM-dependent path is Stage 3 → Stage
4B and Stage 3 → Stage 5B. Stage 4A uses the Stage 2 course, membership,
authorization, material, and job contracts; it must not create a parallel AI,
embedding, retrieval, or consolidation pipeline.

**Build:** Deliver staff PDF upload, material status, delete, and retry
visibility; and enrolled-student access to active course PDFs through
Resources. Enforce upload validation, material lifecycle, and server-side
course/role authorization. Clearly show that AI consolidation is unavailable.

**Verify:** Test upload limits/type handling, non-AI processing failures,
material replacement/invalidation, role/course authorization, and staff-visible
failure states.

**Handoff/gate:** Engineer records implementation and tests in the staff
feature `tasks.md`; independent tester verifies the approved non-AI acceptance
criteria in `test-report.md`. Completion of Stage 4A does not authorize SoC,
RAG, embeddings, or consolidation work.

### Stage 4B — Deliver AI-dependent staff material governance (after Stage 3)

**Owner:** @p-xp.

**Prerequisites:** Completed Stage 3 and human approval of the updated
AI-dependent staff specification/design.

**Build:** Deliver the staff material-consolidation flow as a server-side,
asynchronous job with the following implementation slices:

1. **Eligibility snapshot.** On request, authorize the staff member and course
   server-side, accept only selected active PDFs from that course, and persist
   an immutable job snapshot containing material/version IDs and page-aware
   chunks. Never accept client-provided text, course IDs, approval status, or
   citation data as authoritative. Reject deleted, failed, processing,
   cross-course, or stale sources before a provider call.
2. **Grounded Knowledge Map generation.** Retrieve bounded source chunks from
   every active PDF in the course, call the selected SoCLaaS chat model through
   the Stage 3 adapter, and require one structured course Knowledge Map with
   topics, subtopics, navigation descriptions, and source material IDs/pages.
   Treat PDF text, filenames, and retrieved content as quoted untrusted data,
   never as instructions. Enforce output-size and source-count limits derived
   from the selected model's context window; use deterministic chunk batching
   rather than silently truncating a course's materials. Learning objectives
   and candidate FAQs are out of scope.
3. **Validation and persistence.** Parse the provider response defensively;
   reject malformed output, invented/missing citations, citations to a source
   outside the all-active-PDF job snapshot, or output unsupported by active
   pages. Persist only the generated Knowledge Map marked `pending_review`,
   with provenance, model public ID, prompt-template version, source-material
   versions, and safe job outcome metadata. A map is never a forum post and is
   never automatically student-navigation eligible.
4. **Review and retrieval boundary.** Permit only currently authorized staff
   of that course to approve or reject a pending Knowledge Map. Approval must
   be server-recorded with reviewer/time and re-check that every active source
   is in the snapshot; rejection, source change, or deletion/invalidation marks
   it ineligible. Student retrieval may use an approved/current map only for
   navigation and must still ground every claim in active original PDF filename/
   page citations.
5. **Operations and failure UX.** Map 429 to a neutral quota/rate-limited job
   state, 5xx/503 and retry-safe network timeouts to a retry-later state, and
   400/401/403 or malformed/ungrounded output to a safe failed state requiring
   configuration or staff action as appropriate. Preserve no partial artefact
   on failure, prevent duplicate active jobs for the same normalized source
   snapshot, and enforce the documented manual retry guardrail.

Each course has at most one current Knowledge Map. A successful staff-requested
generation produces a reviewable map with source PDF/page links; it becomes
student-navigation eligible only after authorized staff approval. Uploading,
deleting, or replacing active source material immediately marks the map stale,
and stale maps are excluded from student retrieval until staff manually request
an update and approve its result.

**Verify:** Test the adapter against mocked and recorded-safe SoCLaaS responses
for 400, 401, 403, 429, 5xx/503, timeout, malformed JSON, and valid chat/
embedding outcomes; test provider calls only from server/worker code with no
key in responses/logs. Test map generation/review decisions, one-map-per-course
enforcement, all-active-PDF source snapshots, stale-on-source-change behavior,
manual-only updates, student-navigation eligibility, duplicate-job prevention,
prompt-injection fixtures, forged/cross-course approval attempts, and
staff-visible AI failure states.

**Handoff/gate:** Engineer records implementation and tests in the staff feature
`tasks.md`; independent tester verifies the approved acceptance criteria in
`test-report.md`. Defects return to @p-xp; unresolved product decisions return
to the product owner through the specification.

### Stage 5A — Deliver the shared student/staff forum experience (non-LLM)

**Ownership:** This stage comprises two independently owned role tasks:
@ziyang owns the student forum/dashboard flow, and @p-xp owns the staff
forum/dashboard flow and staff-only moderation controls. The shared server
contract remains owned by @p-xp. Each owner records evidence for the
role-facing work they deliver; they must coordinate contract changes through
the shared-course-forum specification.

**Prerequisites:** Approved forum specifications/design; stable shared identity,
course-membership, and forum contracts from Stage 2. This stage does **not**
depend on Stage 3, an LLM, embeddings, retrieval, or AI-material fixtures.

**Build:** Deliver the course dashboard and course-scoped forum for both
enrolled students and course staff. Both roles can browse forum posts and
threads, create posts/questions, reply/comment, and edit/delete their own
content where the shared contract permits it. Provide staff forum controls
already authorized by Stage 2 (including student-content moderation) without
granting staff authority over another staff member's content. Retain server-side
course and role authorization as the source of truth.

Provide clear loading, empty, validation-error, permission-denied, and
request-failure states. This stage contains no AI-help entry point, AI-service
state, citation display, generated output, or automatic forum publishing.

**Verify:** Test student and staff enrolment-scoped visibility, cross-course
rejection, post/comment CRUD, staff moderation boundaries, author-edit/delete
boundaries, validation, and the loading/empty/error/denial states for both
roles.

**Handoff/gate:** Each owner records role-scoped implementation and test
evidence in their forum task handoff. An independent tester verifies the
complete two-role forum experience in the forum feature `test-report.md`.
Integration defects in shared services are handed to @p-xp with a reproducible
case; ambiguities return to the product owner/specification. Completion
authorizes the non-AI forum experience only; it does not authorize any LLM/RAG
work.

### Stage 5B — Deliver the student AI-help experience (LLM-dependent)

**Owner:** @ziyang.

**Prerequisites:** Completed Stage 3; approved student-AI specification and UI
design; stable authorized AI-service contract; and suitable approved
material/test fixtures supplied by @p-xp. Stage 5A is not a technical
prerequisite, but the AI UI must not automatically publish into the forum.

**Build:** Deliver student AI help that requests server-side authorized
retrieval and shows a clearly labelled hint, explanation, or clarifying
question with original filename/page citations. AI output is reference
aid/draft material only and is never automatically published to the forum.
Implement the approved restricted-assessment response behavior and clear
rate-limit and AI-service-failure states.

**Verify:** Test enrolment-scoped visibility, cross-course rejection, citation
display, AI failure handling, and the approved academic-integrity/security
cases.

**Handoff/gate:** @ziyang records implementation and test evidence in the
student-AI feature `tasks.md`; independent tester verifies it in
`test-report.md`. Integration defects in shared services are handed to @p-xp
with a reproducible case; ambiguities return to the product owner/specification.

### Stage 6 — Deploy shared production data and application services

**Owner:** @p-xp for infrastructure; human product owner approves the hosting,
data-retention, backup, and production-access decisions.

**Prerequisites:** The application has production-ready migrations and a
documented production configuration contract. Development and production
credentials, databases, and object-storage locations are separate.

**Build:** Deploy one server-authoritative API reachable over HTTPS by users on
different networks, plus a production PostgreSQL service reachable only by the
API/runtime. Configure the API with the production database connection through
deployment-managed secrets; never place database credentials or a database
network endpoint in the browser bundle. Apply and record migrations before
serving traffic.

Deploy private, durable object storage for uploaded PDFs and future derived
artefacts. Store course/material metadata, ownership, lifecycle status, and
object references in PostgreSQL; store PDF bytes in the private object store.
Every list, download, processing, and retrieval request must re-authorize the
current user and course through the API before accessing an object. Object URLs
must not be public or durable client capabilities; use an authorized API stream
or narrowly scoped, short-lived signed delivery mechanism. Deletion and
invalidation must remove or make inaccessible both the database record and
stored object/derived data.

Configure backups, restore testing, health/readiness checks, migration failure
and rollback procedures, log redaction, quotas/retention, and least-privilege
service credentials. The deployment must support concurrent users from
different networks seeing the same current accounts, memberships, courses,
forum records, and authorized PDFs—not each user's local Docker volume or
in-memory process state.

**Verify:** From separate network/browser sessions, create an account/course
and confirm the same data is visible to an authorized second user through the
shared deployment. Verify persistence across API restart, private database and
object-store network boundaries, revocation before a direct PDF download,
cross-course object denial, deletion/invalidation, backup restore, migration
on a clean environment, and failure-safe health/error behavior.

**Handoff/gate:** Engineer records deployment configuration without secrets,
live test evidence, restore evidence, known limits, and operating ownership.
An independent tester verifies the multi-user journey and private-material
access boundary before the release is accepted.

### Stage 7 — Integrate, verify, and prepare submission evidence

**Owner:** @p-xp for release regression and required artefacts; @ziyang
supports student-flow regression evidence; tester independently verifies the
release; human product owner accepts it.

**Build/verify:** Run the full security, authorization, migration, unit,
integration, and end-to-end suites against the Stage 6 deployment. Configure
GitHub Actions quality gates and the team-managed release workflow, including
the approved SoC LLM configuration. Verify the live peer-tester journey using
the shared production services.

Complete `docs/UserGuide.md`, `docs/DeveloperGuide.md`, `docs/Reflections.md`,
the required interaction summaries under `logs/`, and the GitHub Pages product
website. Ensure these reflect the released behavior and acknowledge reused work.

**Handoff/gate:** Tester supplies final independent `test-report.md` evidence.
The human product owner reviews the release checklist and accepts only when the
repository/layout, public accessibility, deployment, documents, security
evidence, and master-branch state meet the assignment requirements.

## Product-owner review and clarification required

The following choices materially affect behavior, security, scope, or grading
evidence. They are intentionally not assumed by this plan and must be decided
in the relevant feature specification before its implementation gate.

| Decision needed | Why review is needed | Blocks / affects |
| --- | --- | --- |
| **Academic-integrity boundary**: what counts as a restricted assessment, how it is identified, and the exact allowed/refused AI-help behavior. | Determines whether AI may give hints, explanations, partial solutions, or refuse; this cannot be inferred safely. | Student Stage 5B AI help, output policy, security tests, user guide; it does not block the approved staff Stage 3/4B path. |
| **Course membership lifecycle**: who may create courses, add/remove students, and whether students can self-enrol or leave. | Defines authorization and audit requirements. | Shared APIs, staff dashboard, student visibility. |
| **Forum moderation and visibility policy**: deletion/editing, reporting, staff controls, and visibility of staff/AI-related content. | Changes data lifecycle, role permissions, and user flows. | Forum spec/design and tests. |
| **Staff approval policy**: whether approved original PDF chunks alone are usable, whether generated consolidation artefacts require approval, and who may approve them. | Governs what the student AI may retrieve and the review workflow. | Ingestion, staff flow, RAG filters, security tests. |
| **Material limits and retention**: permitted PDF size/page limits, replacement/deletion behavior, retention of old versions and audit logs. | Sets operational cost, privacy, and failure-state behavior. | Upload processing, storage, deployment, documentation. |
| **Authentication and tester access approach**: identity provider/login method and how peer testers receive authorized staff/student accounts. | Affects security model, setup effort, and accuracy of the user guide. | Platform foundation, deployment, user guide. |
| **Deployment target and data handling**: approved hosting, production-data policy, backup/recovery expectations, and production access owners. | Required for separate environments and deployment evidence; cannot be selected by the plan alone. | CI/CD, production configuration, release acceptance. |
| **Scope triage for the deadline**: the minimum release scope if all listed capabilities cannot be completed. | Prevents silently dropping required AI/security/workflow evidence. | Scheduling and any feature deferral. |

## Definition of done for every stage/feature

Before a feature is treated as complete, it has:

1. An approved specification and, for user-facing work, approved design that
   match the delivered behavior.
2. A single accountable implementation owner and an engineer `tasks.md` with
   changed areas, test commands/results, limitations, and next handoff.
3. Automated tests appropriate to the task, including a negative/security case
   whenever data crosses a role, course, upload, or AI trust boundary.
4. Independent tester evidence in `test-report.md` for substantial features.
5. Updated user/developer/process documentation where released behavior or
   required submission evidence changed.
