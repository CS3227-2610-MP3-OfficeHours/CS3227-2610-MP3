# SoCLaaS model fallback — UI disclosure

**Status:** Approved with the feature requirements on 2026-10-21.

The existing staff-only **Knowledge Maps by PDF** area displays this fixed
message only when the server's authenticated SoCLaaS model catalog check has
confirmed both configured model IDs:

> Generation uses Gemma 4 26B. If it has a temporary service failure, the
> system tries Qwen 3.5 9B once before reporting a failure.

The disclosure is informational. It does not expose gateway errors, credentials,
URLs, or catalog metadata. While a job runs, the UI continues to say
“Generating” without showing which provider call is active.
