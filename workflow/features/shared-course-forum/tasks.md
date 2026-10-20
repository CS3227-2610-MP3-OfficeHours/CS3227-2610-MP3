# Shared course forum — Engineering handoff

Status: Implemented — ready for independent verification
Approval: Implementation follows the product-owner-approved spec and design, 2026-10-20
Last handoff: Engineer → Tester / @ziyang, 2026-10-20 — shared server contract and authorization suite are ready for verification and student UI consumption.

## Auth-entry UI follow-up — 2026-10-21

**Approved scope:** The public entry page now lets a visitor choose Student or
Staff. Student remains a non-authenticated placeholder. Staff users choose
between log-in and self-selected staff sign-up; both paths use the existing
server-authoritative authentication endpoints.

**Changed areas:** `frontend/src/main.tsx` adds the role selector, Student
placeholder, Staff chooser, and labelled pending/error-aware staff log-in and
sign-up forms. `frontend/src/styles.css` adds the matching role-card and auth
form treatments. The sign-up request always sends `role: "staff"`; the UI
states that staff accounts are not institutionally verified in this test
project.

**Verification:**

```text
PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm run build
git diff --check
```

Both passed on 2026-10-21. Student authentication and the student dashboard
remain intentionally unimplemented.

## Staff course-creation UI follow-up — 2026-10-21

**Changed areas:** `frontend/src/main.tsx` now exposes a staff-only **Create
course** action in the dashboard sidebar and staff empty state. Its labelled
course-code/name form calls the existing server-authoritative `POST
/api/courses` endpoint, preserves server validation feedback, prevents
duplicate submissions, and selects the successfully created course. Students
receive no create control. `frontend/src/styles.css` adds the form layout.

**Verification:**

```text
PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm run build
git diff --check
```

Both passed on 2026-10-21.

## Architecture refinement — React/TypeScript + PostgreSQL

**Input decision:** Product Foundation D0-13, approved 2026-10-20. The Java
API remains server-authoritative; this refinement adds no student/staff UI or
AI/material behavior.

### Changed areas

- `frontend/`: Vite-compatible React 19 + TypeScript strict-mode foundation;
  it has a deliberately minimal entry point only, with no database variables or
  credentials.
- `docker-compose.yml`, `scripts/run-postgres-local.sh`, `.env.example`, and
  `README.md`: local PostgreSQL 17 developer path and JDBC driver guidance.
- `PostgresPlatform` and application configuration: validated PostgreSQL JDBC
  settings, migration tracking/application, durable loading/saving through the
  existing relational schema, and explicit production refusal to start without
  PostgreSQL settings. All existing authorization checks remain in
  `PlatformService`.

### Verification and limitations

```text
sh scripts/test.sh
Platform integration tests: PASS
git diff --check
```

The dependency-free integration suite continues to cover the authorization
matrix. PostgreSQL runtime behavior requires a local Docker engine and JDBC
JAR; they are unavailable in this execution environment, so the Compose-backed
startup/migration path could not be executed here. `scripts/test-postgres.sh`
covers restart persistence plus membership-revocation authorization when those
local prerequisites are present. The adapter loads persisted
records on startup and writes each successful mutation transactionally. It uses
a simple whole-state transactional write strategy for this small course project;
a later performance pass should replace it with targeted repository writes.

### Next handoff

Tester: run `docker compose up -d postgres`, configure a local JDBC JAR, then
run `sh scripts/run-postgres-local.sh`; restart it and verify seed/user/course
records survive. Student UI owner: install dependencies under `frontend/` and
add feature-approved routes/components against `docs/shared-course-forum-api.md`.

## Delivered scope

- Added a dependency-free Java 25 HTTP application foundation with validated
  environment configuration, a documented `.env.example`, and local run/test
  scripts. It uses PBKDF2-HMAC-SHA256 password hashes and opaque, HttpOnly,
  SameSite session cookies; plaintext passwords are neither serialized nor
  logged.
- Added the production relational migration at `db/migrations/V1__shared_course_forum.sql`, covering users, courses, memberships, posts, comments, indexes, and cascade rules. The initial local adapter is in-memory so the project remains runnable without an unapproved database dependency.
- Implemented server-side session authentication and centralized role,
  membership, ownership, and moderator-delete checks.
- Implemented courses, role-derived membership by username, immediate student
  revocation, hard course/post/comment deletion, and two isolated development
  seed courses.
- Implemented the complete course-scoped forum server contract. Post limits are
  enforced before persistence. Supplied HTML is escaped before a deliberately
  small Markdown renderer emits safe presentation markup. No attachment or
  search routes exist.
- Published the UI-consumable route/error contract in
  `docs/shared-course-forum-api.md`.

## Changed areas

- `src/main/java/edu/nus/tahelpdesk/`: application configuration, HTTP API,
  authentication/session state, domain models, authorization service, safe
  Markdown renderer, and development seed data.
- `src/test/java/edu/nus/tahelpdesk/PlatformIntegrationTest.java`: authorization,
  deletion, validation, cross-course, and safe-rendering coverage.
- `db/migrations/`: initial portable relational schema.
- `scripts/`, `.env.example`, `README.md`, and `docs/`: local setup and stable
  API handoff information.

## Verification evidence

Command run successfully:

```text
sh scripts/test.sh
Platform integration tests: PASS
```

The test suite includes: duplicate and invalid sign-up values, bad password,
student course-creation denial, creator membership, idempotent/unknown member
addition, non-course-staff membership change denial, cross-course student and
staff reads, cross-course post IDs, title/description validation, escaped
script markup, author-only edits, student-to-student deletion denial,
staff-to-staff deletion denial, course-staff student-comment moderation,
post-to-comment cascading hard deletion, removed-student read/write revocation,
staff-membership removal denial, course deletion revocation, and cross-course
isolation after deletion.

Also run successfully:

```text
javac --release 25 --add-modules jdk.httpserver -Xlint:all -d /private/tmp/tahelpdesk-lint $(find src/main/java -name '*.java')
```

No compiler warnings were emitted after the final fix.

## Limitations and next handoff

- The default dependency-free test/local path is in-memory. The documented
  PostgreSQL path now applies the supplied migration and persists the shared
  platform state; it still requires a local Docker engine and PostgreSQL JDBC
  driver, which were unavailable in this execution environment.
- The execution sandbox prevents TCP listener binding (`SocketException:
  Operation not permitted`), so the HTTP process could not be exercised over a
  socket here. The application compiled and the service-level integration suite
  passed. A tester should run `sh scripts/run-local.sh` locally, then exercise
  the routes in the API contract with separate sessions.
- Comments have no maximum length because the approved specification explicitly
  does not authorize one. There is no UI in this slice.
- Next owner: independent tester verifies the approved feature and writes
  `workflow/features/shared-course-forum/test-report.md`; @ziyang may consume
  `docs/shared-course-forum-api.md` for the student-facing UI.
