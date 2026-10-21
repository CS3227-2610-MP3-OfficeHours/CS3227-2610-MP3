# SoCLaaS model fallback — security planning review

Status: Proposed — not implemented

Date: 2026-10-21

## Selected configuration for review

- Primary: `gemma4:26b` (Gemma 4 26B).
- Fallback: `qwen3.5:9b` (Qwen 3.5 9B).

These values are deployment configuration, not browser input. They remain
subject to the staff key's authenticated `GET /v1/models` catalog check.

## Scope

Proposed reliability change for staff Knowledge Map generation: after a
transient primary-model failure, make at most one fresh request to a configured
alternate model available to the same SoCLaaS API key.

## Required policy

- The primary and fallback IDs must be explicit server configuration and must
  be selected only from the authenticated key's `GET /v1/models` catalog. Do
  not hard-code a guessed model ID or allow a browser request to select one.
- Retry only a transient 5xx, network, or timeout outcome. Do not fallback on
  401/403, malformed request/response, source-validation failure, or 429: a
  second model is unlikely to fix key/configuration/quota failures and can
  increase load.
- Make at most two total provider calls per manual job: primary then fallback.
  Preserve candidate/approved map state if both fail and record only a safe
  staff-facing failure outcome, never model names, prompts, secrets, or raw
  gateway diagnostics.
- Send the same immutable material/page snapshot and prompt to both models.
  Validate the fallback output through the existing source/page/citation and
  human-approval gates; model fallback must never bypass provenance checks.
- Leave fallback disabled when no explicit approved fallback model is
  configured. A 502 may be gateway-wide, so fallback is best-effort rather
  than an availability guarantee.

## Required verification before implementation

1. Primary 5xx/timeout then fallback success returns validated content; the
   test records two calls using only configured model IDs.
2. Primary 429, 401/403, malformed response, and source-validation failure
   make no fallback call.
3. Both transient attempts fail without overwriting candidate/approved maps.
4. Fallback output with wrong/foreign/invalid citations remains unverified and
   cannot be approved.
5. Model IDs, raw errors, and credentials never appear in HTTP/UI responses or
   logs intended for staff.
