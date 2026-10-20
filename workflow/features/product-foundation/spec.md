# Product Foundation and Phase 0 Guardrails

Status: Blocked — Phase 0 is otherwise complete; awaiting external SoC LLM operating-envelope information
Approval: Partially approved — all product-owner decisions except the externally supplied SoC-service operating details are approved and recorded below.
Last handoff: Product manager → Human product owner / SoC service owner, 2026-10-20 — Railway hosting and deployment access are approved. Provide the official SoC LLM model, embedding, quota, and outage details before approving affected AI/RAG specifications.

## Purpose and outcome

This umbrella specification records the confirmed project boundary, delivery
guardrails, and decisions that must be made before the team writes or approves
implementation-ready feature specifications. It does not approve an application
design, data model, or final technical architecture beyond the approved
deployment boundary recorded here.

The intended product is a University TA / assignment-help desk with two roles:
staff and students. Staff manage course material and use a staff AI feature to
consolidate uploaded lecture PDFs into reviewable knowledge artefacts. Enrolled
students use course-scoped forum capabilities and a student AI help feature
grounded in authorized course materials. The product must be suitable for the
assignment's AI Security, SDD, Basic Multi-Agent SE, deployment, documentation,
and evidence requirements.

## Confirmed constraints

The following are confirmed by `Instructions_and_Planning.md` and
`workflow/project-overview.md`; later specifications and implementations must
preserve them.

1. There are two distinct user roles, staff and student, and each needs an
   LLM-based AI feature.
2. The staff AI feature consolidates uploaded lecture PDFs into reviewable
   course knowledge artefacts. The student AI feature provides material-grounded
   help, such as hints, explanations, or clarifying questions, with references
   to the original materials.
3. Staff create and manage courses and course materials. Students participate
   in a forum only for courses to which they are authorized. Staff may create
   or reply to forum posts.
4. Course data and materials must be accessible only to authorized members;
   the server, not only the UI, must enforce role and course scope.
5. AI help is grounded in approved, current source material. Generated
   summaries/consolidations are not the sole source of truth.
6. AI output must not be automatically published as a forum response.
7. Uploaded documents, filenames, forum content, generated summaries, and user
   prompts are untrusted input, including possible direct or indirect prompt
   injection.
8. The product must use the SoC LLM service and safely handle its restrictions,
   rate limits, and failures.
9. The project must use spec-driven development and leave durable workflow
   evidence for specialized-agent handoffs, implementation, and independent
   verification.
10. The team, rather than a Codex/Claude build-and-host environment, manages
    online deployment. Development runs locally with Docker Compose; production
    runs on Railway with a public web service, a private background worker for
    PDF/AI jobs, PostgreSQL with pgvector on persistent storage, and a private
    S3-compatible Railway bucket for PDFs. The database has no public endpoint.
    SoC credentials exist only as production secrets.
11. Required submission evidence includes the public repository structure,
    workflow artefacts, user/developer guides, GitHub Pages product website,
    reflection document, verified summaries of development interactions, and
    an up-to-date `master` branch at submission.
12. Anyone may self-sign up as either a student or staff user with a username
    and password. This test project does not require staff-account verification.
13. Students cannot self-enrol in or withdraw from courses. Course creators
    automatically become course staff; course staff may add students or staff
    accounts to their course by entering a username. Staff can access only
    courses where they are course staff.
14. Course materials are PDF-only: at most 20 MB and 100 pages per PDF, with
    at most 20 active PDFs per course. Only staff may upload or delete them.
15. The minimum release is a working web application with account sign-up,
    course creation, staff-managed enrolment, posting and commenting, and both
    AI features.
16. Courses require a code and name and may be renamed/deleted by assigned
    course staff. Forum posts require a title (maximum 120 characters) and may
    have a Markdown description (maximum 2,000 characters); attachments and
    search are out of scope. Enrolled students may browse/download active PDFs
    in a course Resources section.

