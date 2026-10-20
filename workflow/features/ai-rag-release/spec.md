# Shared AI/RAG Security and Deployment/Release Behavior

Status: Draft — blocked from implementation approval pending SoC operating-envelope details
Approval: Pending human approval; Phase 0 authorization, material, and Railway decisions are incorporated below.
Last handoff: Product manager → Human product owner / @p-xp, 2026-10-20 — recorded course-staff scope and approved consolidation-retrieval policy; resolve unavailable SoC details before approval.

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
- Generated staff consolidations are reviewable supporting artefacts and not
  the sole source of truth. They may influence student retrieval only after
  explicit approval by authorized staff for that course; they cannot make an
  inactive/deleted source eligible for student retrieval, and student-facing
  claims must still cite original PDF filename/page sources.
- PDFs, filenames, extracted text, forum posts/comments, generated artefacts,
  and user prompts are untrusted data, including possible direct and indirect
  prompt injection.
- Student AI is guidance/train of thought, not a source of specific or readily
  copy-pasteable answers, and is never automatically published in the forum.
  A more observable guidance-versus-worked-solution threshold is out of scope
  for this release.
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

## Scope

This feature covers the common policy/service boundary for:

1. authenticated, course-scoped material ingestion, processing, storage,
   retrieval, provenance, and eligibility changes;
2. a controlled SoC request boundary for both role features, including
   versioned system/policy prompts, output validation, and provider failure;
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

- Selecting a SoC model, embeddings provider/method, rate/concurrency values,
  quota budget, queue policy, timeout/backoff values, or provider outage
  contract before those official details are available.
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
5. preserve the student guidance-only/no-copy-pasteable-answer policy and
   prevent automatic forum publication; and
6. prevent retrieved text from requesting credentials, hidden prompts, changed
   policy, broader tool access, or unauthorized external actions.

For staff consolidation, generated artefacts remain reviewable outputs rather
than instructions or authority. A consolidation derived from source PDFs may
influence student retrieval only when authorized staff for that course have
explicitly approved it. The shared service must exclude pending, rejected,
deleted, cross-course, or source-invalidated artefacts and must still ground
student-facing claims in active original PDF filename/page sources. The
staff-material specification owns the review user flow; this shared feature
guarantees that untrusted source text cannot self-approve itself or change
eligibility.

## User-visible failure and safe fallback behavior

Both role features must expose accurate, non-sensitive states for validation
failure, permission denial, no eligible material, insufficient evidence,
processing failure, rate/quota limitation, temporarily unavailable SoC service,
and rejected unsafe/malformed output. They must retain access to unrelated
authorized non-AI functionality where possible.

Until the SoC operating envelope is known, a rate/quota/provider failure
means: show an unavailable/retry-later result, do not fabricate a result, do
not silently switch provider, and do not leak whether an unauthorized course
has material. Exact retry, queueing, concurrency, and timeout behavior is
blocked rather than assumed.

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
- pending, rejected, forged-approved, cross-course, or source-invalidated
  consolidations offered as student-AI context;
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

### Blocking SoC decision — do not infer

The SoC service owner/human product owner must provide and record:

1. permitted LLM model(s) and their relevant input/output limits;
2. available embedding capability and its approved use, if any;
3. request, concurrency, and quota/budget limits;
4. official rate-limit/error/outage semantics; and
5. the accepted behavior if no suitable embedding capability is available.

Only after this decision can engineering choose an embedding/retrieval design,
configure concrete queue/retry/timeout behavior, set user-visible usage-limit
copy, or claim AI service availability. The approved safe fallback before then
is an explicit unavailable/retry-later state with no substitute provider and
no fabricated result.

### Confirmed generated-consolidation retrieval policy

Staff-generated consolidation artefacts may influence student-AI retrieval in
addition to active source PDFs only after explicit approval by authorized staff
for the artefact's course. They are supporting context, not replacement source
material or the sole basis for student-AI output. The server must record and
authorize the approval, exclude pending/rejected/deleted/cross-course or
source-invalidated artefacts, and retain original PDF filename/page citations
for every student-facing material claim.

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
5. Student AI output cannot contain the requested specific or
   readily-copy-pasteable answer when tested with defined restricted-answer
   prompts; it returns only allowed guidance/refusal/redirect behavior.
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
11. Before implementation acceptance, the official SoC operating-envelope
    decision is recorded and the concrete implementation/test limits are added
    to this specification or an approved linked decision record. Without it,
    the shared AI/RAG feature remains blocked rather than silently assumed.
12. Student-AI retrieval may use an approved consolidation only as supporting
    context. The implementation server-verifies authorized same-course staff
    approval and current supporting sources, excludes every other artefact
    state, and retains original PDF filename/page grounding for student-facing
    claims.

## Required approvals and next handoff

**Approval required:** Human product owner approves this shared feature after
the SoC decision is supplied.
The Railway topology is already approved, but
implementation must still verify it. Security controls or scope changes that
weaken course authorization, grounding, or the academic-integrity boundary
require renewed human review.

**Next owner:** @p-xp turns the approved contract into shared implementation
tasks only after the SoC gate and role-specific UI designs are approved. The
engineer records implementation/test evidence in `tasks.md`; an independent
tester writes `test-report.md`. The product manager updates this specification
if the official SoC contract changes any user-visible behavior.
