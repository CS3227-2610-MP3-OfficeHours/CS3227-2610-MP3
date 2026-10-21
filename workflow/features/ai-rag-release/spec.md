# Shared AI/RAG Security and Deployment/Release Behavior

Status: Approved for the Stage 3 staff AI/RAG foundation; student AI policy is deferred to Stage 5B
Approval: Human product-owner approval recorded in chat, 2026-10-21 — approved Stage 3 and the staff Knowledge Map flow; student academic-integrity behavior belongs to the student owner’s later feature.
Last handoff: Human product owner → Engineer, 2026-10-21 — implement the approved shared provider/retrieval foundation for staff Knowledge Maps; do not expose student AI behavior yet.

## Purpose and outcome

This shared feature defines the non-negotiable security, authorization,
grounding, safe-degradation, deployment, and release behavior used by both
role-specific AI features. It protects the student AI-help flow and the staff
material-consolidation flow from untrusted content and cross-course access,
while providing a team-managed production deployment separate from development.

**@p-xp** owns this shared feature, the server-side AI/RAG controls, and release
configuration. Role-specific owners consume the published shared contract and
must not create a separate LLM client or bypass shared authorization/retrieval
controls.

## Confirmed requirements

- The only LLM provider for the project is the SoC LLM service. A provider
  outage or quota limit must never cause a silent alternative-provider call or
  a fabricated AI result.
- The server enforces identity, role, and course authorization before access to
  materials, retrieval, or AI operations. Retrieval is constrained to the
  authorized course before similarity search/source selection.
- Eligible student-AI sources are active, staff-uploaded source PDFs in the
  requested course. PDFs are only accepted up to 20 MB and 100 pages, with up
  to 20 active PDFs per course; only staff may upload or delete them.
- Enrolled students may browse/download active course PDFs through a dedicated
  Resources section in the course forum. The server authorizes every Resources
  list, download, and citation target against current course membership; PDFs
  are never public object-storage URLs.
- Each course has one staff-generated, reviewable Knowledge Map that is a
  navigation aid, not a source of truth. It may guide student retrieval only
  after explicit approval by authorized staff for that course and only while it
  is current for every active PDF; student-facing claims must still cite
  original PDF filename/page sources.
- PDFs, filenames, extracted text, forum posts/comments, generated artefacts,
  and user prompts are untrusted data, including possible direct and indirect
  prompt injection.
- Student academic-integrity behavior and student-facing output policy are
  deferred to the student owner’s Stage 5B specification. Stage 3 must not
  expose a student AI endpoint before that policy is approved.
- Development uses local Docker Compose. Production is a team-owned Railway
  project connected to the team GitHub repository: public web service, private
  PDF/AI worker, PostgreSQL with pgvector and persistent storage, and a private
  S3-compatible Railway PDF bucket. The database has no public endpoint.
- Production secrets, including SoC credentials, exist only in Railway's secret
  manager and never in the repository, browser/client, test fixture, or log.
  @p-xp is primary deployment owner; @ziyang has secondary access.
- GitHub Actions validates pull requests. `master` is protected, and a
  production deploy happens only after required checks and merge. Backups,
  retention, and recovery are out of scope, though ordinary redeploys must
  retain the database on persistent storage.
- SoCLaaS applies per-key service levels, queueing/fairness, rate limiting,
  usage tracking, backend-slot reservation, load balancing, and failover. The
  product owner accepts normal use without a published numeric quota, budget,
  or concurrency contract. This is not an availability guarantee: the service
  must retain bounded local concurrency and safe 429/5xx/timeout outcomes.

## Scope

This feature covers the common policy/service boundary for:

1. authenticated, course-scoped material ingestion, processing, storage,
   retrieval, provenance, and eligibility changes;
2. a controlled SoC request boundary for the staff Knowledge Map flow,
   including versioned prompts, output validation, and provider failure; and
   a future student-facing consumer contract with no enabled endpoint;
3. defenses and test evidence for direct and indirect prompt injection,
   cross-course access, hostile uploads, unauthorized source use, and
   policy-violating model output;
4. production-safe operational visibility that records enough structured
   event information to investigate failures without logging credentials or
   raw protected material; and
5. Railway deployment/release gates and the release evidence required for the
   assignment's security, SDD, multi-agent workflow, and separate-environment
   requirements.

### Explicit non-goals

- Promising a numeric SoCLaaS quota, budget, concurrency level, or service
  availability. Model selection, bounded client concurrency, timeouts, and
  retry/backoff values are engineering decisions validated by tests.
- Replacing the staff-material or student-AI specifications and their required
  UI designs.
- Public material hosting, a public database, cross-course search, anonymous
  AI access, or client-side access to SoC credentials.
- Automatic AI publication/moderation in the forum; the project has no general
  reporting/moderation workflow.
- Backup, long-term retention, disaster recovery, or restoration guarantees.

## Roles, authorization, and shared contract