## Work division and feature boundary

The existing plan assigns @ziyang the student dashboard, student forum flows,
student AI help, and their tests. It assigns @p-xp the initial/shared platform,
server-side security foundations, staff-facing features, deployment, and
process/documentation artefacts. Shared interfaces are owned by @p-xp; student
work consumes documented shared contracts.

Subsequent Stage 1 work must create separate feature specifications at least
for:

- shared identity, course membership, and course-scoped forum;
- staff material upload, processing, review, and consolidation;
- student AI help; and
- shared AI/RAG security and deployment/release behavior.

User-facing specifications require a UI design before implementation. Each
substantial feature remains `Draft` until the human product owner has approved
the consequential decisions it contains.

## Explicit assumptions — not approvals

These planning assumptions allow specification work to proceed but must not be
implemented as policy or architecture until the relevant approval is recorded.

| ID | Assumption | Impact if changed |
| --- | --- | --- |
| A1 | “Staff” and “student” are the only product roles in the initial release. | A new administrator, tutor, or guest role changes authorization and UI scopes. |
| A2 | Course access will require a defined membership relationship rather than a globally visible course. | Changes forum/material/RAG access checks and tester setup. |
| A3 | A source PDF/page citation can be shown to an authorized student when the student AI uses that material. | Changes material metadata, retrieval output, and UI. |
| A4 | Human staff review is part of the staff consolidation flow. | Changes when generated artefacts may influence retrieval. |
| A5 | The team can obtain approved SoC LLM credentials and the official operating envelope. | Without these, AI/RAG plans need a documented deferral or scope decision. |

## Non-goals for Phase 0

- Choosing the authentication provider, detailed data model, or source-code
  architecture.
- Defining final schema/API contracts, UI layouts, retention policy, or final
  technical architecture.
- Authorizing unrestricted solution generation, automated AI forum posting, or
  cross-course content access.
- Replacing later feature specifications, UI designs, engineering task records,
  security reviews, or independent test reports.

## Decision log

