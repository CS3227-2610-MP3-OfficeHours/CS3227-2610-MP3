Status: Approved
Approval: Human product-owner approval recorded in chat, 2026-10-22 — add `reasoning_effort: "none"` to staff Knowledge Map chat completion requests while keeping the current prompt unchanged.
Last handoff: Product manager → Engineer, 2026-10-22 — implement the approved request-parameter change and record regression evidence; preserve all listed behavior outside this parameter.

# Staff Knowledge Map reasoning effort

## Problem and outcome

Staff Knowledge Map requests have experienced gateway failures near a
one-minute boundary. Prior direct probes with the same extracted `l0.pdf`
content and Knowledge Map prompt returned HTTP 502 when `reasoning_effort` was
omitted, and HTTP 200 when it was set to `none`. This is evidence for a useful
request variant, not proof that reasoning mode caused the earlier failures.

Set `reasoning_effort` to the string value `none` on every staff Knowledge Map
chat completion request. Preserve the current system prompt verbatim and keep
the existing source framing, model choice, temperature, timeout, retry/fallback
policy, and validation behavior unchanged.

## Roles and user goal

- **Staff:** generate a reviewable, per-material Knowledge Map from authorized
  course PDFs as before, with the request sent using the approved reasoning
  effort setting.
- **Students and other unauthorized users:** no change; they cannot invoke or
  access staff Knowledge Map generation.

## Scope

- Add or retain the JSON field `"reasoning_effort": "none"` in the request
  body for each chat completion attempt, including a configured fallback-model
  attempt.
- Keep all current system-prompt text exactly the same.
- Preserve the existing two-message format: system instructions in the system
  message and the page-labeled source packet inside the user message, framed by
  `<course_sources>` tags.
- Preserve the existing configured primary/fallback model selection and
  fallback trigger/count.
- Keep completion timeout, catalog-validation timeout, `temperature: 0`,
  authentication, and JSON request headers unchanged.
- Keep authorization, source snapshot/currentness, source injection framing,
  output citation checks, approval gating, and safe provider-failure handling
  unchanged.

## Non-goals

- Editing, shortening, rewording, or versioning the system prompt.
- Changing PDF extraction, source packet content/order/size, page labels, or
  source envelope.
- Changing the model, temperature, timeout, retry/fallback behavior, or output
  validation.
- Treating the parameter as a proven fix for all 502s or promising a latency
  target. Gateway latency and failures remain externally variable.
- Exposing provider request details, prompts, source text, credentials, or raw
  provider errors to staff or students.

## User-visible and failure behavior

There is no UI or API response contract change. Authorized staff continue to
receive the existing queued/generating/success/failure states. A provider
failure remains a safe failed Knowledge Map job, and existing map slots remain
unchanged according to the per-material Knowledge Map contract. Students and
non-members remain denied by existing server-side authorization.

## Dependencies and assumptions

- The SoCLaaS chat completions endpoint accepts `reasoning_effort: "none"` for
  the configured staff models. This was successful in direct probes but should
  still be covered by request-construction tests.
- Existing approved per-material Knowledge Map and model-fallback
  specifications continue to govern authorization, source lifecycle, model
  fallback, and map review behavior.
- Latency experiments are diagnostic only; tests must not rely on live timing
  or claim that one setting guarantees a successful response.

## Acceptance criteria

1. Every primary staff Knowledge Map completion request serializes
   `reasoning_effort` as the JSON string `"none"`.
2. When fallback is configured and invoked under the existing policy, the
   fallback completion request also serializes `reasoning_effort` as `"none"`.
3. Request regression tests assert that the exact existing system prompt is
   unchanged, including its untrusted-source instructions and citation/output
   requirements.
4. Request regression tests assert that the existing two-message structure,
   source envelope, and page-labeled source content are unchanged; instructions
   and parsed PDF content are not concatenated into one message.
5. Request regression tests assert the model and `temperature: 0` are unchanged
   for primary and fallback attempts, and that the completion timeout and
   existing fallback eligibility/count remain unchanged.
6. Existing source-page/currentness and citation checks still reject missing,
   malformed, foreign, stale, or out-of-range citations; the new request field
   does not bypass or weaken review/approval gating.
7. Existing staff-only authorization and safe provider-failure behavior remain
   unchanged. No new prompt, source text, provider diagnostics, or credentials
   are exposed through a client response or logs.
8. The relevant automated Java suites pass, with actual command and results
   recorded in the engineering handoff. No live service timing result is
   required for acceptance.

## Open decisions

None for this narrowly scoped change. Further prompt edits, model behavior
changes, timeout/fallback changes, or new telemetry require separate review and
approval.

## Next handoff

Engineer: review the current working-tree state before editing because the
adapter may already contain an uncommitted `reasoning_effort: "none"` change.
Implement only any missing parts of these criteria, add/update request tests,
and record changed files and test evidence in `tasks.md`. Then hand off for the
required read-only code-quality review before independent testing.
