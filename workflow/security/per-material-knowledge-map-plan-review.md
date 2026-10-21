# Per-material Knowledge Maps — security planning review

Status: Planning review — implementation not started

Date: 2026-10-21

## Scope

Review of the proposed material-scoped map/candidate/job design in
`workflow/specs/per-material-knowledge-maps.md`. It changes AI input scope,
stored generated content, staff review, bulk job scheduling, and the future
student-navigation eligibility boundary.

## Required controls

- Resolve course membership and staff role server-side for every map/job route
  before exposing status, content, filename, citation, or failure information.
- Bind an immutable job snapshot to course ID, material ID, material digest,
  and current page provenance. Completion must re-check each value; a deleted,
  inactive, changed, or cross-course source cannot replace a retained map.
- Frame one PDF's extracted text as untrusted data in the provider request.
  A document instruction cannot override the system prompt, authorize an
  action, select another source, or cause publishing.
- Validate every citation against the selected material's current nonblank
  pages. Do not accept a valid page from another same-course PDF as support.
- Preserve a candidate/approved map on provider failure, but never treat a
  failure message or stale candidate as content eligible for student AI.
- Bulk requests must be locally bounded and deduplicate in-flight work per
  material. A client cannot cause an unbounded fan-out, bypass rate limits, or
  amplify retries by supplying arbitrary IDs.
- Rejecting a candidate removes only the target generated body. It must not
  delete the source PDF, approved map, another course's state, or audit
  decision data.
- This development-stage migration removes obsolete course-wide map records;
  routes and persistence must be removed together so no legacy endpoint can
  accidentally expose or authorize an outdated map.

## Security test cases required before acceptance

1. Student, removed student, unrelated staff, and forged material/course IDs
   cannot list, read, generate, approve, reject, or infer map/job existence.
2. A direct/indirect PDF prompt injection and hostile provider output remain
   text only and cannot alter authorization, request scope, source lifecycle,
   or approval.
3. A response citing another PDF, a non-existent page, a page with no current
   extracted text, a deleted PDF, or a changed digest is retained only as
   unverified or discarded; it cannot be approved.
4. Source deletion, material retry/source mutation, and cross-course movement
   while a job is queued/running cannot revive output or overwrite either map
   slot.
5. 429, 5xx, timeout, malformed response, and queue submission failure retain
   prior candidate/approved content and disclose only safe staff failure text.
6. Bulk generation with 20 PDFs observes configured concurrency/rate limits,
   deduplicates repeated clicks, reports partial outcomes, and does not send
   one PDF's text to another PDF's request.

## Residual risk

Smaller prompts reduce exposure and operational failure impact but do not make
PDF prompt injection or model hallucination solved. Authorized staff still see
untrusted generated content. Page-level validation, explicit labels, and
approval reduce the risk; original authorized PDFs remain the only source of
truth for future student-facing claims.
