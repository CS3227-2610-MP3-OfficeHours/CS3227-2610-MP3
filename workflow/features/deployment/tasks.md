# Engineer Tasks — `deployment` (Stage 6 shared production deployment)

Status: Implementation complete and locally verified; **not yet deployed**.
Input artefacts: `workflow/features/deployment/spec.md` (Draft),
`workflow/implementation_plan.md` Stage 6, `AGENTS.md`,
`workflow/development-process.md`.
Working tree: branch `claude/determined-shannon-362118`, based on `b98e95f`
("docs: record material AI feature requirements and reviews"). Nothing is
committed or pushed.
Last handoff: engineer → @ziyang (deployment owner), 2026-10-21. The deployment
mechanism is built and verified as far as it can be without Railway and Docker
credentials. The live environment must be created before a tester can verify
Stage 6. See "Blockers" for exactly what is needed.

> **Honest status up front.** Every acceptance criterion that depends on a live
> deployment (AC1–AC10, AC13) is **unverified**. No Railway project exists yet
> from this session: `railway whoami` returns `404`, so the CLI here is not
> authenticated, and Docker is not installed on this machine, so the image has
> never been built. What *is* verified is listed under "Evidence gathered" with
> the command that produced it.

## Implementation checklist

- [x] Add single-origin static serving so one service can host the API and the
      built client (`StaticSite`, wired through `AppConfig` and `HttpApi`).
- [x] Add `TA_HELP_DESK_STATIC_DIR` to the environment-only config contract.
- [x] Add automated static-serving tests, including path traversal (AC12).
- [x] Pin the PostgreSQL JDBC driver by SHA-512 (`scripts/ensure-jdbc.sh`).
- [x] Write the production `Dockerfile` (client build → server build → JRE
      runtime) and `scripts/docker-entrypoint.sh`.
- [x] Write `railway.json` with the `/health` check and a single replica.
- [x] Write `.github/workflows/deploy.yml`: `scripts/test.sh` quality gate on
      every push/PR, deploy only on `master` after it passes.
- [x] Add CI checks that fail the build if a server-only value reaches the
      client bundle or a `.env` file is tracked.
- [x] Document the configuration contract, rollback, and tester access (below).
- [ ] **Create the Railway project, databases, and variables** — blocked, needs
      the owner's Railway account.
- [ ] **Deploy and gather live evidence for AC1–AC10 and AC13** — blocked.

## Topology

```
                    Internet (HTTPS, Railway edge certificate)
                                   │
                         ┌─────────▼──────────┐
                         │ Railway edge / TLS │   terminates TLS, proxies HTTP
                         └─────────┬──────────┘
                                   │  $PORT
         ┌─────────────────────────▼──────────────────────────┐
         │ service: ta-help-desk  (Dockerfile, 1 replica)     │
         │  • com.sun.net.httpserver on JDK 25                │
         │  • /health, /api/** (session-authorized)           │
         │  • /, /assets/** served from /app/client           │
         │  • applies db/migrations at startup, before traffic│
         └─────────────────────────┬──────────────────────────┘
                                   │ Railway private network only
                                   │ (*.railway.internal, no public proxy)
                         ┌─────────▼──────────┐
                         │ service: Postgres  │  PDF bytes in course_materials
                         └────────────────────┘  .content (bytea)
```

Two Railway **environments** in one project, each with its own Postgres service
and its own variables:

| Environment | Database | SoCLaaS key | Deployed from |
| --- | --- | --- | --- |
| `production` | its own Postgres service | production `SOCLAAS_STAFF_API_KEY` | pushes to `master`, via GitHub Actions |
| `development` | its own separate Postgres service | development `SOCLAAS_STAFF_API_KEY` | manual `railway up --environment development` |

No value is shared between the two. Railway environments do not share variables
or volumes, so this satisfies spec R2.4.

### Why there is no separate object store

Spec non-goal N1. Uploaded PDF bytes are stored in `course_materials.content`
(`bytea`) by the existing shared platform, and the only route that returns them
is `GET /api/courses/{courseId}/resources/{materialId}/download`, which calls
`PlatformService.downloadResource` and re-authorizes the caller's current course
membership on every request (`src/main/java/edu/nus/tahelpdesk/HttpApi.java:54`).
That satisfies the Stage 6 requirements that matter — private storage, no
public or durable object URL, re-authorization before any byte is served, and
deletion removing the bytes with the record — without introducing a second
credential and a second failure mode. The cost is that large PDFs consume
database storage and sit in the snapshot flush; that is recorded as a known
limitation below.

