# Shared course forum — Engineering handoff

Status: Implemented — ready for independent verification
Approval: Implementation follows the product-owner-approved spec and design, 2026-10-20
Last handoff: Engineer → Tester / @ziyang, 2026-10-20 — shared server contract and authorization suite are ready for verification and student UI consumption.

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

- The runnable local persistence adapter is intentionally in-memory. Deployers
  must wire the supplied SQL migration to a production database repository
  before production use; doing so does not change the published API contract.
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
