# Implementation Plan — University TA Help Desk

## Ownership rule

- **@ziyang** owns only student-facing development: the student dashboard, student
  forum flows, and the student AI help feature and its tests.
- **@p-xp** owns initial project setup, shared platform infrastructure, security
  foundations, deployment, documentation/process artifacts, and every staff-facing
  feature.
- A task has one accountable owner. A completed task should link to its pull
  request, tests, and any relevant specification or security-test evidence.

## Phase 0 — Specifications and project foundation

- [ ] **@p-xp** Create `workflow/specs/` and write the initial product specification:
  role definitions, course-membership rules, material lifecycle, forum behaviour,
  AI behaviours, and acceptance criteria.
- [ ] **@p-xp** Write an RBAC and trust-boundary specification covering staff,
  students, courses, PDFs, forum content, generated artefacts, and the SoC LLM API.
- [ ] **@p-xp** Create the repository structure (`src/`, `workflow/`, `docs/`,
  `logs/`) and TypeScript application skeleton.
- [ ] **@p-xp** Configure local development with Docker Compose for the application,
  PostgreSQL with pgvector, and S3-compatible local object storage.
- [ ] **@p-xp** Configure environment-variable validation and separate development
  and production configuration templates; ensure API keys and secrets are never
  committed.
- [ ] **@p-xp** Set up formatting, linting, type checks, unit-test tooling, end-to-end
  test tooling, and a pull-request quality gate.
- [ ] **@p-xp** Design and implement database migrations for users, roles, courses,
  enrolments, posts, replies, materials, material versions, chunks, AI jobs,
  citations, review decisions, and audit events.
- [ ] **@p-xp** Implement authentication and server-side course-scoped authorization
  helpers. UI visibility must not be the only access control.
- [ ] **@p-xp** Add a seed script with at least two courses, staff accounts, student
  accounts, approved material, and test data for cross-course access checks.

## Phase 1 — Shared platform and forum capabilities

- [ ] **@p-xp** Implement shared course and membership management APIs, with tests
  that reject access for users outside the course.
- [ ] **@p-xp** Implement the shared forum data layer and server APIs for creating,
  reading, and listing posts and replies, including pagination and course filters.
- [ ] **@p-xp** Implement safe rendering and input validation for user-authored
  questions and replies; prevent stored XSS and reject malformed requests.
- [ ] **@p-xp** Add immutable, structured audit events for material uploads, AI
  requests, retrieval source IDs, review decisions, access denials, and security
  blocks. Do not log secrets.

## Phase 2 — Staff experience and material governance

- [ ] **@p-xp** Build the staff dashboard for creating courses and viewing course
  membership, material-processing status, and AI audit activity.
- [ ] **@p-xp** Build staff PDF upload and management flows. Enforce allowed file
  types, file-size/page-count limits, private storage, content hashing, and
  server-side course authorization.
- [ ] **@p-xp** Build the background ingestion workflow: isolated PDF text extraction,
  page-aware chunking, chunk provenance, embedding generation, retry/backoff, and
  a visible failed-job state.
- [ ] **@p-xp** Build the staff AI material-consolidation feature. It should generate
  a topic/week knowledge map, learning objectives, and candidate FAQ items, all
  with links to original PDF pages.
- [ ] **@p-xp** Build an approval workflow for staff-generated knowledge artefacts.
  Only approved, current artefacts and source material may be available to
  student-facing retrieval.
- [ ] **@p-xp** Implement material versioning, invalidation, and reprocessing so a
  replaced PDF cannot leave stale generated summaries or chunks active.
- [ ] **@p-xp** Add staff tests for uploads, approval/rejection, version invalidation,
  failed ingestion, and authorization boundaries.

## Phase 3 — Student experience

- [ ] **@ziyang** Build the student dashboard showing only courses in which the
  authenticated student is enrolled.
- [ ] **@ziyang** Build student forum views for browsing course posts, viewing a
  thread, creating a question, and replying to another student or staff post.