## How migrations run

**Decision: the application applies them at startup. No new migration tool.**

`PostgresPlatform.open` is called from `Main` *before* `HttpServer.start()`
(`src/main/java/edu/nus/tahelpdesk/Main.java:11`). It creates
`schema_migrations` if absent, then applies each of `V1__shared_course_forum.sql`
through `V5__material_knowledge_maps.sql` that is not already recorded, each in
its own transaction, rolling back on failure
(`src/main/java/edu/nus/tahelpdesk/PostgresPlatform.java:36-46`). The container
therefore only needs `db/migrations/` present at the working directory, which
the `Dockerfile` copies to `/app/db/migrations`.

Consequences, stated plainly:

- A migration failure throws out of `Main`, the container exits non-zero, the
  Railway health check never passes, and Railway keeps the previous healthy
  deployment serving traffic. Traffic is never served against a half-migrated
  schema.
- Migrations are applied by the application's own database user, so that user
  needs DDL rights on its database. It is the owner of its own database and
  nothing else; it is not a shared cluster superuser.
- With one replica (`railway.json` `numReplicas: 1`) there is no concurrent
  migration race. Raising the replica count would introduce one, which is why
  non-goal N2 excludes scaling.
- Adding `V6` later requires adding the filename to the list in
  `PostgresPlatform.migrate`. That is a pre-existing property of the shared
  platform, not something this stage changed.

## Configuration contract (production)

Set these as Railway **variables on the application service** in the
`production` environment. Values shown are literal where they are not secret,
and described where they are. **No secret value appears in this file or
anywhere in the repository.**

| Variable | Value | Secret? |
| --- | --- | --- |
| `TA_HELP_DESK_ENV` | `production` | no — also baked into the image |
| `TA_HELP_DESK_PORT` | leave unset; the entrypoint adopts Railway's `$PORT` | no |
| `TA_HELP_DESK_STATIC_DIR` | `/app/client` (baked into the image) | no |
| `TA_HELP_DESK_SESSION_SECRET` | a fresh random string of **at least 32 characters**, unique to production | **yes** |
| `TA_HELP_DESK_DATABASE_URL` | `jdbc:postgresql://${{Postgres.RAILWAY_PRIVATE_DOMAIN}}:5432/${{Postgres.PGDATABASE}}` | no literal, but treat as sensitive |
| `TA_HELP_DESK_DATABASE_USER` | `${{Postgres.PGUSER}}` | reference only |
| `TA_HELP_DESK_DATABASE_PASSWORD` | `${{Postgres.PGPASSWORD}}` | **yes**, by reference |
| `SOCLAAS_BASE_URL` | `https://soclaas-api.comp.nus.edu.sg/v1` | no |
| `SOCLAAS_STAFF_API_KEY` | the production staff key | **yes** |
| `SOCLAAS_STAFF_CHAT_MODEL` | `gemma4:26b` | no |
| `SOCLAAS_STAFF_FALLBACK_CHAT_MODEL` | `qwen3.5:9b` | no |
| `SOCLAAS_STUDENT_API_KEY` | leave unset until Stage 5B | **yes** when set |

`${{Postgres.…}}` is a Railway variable reference, resolved at deploy time from
the Postgres service in the same environment. Using
`RAILWAY_PRIVATE_DOMAIN` rather than `PGHOST` guarantees the private network
address, so the database is never reached through a public TCP proxy. Replace
`Postgres` with the actual service name if it differs.

`TA_HELP_DESK_DATABASE_URL` must start with `jdbc:postgresql://` or
`AppConfig.load` refuses to start. Railway's own `DATABASE_URL`
(`postgres://…`) is **not** usable directly; that is why the JDBC URL is
assembled from references instead.

### GitHub repository configuration

| Name | Kind | Value |
| --- | --- | --- |
| `RAILWAY_TOKEN` | repository **secret** | a Railway **project token** scoped to the `production` environment |
| `RAILWAY_PROJECT_ID` | repository **variable** | the project's UUID |
| `RAILWAY_SERVICE` | repository **variable** | the application service's name, e.g. `ta-help-desk` |
| `PRODUCTION_URL` | repository **variable** | the live origin, e.g. `https://<name>.up.railway.app` |

The workflow also uses a GitHub `production` environment, so required reviewers
can be added later without touching the workflow file.

