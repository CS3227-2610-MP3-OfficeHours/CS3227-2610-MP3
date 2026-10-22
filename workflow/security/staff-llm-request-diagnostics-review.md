# Staff LLM request diagnostics review

**Date:** 2026-10-22
**Scope:** Staff Knowledge Map chat request format, completion timeout, and
synthetic endpoint probes. This review does not include the original failed
course request because its payload and gateway response body were not retained.

## Findings

- The app sends a valid chat-completions JSON object with `model`, `messages`,
  and `temperature: 0`, plus bearer and JSON headers. `reasoning_effort` is
  optional for the tested endpoint; both its presence and absence succeeded.
- The completion timeout is now three minutes. Model-catalog validation keeps
  its separate 45-second timeout. A local mock response delayed 46 seconds
  succeeded under the completion timeout.
- No request format or payload-size failure was reproduced. Synthetic app-style
  requests with approximately 1,000, 30,000, and 100,000 source characters all
  returned HTTP 200. The live probes did not include course material.
- The exact cause of the reported 502 remains unconfirmed. A 502 is a response
  from the gateway path, whereas a local Java request timeout is caught as an
  I/O failure. The app currently discards provider response bodies and does not
  retain per-attempt status or duration, so the original failure cannot be
  distinguished further from this workspace.

## Verification

- `sh scripts/test.sh` — **PASS**: PlatformIntegrationTest, HttpApiIntegrationTest,
  and SoCLaaSClientTest. The PDFBox malformed-fixture diagnostic was emitted by
  an existing negative test; the test suite passed.
- Live endpoint probes — **PASS**: five synthetic request variants returned
  HTTP 200. Completion times ranged from 1.9 to 21.8 seconds. The three-minute
  client timeout was used for these probes.

## Residual risk and next diagnostic

Live success cannot establish why a different source snapshot received 502.
For a future occurrence, record bounded server-side metadata per attempt:
HTTP status, elapsed time, request byte count, and selected model. Do not record
authorization headers, source text, prompts, or raw provider response bodies.
Compare a sanitized, authorized reproduction of the failing source against a
small synthetic request if the 502 recurs.