| ID | Status | Decision / evidence | Owner / next action |
| --- | --- | --- | --- |
| D0-01 | Confirmed | Use a two-role TA help desk, with student and staff AI features. | Product source documents; decompose in Stage 1. |
| D0-02 | Confirmed | Use SoC LLM; handle resource limits and failures safely. | @p-xp records approved operational capabilities in shared AI/RAG spec. |
| D0-03 | Confirmed | Keep AI retrieval course-authorized, grounded, cited, and non-auto-published. | Product manager makes behavior testable in AI specs. |
| D0-04 | Confirmed | Student AI may provide direction and a train of thought, but must not provide specific answers. Its responses must not be readily copy-pasteable as an answer. | Product manager turns this boundary into refusal/redirect and acceptance tests in the student-AI specification. |
| D0-05 | Confirmed | Staff create courses and manage enrolment. The creator automatically becomes course staff; course staff may add student or staff accounts by username, and staff can access only courses where they are course staff. Staff self-sign up with a username/password and receive staff authority without verification for this test project. Students cannot self-enrol or withdraw. | Shared identity/course spec defines server-enforced account, username lookup, staff/student membership, and course-scoped authorization behavior. |
| D0-06 | Confirmed | No reporting or general moderation workflow. Any user may edit or delete their own posts and comments. Course staff may delete student posts/comments in their course, but may not delete another staff member’s posts/comments. Removing a student immediately revokes that course-forum access. Posts/comments are hard-deleted from the backend; deleting a post also removes its comments. | Shared forum spec must enforce the actor/author-role matrix, revocation, and hard-deletion behavior on the server and test it. |
| D0-07 | Confirmed | Only course staff may upload and delete course materials. Accept PDF only, up to 20 MB and 100 pages each, with up to 20 active PDFs per course. Staff-uploaded active source PDFs are retrieval-eligible. A consolidation is supporting material, not the sole authority, and may influence student-AI retrieval only after explicit approval by authorized staff for that course. Student-facing claims must still be grounded in and cite original PDF filename/page sources. | Staff material and AI specifications make validation, authorization, active status, explicit approval, and original-source grounding testable. |
| D0-08 | Blocked — external information | SoC LLM model, embedding, quota, and outage capabilities are not currently available. | Obtain official service details; do not select an LLM/RAG design or claim AI availability before then. |
| D0-09 | Confirmed | Users may self-sign up as either a student or staff user with a username and password. No verification is required for this test project; peer testers can create the account type they need. | Shared identity spec defines authentication and access-control acceptance criteria. |
| D0-10 | Approved | Use local Docker Compose for development and Railway for production. The Railway project is team-owned and connected to the team GitHub repository. Production comprises a public web service, private PDF/AI worker, PostgreSQL with pgvector on persistent storage, and a private S3-compatible Railway bucket for PDFs; the database has no public endpoint. SoC credentials are production secrets only. @p-xp is the primary deploy owner and @ziyang is the secondary owner. GitHub Actions validates pull requests; `master` is protected and production deploys after required checks and merge. Backup and retention/recovery are out of scope, but persistent database storage is required across ordinary redeploys. | @p-xp owns deployment documentation and shared release configuration; @ziyang retains secondary access. |
| D0-11 | Confirmed | Minimum release: working web application with account sign-up, course creation, staff-managed enrolment, posting/commenting, and both AI features. | The implementation plan and Stage 1 specifications must preserve this scope. |
| D0-12 | Confirmed | Course staff may rename/delete a code-and-name course. Posts use mandatory 120-character titles and optional 2,000-character Markdown descriptions; no attachments/search. Replacing a PDF is delete-then-upload. Failed jobs expose a manual retry; until official SoC limits are known, the application permits at most three manual retry-job submissions per minute per staff member per course. Enrolled students browse/download active PDFs through a server-authorized course Resources section. | Shared course/forum and staff-material specs define validation, safe rendering, authorization, and state behavior. The retry guardrail is interim and not an asserted SoC quota. |
| D0-13 | Approved | Use a React + TypeScript client for the web application, retain Java for the server-authoritative JSON API, and use PostgreSQL for durable application data. The client never receives database credentials or acts as an authorization authority. | @p-xp owns the shared React scaffold and PostgreSQL persistence adapter; @ziyang owns student-facing flows that consume the published API. |

## Required approvals

The following require an explicit human decision. Until then, the corresponding
feature can be specified with alternatives but cannot be approved for
implementation.

1. **SoC LLM operating envelope:** Confirm available model and embedding
   capabilities, applicable call/concurrency limits, budget/quota handling,
   and acceptable fallback behavior when the service is unavailable.

## Operational elaboration

The material and deployment rules below elaborate confirmed policy.

### Material limits and retrieval eligibility

The approved first-release limits are PDF files only, with a maximum of 20 MB
and 100 pages per file, and a maximum of 20 active source PDFs per course.
Reject password-protected or malformed PDFs with a clear error. These limits
constrain processing cost while supporting ordinary lecture material; they may
be adjusted later if the SoC operating envelope requires it.

Only a source PDF uploaded by a staff member for that course and marked
**active** is eligible for student AI retrieval. A staff-generated
consolidation may influence retrieval only after explicit approval by an
authorized staff member for that course. It remains supporting material, may
not replace the source PDF as the sole authority, and cannot satisfy
student-facing grounding: those claims must cite original PDF filename/page
sources. Deleting a material item must immediately remove it and its derived
data from new retrieval requests. Retention or recovery after deletion is out
of scope for this test project.

### SoC-unavailable safe behavior

Until the SoC operating envelope is confirmed, the product must not fabricate
LLM results, silently substitute an unapproved provider, or expose source
materials outside course authorization. When AI is unavailable or quota is
exhausted, it should show an explicit unavailable/retry-later state and keep
the forum and authorized materials usable. Final retry/backoff behavior, quotas,
and any precomputed retrieval approach remain blocked on service information.

