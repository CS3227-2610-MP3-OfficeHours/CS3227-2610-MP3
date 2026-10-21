# Per-material Knowledge Maps — independent test report

Status: Needs changes

Date: 2026-10-21

## Evidence run

- `sh scripts/test.sh` — PASS. `PlatformIntegrationTest` and
  `SoCLaaSClientTest` pass. PDFBox logs an expected malformed-PDF fixture
  diagnostic during the hostile-upload assertion.
- `npm run build` in `src/frontend` — not run: this test environment has no
  `npm`/Node runtime (`zsh: command not found: npm`). This is an environment
  limitation, not a product result. There are also no frontend automated tests
  in the repository for this feature.

## Verified behaviour

- Material-scoped data, routes, candidate/approved slots, and V5’s removal of
  the obsolete course-wide table are implemented.
- The server checks course-staff access for listing, generation, review, and
  uses course-scoped material lookup. The server integration test covers a
  student and cross-course-staff denial for one-PDF generation.
- A one-PDF request is constructed from that PDF’s stored page text. The
  integration test checks both pages of `Week 2.pdf`, and no other source is
  passed in that fixture.
- A provider failure retains an approved map; an unverified candidate cannot
  be approved; rejecting it preserves the approved map.
- Candidate/approved output and provider messages are rendered as React text,
  and source-support links use the server-authorized resource path rather than
  a model-supplied URL.

## Blocking findings

1. **Bulk generation silently drops eligible PDFs and cannot report partial
   outcomes.** `PlatformService.generateAllKnowledgeMaps` catches every
   `ApiException` and returns only successfully submitted jobs. With the
   four-slot semaphore, a course of up to 20 eligible PDFs can return a 202
   after queueing only four jobs; skipped/busy/in-flight PDFs have no job or
   result in the response. The HTTP response has no queued/skipped counts or
   per-material outcome, and the UI always says generation was queued. This
   violates the approved bulk contract and security review requirements for
   bounded work, partial outcomes, and visibility of rate-limit/deduplication
   decisions. `PlatformIntegrationTest` tests the single-generation fifth-job
   rate limit, but has no bulk 20-PDF or partial-result test.

2. **Citation validation accepts a map when only one citation is valid.**
   `hasValidCitation` returns true on its first valid selected-PDF page. The
   approved contract requires every factual claim to be supported by the
   selected material’s current nonblank pages. A response that cites one valid
   page and then makes uncited or wrong-source claims is therefore marked
   verified and can be approved. Add claim/citation validation (or a defined
   conservative format that makes it enforceable) and fixtures for another
   filename, nonexistent/blank page, mixed valid/invalid citations, and
   prompt-injected provider output.

3. **The staff UI does not refresh asynchronous job completion.**
   `ManageMaterials` loads once after `POST` and has no polling, subscription,
   or explicit refresh action. A returned job normally remains displayed as
   queued/generating in that browser even after the server stores its candidate
   or failure, until the page/tab remounts. This prevents the required
   per-PDF completed/failed and retained-content states from reliably being
   displayed next to the source PDF.

## Required but unproven security/lifecycle evidence

- No test covers failed updates retaining an existing **candidate** (the
  existing case retains only an approved map), newer success replacing only the
  same candidate, or independent maps for two PDFs.
- No test covers bulk partial success/failure, per-material in-flight
  deduplication through the bulk endpoint, or the required 20-PDF bounded
  operation.
- No test covers map list/approve/reject endpoint denials for students,
  removed students, forged IDs, and unrelated staff; no HTTP-level test covers
  the retired legacy route.
- No test covers deletion/retry/source mutation while a job is queued or
  running. The code removes map/job state and the completion guard prevents a
  missing job from being restored, but the required race evidence is absent.
- A job stores the material digest but not a page-provenance snapshot; the
  implementation consequently cannot independently re-check the page snapshot
  specified by the security review if extraction provenance changes without a
  byte-digest change.
- No database migration test verifies V3-to-V5 behavior with PostgreSQL, and
  no frontend test covers review confirmations, skipped states, accessibility,
  or untrusted rendering.

## Re-verification required

Fix the three blocking findings, add the missing focused tests (including
authorization and in-flight deletion/source-change cases), then rerun the
server suite and a frontend build/test in an environment with Node available.

## Re-verification — 2026-10-21

Status: Needs changes

### Evidence rerun

- `sh scripts/test.sh` — PASS. `PlatformIntegrationTest` and
  `SoCLaaSClientTest` pass; the malformed-PDF fixture continues to emit its
  expected PDFBox diagnostic.
- Frontend build — attempted with the available Node 24 runtime. It cannot
  start because the local frontend dependency installation is incomplete:
  `tsc: command not found`. This is an environment/dependency issue, but it
  leaves the TypeScript/UI remediation unverified.

