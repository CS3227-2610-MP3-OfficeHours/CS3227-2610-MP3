# Staff Knowledge Map reasoning effort — implementation handoff

**Status:** Implemented; ready for independent verification.

## Scope

Implement the approved request parameter in
`workflow/features/staff-llm-reasoning-effort/spec.md`: set
`reasoning_effort` to `none` on staff Knowledge Map chat requests while keeping
the prompt and other behavior unchanged.

## Implementation

- Added `reasoning_effort: "none"` to the completion request body. The shared
  request builder applies it to primary and fallback model attempts.
- Left the system prompt, two-message structure, source envelope, model,
  temperature, timeouts, fallback rules, and output validation unchanged.
- Expanded `SoCLaaSClientTest` to assert the exact system prompt, parsed user
  message/source envelope, model and temperature, the new field on primary and
  fallback requests, and existing JSON newline escaping.

## Review and verification

- Code-quality review — **no actionable findings**; the reviewer also rechecked
  the corrected parsed-message test assertion.
- `sh scripts/test.sh` — **PASS**: PlatformIntegrationTest,
  HttpApiIntegrationTest, and SoCLaaSClientTest. The test run required local
  socket access for mock HTTP servers. PDFBox emits its expected malformed-PDF
  diagnostic during the negative fixture; the suite passes.
- Live diagnostic experiment using the extracted `l0.pdf` packet and unchanged
  app prompt: without the field, HTTP 502 after 61.164 seconds; with
  `reasoning_effort: "none"`, HTTP 200 after 23.912 seconds. This is diagnostic
  evidence only and does not guarantee future gateway availability.

## QA handoff

Input: this handoff, the approved feature specification, the request builder,
and `SoCLaaSClientTest`. Deliverable: independent `test-report.md` covering all
acceptance criteria and the actual server suite result. Next owner: tester;
after a passing report, acceptance auditor reviews the feature artefacts.