### Development configuration

Local development is unchanged and needs no production value:

```sh
cd src/frontend && npm ci && npm run build && cd ../..
TA_HELP_DESK_STATIC_DIR=src/frontend/dist sh scripts/run-local.sh
```

`docker-compose.yml` still provides the local-only Postgres for
`scripts/run-postgres-local.sh` and `scripts/test-postgres.sh`. Its credentials
are local-only and deliberately worthless.

## Rollback

1. **Fastest, no rebuild.** Railway dashboard → the service → *Deployments* →
   the last known-good deployment → *Redeploy*. Railway rebuilds that commit's
   image and cuts over only after `/health` passes. Confirm with
   `curl https://<origin>/health`.
   CLI equivalent: `railway redeploy --service <name> --environment production`
   redeploys the current release; use the dashboard to pick an older one.
2. **Roll back the source.** `git revert <bad commit>` and push to `master`.
   The workflow re-runs `scripts/test.sh` and deploys the reverted tree. Slower,
   but it keeps `master` and production in agreement.
3. **A migration that must be undone.** There are no down-migrations. A forward
   fix is required: add a new `V<n>` that corrects the schema and add it to the
   list in `PostgresPlatform.migrate`. Rolling the *image* back while leaving a
   newer schema in place is only safe if the older code tolerates the newer
   schema — verify that before doing it, and prefer the forward fix.
4. **Database restore.** Railway's Postgres service exposes backups under the
   service's *Backups* tab; restoring creates a new volume. Per spec D2 this has
   **not** been drilled, and non-goal N3 excludes automated restore testing. Do
   not claim restore evidence that does not exist.

Rollback is in the workflow's `concurrency` group `deploy-refs/heads/master`
with `cancel-in-progress: false`, so a rollback deploy cannot race a pending
one.

## Peer-tester access

`Main` calls `SeedData.seedIfEmpty(platform)` when the production database is
empty, so the first start of a clean database creates the seed accounts. A peer
tester gets one of two routes:

1. **Self-signup (no coordination needed).** Open the deployed URL, choose
   *Sign up*, pick the `staff` or `student` role. Staff can then create a course;
   a student must be added to a course by staff from the *Members* tab, since
   there is no self-enrolment.
2. **Seed accounts.** `SeedData` creates `seed.staff` (staff), `seed.student`
   and `seed.other` (students), all with the password
   `local-development-password`, and enrols `seed.student` in course `CS3227`
   and `seed.other` in `CS2103`
   (`src/main/java/edu/nus/tahelpdesk/SeedData.java:12-17`).

> ### ⚠ Security finding that needs a decision before going live
>
> `Main` calls `SeedData.seedIfEmpty(platform)` on **any** empty database,
> production included (`src/main/java/edu/nus/tahelpdesk/Main.java:13`). So the
> first production start creates a **staff** account whose password is committed
> to a repository the assignment requires to be **public**. Anyone who reads the
> repo can sign in as staff on the live deployment, create courses, and upload
> and read materials.
>
> This is pre-existing shared-platform behaviour, not something this stage
> introduced, and it is harmless in local development — which is why it is
> flagged here rather than changed unilaterally. It is a real exposure once the
> deployment is public. Three options, for the product owner (this extends spec
> D1/D3):
>
> 1. **Accept it.** Defensible only if the live database is explicitly
>    throwaway demo data and the team is content that any reader can act as
>    staff. Say so in the user guide.
> 2. **Make the seed password an environment variable** so production gets a
>    value that is not in the repo, and peer testers are told it out of band.
>    Smallest change; keeps the convenient demo accounts.
> 3. **Skip seeding in production** and have peer testers self-sign-up via
>    route 1. Safest; costs the prepared `CS3227`/`CS2103` demo courses.
>
> I have not implemented any of these, because changing authentication seeding
> is a product and security decision and `SeedData` is shared-platform code
> owned by @p-xp. **Decide this before the first production deploy**, since the
> accounts are created on first start and the password is already public.

A two-person walkthrough for the multi-user criterion (AC3):

1. Tester A signs up as `staff`, creates a course, uploads a PDF under
   *Materials*, and creates a forum post.
2. Tester A adds Tester B's username from the *Members* tab.
3. Tester B, **on a different network**, signs in, selects the same course, and
   sees the same post and the same PDF under *Resources*, and downloads it.
4. Tester A removes Tester B from *Members*. Tester B replays the download URL
   and is denied (AC8).