### Remediation confirmed

- Jobs are now created before waiting on the semaphore, so a fifth request is
  retained as a visible `queued` job instead of being rejected at submission.
  The existing server test covers that single-request case.
- `ManageMaterials` now polls while a job is queued/generating and exposes a
  **Refresh map status** control. Static review confirms the timer is cleaned
  up when no active job remains or the component changes.
- Citation validation is stricter than before: it rejects a selected-source
  citation to a non-extracted page and non-empty lines without the selected
  filename.

### Remaining blocking findings

1. **The bulk queue is not locally bounded and still has no outcome contract.**
   Moving `acquireUninterruptibly()` into the submitted task makes the
   single-thread executor accept every request into its unbounded internal
   queue; the semaphore does not bound queued work. Across courses/users a
   client can grow that backlog without a queue limit. `generateAllKnowledgeMaps`
   returns only a list of newly submitted jobs and silently omits materials
   already queued/generating; it still returns neither queued/skipped counts
   nor per-material skip reasons. The API/UI consequently always announce
   success rather than partial outcomes. This fails the approved bulk and
   security requirements.

2. **Citation validation is still not sufficient for “every claim.”**
   It accepts any non-empty line containing one valid selected-PDF citation,
   even when that same line also contains a citation to another PDF or an
   unsupported second factual claim. For example, `Claim A (l0.pdf, p. 1);
   Claim B (other.pdf, p. 1)` passes for `l0.pdf`. Add a conservative
   per-claim/citation grammar that rejects any foreign citation and associates
   every claim with exactly a validated selected-source page. Add direct tests
   for mixed valid/foreign citations, invalid page, blank page, and injected
   provider output.

3. **Required lifecycle, authorization, and persistence evidence remains
   absent.** The current integration test does not exercise bulk generation,
   two independent material maps, candidate-retention after a failed update,
   candidate replacement, source deletion while queued/running, student or
   removed-user map-list/review denial, or HTTP endpoint behavior. The
   PostgreSQL test covers only users/posts; it does not prove V5 map/job/page
   persistence or migration. These are acceptance criteria, not optional
   coverage.

The prior failure state therefore remains: do not proceed to acceptance audit
until these defects are fixed, focused tests are added, and the frontend build
passes with a complete dependency installation.

## Final remediation re-verification — 2026-10-21

Status: Needs changes

### Evidence

- `sh scripts/test.sh` — PASS. Both server test mains pass; the PDFBox error
  remains the expected malformed-fixture diagnostic.
- The frontend build was not re-run: the prior attempt established that the
  checked-out frontend dependencies do not expose `tsc`. No frontend test
  suite exists in the repository.

### Fixed implementation findings

- The executor is now bounded: four workers plus an `ArrayBlockingQueue` of
  sixteen jobs. This covers the maximum of twenty active PDFs in one bulk run
  without parking request threads behind provider capacity. A rejected enqueue
  is persisted as a safe failed job, so retained map slots are not mutated.
- Bulk generation returns a state/job for every active material, including a
  pre-existing queued/generating job rather than creating a duplicate.
- The citation grammar now requires each non-heading, non-empty line to end
  with canonical `(selected.pdf, p. N)` support and validates the cited page
  against current nonblank extracted pages. This resolves the prior simple
  “one valid citation anywhere” acceptance path.

### Remaining blockers

1. **The bulk HTTP contract remains incomplete.** The approved specification
   requires queued/skipped counts and per-material job identifiers. The route
   still returns only `{ jobs: [...] }`, with no explicit counts or skipped
   reason. Further, when executor submission is rejected, the service stores a
   failed job but returns the pre-rejection `queued` job from the individual
   route. The subsequent list endpoint will show the failure, but the `202`
   response is inaccurate and cannot be used as a reliable bulk outcome.

2. **The new queue and citation controls are untested.** The integration test
   still only exercises five individual jobs, not one 20-PDF bulk request or
   queue rejection/partial results. It has no direct fixture for strict
   canonical citations, foreign citations preceding a valid selected-source
   citation, invalid/blank pages, or injected provider output. The grammar
   can still accept a line that contains a foreign citation before a final
   valid selected-source citation, because it only requires the final
   canonical selected citation to end the line.

3. **The earlier required lifecycle, authorization, UI, and persistence
   evidence is still missing and remains blocking under the approved
   acceptance gate.** There are no tests for candidate retention after a
   failed update, per-material replacement independence, deletion/source
   change while queued/running, map list/approve/reject denials, the legacy
   route transition, or V5 map/job/page persistence. No frontend build or UI
   tests prove polling, manual refresh, partial results, confirmations, or
   accessible untrusted rendering.

The completed implementation improvements are real, but the feature does not
meet its documented independent-test gate. An acceptance audit must not start
while these defects and evidence gaps remain.