Every AI/RAG request enters the server with an authenticated principal and an
explicit course context. The server must determine role and current membership
before it retrieves, processes, or sends content to SoC. It must not trust a
course ID, material ID, citation link, role flag, client-provided retrieval
filter, or model output as authorization evidence.

For staff operations, access requires course-staff membership: the course
creator receives it automatically, and current course staff may add other
staff accounts by username. A staff account without that relationship must be
denied material, consolidation, and staff course operations for the course.

The common contract must return a typed outcome that permits the role feature
to render one of: accepted/processing, grounded result, no eligible material,
insufficient evidence, validation failure, permission denied, rate/quota
limited, temporarily unavailable, or safe rejected output. It must not expose
credentials, raw system prompts, embeddings/vector identifiers, private object
storage locations, or content/metadata from unauthorized courses.

Material provenance must associate a selected source with its course, source
PDF identity, page number(s), activity/currentness state, and the event that
made it eligible. The final storage/schema/API shape is an engineering design
decision, but it must support server-side filtering, source invalidation, and
the citations required by the role-specific specifications.

The shared material contract must also provide a server-authorized Resources
listing/download path for active course PDFs. A stale citation or guessed
download URL must be rechecked against current membership and material status;
it cannot bypass removal, deletion, or course isolation.

## Shared safety and grounding behavior

The AI boundary must apply a fixed, versioned policy that cannot be overridden
by user or retrieved content. It must:

1. treat all user, forum, filename, PDF, extracted-text, and generated-artefact
   content as untrusted data rather than operating instructions;
2. limit content supplied to the model to the authorized course and the
   minimal sources relevant to the permitted task;
3. require source provenance and page-aware citations for grounded student
   assistance and source-linked staff generated artefacts;
4. reject/safely suppress malformed, uncited, cross-course, stale/inactive,
   unsupported, or policy-violating output rather than presenting it as valid;
5. prevent automatic forum publication and avoid exposing a student AI
   response path until the student feature’s academic-integrity policy is
   approved; and
6. prevent retrieved text from requesting credentials, hidden prompts, changed
   policy, broader tool access, or unauthorized external actions.

For staff Knowledge Maps, the generated map remains a reviewable navigation
output rather than instructions or authority. There is at most one current map
per course; its source snapshot comprises every active PDF in that course.
An upload, deletion, or replacement immediately marks the map stale. Only an
authorized staff member may manually request regeneration and approve the
result. The shared service excludes pending, rejected, stale, deleted,
cross-course, or source-invalidated maps and must still ground student-facing
claims in active original PDF filename/page sources. The staff-material
specification owns the review user flow; this shared feature guarantees that
untrusted source text cannot self-approve itself or change eligibility.

## User-visible failure and safe fallback behavior

Both role features must expose accurate, non-sensitive states for validation
failure, permission denial, no eligible material, insufficient evidence,
processing failure, rate/quota limitation, temporarily unavailable SoC service,
and rejected unsafe/malformed output. They must retain access to unrelated
authorized non-AI functionality where possible.

For a rate/quota/provider failure, show a safe rate-limited or
unavailable/retry-later result, do not fabricate a result, do not silently
switch provider, and do not leak whether an unauthorized course has material.
The implementation selects bounded timeout, queueing, concurrency, and retry
values and verifies them; it must not promise provider capacity.

## Threat and abuse cases

The shared test corpus and release evidence must include representative cases
for all of the following:

- direct jailbreaks asking to ignore policy, reveal system prompts/secrets,
  retrieve another course, or generate a specific student answer;
- indirect injection in PDF body text, filename, extracted text, forum
  content, or generated consolidation asking the model/service to bypass
  policy, reveal data, or take an action;
- modified IDs, forged role/membership claims, guessed storage/citation URLs,
  and retrieval requests across courses;
- unsupported, malformed, password-protected, oversized, or over-page-limit
  uploads, including a material-deletion/invalidation race;
- stale, inactive, deleted, uncited, or wrong-course sources and
  fabricated/malformed provider output;
- removed-student, cross-course, or guessed direct-resource download attempts;
- pending, rejected, forged-approved, stale, cross-course, or source-invalidated
  Knowledge Maps offered as student-AI navigation context;
- SoC rate limits, quota exhaustion, timeout/outage, and secrets accidentally
  supplied in source material, prompts, responses, exceptions, or logs; and
- malicious attempts to auto-publish AI content or use a client call to bypass
  the common server boundary.

## Deployment and release requirements

Production must remain separately configured from local development and must
use the approved Railway topology. The PDF bucket, worker, database, and SoC
credentials are private to their required services; the web service exposes
only intended application endpoints. Persistent storage must be verified across
an ordinary redeploy. No backup/recovery claim may be made in product
documentation.

Before a production release, required GitHub Actions checks must pass on the
merged protected `master` branch. The release checklist must include
environment/secret review, private-database verification, course-authorization
and AI negative-test evidence, live safe-failure verification where feasible,
and confirmation that docs and workflow artefacts describe the actual release.