> **Decision needed before publishing the user guide (spec D3):** whether peer
> testers use the committed seed accounts or self-signup. The default assumed
> here is that both work.

## Evidence gathered

All of the following was run in this worktree on 2026-10-21. Commands are given
so a tester can repeat them.

### Server and client build

| # | Check | Command | Result |
| --- | --- | --- | --- |
| E1 | Platform authorization suite | `edu.nus.tahelpdesk.PlatformIntegrationTest` | `Platform integration tests: PASS` |
| E2 | SoCLaaS adapter suite | `edu.nus.tahelpdesk.SoCLaaSClientTest` | `SoCLaaS client tests: PASS` |
| E3 | New static-serving suite | `edu.nus.tahelpdesk.StaticSiteTest` | `Static site tests: PASS` |
| E4 | Client type-check and build | `npm run build` in `src/frontend` | `✓ built in 1.21s`; `dist/index.html` 0.37 kB, `dist/assets/index-*.css` 6.10 kB, `dist/assets/index-*.js` 217.16 kB |
| E5 | Client bundle carries no server-only value | `grep -ril 'soclaas\|jdbc\|SESSION_SECRET\|postgres' src/frontend/dist` | no match |
| E6 | Repository carries no key, production endpoint, or deploy token | `git ls-files -co --exclude-standard \| xargs grep -lIE '…'` | no match |
| E7 | JDBC driver pin is genuine | compared the downloaded jar against Maven Central's published checksum | local SHA-1 `81b840fb…ed780` equals the published `.sha1`; the SHA-512 pinned in `scripts/ensure-jdbc.sh` is taken from that same verified file |
| E8 | Workflow and config files are well-formed | `yaml.safe_load`, `json.load`, `sh -n` | `deploy.yml` parses with jobs `test`, `deploy`; `railway.json` parses; all four shell scripts pass `sh -n` |

E1–E3 are what `scripts/test.sh` runs. **`scripts/test.sh` itself could not be
run end to end on this Windows machine**, because it joins classpath entries
with `:` and the Windows JVM splits on `;`, which produces
`ClassNotFoundException`. The three suites were therefore compiled and run
individually with `;` separators, which exercises exactly the same classes. The
script is POSIX and is expected to run as written on the `ubuntu-24.04` runner;
**that expectation is unverified until the workflow runs**. Related: the brief
for this work warned that `SoCLaaSClientTest` can abort where loopback socket
binding is forbidden. It bound `127.0.0.1:0` successfully here, so that
sandbox limitation does not apply on this machine, and a normal GitHub runner
permits loopback binding.

### Single-origin serving, against a running server

Started with `TA_HELP_DESK_STATIC_DIR=src/frontend/dist TA_HELP_DESK_PORT=8099`
against the real `dist/` build. Startup logged
`TA Help Desk listening on port 8099 (API and client)`.

| # | Request | Result | Criterion |
| --- | --- | --- | --- |
| E9 | `GET /health` | `200`, 15-byte JSON body | AC2 mechanism |
| E10 | `GET /` | `200`, 371 bytes, the client shell | AC1 mechanism, R1.1 |
| E11 | `GET /assets/index-wZagTTUG.js` | `200`, 217 160 bytes | R1.1 |
| E12 | `GET /courses/some-route` | `200`, the shell (client routing) | R1.4 |
| E13 | `GET /api/me` | `401` | R1.4 — the client build does not shadow the API |
| E14 | `GET /api/courses` | `401` | R1.4 |
| E15 | `GET /api` | `401`, not the shell | R1.4 |
| E16 | traversal: `/../secret.txt`, `/assets/../../secret.txt`, `/%2e%2e%2fsecret.txt`, `/..%2f..%2fsecret.txt`, backslash and `%5c` variants, `//etc/passwd` | none returned the planted file beside the build directory; each answered with the shell or a client error | **AC12 met** |
| E17 | `HEAD /assets/…js` | `200` with headers and no body | R1.1 |
| E18 | API-only mode (`TA_HELP_DESK_STATIC_DIR` unset) | `/health` `200`, `/` `401` — unchanged pre-existing behaviour | no regression |

E16–E18 are automated in `src/test/java/edu/nus/tahelpdesk/StaticSiteTest.java`,
so they re-run on every CI build rather than relying on this one session.

### Fail-safe configuration behaviour

