# Feature Specification — `deployment` (Stage 6 shared production deployment)

Status: Draft
Approval: Pending — the Railway hosting topology is already approved in
`workflow/implementation_plan.md` Stage 6; the remaining decisions under
"Open decisions" below need a human product-owner decision before the live
production environment is treated as released.
Last handoff: orchestrator → engineer, 2026-10-21, specify the team-managed
production deployment so Stage 6 can be implemented and independently verified.

## Why this is a substantial feature

Stage 6 changes deployment behavior, the configuration contract, the security
boundary around uploaded PDFs, and how peer testers obtain accounts. Under
`AGENTS.md` that makes it substantial, so it gets a specification before
implementation rather than proceeding as a low-risk fix.

## Context the specification must respect

- The server is plain `com.sun.net.httpserver` on JDK 25, compiled by `javac`
  through `scripts/test.sh`. There is no Maven/Gradle build and no servlet
  container, so a production artifact has to be assembled explicitly.
- `PostgresPlatform.open` already applies `db/migrations/V1..V5` and records
  them in `schema_migrations` before `Main` calls `HttpServer.start()`. Stage 6
  does not need a new migration tool; it needs the migration directory present
  in the production image and a documented failure/rollback path.
- Uploaded PDF bytes live in `course_materials.content` (`bytea`), not in a
  separate object store. Downloads are only reachable through
  `GET /api/courses/{id}/resources/{materialId}/download`, which re-authorizes
  the caller in `PlatformService` on every request.
- The React client is a Vite build in `src/frontend`. The Java server currently
  serves no static files at all, so a single-origin production deployment needs
  static serving added.
- `AppConfig` is environment-only and refuses to start in production without a
  PostgreSQL JDBC URL and a session secret of at least 32 characters.

## Confirmed requirements

### R1 — One HTTPS origin serving API and client

R1.1 One deployed service serves the JSON API and the built client on the same
origin, so the browser never needs a cross-origin API host and the
`ta_session` cookie stays `HttpOnly; SameSite=Strict; Secure`.

R1.2 TLS is terminated by the platform edge. The container listens on plain
HTTP on the port given by the platform, and `TA_HELP_DESK_ENV=production`
switches the session cookie to `Secure`.

R1.3 `GET /health` returns `200 {"status":"ok"}` without authentication and is
the platform's health-check target.

R1.4 Static client assets are served only from the configured build directory.
A request that resolves outside that directory is refused. Unknown non-`/api`
`GET` paths return the client shell so client-side routing works; unknown
`/api` paths keep their current authenticated-or-hidden behavior.

### R2 — Private managed PostgreSQL with migrations applied before traffic

R2.1 Production PostgreSQL is a managed service in the same project, reachable
only over the provider's private network. No public TCP proxy is used on the
application path.

R2.2 The application receives the database host, port, database name, user, and
password through deployment-managed variable references. No credential,
hostname, or port appears in the repository, the client bundle, or logs.

R2.3 `db/migrations/` ships inside the image at the process working directory.
Startup applies every unapplied migration, each in its own transaction, and
records it in `schema_migrations`. A migration failure aborts startup, so the
platform keeps the previous healthy release serving traffic.

R2.4 Development and production use separate databases and separate SoCLaaS
keys. No value is shared between the two environments.

### R3 — Private, re-authorized PDF delivery

R3.1 PDF bytes stay in the private production database. There is no public,
guessable, or durable object URL.

R3.2 Every list and download re-authorizes the current user against the current
course membership server-side before any byte is returned.

R3.3 Removing a member, deleting the material, or deleting the course makes a
previously working download fail for that user.

### R4 — Team-managed CI/CD quality gate

R4.1 A GitHub Actions workflow runs `scripts/test.sh` on every push and pull
request affecting the application, and type-checks and builds the client.

R4.2 Deployment to production happens only on a push to `master` and only
after the test job passes.

R4.3 The deployment step authenticates with a repository secret. No deployment
credential is committed.

R4.4 The workflow uses no Claude- or Codex-hosted build or hosting environment,
as `Instructions_and_Planning.md` requires.

