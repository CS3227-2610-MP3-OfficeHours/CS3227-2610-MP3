# Implementation Plan — University TA Help Desk

## Plan status and purpose

**Status: Draft — requires product-owner review before feature implementation.**

This is the broad delivery order for the two-role University TA Help Desk. It
preserves the existing work division and adds the approval gates and handoffs
needed by the repository workflow. It is not a substitute for approved,
feature-specific requirements in `workflow/features/<feature-name>/`.

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

**Build:** Implement the guarded SoC LLM integration once for both role
features, including configuration, quotas/concurrency, timeouts, backoff, safe
failure handling, and telemetry. Implement course-authorized ingestion and
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

### Stage 4A — Deliver non-AI staff material governance (approved before Stage 3)

**Owner:** @p-xp.

**Sequencing decision (human-approved, 2026-10-20):** The staff feature is
split so the non-AI material experience can proceed while the SoC operating
envelope is unavailable. The implementation order is Stage 2 → Stage 4A →
Stage 3 → Stage 4B → Stage 5. Stage 4A uses the Stage 2 course, membership,
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

**Prerequisites:** Completed Stage 3 and a recorded, approved SoC operating
envelope.

**Build:** Deliver the staff material-consolidation flow. Generated knowledge
maps, learning objectives, and candidate FAQs remain reviewable artefacts with
source PDF/page links. Staff approve/reject generated artefacts, and only
approved/current artefacts and source material become eligible for student
retrieval.

**Verify:** Test consolidation/review decisions, generated-artefact retrieval
eligibility, AI security controls, and staff-visible AI failure states.

**Handoff/gate:** Engineer records implementation and tests in the staff feature
`tasks.md`; independent tester verifies the approved acceptance criteria in
`test-report.md`. Defects return to @p-xp; unresolved product decisions return
to the product owner through the specification.

### Stage 5 — Deliver the student forum and AI-help experience

**Owner:** @ziyang.

**Prerequisites:** Approved student specifications/design; stable shared forum
and AI-service contracts from Stages 2–3; suitable approved material/test
fixtures supplied by @p-xp.

**Build:** Deliver the enrolled-student course dashboard; course-scoped forum
browsing, threads, question creation, and replies; and clear loading, empty,
validation-error, permission-denied, rate-limit, and AI-service-failure states.
Deliver student AI help that requests server-side authorized retrieval and shows
a clearly labelled hint, explanation, or clarifying question with original
filename/page citations. AI output is reference aid/draft material only and is
never automatically published to the forum. Implement the approved
restricted-assessment response behavior.

**Verify:** Test enrolment-scoped visibility, cross-course rejection, posting
and replies, citation display, AI failure handling, and the approved
academic-integrity/security cases.

**Handoff/gate:** @ziyang records implementation and test evidence in the
student feature `tasks.md`; independent tester verifies it in `test-report.md`.
Integration defects in shared services are handed to @p-xp with a reproducible
case; ambiguities return to the product owner/specification.

### Stage 6 — Integrate, verify, deploy, and prepare submission evidence

**Owner:** @p-xp for release infrastructure and required artefacts; @ziyang
supports student-flow regression evidence; tester independently verifies the
release; human product owner accepts it.

**Build/verify:** Run the full security, authorization, migration, unit,
integration, and end-to-end suites. Configure GitHub Actions quality gates and
a team-managed deployment workflow with separate development/production
credentials, data stores, object storage, and SoC LLM configuration. Add health
checks, production-safe logging, migration and rollback procedures, then verify
the live peer-tester journey.

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
| **Academic-integrity boundary**: what counts as a restricted assessment, how it is identified, and the exact allowed/refused AI-help behavior. | Determines whether AI may give hints, explanations, partial solutions, or refuse; this cannot be inferred safely. | Student AI help, output policy, security tests, user guide. |
| **Course membership lifecycle**: who may create courses, add/remove students, and whether students can self-enrol or leave. | Defines authorization and audit requirements. | Shared APIs, staff dashboard, student visibility. |
| **Forum moderation and visibility policy**: deletion/editing, reporting, staff controls, and visibility of staff/AI-related content. | Changes data lifecycle, role permissions, and user flows. | Forum spec/design and tests. |
| **Staff approval policy**: whether approved original PDF chunks alone are usable, whether generated consolidation artefacts require approval, and who may approve them. | Governs what the student AI may retrieve and the review workflow. | Ingestion, staff flow, RAG filters, security tests. |
| **Material limits and retention**: permitted PDF size/page limits, replacement/deletion behavior, retention of old versions and audit logs. | Sets operational cost, privacy, and failure-state behavior. | Upload processing, storage, deployment, documentation. |
| **SoC LLM operational budget**: available model/embedding capabilities, quotas, rate limits, and acceptable fallbacks. | Determines feasible retrieval/embedding design and user-visible degradation. | Shared AI service, test fixtures, deployment. |
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