| # | Check | Result |
| --- | --- | --- |
| E19 | `TA_HELP_DESK_ENV=production` with no database URL | refuses to start: `TA_HELP_DESK_DATABASE_URL is required in production and whenever database credentials are supplied.` |
| E20 | `TA_HELP_DESK_ENV=production` with a 8-character session secret | refuses to start: `TA_HELP_DESK_SESSION_SECRET must be at least 32 characters in production.` |
| E21 | entrypoint with Railway's `PORT=4567` | adopts it: `port=4567` |
| E22 | entrypoint with explicit `TA_HELP_DESK_PORT=9000` and `PORT=4567` | the explicit value wins: `port=9000` |
| E23 | entrypoint in production with no database URL | exits `1` before starting the JVM |
| E24 | entrypoint startup log with a password and session secret set | logged `env=production port=8080 client=none database=configured` — the shape only, **no secret value** (R5.2) |

### Migration SQL review

`db/migrations/*.sql` contain no `$$`-quoted function body, no `CREATE FUNCTION`
or `CREATE TRIGGER`, and no `;` inside a string literal, so the
split-on-semicolon applied by `PostgresPlatform.migrate` is safe for all five
files. This is a static review, **not** a migration run.

## Blockers — what is still unverified and what is needed

Nothing below can be closed from this session. Each item names exactly what is
needed.

| # | Unverified | Why | What is needed |
| --- | --- | --- | --- |
| B1 | The Docker image builds at all | Docker is not installed on this machine (`docker: command not found`) | Either run `docker build -t ta-help-desk .` locally, or let the first Railway deploy build it. Expect the first build to surface any `Dockerfile` error; it has never been executed. |
| B2 | AC1, AC2 — the live HTTPS URL and `/health` | No Railway project exists; `railway whoami` returns `404`, so the CLI here is unauthenticated | Authenticate the Railway CLI (`railway login`), or create the project in the Railway dashboard and give me the project ID and service name. |
| B3 | AC3 — two users on different networks | Needs the live deployment | B2, then the two-person walkthrough above. |
| B4 | AC4 — the schema migrated on a clean database | Needs a real PostgreSQL. A local PostgreSQL 13 is running on this machine but requires a password that was not supplied, and Docker is unavailable, so `scripts/test-postgres.sh` could not run | Either the local Postgres superuser password (then `scripts/test-postgres.sh` can run against a throwaway database), or B2. |
| B5 | AC5 — PDF upload and download against production storage | Needs the live deployment and database | B2. |
| B6 | AC6 — persistence across an API restart | Needs the live deployment | B2. |
| B7 | AC7 — the database is not publicly reachable | Needs the live Railway project to inspect | B2. The configuration above uses `RAILWAY_PRIVATE_DOMAIN` precisely so no public proxy is on the application path; that still has to be confirmed in the dashboard. |
| B8 | AC8, AC9 — revocation and cross-course download denial | The authorization logic is already covered by `PlatformIntegrationTest` (E1), but not against the live deployment | B2. |
| B9 | AC10 — no secret in deploy logs | The repository and bundle are clean (E5, E6); deploy logs do not exist yet | B2, then read the Railway deploy log. |
| B10 | AC11 — `scripts/test.sh` gates the deploy | The workflow has never run; GitHub Actions is not enabled for this branch and nothing is pushed | Permission to commit and push this branch, then open a pull request so the `test` job runs without deploying. |
| B11 | AC13 — rollback restores the previous release | Needs at least two deployments to exist | B2, twice. |

## Known limitations

1. **Sessions are in-process.** `SessionManager` keeps them in memory, so every
   deploy and every restart signs all users out. One replica only. Spec A4/D5.
2. **One replica, by necessity.** `PlatformService` persists by deleting and
   reinserting every row on each write, so two replicas would clobber each
   other's state. `railway.json` pins `numReplicas: 1`. This is a pre-existing
   shared-platform characteristic owned by @p-xp, not introduced here.
3. **PDF bytes live in PostgreSQL** and are rewritten by that whole-snapshot
   flush. Fine for demo-scale materials; it will not scale. Spec N1.
4. **No backup/restore drill.** Provider snapshots only. Spec N3, D2.
5. **No down-migrations.** Schema problems need a forward fix.
6. **`scripts/test.sh` is POSIX-only.** It fails on Windows because of the `:`
   classpath separator. It is unchanged in that respect; this stage only
   appended the new static suite to it.
7. **Adding a migration needs a code change** — the filename list in
   `PostgresPlatform.migrate`. Pre-existing.
