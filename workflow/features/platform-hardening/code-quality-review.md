# Platform hardening — code-quality review

Status: Pass after engineering remediation
Reviewer: Code-quality agent
Review type: Static, read-only review; no tests were run by this reviewer.
Last reviewed: 2026-10-22

## Reviewed scope

- Approved requirements: `workflow/features/platform-hardening/spec.md`.
- Application changes: bounded JSON request handling in `HttpApi`, the 413
  `ApiException` contract, and PostgreSQL migration registration.
- Database migrations: `V5__material_knowledge_maps.sql` and
  `V7__material_resource_only_status.sql`.
- Test and security evidence: HTTP, platform, and PostgreSQL integration
  tests, `tasks.md`, and
  `workflow/security/platform-hardening-upload-boundary-review.md`.

The review checked the endpoint-specific body limits and their bypass paths,
safe error handling, material-status/API consistency, V5/V7 migration
behavior, and the relevant test coverage against the approved specification.

## Initial findings returned to engineering

### P2 — Handler-level request-boundary coverage was incomplete

`HttpApiIntegrationTest` initially tested missing and understated
`Content-Length` only by calling the bounded-reader helper. That did not
exercise `HttpApi.handle`, verify the HTTP 413 JSON response, or prove that
the affected endpoints did not mutate service state. The approved requirements
cover both normal JSON and material-upload routes.

### P2 — `resource_only` AI ineligibility was untested

The existing tests established that an image-only material could persist and
remain downloadable, but did not attempt Knowledge Map generation after the
material reached `resource_only`. The approved requirements require this
status to remain unavailable to AI processing.

## Engineering remediation and final re-review

Engineering added a controlled `HttpExchange` test harness in
`HttpApiIntegrationTest`. It now sends one-byte-over-limit bodies with missing
and understated `Content-Length` values to both endpoint classes through
`HttpApi.handle`. The assertions verify HTTP 413, the `request_too_large`
code, exactly the safe error fields, the non-sensitive message
`The request is too large.`, and no signup or material mutation.

Engineering also updated `PlatformIntegrationTest` to configure a Knowledge
Map generator and verify that generating a map for an image-only
`resource_only` material returns HTTP-style domain status 422 after the status
transition. The existing authorized-download and PostgreSQL persistence
coverage remains in place.

The final static re-review found both P2 findings resolved. No residual
actionable P0–P3 findings were identified in the reviewed remediation. This
conclusion covers code quality and specification-aligned static review only;
it does not replace the engineer's recorded test results or independent
tester verification.