- [ ] **@ziyang** Integrate the shared forum APIs into the student UI with useful
  loading, empty, validation-error, and permission-denied states.
- [ ] **@ziyang** Build the student AI help flow: a student asks a course-material
  question and receives a clearly labeled AI hint, explanation, or clarifying
  question with citations to original PDF filename and page number.
- [ ] **@ziyang** Ensure the student UI presents AI output as a draft/reference aid,
  not an automatically published forum reply; the student chooses whether to post
  their own response.
- [ ] **@ziyang** Implement the student-facing restricted-assessment response UX:
  explain the boundary and offer conceptual guidance, source references, or a
  partial hint rather than a complete worked answer.
- [ ] **@ziyang** Add student-flow end-to-end tests for enrolment-scoped course
  visibility, posting/replying, cited AI help, and safe AI failure/rate-limit UI.

## Phase 4 — LLM service and AI security

- [ ] **@p-xp** Implement one guarded SoC LLM service module used by both AI
  features. Centralize API configuration, per-user/course quotas, concurrency
  limits, request timeouts, rate-limit backoff, error handling, and telemetry.
- [ ] **@p-xp** Implement server-side RAG retrieval that always filters approved
  chunks by the requesting user's authorized `courseId` *before* similarity search.
- [ ] **@p-xp** Define versioned prompt templates. Treat PDF text, generated
  consolidations, forum content, filenames, and user input as untrusted data—not
  executable instructions.
- [ ] **@p-xp** Add output-policy checks for citation presence, unsupported claims,
  restricted-assessment answer patterns, unsafe content, and malformed output;
  record policy decisions in the audit log.
- [ ] **@p-xp** Build a security test corpus containing direct jailbreak requests,
  indirect instructions embedded in PDFs, hostile forum posts, misleading
  filenames, cross-course data requests, and oversized/malformed uploads.
- [ ] **@p-xp** Automate security tests proving that retrieved malicious text cannot
  override system policy, unauthorized course material is never retrieved, and
  unapproved/stale artefacts are excluded.
- [ ] **@p-xp** Document residual risks and the limits of prompt-based protections;
  record expected behaviour and evidence for each security scenario.

## Phase 5 — Verification, deployment, and submission evidence

- [ ] **@p-xp** Configure GitHub Actions to run linting, type checks, unit tests,
  end-to-end tests, migration checks, and the AI-security test suite on pull
  requests and `master`.
- [ ] **@p-xp** Containerize the app and worker, and create a deployment workflow to
  a team-managed online environment. Use separate production database, object
  storage, credentials, and SoC LLM configuration.
- [ ] **@p-xp** Add health checks, production-safe logging, migration deployment, and
  a rollback procedure; verify the deployed app is usable by peer testers.
- [ ] **@p-xp** Create `docs/UserGuide.md` with exact student/staff workflows,
  account/test-data instructions, and AI-feature limitations.
- [ ] **@p-xp** Create `docs/DeveloperGuide.md` covering architecture, data model,
  authorization, RAG pipeline, security controls, deployment, test strategy,
  workflow artefacts, and reuse acknowledgement.
- [ ] **@p-xp** Maintain prompt/interaction summaries in `logs/` and verify that each
  summary accurately reflects the original interaction.
- [ ] **@p-xp** Create `docs/Reflections.md` with concrete evidence about AI security,
  SDD, multi-agent development handoffs, human approval gates, and defects found
  by tests or review.
- [ ] **@p-xp** Publish and verify the required GitHub Pages product website.
- [ ] **@p-xp** Perform a final release checklist: public repository name and layout,
  `master` up to date, no secrets, deployment live, docs match the released product,
  and all required evidence committed.

## Definition of done

Each completed task must have:

1. A matching or updated specification/acceptance criterion in `workflow/specs/`.
2. Code reviewed against authorization, validation, and error-handling requirements.
3. Automated tests appropriate to the task, including a negative/security case where
   the task crosses a trust boundary.
4. A brief entry in the relevant workflow or log artefact explaining the evidence
   and any remaining limitation.