### Team-managed hosting and production data

Use local Docker Compose for development and a team-owned Railway project,
connected to the team GitHub repository, for production. @p-xp is the primary
deployment owner and @ziyang is the secondary owner. The production topology is
a public web service, a private background worker for PDF/AI jobs, PostgreSQL
with pgvector on persistent storage, and a private S3-compatible Railway bucket
for PDFs. The database must not have a public endpoint.

Store SoC and other production secrets only in Railway's secret manager; never
in the repository or client. GitHub Actions validates pull requests, `master`
is protected, and production deploys only after required checks and merge.
Persistent database storage is mandatory so ordinary redeploys do not erase the
application database, even though backups and retention/recovery are out of
scope for this test project.

Collect only data necessary for authentication, course access, forum activity,
and material processing; avoid real student data during testing. Backup and
retention/recovery policies are intentionally out of scope for this test
project; this limitation must be visible in the deployment documentation.

## Phase 0 acceptance criteria

Phase 0 is complete when all of the following are true:

1. The confirmed product-owner decisions in the decision log are reflected in
   every dependent feature specification. The approved Railway deployment
   boundary is reflected in affected platform/release specifications. The SoC
   operating envelope has an explicit decision, date, and rationale before
   affected AI/RAG specifications are approved, or those capabilities are
   explicitly deferred with the resulting scope recorded.
2. The decision record confirms the two-role boundary, SoC LLM requirement,
   course-scoped authorization requirement, AI trust boundaries, and
   team-managed deployment constraint without contradicting the source
   documents.
3. The product owner identifies the accountable owner for each Stage 1 feature
   specification, preserving @ziyang's student-facing and @p-xp's shared/staff
   responsibilities.
4. The product owner either confirms the Stage 1 feature list above or records
   approved additions/removals and their impact on role coverage and assignment
   requirements.
5. Each resulting feature specification can state server-enforced authorization,
   user-visible failure behavior, applicable AI safety/grounding behavior, and
   testable acceptance criteria without guessing a pending decision.

## Risks and blockers

| Risk / blocker | Safe response |
| --- | --- |
| A pending decision would set an academic-integrity, authorization, security, deployment, or scope policy. | Do not infer it; retain the feature as Draft and request product-owner approval. |
| SoC LLM capabilities or quota are unknown. | Do not select a retrieval/embedding design; obtain the service details and record a safe failure plan. |
| Staff self-sign-up grants privileged authority without verification. | This is accepted only for the test-project context; enforce it intentionally on the server and document that it is unsuitable for production use. |
| Deleted material/data has no retention or recovery policy. | Treat deletion as irreversible for this test project, document the limitation, and do not promise restoration. |
| Railway deployment configuration exposes a database or a production secret. | Keep the database private, use Railway-managed secrets only, and verify configuration before release. |
| A later feature spec conflicts with this document's confirmed constraints. | Treat the source constraints as controlling and return the conflict for human resolution. |

## Next handoff

**Feature:** `product-foundation` — Phase 0 blocked only on external SoC LLM operating-envelope information.

**Input artefacts:** `AGENTS.md`, `Instructions_and_Planning.md`,
`workflow/project-overview.md`, `workflow/development-process.md`, and
`workflow/implementation_plan.md` (working tree contains pre-existing
uncommitted changes to the plan).

**Deliverable completed:** this Phase 0 constraint, assumption, decision, and
approval record.

**Next owner:** SoC service owner or human product owner supplies the official
SoC LLM operating envelope. The product manager may now draft Stage 1 feature
specifications using the confirmed decisions. An engineer must wait for each
feature's own explicit approval and for the SoC gate where that feature depends
on AI/RAG behavior; platform and non-AI feature work is no longer blocked on
hosting.
