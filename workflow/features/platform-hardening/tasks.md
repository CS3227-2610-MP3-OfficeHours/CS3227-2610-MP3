# Platform hardening — engineering handoff

Status: Implemented; awaiting code-quality review
Approval: Requirements approved in `spec.md` on 2026-10-22.
Last handoff: Engineer → Code quality, 2026-10-22.

## Delivered work

- Added `V7__material_resource_only_status.sql`, a forward-only PostgreSQL
  migration that permits `resource_only` while retaining every previous
  material status. Registered V7 in `PostgresPlatform`.
- Expanded the V5 comment to record the approved decision to discard obsolete,
  disposable legacy course Knowledge Map test data.
- Added bounded JSON request reading in `HttpApi`: 1 MiB for normal JSON and
  28 MiB for `POST /api/courses/{courseId}/materials`. Declared oversized
  requests are rejected before reading; missing, invalid, or understated
  lengths are capped while reading. The safe API response is HTTP 413 with
  `request_too_large` and `The request is too large.`
- Added loopback and handler-level HTTP boundary coverage, including absent and
  understated `Content-Length` values for normal JSON and material uploads,
  and PostgreSQL migration/lifecycle coverage including persistence and reload
  of an image-only `resource_only` material. The integration suite also proves
  a configured Knowledge Map generator rejects `resource_only` material with
  422 after its status transition.
- Wrote the required upload-boundary security review and negative test cases at
  `workflow/security/platform-hardening-upload-boundary-review.md`.

## Test evidence

- `sh scripts/test.sh` — PASS: `Platform integration tests: PASS`,
  `HTTP API integration tests: PASS`, and `SoCLaaS client tests: PASS`.
  PDFBox emits its pre-existing malformed-PDF parser diagnostic during the
  platform suite; the suite passes.
- `sh scripts/test-postgres.sh` — PASS: `PostgreSQL platform integration
  tests: PASS` against the repository's local Docker PostgreSQL service.

## Scope and limitations

- Tests cover declared over-limit requests through the loopback HTTP server.
  Missing and understated `Content-Length` behavior is exercised at the
  `HttpApi` handler using a controlled `HttpExchange`, because the JDK
  loopback client/server normalizes request framing before application code
  receives it.
- The request cap establishes an application boundary but does not replace
  production connection, concurrency, reverse-proxy, or PDF parser limits.

## Next owner

Code-quality agent: perform the required read-only review of the changed
application, migrations, tests, and security artefact against
`workflow/code-quality.md` before independent testing.