8. **The student SoCLaaS key is intentionally unset**, since Stage 5B is
   blocked on Stage 3. Student AI help is not part of this deployment.

## Changed areas

| Path | Change |
| --- | --- |
| `src/main/java/edu/nus/tahelpdesk/StaticSite.java` | **new** — serves the built client from one directory; refuses paths resolving outside it; shell fallback for client routes; `no-store` for the shell, immutable caching for content-hashed assets |
| `src/main/java/edu/nus/tahelpdesk/HttpApi.java` | new 3-argument constructor; serves the client for non-`/api` GET/HEAD requests *before* the session check, so the app loads before sign-in while the API stays authorized |
| `src/main/java/edu/nus/tahelpdesk/AppConfig.java` | added the optional `staticDirectory` component, read from `TA_HELP_DESK_STATIC_DIR` |
| `src/main/java/edu/nus/tahelpdesk/Main.java` | opens the client build and passes it to `HttpApi`; startup log no longer claims `localhost` |
| `src/test/java/edu/nus/tahelpdesk/StaticSiteTest.java` | **new** — 30+ assertions over raw sockets, including the traversal corpus |
| `scripts/test.sh` | runs the new static suite |
| `scripts/ensure-jdbc.sh` | **new** — SHA-512-pinned PostgreSQL JDBC 42.7.8 fetch |
| `scripts/ensure-pdfbox.sh` | checksum helper now prefers `sha512sum` and falls back to `shasum`, so it works in the Debian/Ubuntu-based build image as well as on macOS. Same pin, same failure behaviour. |
| `scripts/docker-entrypoint.sh` | **new** — adopts the platform's `$PORT`, fails fast on missing production config, logs configuration shape without values |
| `Dockerfile` | **new** — three stages (Node 22 client build, JDK 25 server build, JRE 25 runtime), non-root `app` user, `db/migrations` at the working directory |
| `.dockerignore` | **new** |
| `railway.json` | **new** — Dockerfile builder, `/health` check, 1 replica, restart-on-failure |
| `.github/workflows/deploy.yml` | **new** — `test` job (`scripts/test.sh`, client build, secret/`.env` checks) and `deploy` job gated on it and on `master` |
| `.env.example` | documents `TA_HELP_DESK_ENV` and `TA_HELP_DESK_STATIC_DIR`; points to this file for production |
| `README.md` | single-origin local run and a production-deployment section |
| `workflow/features/deployment/spec.md` | **new** |

No file owned by another in-flight feature was touched, and no existing
behaviour changed when `TA_HELP_DESK_STATIC_DIR` is unset (E18).

## QA handoff

**Next owner:** @ziyang, to create the Railway project and supply B2, or to
authorize committing and pushing this branch so B10 can run first.

**Do not treat Stage 6 as complete.** Under the `AGENTS.md` completion gate this
work cannot be reported complete: it is substantial and security-sensitive, the
deployment-dependent acceptance criteria have no actual results, and no
independent `test-report.md` exists. The orchestrator must not summarize Stage 6
as delivered, and the Stage 6 row in
`workflow/implementation_plan.md` should stay **Not started** — or become
**Partial** noting that the mechanism is built and locally verified but no live
deployment, migration, or multi-user evidence is recorded.

**Suggested order once unblocked:**

1. Push this branch and open a pull request. The `test` job runs and does not
   deploy — this closes B10 and validates `scripts/test.sh` on Linux.
2. Create the Railway project, the two environments, and the two Postgres
   services. Set the variables from the contract above. Closes B1, B7.
3. Merge to `master`. The deploy job runs. Record the origin, the deploy log,
   and `schema_migrations` contents. Closes B2, B4, B9.
4. Run the two-person walkthrough and the PDF upload/download and revocation
   cases. Closes B3, B5, B8.
5. Restart the service, then redeploy the previous deployment. Closes B6, B11.
6. Hand to the tester for an independent `test-report.md` covering the
   multi-user journey and the private-material boundary, then to the acceptance
   auditor, as Stage 6's handoff gate requires.

**Open product decisions still outstanding:** spec D1–D5, plus the seed-account
exposure flagged under "Peer-tester access". That one is a **prerequisite for
step 2 above**, not a parallel decision: the staff account is created on the
first start of the production database using a password committed to a public
repository. D2 (backup/restore expectation) and D3 (tester credential delivery)
affect what the tester must verify, so decide them before testing starts.
