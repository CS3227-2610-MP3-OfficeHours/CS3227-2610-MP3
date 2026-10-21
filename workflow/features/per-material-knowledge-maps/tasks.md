# Per-material Knowledge Maps — implementation evidence

Status: Implementation submitted for independent testing

## Implemented

- Replaced the course-wide map model/routes with material-scoped candidate and
  approved slots plus a persisted per-material generation job.
- A generation attempt now retains both slots until a successful response
  replaces the candidate; failures are held on the job, not in map content.
- Generation builds a source packet from only the selected PDF and validates
  citations against only that PDF's current extracted pages. It conservatively
  leaves output unverified when an output line has no selected-source citation
  or uses an invalid selected-source page.
- Added individual and bulk generation endpoints, source-change cancellation,
  material-deletion cleanup, candidate approval/promotion, and candidate-only
  rejection.
- Added the per-PDF staff UI with independent actions, retained-map failure
  state, bulk generation action, automatic job polling, and manual refresh.
- Bulk requests persist every eligible job as `queued` before worker capacity
  is acquired, so semaphore pressure delays rather than silently omits work.
- The worker executor now has four workers and a bounded 16-job backlog. A
  full queue records a safe failed job outcome for that PDF instead of blocking
  the staff request or accepting unbounded work.
- Candidate citation grammar requires each non-heading, nonblank line to end
  in canonical `(filename.pdf, p. N)` support for the selected PDF; foreign,
  malformed, invalid-page, and trailing-text citation lines remain unverified.
- Added V5 development migration that drops obsolete course-wide persistence
  and creates map-slot/job tables.

## Evidence

- `sh scripts/test.sh` — PASS (Platform integration tests and SoCLaaS client
  tests). PDFBox emits an expected malformed-fixture diagnostic during the
  hostile-PDF test.
- Queue coverage asserts that a fifth independent PDF remains visible as
  `queued` while four provider slots are occupied.
- Frontend build could not be completed in this sandbox after its local
  `node_modules` directory was absent; package download is blocked by DNS.
  The source was manually checked and must be rebuilt by the independent tester.

## Handoff

Next owner: tester. Verify the per-material API contract, retention behaviour,
authorization, V5 migration and the staff UI states specified in `spec.md`.
