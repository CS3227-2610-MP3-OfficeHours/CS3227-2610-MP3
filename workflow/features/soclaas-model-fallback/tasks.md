# SoCLaaS model fallback — implementation handoff

**Status:** Implemented; ready for independent QA.

## Scope

Implement the approved bounded staff Knowledge Map fallback in
`workflow/specs/soclaas-model-fallback.md` and its security planning review.
Existing uncommitted per-material Knowledge Map work was present and preserved.

## Implementation

- Added authenticated startup `GET /v1/models` validation for the primary and,
  when explicitly configured, fallback IDs. Invalid/unavailable catalog
  configuration leaves staff generation unavailable. An omitted fallback
  permits a validated primary-only client.
- Added one-call-per-model generation behavior. Only 5xx, I/O, and timeout
  outcomes enter one fallback call, with the same request snapshot and body
  except for `model`. 429, configuration, malformed/empty response, and
  rejected outcomes stop after the primary.
- Added a staff-authorized `fallbackConfigured` capability to the existing
  Knowledge Maps response. The UI displays the fixed Qwen/Gemma disclosure
  only when this server value is true.
- Documented `SOCLAAS_STAFF_CHAT_MODEL=gemma4:26b` and
  `SOCLAAS_STAFF_FALLBACK_CHAT_MODEL=qwen3.5:9b` in `.env.example` and README.
- No provider ID, catalog contents, diagnostics, prompt, or secret is returned
  through the new capability field.

## Verification executed

- `sh scripts/test.sh` — **PASS** (server compilation, PlatformIntegrationTest,
  and SoCLaaSClientTest). The run required approved local socket binding for
  the test's in-process mock gateway.
- Frontend build/typecheck — **not verified**: package registry access was
  unavailable and dependencies were not present in the workspace store.
- Live SoCLaaS catalog validation — **not performed**: no staff key is
  configured in this workspace. Each deployment performs authenticated catalog
  validation before enabling generation.

## QA handoff

Input: this implementation and tests; approved spec and security review listed
above. Deliverable: independent `test-report.md` covering acceptance criteria,
including authorization, fallback bounds, unchanged candidate/approved map
state on failures, and disclosure secrecy. Known evidence limits are frontend
build dependency access and unavailable live deployment credentials. Next owner:
tester, then acceptance auditor after a passing report.