### R5 — Documented operations

R5.1 `workflow/features/deployment/tasks.md` records the topology, the exact
environment-variable contract with no secret values, the rollback procedure,
how a peer tester obtains a staff and a student account, and the live evidence
gathered.

R5.2 Logs record no API key, password, session token, prompt, or PDF text.

## Assumptions

- A1 Railway is the hosting provider, per the approved Stage 6 topology.
- A2 The platform edge provides a trusted TLS certificate on a platform
  subdomain; no custom domain is required for submission.
- A3 Seed accounts from `SeedData` are acceptable for peer-tester access, since
  `Main` seeds an empty production database on first start.
- A4 Single application instance. The in-process `SessionManager` keeps sessions
  in memory, so a restart signs users out; that is acceptable for this release.

## Non-goals

- N1 A separate object store (S3, Railway volume) for PDFs. Bytes stay in
  PostgreSQL for this release.
- N2 Horizontal scaling, shared session storage, or zero-downtime migration.
- N3 Automated backup-restore drills beyond the provider's own snapshots.
- N4 A custom domain, CDN, or WAF.
- N5 Student AI help (Stage 5B) and AI-dependent staff governance (Stage 4B),
  which remain blocked on Stage 3.

## Acceptance criteria

| ID | Criterion | How it is verified |
| --- | --- | --- |
| AC1 | The deployed HTTPS URL loads the client in a browser. | Open the URL; the sign-in view renders. |
| AC2 | `GET /health` over HTTPS returns `200 {"status":"ok"}`. | Request the live URL. |
| AC3 | Two users on different networks see the same data. | User A creates a course and a post; user B on another network signs in and sees both. |
| AC4 | The schema is migrated on a clean database. | `schema_migrations` lists `V1..V5`; deploy logs show startup completing before the health check passes. |
| AC5 | PDF upload and download work against production storage. | Staff upload a PDF, then download it as an enrolled student and byte-compare. |
| AC6 | Data survives an API restart. | Restart the service; the course, post, and material are still present. |
| AC7 | The database is not publicly reachable on the application path. | No public database endpoint is configured for the app; the client bundle contains no database host. |
| AC8 | Revocation blocks a direct download URL. | Remove the student from the course, then replay the download URL; it fails. |
| AC9 | A cross-course download is denied. | Request a material of a course the user is not in; it fails. |
| AC10 | No secret reaches the repo, the bundle, or logs. | Grep the tree, the built bundle, and the deploy logs. |
| AC11 | `scripts/test.sh` gates deployment. | A push with a failing test does not deploy. |
| AC12 | Static serving refuses path traversal. | Automated test for `..` and absolute escapes. |
| AC13 | Rollback restores the previous release. | Redeploy the prior deployment and confirm `/health`. |

## Open decisions for the human product owner

| Decision | Why it is needed | Default this spec assumes |
| --- | --- | --- |
| D1 Production data policy: is the live database treated as throwaway demo data? | Governs whether a destructive redeploy is acceptable. | Throwaway demo data; no personal data entered. |
| D2 Backup/restore expectation for submission. | Stage 6 lists backup/restore evidence; a real drill needs paid provider features. | Provider snapshots only, recorded as a known limitation (N3). |
| D3 Peer-tester credential delivery: seed accounts, or tester self-signup? | Affects the user guide and the security model. | Documented seed accounts (A3), with self-signup also available. |
| D4 Who holds production access. | Stage 6 requires a named operating owner. | @ziyang operates the deployment; @p-xp owns the shared platform contract. |
| D5 Whether in-memory sessions dropping on restart is acceptable for release. | Affects the user guide and AC6 wording. | Acceptable (A4). |

## Out-of-scope characteristic noticed while specifying

`PlatformService` persistence flushes the whole snapshot by deleting and
reinserting every row on each write. That is correct for a single instance but
will not scale and makes concurrent writers unsafe. It is a pre-existing
shared-platform characteristic owned by @p-xp, not part of this stage, and it
reinforces non-goal N2.
