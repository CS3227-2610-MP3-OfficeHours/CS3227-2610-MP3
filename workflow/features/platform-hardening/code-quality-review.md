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
## CI request-limit regression follow-up — 2026-10-22

Review type: Static review only; no tests were run.

### Initial finding

- **P2 — Documentation-evidence mismatch.** The completed-test report and
  upload-boundary security review stated that a real loopback HTTP material
  upload larger than 28 MiB received a 413 response. The CI-stable replacement
  instead invokes the handler through a controlled `HttpExchange`, using a
  request stream that fails if it is read. The evidence needed to distinguish
  that handler-level declared-length coverage from real loopback coverage.

### Remediation reviewed

The updated test evidence now states that the controlled handler exchange
supplies an authorized material-upload request with an over-limit declared
`Content-Length` and a fail-on-read body stream. It records that this proves
the safe 413 response is returned without consuming the body, parsing JSON,
decoding Base64, or creating a material. It also records that the exactly
28 MiB material upload remains real loopback coverage for the accepted
boundary.

### Final result

The P2 finding is resolved. The updated wording in `test-report.md` and
`workflow/security/platform-hardening-upload-boundary-review.md` matches the
test's controlled handler assertions for the 413 status, safe error shape,
body non-consumption, and no material mutation. No residual actionable
code-quality findings were found in this follow-up scope.