The released repository must contain the required user/developer guides,
GitHub Pages product site, reflection, and verified interaction-summary logs.
Those artefacts must report known AI limitations and SoC availability limits
without including secrets, personal data, or raw protected material.

## Dependencies, decisions, and blockers

### Required dependencies

- Shared identity/course membership and material-eligibility contracts.
- The approved Railway project and team access established by @p-xp/@ziyang.
- A UI design for each role-specific, user-facing AI flow.
- An independent tester's final evidence against the release criteria.

### Recorded SoCLaaS operating decision

On 2026-10-21, the human product owner accepted normal-use operation without
published numeric quota, budget, or concurrency values. The verified staff-key
catalog includes `bge-m3` for embeddings and `gemma4:26b`, `llama3.1:8b`,
`ornith1.5:35b`, `qwen3.5:9b`, `qwen3.6:35b`, `qwen3.8:27b`,
`qwen3-coder-next`, and `qwen3-vl:32b` for chat (plus operator aliases and
test models). Engineering must select only IDs returned by the relevant key's
`GET /v1/models` response.

This removes numerical provider limits as an implementation blocker. It does
not permit unbounded submission, an availability promise, or a fallback
provider. The common service must use bounded local concurrency; treat 429 as
neutral quota/rate limiting; treat 5xx/503/timeouts as temporary failure; and
retain the safe no-fabrication/no-leakage behavior. A separate student key must
be catalogued before student-AI implementation, but is not required for the
staff-only shared-service work.

### Confirmed Knowledge Map policy

Each course has one Knowledge Map generated only from its active PDFs. Staff
manually request its generation or update; the system never regenerates it
automatically. A source upload, deletion, or replacement immediately marks the
map stale. A map may guide student-AI retrieval only after authorized staff
approve the newly generated map and only while it remains current. It is
supporting navigation context, not replacement source material or the sole
basis for student-AI output. The server records and authorizes approval,
excludes pending/rejected/stale/deleted/cross-course or source-invalidated maps,
and retains original PDF filename/page citations for every student-facing claim.

## Acceptance criteria

1. Every material ingestion, retrieval, and AI request is authenticated and
   server-authorized for role and course before the service accesses a source,
   performs similarity search, or calls SoC.
2. A request with a forged/changed course, material, citation, role, or
   membership identifier cannot retrieve or reveal the existence of content
   from an unauthorized course.
3. Student-facing grounded output includes original source-PDF/page citations;
   staff generated artefacts preserve source links. Uncited, invalid,
   cross-course, inactive, deleted, stale, or otherwise ineligible sources do
   not produce a successful grounded result.
4. A prompt injection in any listed untrusted channel cannot override system
   policy, change authorization, reveal prompts/secrets, broaden retrieval,
   self-approve material, trigger an external action, or auto-publish to the
   forum.
5. Stage 3 exposes no student AI response endpoint. The student feature defines
   and tests its academic-integrity behavior before any such endpoint is enabled.
6. Invalid PDFs and uploads outside the PDF/20 MB/100-page/20-active-per-course
   limits are rejected before they become retrieval eligible, and only staff
   may upload/delete course material.
7. An enrolled member can browse/download an active PDF only through the
   server-authorized Resources contract for that course. A removed/non-member
   actor, cross-course request, stale citation, deleted/inactive PDF, or guessed
   storage URL does not disclose or serve protected material.
8. When SoC is rate-limited, quota-exhausted, unavailable, times out, or
   returns malformed/policy-violating output, the common service returns an
   accurate safe outcome; it does not fabricate an AI result, use another
   provider, reveal secrets, or interrupt unrelated authorized application use.
9. Production uses a team-owned Railway project with the approved public web,
   private worker, private object storage, and non-public persistent database
   boundaries. No SoC secret is committed, sent to the client, or written to
   release logs.
10. Required checks pass before protected-`master` production deployment, and
   release evidence includes authorization/security/AI negative tests,
   environment boundary checks, an ordinary-redeploy persistence check, and an
   independent tester report.
11. The implementation documents its selected permitted model IDs and bounded
    local timeout/concurrency/retry values, verifies safe 429/5xx/timeout
    behavior, and makes no numeric provider-capacity claim.
12. Student-AI retrieval may use only the single approved, current course
    Knowledge Map as navigation context. The implementation server-verifies
    same-course staff approval and a source snapshot containing every active
    PDF, excludes every other map state, and retains original PDF filename/page
    grounding for student-facing claims.

## Required approvals and next handoff

**Approval:** The human product owner approved the Stage 3 staff foundation on
2026-10-21. The SoCLaaS normal-use decision is recorded above; the Railway topology is already approved, but
implementation must still verify it. Security controls or scope changes that
weaken course authorization, grounding, or the academic-integrity boundary
require renewed human review.

**Next owner:** @p-xp implements the shared staff provider/retrieval foundation
and records implementation/test evidence in `tasks.md`; an independent tester
writes `test-report.md`. The student owner supplies the separate academic-
integrity policy before any student AI implementation begins.
