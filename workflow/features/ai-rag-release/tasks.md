# Shared AI/RAG Foundation — Engineering Tasks

Status: Implementation complete — awaiting independent testing
Approval: Human product-owner approval recorded in chat, 2026-10-21 — staff Stage 3 foundation only; no student AI behavior.
Last handoff: Engineer → Tester, 2026-10-21 — Stage 3 staff-only Knowledge Map foundation is implemented; independently verify the recorded acceptance criteria before any completion claim.

## Completed in this increment

- Added `SoCLaaSClient`, a server-only adapter that reads the staff key only
  from environment configuration and calls SoCLaaS chat completions.
- Added fixed untrusted-source prompt treatment and typed safe outcomes for
  configuration, 429, temporary 5xx/network failure, and rejected responses.
- Added a loopback fake-gateway test. It verifies prompt framing and error
  mapping without using a real key or sending protected material.
- Added the adapter test to `scripts/test.sh`.
- Implemented one persisted Knowledge Map per course, including the complete
  active-PDF snapshot, request/review identity, state, failure state, and
  timestamps. Added `V3__course_knowledge_maps.sql` and PostgreSQL load/save.
- Implemented staff-only status, manual generate/update, approve, and reject
  endpoints. No student Knowledge Map or AI endpoint exists.
- Generation packages every active PDF as untrusted source data, with bounded
  literal-text extraction and filename/page-range metadata. It uses a bounded
  four-slot local queue and preserves neutral rate-limit/unavailable outcomes.
- Uploaded, deleted, retried, or changed source material immediately marks an
  existing map stale. A source change during generation discards the result.
- The generated output must contain a valid in-range filename/page citation for
  every source PDF before it can enter `pending_review`; an authorized staff
  member must approve it before a future student feature can consume it.
- Added server authorization/currentness lifecycle tests, including student
  denial, approval, full active-source snapshot, and immediate staleness.
- Added negative/regression coverage for untrusted injected source text and
  fixed prompt framing; cross-course staff-map denial; multi-PDF snapshots;
  a source change while the provider call is in flight; malformed responses,
  timeout, 401, 429, and 5xx provider outcomes; and the four-slot queue limit.

## Test evidence

- `sh scripts/test.sh` — PASS (platform authorization/material suite and
  loopback SoCLaaS adapter suite).

## Limitation / next handoff

The simple dependency-free PDF extractor establishes a citation-aware
foundation but is not a production-quality PDF text/page parser. Before release
or student retrieval, replace it with a page-preserving parser and add
adversarial output/injection test fixtures. This Stage 3 code intentionally
does not send any material until a staff member manually requests generation.

Next owner: Tester independently verifies Stage 3, with particular attention to
cross-course denial, stale-generation races, output-citation rejection, and
safe provider failures.
