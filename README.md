# University TA Help Desk

The shared platform is a Java 25, server-authoritative JSON API with a React +
TypeScript client foundation. It provides account sessions, course memberships,
and a server-authorized course forum. Feature UI is deliberately not part of
this shared scaffold.

## Local development

Run the in-memory service and authorization suite without external services:

```sh
sh scripts/test.sh
sh scripts/run-local.sh
```

The Java scripts obtain Apache PDFBox 3.0.8 on first use in the ignored
`.local-lib/` cache and verify its pinned SHA-512. This requires `curl` and
network access once; no third-party JAR is committed to the repository.

For durable local PostgreSQL storage, start the local-only database and supply
the PostgreSQL JDBC driver outside source control:

```sh
docker compose up -d postgres
mkdir -p .local-lib
curl -L https://jdbc.postgresql.org/download/postgresql-42.7.8.jar -o .local-lib/postgresql.jar
sh scripts/run-postgres-local.sh
# In another terminal, run the database-backed persistence/authorization suite:
sh scripts/test-postgres.sh
```

The application automatically applies the tracked database migrations and records them
in `schema_migrations`. The PostgreSQL runtime uses
`TA_HELP_DESK_DATABASE_URL`, `TA_HELP_DESK_DATABASE_USER`, and
`TA_HELP_DESK_DATABASE_PASSWORD`; production refuses to start without them.
Keep those values server-side—there are no client database credentials.

The Vite React/TypeScript client is in `src/frontend/`; the Java API and its
tests remain in `src/main/` and `src/test/`. With Node.js installed, run the
following from the repository root:

```sh
cd src/frontend
npm install
npm run dev
```

The client intentionally contains only the shared React/TypeScript entry
point; feature owners add approved student/staff views.

Copy `.env.example` to `.env` only when changing local settings. Never commit a
real session or application secret. Both Java launch scripts load this local
file into their process environment when it exists. Staff Knowledge Map
generation uses `SOCLAAS_STAFF_API_KEY` only on the server. Configure
`SOCLAAS_STAFF_CHAT_MODEL=gemma4:26b` and
`SOCLAAS_STAFF_FALLBACK_CHAT_MODEL=qwen3.5:9b` to enable the bounded fallback.
At startup, the server validates configured model IDs against the authenticated
SoCLaaS `/v1/models` catalog; if validation fails, staff generation remains
unavailable. If the fallback variable is omitted, the validated primary model
may be used alone and the UI does not claim fallback is available. A temporary
primary service failure triggers at most one fallback request.
`SOCLAAS_STUDENT_API_KEY` is reserved for the separately owned future student
feature and is not used yet.
