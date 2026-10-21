# Staff Knowledge Map model fallback

Status: Approved for implementation — user requested implementation of this exact bounded plan on 2026-10-21

Date: 2026-10-21

## Handoff summary

Implement a bounded, server-only SoCLaaS fallback for staff Knowledge Map
generation. The configured primary is **Gemma 4 26B** (`gemma4:26b`); the
configured fallback is **Qwen 3.5 9B** (`qwen3.5:9b`). A manual generation job
may make no more than two provider calls: primary first, then fallback only
for a transient primary failure.

This is a best-effort reliability feature, not an availability guarantee: a
502 can originate in shared gateway infrastructure and may affect both models.

## Product and user-facing contract

### Intended users

Only authorized course staff can start or view Knowledge Map jobs. No student
route, client configuration, or browser request may select/observe provider
credentials or change the model choice.

### Clear model disclosure

Before a staff member generates a map, the **Knowledge Maps by PDF** area says:

> Generation uses Gemma 4 26B. If it has a temporary service failure, the
> system tries Qwen 3.5 9B once before reporting a failure.

This is informational—not a provider-capacity promise—and is rendered only
after the server confirms its fallback configuration is valid. It does not
show raw gateway errors, API keys, endpoint URLs, or internal model catalog
metadata.

Deployment documentation and `.env.example` must name the same explicit IDs:

```text
SOCLAAS_STAFF_CHAT_MODEL=gemma4:26b
SOCLAAS_STAFF_FALLBACK_CHAT_MODEL=qwen3.5:9b
```

The process must not silently substitute a default fallback if the second value
is absent. Fallback is disabled in that case, and the UI must not claim it is
available.

## Startup configuration and catalog validation

1. Read the existing staff API key, base URL, primary-model variable, and new
   fallback-model variable only on the server.
2. When fallback is configured, call authenticated `GET /v1/models` during
   startup (or before installing the client) and require both exact IDs to be
   in the returned catalog for that key.
3. If the key, model catalog request, primary ID, or fallback ID is invalid or
   unavailable, do **not** start a client that claims fallback. Either fail
   startup with a safe operator-facing configuration error or install the
   existing primary-only client only when that primary is independently
   validated; choose and document one consistent bootstrap policy.
4. Never make a browser-supplied model ID authoritative, cache the catalog as
   an authorization bypass, or log the API key/authorization header.

Recommended bootstrap policy: validate both selected IDs before enabling staff
LLM generation; if validation cannot complete, leave generation unavailable
and give authorized staff the existing safe configuration/unavailable message.
This prevents a misleading “fallback enabled” display.

## Request/fallback behavior

The client must build the same immutable source packet, system prompt,
temperature, timeout, and request body for both calls. Only `model` changes.

| Primary outcome | Fallback call? | Final behavior |
| --- | --- | --- |
| Successful HTTP response with non-empty model text | No | Validate and process primary output normally. |
| 5xx response, I/O/network failure, or request timeout | Yes, once | Try `qwen3.5:9b` with the identical snapshot. |
| 429 rate/budget limit | No | Preserve maps and report the existing busy/rate-limited result. |
| 401/403, invalid model/configuration | No | Preserve maps and report safe configuration failure. |
| Other 4xx, malformed response, empty content, or client serialization failure | No | Preserve maps and report rejected/unavailable as currently appropriate. |
| Fallback success | N/A | Validate output exactly as primary output. |
| Fallback transient failure | N/A | Stop after two total attempts; preserve maps and report safe temporary unavailability. |

The fallback must not retry citation validation failures or start a new job.
If either model returns output, it remains untrusted and must pass the current
source/current-page/citation validation and staff approval requirements. A
fallback result can be retained only as the same verified or unverified
candidate state that a primary result would produce.

## Data, API, and UI impact

- No public API request adds a `model` parameter. Existing staff map/job
  responses may add a safe boolean/string capability such as
  `fallbackConfigured`, but must not expose raw provider diagnostics.
- No model name needs to be stored with map content for user display. If the
  project records it operationally for audit/debugging, it must remain
  server-only, be bounded, and never influence approval/currentness.
- Add a staff-only configuration-status field/endpoint only if needed for the
  disclosure above. It must require the same course-staff authorization as
  map generation and return only the fixed public display names/availability.
- The UI remains honest during a fallback attempt: show `Generating` until the
  job finishes; do not expose which provider call is in flight or promise a
  retry succeeded.

## Security constraints

- Keep the existing course authorization before job creation and preserve the
  material/page snapshot across both provider calls.
- Treat PDF text, filenames, generated output, and provider response bodies as
  untrusted data. The alternate model does not relax prompt framing, provenance
  checks, or approval gates.
- Cap work at two provider calls per manual job. Do not fall back after 429 to
  avoid doubling quota/budget pressure, and do not add automatic repeated
  retries.
- Preserve existing candidate/approved map content when either attempt fails.
- Return safe generic staff messages only; never leak the fallback model's raw
  error, model catalog, credentials, or source packet.

## Implementation handoff

**Inputs:** this specification; `workflow/security/soclaas-model-fallback-plan-review.md`;
`src/main/java/edu/nus/tahelpdesk/SoCLaaSClient.java`; bootstrap in `Main.java`;
current map job service/UI; existing SoCLaaS tests.

1. Add a model-catalog client/parser and configuration object that holds the
   two configured IDs and exposes no secret to the frontend.
2. Refactor the chat-completion code into one-call-with-model logic; wrap it
   in the exactly-once fallback policy above, preserving typed failures.
3. Wire bootstrap validation and safe unavailable/configuration behavior.
4. Add the staff-facing fixed model disclosure only when server configuration
   confirms the pair. Update README and `.env.example` with both values.
5. Add automated tests below, then update feature tasks/evidence and request
   independent security/test review. Do not commit unless separately asked.

## Required acceptance evidence

1. Mock `/v1/models` returns both configured IDs: startup enables fallback;
   missing primary/fallback ID or catalog failure does not claim it is enabled.
2. Primary 502/503, timeout, and I/O failure cause exactly two calls in order:
   `gemma4:26b`, then `qwen3.5:9b`; fallback success completes the normal map
   validation flow.
3. Primary 429, 401, 403, 400, malformed response, empty output, and source
   validation error cause exactly one call—no fallback.
4. Both transient calls fail without losing the existing candidate or approved
   map, including the per-material map lifecycle.
5. Fallback output with invalid, foreign, stale, or missing citations remains
   unverified and cannot be approved.
6. Model IDs, model-catalog details, raw errors, source text, and credentials
   are absent from API/UI responses. Authorized staff see the fixed disclosure;
   students/non-members cannot access any new configuration/status endpoint.
7. Server suite passes and the frontend typecheck/build passes in an environment
   with complete dependencies.

## Decisions already supplied by the product owner

- Primary: `gemma4:26b` (Gemma 4 26B).
- Fallback: `qwen3.5:9b` (Qwen 3.5 9B).
- Both selected models must be clearly stated before staff initiate generation.

## Approval record

The user's direct request to implement this plan approves the stated primary,
fallback, bounded retry policy, and recommended bootstrap behavior. The live
catalog has not been queried in this workspace because the staff key is not
configured; each deployment validates its selected IDs with its own key before
enabling generation.
