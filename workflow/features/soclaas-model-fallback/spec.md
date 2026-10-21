# SoCLaaS model fallback

**Status:** Approved for implementation by the user's request on 2026-10-21.

The canonical approved requirements and acceptance criteria are in
[`workflow/specs/soclaas-model-fallback.md`](../../specs/soclaas-model-fallback.md).
The applicable security planning review is
[`workflow/security/soclaas-model-fallback-plan-review.md`](../../security/soclaas-model-fallback-plan-review.md).

Scope: staff-only Knowledge Map generation uses configured `gemma4:26b` first
and makes at most one `qwen3.5:9b` fallback request after a transient 5xx,
network, or timeout failure. Both IDs must validate against the authenticated
key's SoCLaaS model catalog before fallback is reported as enabled. The same
source snapshot and request settings are used for both calls, and existing
source/citation validation and staff approval gates remain in force.
