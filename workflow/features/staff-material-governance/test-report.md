# Staff Material Governance — independent verification

Status: Pass — final Stage 4B Knowledge Map UI re-verification recorded, 2026-10-21
Approval: The returned P1 and P2 findings are independently verified as resolved.
Last handoff: Tester → Human reviewer / acceptance auditor, 2026-10-21 — Stage 4B UI verification has passed; proceed with the required artefact-only completion review.

## Stage 4B Knowledge Map UI independent verification

**Scope:** Approved staff-only Course Knowledge Map UI in
`src/frontend/src/main.tsx` and `src/frontend/src/styles.css`. This review did
not alter implementation. It reviewed the approved `spec.md` and `design.md`,
the current uncommitted frontend diff, and the backing API/service tests.

### Executed checks

| Check | Actual result |
| --- | --- |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm --prefix src/frontend run build` | **PASS** — TypeScript and Vite production build completed. |
| `sh scripts/test.sh` | **PASS** — `Platform integration tests: PASS` and `SoCLaaS client tests: PASS`. The latter required local loopback permission for its embedded test HTTP server. |
| `git diff --check` | **PASS** — no whitespace errors. |
| Static UI/API review | **Completed** — reviewed the staff-only tab condition, panel states/actions, text rendering, source links, and service/API lifecycle tests. No browser-rendering or frontend component-test harness exists in this repository. |

### Acceptance coverage

| Required Stage 4B behavior | Evidence | Assessment |
| --- | --- | --- |
| Staff-only access and server boundary | The Manage materials tab and `ManageMaterials` render only when `user.role === "staff"`; the service tests prove students and unrelated course staff receive hidden/denied results for map generate/read. | Pass. |
| No-map state and manual all-active-source generation | The panel shows an explicit no-map message and disabled Generate control without active PDFs. Generation confirms all active PDF count and calls the manual generate endpoint. Integration tests verify the map snapshots every active source. | Pass. |
| Generating and stale states | A written `generating` status, source count, and `role="status"` message render; the panel polls while generating. The service test verifies a source change makes a map stale and discards the in-flight result. | Pass. |
| Safe failure and retry-later presentation | A server-recorded failed map renders a text status plus safe `job-failure` notice and an Update action. Provider rate-limit/unavailability failures are mapped safely by the backing service. However, a failed map **read** is caught and converted to `map = null`, so the UI says “No Knowledge Map has been generated yet” rather than surfacing a safe unavailable/retry state. | **Fail — P1.** |
| Pending-review approval/rejection | Pending maps provide labelled approve/reject controls and confirmations; backend tests prove only course staff can decide and status becomes approved. | Pass, subject to the reviewer-display defect below. |
| Decision audit presentation | The API supplies `reviewedBy` and `reviewedAt`, but the UI only renders “Decision recorded <time>.” The approved design requires server-confirmed reviewer **and** time after a decision. | **Fail — P1.** |
| Source support links | The UI derives `PDF filename — p. N` links from validated map citations and uses the re-authorized course resource download route. Service tests require valid citations for every active source before a map can be pending review. | Pass. |
| Untrusted map/filename rendering | React renders the map content, filenames, error text, and citation labels as text; no raw HTML insertion is used. Links are generated from IDs/routes, not model-provided URLs. | Pass. |

### Defects returned to engineering

1. **[P1] Map-read failures are misrepresented as an empty map.**
   In `ManageMaterials.load`, the nested `GET /knowledge-map` catch sets the
   map to `null` for every error. That is appropriate only for the documented
   no-map response; a transient/network/5xx response is thereby hidden as
   “No Knowledge Map has been generated yet.” Preserve the last safely loaded
   map where possible and show the approved safe unavailable/retry-later state
   for a failed read.

2. **[P1] The UI omits the reviewer identity after approve/reject.**
   The API already returns `reviewedBy`; `KnowledgeMapPanel` displays only
   `reviewedAt`. Display the server-confirmed reviewer and decision time in the
   read-only approved/rejected summary, as required by the approved design.

### Verdict and next handoff

**Needs changes.** The two P1 UI defects above prevent a pass because they
leave a required safe failure state and an explicit review-audit field
unsatisfied. Engineering should address only these findings, add focused UI
coverage if a harness is introduced, run the build and relevant suite, then
return the feature for independent re-verification. The Stage 4A evidence
below remains historical and unchanged.

## Stage 4B targeted re-verification

This re-verification reviewed only the two named P1 findings returned to
engineering and the directly affected refresh behavior. It did not change
implementation.

| Re-check | Actual result |
| --- | --- |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm --prefix src/frontend run build` | **PASS** — TypeScript and Vite production build completed. |
| `sh scripts/test.sh` | **PASS** — `Platform integration tests: PASS` and `SoCLaaS client tests: PASS`; the latter ran with test-only local loopback permission. |
| `git diff --check` | **PASS** — no whitespace errors. |
| Targeted source/API review | **Completed** — verified status-carrying API errors, 404-only no-map handling, unavailable/retry rendering, and reviewer/time rendering from the existing response fields. |

### Returned-finding results

1. **Resolved — map-read errors are no longer presented as no map.** `api()`
   now attaches the HTTP status to its thrown error. The map load treats only
   a `404` as no map; all other failures set `mapError`. `KnowledgeMapPanel`
   then renders the text-labelled **Knowledge Map unavailable** alert plus
   **Try again**, rather than the no-map message.

2. **Resolved — decision audit identity is displayed.** The decision summary
   now renders `Decision recorded by {reviewedBy} on {reviewedAt}`. The
   existing API response already supplies both server-recorded fields.

### Remaining defect

1. **[P2] A failed map refresh discards safely loaded map content.** In the
   non-404 map-read catch branch, `setMap(null)` runs before the unavailable
   state is shown. Consequently, a staff member who has already loaded an
   approved/pending map loses its safely loaded review context on a transient
   refresh failure. The approved design requires network failure to preserve
   safe loaded data where possible. Retain the prior `map` while displaying
   the safe unavailable/retry notice; clear it only for the confirmed no-map
   (`404`) condition or course change.

**Re-verification verdict: Needs changes.** The two original P1s are closed,
but this P2 leaves the required network-failure behavior incomplete. After the
small state-handling correction, rerun the frontend build and return the UI
for one further independent re-verification.

## Stage 4B final P2 re-verification

| Re-check | Actual result |
| --- | --- |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm --prefix src/frontend run build` | **PASS** — TypeScript and Vite production build completed. |
| `git diff --check` | **PASS** — no whitespace errors. |
| Targeted refresh-state and rendered-branch review | **Completed** — reviewed `ManageMaterials.load` and `KnowledgeMapPanel` together. |

The state-handling part of the P2 correction is present: a non-404 map read
failure no longer calls `setMap(null)`, while a confirmed `404` still clears
the map. However, the UI’s top-level panel branch remains:

```tsx
mapError ? <unavailable notice> : !map ? <no-map state> : <map panel>
```

Therefore any `mapError` hides the retained map panel, its content, source
support, status, and review context. This does not satisfy the approved
network-failure requirement to preserve safely loaded data *in the UI* where
possible. Render the unavailable/retry notice alongside (rather than instead
of) the existing map panel when `map` is present; retain the error-only panel
for cases with no prior map.

**Final P2 re-verification verdict: Needs changes.** The implementation is
type-safe and built successfully, but the P2 is not fully resolved in the
visible staff flow.

## Final Stage 4B P2 presentation re-verification

| Re-check | Actual result |
| --- | --- |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm --prefix src/frontend run build` | **PASS** — TypeScript and Vite production build completed. |
| `git diff --check` | **PASS** — no whitespace errors. |
| Targeted rendered-state review | **PASS** — `mapError` independently renders the safe unavailable/`Try again` notice; a retained map independently renders the map panel below it; the no-map text renders only when both `mapError` and `map` are absent. |

The non-404 map-read path retains the current map and records the safe error.
The panel now renders the failure notice without using it as an exclusive
branch, then renders the retained status, content, sources, and review context
when a map exists. A confirmed `404` remains the sole map-read outcome that
clears the map and shows the genuine no-map state. This satisfies the approved
network-failure requirement to preserve safely loaded data where possible.

**Final Stage 4B verification verdict: Pass.** All previously named P1/P2
findings are resolved. The earlier recorded full service/client suite result
remains applicable because this final remediation only changes frontend
conditional rendering; the final frontend build and diff validation above were
run against this exact source revision.

## Re-verification update

This update supersedes the prior assessment of the two reported blockers.

| Re-check | Actual result |
| --- | --- |
| `sh scripts/test.sh` | **PASS** — now covers simple malformed-PDF rejection, processing-to-active, processing-to-failed, failed Resources exclusion, and manual retry, in addition to authorization/deletion cases. |
| `sh scripts/test-postgres.sh` | **PASS** — `PostgreSQL platform integration tests: PASS`; V2 migration still applies (but material persistence itself is not covered). |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm --prefix frontend run build` | **PASS** — TypeScript/Vite production build completed. |
| `git diff --check` | **PASS** — no whitespace errors. |
| Direct lifecycle/rate probe | **PASS** — a non-text PDF reaches `failed`; Resources remains empty; three retries are accepted after failure and the fourth is rejected with the safe retry-limit message. |
| Direct malformed-PDF probe | **FAIL** — the original trivial fixture is now rejected, but a catalog that references nonexistent `/Pages 99 0 R` is accepted as `processing`. |

The local JShell runner reports a macOS preferences-history flush error only at
exit; its displayed service-call results above completed first and are the
evidence recorded here.

### Updated assessment of the two blockers

The processing/failed/retry blocker is resolved: accepted material begins in
`processing`, a non-extractable PDF fails safely, failed material is excluded
from Resources, staff can retry, and the fourth retry in one minute is denied.
The UI now displays failed state/failure text and a Retry action.

The malformed-PDF blocker is only partially resolved. The structural gate
rejects the earlier header/trailer-only fixture, but it does not validate PDF
references or a page tree. The following malformed input is accepted because
the separately present `/Page` object and catalog marker satisfy the heuristic
even though its `/Pages` target does not exist:

```text
%PDF-1.4
1 0 obj
<</Type /Catalog /Pages 99 0 R>>
endobj
2 0 obj
<</Type /Page /Parent 99 0 R>>
endobj
trailer
<</Root 1 0 R>>
%%EOF
```

This violates acceptance criterion 3: malformed PDFs must be rejected before
they become processing/active material. Replace this heuristic with a bounded
real PDF parser/validator (or demonstrably equivalent validation) and add
broken-reference, damaged-xref/trailer, and invalid-page-tree regression
tests. The revised test report remains **Needs changes** for this P1 defect.

## Final focused re-verification

The reported dangling-`/Pages` bypass is fixed. The automated integration test
now submits that exact payload and passes; an independent direct service probe
also returned the safe validation error before a material was created.

The same probe reconfirmed the lifecycle boundary: a non-text document reaches
`failed`, `listResources` returns zero resources for it, three retries are
accepted after each failure, and the fourth produces the safe one-minute
retry-limit response. `sh scripts/test.sh`, `git diff --check`, and the React
production build passed again.

This closes the specific bypass, but not the broader acceptance criterion for
actual PDF parseability. The implementation remains an explicitly
dependency-free regex/marker heuristic: it neither parses nor validates PDF
xref/startxref structure, and its own accepted test fixture omits that
structure. It therefore cannot demonstrate rejection of malformed PDF syntax
as required by criterion 3. Per the verification procedure, a full **Pass**
requires evidence for every required criterion, so the final assessment stays
**Needs changes** pending a real bounded parser/validator (or equivalent
evidence) and focused malformed-structure tests.

## Final re-verification — bounded classic-xref parser

The bounded classic-xref validation closes the preceding parseability evidence
gap for its supported subset. The integration test and a direct service probe
both confirmed that:

- a supported classic-xref PDF is accepted into `processing`;
- missing xref/startxref data is rejected before job creation;
- an invalid `startxref` offset is rejected before job creation; and
- the previously reported dangling `/Pages` reference is rejected before job
  creation.

The direct probe also reconfirmed that a failed local-processing item is absent
from Resources and that a fourth retry in one minute receives the safe
rate-limit response. `sh scripts/test.sh`, `sh scripts/test-postgres.sh`, the
React production build, and `git diff --check` all passed in this final run.

The docs accurately disclose that xref-stream PDFs are rejected. That
rejection is safe, but it is **not** within the presently approved product
scope: the approved specification promises an authorized staff member can
upload a valid, parseable, unprotected PDF within the limits and does not
exclude the standardized xref-stream PDF form. Documentation cannot narrow
that approved requirement. Either extend validation to accept xref-stream
PDFs, or obtain product-owner approval to limit Stage 4A uploads to the
documented classic-xref subset. Until one of those occurs, the feature cannot
receive a full pass.

## Final re-verification — xref-stream support

This update supersedes the preceding classic-xref limitation. The bounded
validator now accepts direct xref streams with unfiltered or safely capped
`FlateDecode` entry data, and the previously missing format support is covered
by the independent test run.

| Check | Actual result |
| --- | --- |
| `sh scripts/test.sh` | **PASS** — covers a valid Flate xref-stream PDF through `processing` to `active`; a corrupt xref-stream object offset, missing xref/startxref, dangling page tree, non-PDF, and encrypted PDFs are rejected before a job is created. It also retains staff/student/cross-course authorization, deletion, Resources filtering, failed processing, and manual retry coverage. |
| Direct/reviewed parser boundary | **PASS** — classic-xref PDFs remain supported; xref-stream object offsets are matched to parsed objects; invalid xref streams fail closed. `FlateDecode` expansion is capped at 1 MiB and unsupported filters/object-stream entry types are safely rejected. |
| Lifecycle/retry guardrail | **PASS** — current suite confirms active/failed transitions, failed-resource exclusion, and manual retry. The earlier independent rate probe remains applicable because this change does not alter retry code: three retries are accepted and a fourth in one minute is denied. |
| `sh scripts/test-postgres.sh` | **PASS** — PostgreSQL migration/persistence regression suite passed. |
| React production build and `git diff --check` | **PASS** — TypeScript/Vite build and whitespace check passed. |

The approved requirement is met by the supported valid PDF forms, including
the newly supported Flate xref stream. Other unsafe/unsupported parser forms
fail safely rather than becoming a material job; this is consistent with the
approved malformed/unsupported-upload safeguard and does not add an AI or
retrieval path.

**Final assessment: Pass for approved Stage 4A non-AI scope.** No new blocking
defect was found. Known test limits remain: the PostgreSQL suite does not yet
restart with a stored material, and automated boundary tests for 20 MiB,
100-page, and 21-item capacity would strengthen future release evidence.

## Scope and inputs

**Feature:** `staff-material-governance`, approved Stage 4A non-AI scope only.

**Working tree:** uncommitted Stage 4A implementation, migration, frontend,
tests, contract documentation, approved `spec.md`/`design.md`, implementation
plan, and `tasks.md` as present on 2026-10-20. This verification changed no
implementation source and made no commit or push.

**Reviewed:** `AGENTS.md`, `workflow/project-overview.md`,
`workflow/development-process.md`, the approved staff specification/design,
the Stage 4A plan, engineering handoff, implementation diff, migration, API,
service/persistence code, React UI, API documentation, and automated tests.

## Executed checks

| Check | Actual result |
| --- | --- |
| `sh scripts/test.sh` | **PASS** — `Platform integration tests: PASS`. This includes service-level valid-PDF upload, declared/renamed/encrypted rejection, staff/student authorization, cross-course denial, deletion, and Resources removal/download denial. |
| `sh scripts/test-postgres.sh` | **PASS** — `PostgreSQL platform integration tests: PASS`. The V2 migration applies successfully, but this test does not create or reload a course material. |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm --prefix frontend run build` | **PASS** — TypeScript and Vite production build completed. |
| `git diff --check` | **PASS** — no whitespace errors. |
| Direct service validation probe using JShell | **FAIL** — `%PDF-1.4 this is not a PDF structure %%EOF` uploaded as `application/pdf` returns material status `active`. The JShell session subsequently reported a local preferences-flush error, but the displayed service result is unambiguous. |

## Criteria coverage

| Approved Stage 4A criterion | Evidence | Assessment |
| --- | --- | --- |
| Non-AI boundary: no SoC, embeddings, RAG, consolidation, or generated artefacts | Static review of routes, service, client, and contract finds only material/Resources operations; UI explicitly says consolidation is unavailable. | Pass. |
| Server-side staff/course authorization for upload, list, delete, retry; generic denial | `PlatformService` checks membership before every operation; tests cover student upload/list denial and unrelated-staff delete denial. | Pass for covered operations. |
| Resources list/download are re-authorized and expose active PDFs only | Source review: list/download call `requireCourseMember`; download repeats material course/status checks and returns bytes with no storage URL. Tests cover enrolled download, cross-course denial, deletion, and stale download denial. | Pass for covered operations. |
| Valid PDF upload constraints and safe lifecycle | 20 MiB, declared content type, header/trailer, `/Encrypt`, and marker-count checks exist; accepted materials become active synchronously. However malformed input is accepted as active. | **Fail — blocking.** |
| Malformed, non-PDF, encrypted, oversized, and over-100-page uploads are rejected before active | Tests cover declared non-PDF, renamed non-PDF, and encryption. Direct probe proves malformed bytes are accepted; no automated size/page-limit test exists. Page counting is a text-marker heuristic, not parseability. | **Fail — blocking.** |
| 20-active-PDF capacity and deletion freeing capacity | Capacity logic counts active/processing states and deletion hides/revokes a resource immediately. No test exercises the 21st upload or upload after deletion. | Needs additional evidence. |
| Staff-visible processing/failed/retry states and three-per-minute retry guardrail | Domain/API model statuses and `429 retry_limited`, but upload has no processing job/failure path; React renders neither failed-state error/retry affordance nor retry-limited state. No test creates a failed material or tests three retries. | Not satisfied / not evidenced. |
| Approved Resources and Manage-materials UI | Production build passes; Resources list/download, upload, active count, delete confirmation, and unavailable-AI text are implemented. But it does not implement the approved retry/failure states, and uses a browser confirm rather than the designed dialog/focus flow. | Needs changes for required lifecycle states. |
| Filename/content remain untrusted and storage paths are private | Filename is returned as text and never used as a server filesystem path; download uses a re-authorized API endpoint with `no-store`/`nosniff`. | Pass by static review for this Stage 4A boundary. |

## Defects

1. **[P1] Malformed PDF is accepted and becomes active.**
   `validateMaterial` treats a leading `%PDF-`, any `%%EOF`, and absence of
   `/Encrypt` as sufficient. It does not parse the document. This violates
   acceptance criterion 3 and permits arbitrary non-PDF content to be made
   available in Resources and future retrieval.

   Reproduction from the repository root after `sh scripts/test.sh`:

   ```sh
   jshell --execution local --class-path build/classes
   ```

   Then create a staff user/course and call `uploadMaterial` with
   `"%PDF-1.4 this is not a PDF structure %%EOF".getBytes(ISO_8859_1)` and
   content type `application/pdf`; the returned status is `active`.

   Required fix: use a real PDF parser or a bounded, reliable validation path
   that rejects malformed documents before storing/activating them; test this
   exact negative case and the page-limit behaviour.

2. **[P1] Required non-AI failure/retry lifecycle is not delivered.**
   The approved Stage 4A plan/design require processing/failure visibility and
   retry controls. The implementation always transitions accepted input to
   `active`; no failed state can be produced. Although a server retry route is
   reserved, the React UI has no retry button, error notice, or rate-limited
   presentation, and no test proves the three-per-minute guardrail.

   Required fix: either implement the approved non-AI processing-failure and
   retry flow end-to-end (including server tests and UI states), or return the
   scope/design to the product owner for an explicit approved deferral. Do not
   mark it complete merely because future processing is planned.

## Verification limits and follow-up evidence required

- The persistence test proves that V2 migration can apply, but not material
  persistence across restart, deletion persistence, or authorization after a
  membership revocation between Resources listing and direct download.
- Add direct tests for 20 MiB boundary, real 100-page boundary, 21st active
  upload, delete-then-reupload, malformed PDF rejection, failed/processing
  exclusion from Resources, retry state/rate limit, and revocation after list
  but before download.
- Browser interaction/accessibility was source-reviewed and production-built,
  not exercised in a browser session. Reverify delete dialog/focus, loading,
  and error states after the lifecycle UI exists.

## Final assessment and next handoff

**Pass (final xref-stream re-verification).** The failure/retry lifecycle,
classic and supported Flate xref-stream validation, malformed/missing/invalid
xref rejection, server authorization, Resources filtering, and non-AI
boundary have independent evidence. Stage 4A may proceed to human review.
Stage 4B remains blocked on the approved SoC operating envelope and is outside
this report.

## Staff dashboard addendum independent verification — 2026-10-21

**Scope:** Approved addendum acceptance criteria 15–20, independently reviewed
on branch `xp/staff-features`. Source, test, and security-review inspection was
read-only. Only this report was updated.

### Commands and actual results

| Check | Result |
| --- | --- |
| `sh scripts/test.sh` | Platform integration tests: **PASS**. The fixture intentionally emits a PDFBox malformed-PDF diagnostic. The subsequent SoCLaaS client test did not run to completion: its local fake HTTP gateway failed to bind with `java.net.SocketException: Operation not permitted`. |
| `cd src/frontend && npm run build` | **NOT VERIFIED**: TypeScript build stopped because `tsc` is unavailable (`sh: tsc: command not found`). No dependencies were installed. |
| `git diff --check` | **PASS**, no whitespace errors. |
| Browser interaction checks | **NOT RUN**: no running browser/application session or installed frontend build executable was available in this verification environment. |

### Criteria coverage

| Criterion | Evidence and result |
| --- | --- |
| 15 — staff Resources upload action and overlay | **Source-reviewed; partial.** `CourseWorkspace` passes the authenticated role into `Resources`; `Upload file` is rendered only when `staff` is true. The centered dialog has a labelled file input, PDF/capacity guidance, close/cancel controls, Escape handling, focus initialization/restoration, and a Tab boundary. Upload errors remain inline and the dialog remains mounted when the upload request rejects. `PlatformIntegrationTest` rejects a student upload and verifies staff upload flows, but no browser interaction was possible. Server authorization is enforced by `requireCourseStaff` before upload validation in `PlatformService.uploadMaterial`. |
| 16 — Knowledge Map name and feature retention | **Source-reviewed / integration evidence.** The staff tab and workspace heading read `Knowledge Map`; source rows, deletion, retry, per-material generation, candidate review, and approve/reject actions remain. Existing integration tests cover staff/student/cross-course denials, generation, approval, rejection, and retaining candidates after failed updates. |
| 17 — no separate Source support links | **Source-reviewed.** Returned map rendering contains the map text and review controls and no Source support link list. Textual citations remain in the generated content. No browser-rendered check was possible. |
| 18 — map viewport and scrolling | **Source-reviewed.** `.map-content` has `max-height: 70vh`, `overflow-y: auto`, a labelled `role="region"`, `tabIndex={0}`, and a visible focus style. Surrounding review actions are outside the content region. Rendered scrolling/keyboard operation was not exercised. |
| 19 — specific diagnostics and approval block | **Integration suite PASS for fixtures.** Tests cover missing citation, malformed citation syntax, foreign source filename, invalid/textless page, trailing punctuation, and the supplied Markdown/bulleted sample shape. Each generated unverified candidate remains approval-ineligible (422). The implementation falls back to the generic message only when content/source is unavailable or no specific reason is collected. **A blocker remains:** the server's `isPlainTopicHeading` heuristic skips any short line (up to 80 characters) whose words all begin uppercase. A factual claim such as `The Course Is Definitely About Nothing` is therefore exempted as a heading and receives no missing-citation diagnostic. With another valid cited claim in the same map, this line can coexist with a candidate that has no verification message and is approvable. The integration suite has no negative fixture for this bypass. |
| 20 — generation prompt and supplied example | **Source-reviewed / partial test evidence.** The prompt requires unformatted plain headings, one claim per line, exact filename/page citation syntax, citations as final line characters, and no punctuation after them; it also prohibits invented citations and requires every text-bearing source to be cited. `SoCLaaSClientTest` contains assertions for these prompt fragments, but the test could not run because loopback binding was denied. `PlatformIntegrationTest` feeds a Markdown/bulleted, trailing-period sample and confirms format/end-of-line reasons and approval denial. It does not establish that generation will repair the sample; the verifier correctly leaves malformed output unverified. |

### Blocking defect

1. **[P1] Citationless factual lines can be mistaken for headings and bypass
   validation.** `PlatformService.isPlainTopicHeading` accepts title-case lines
   based only on capitalization and length. Reproduce with a candidate containing
   a valid cited claim plus `The Course Is Definitely About Nothing` on its own
   line. That line is skipped, so the candidate may have no verification
   message and the normal staff approval path may accept it. Do not infer that
   every capitalized line is a heading. Require an explicit unambiguous heading
   format or conservatively require citations for every nonblank line that is
   not reliably identifiable as a topic heading; add a regression test proving
   the factual line is diagnosed and cannot be approved.

No blocker was confirmed for the possible false “non-canonical format” reason
on a correctly formatted foreign filename citation: inspection shows the
canonical matcher accepts any `.pdf` filename, while the separate comparison
reports the filename mismatch.

### Assessment and handoff

**Needs changes.** Criteria 15–18 have source-level evidence but no live UI
verification; criterion 20's prompt assertions could not execute. More
decisively, criterion 19's missing-citation guarantee is bypassable by the
title-case heuristic, so the approval gate is not reliable for all claim lines.
Return the named P1 to engineering, then re-run `sh scripts/test.sh` in an
environment that permits the SoCLaaS fake gateway's loopback bind, build the
frontend where `tsc` is available, and perform focused browser checks for
overlay role visibility/focus/dismissal/upload errors and map scrolling before
acceptance.

## Remediation re-verification — 2026-10-21

**Scope:** Remediation cycle 1 for the P1 title-case citation bypass, followed
by a focused review of all addendum criteria 15–20. No implementation changes
were made; this section is the only additional report content.

### Retest results

| Check | Result |
| --- | --- |
| `sh scripts/test.sh` | Platform integration tests: **PASS**, including the new regression case. The subsequent SoCLaaS client test again could not bind its loopback fake gateway (`java.net.SocketException: Operation not permitted`), so the script did not finish successfully. |
| `cd src/frontend && npm run build` | **NOT VERIFIED**: `tsc` is not installed in the existing frontend dependencies (`sh: tsc: command not found`). No packages were installed. |
| `git diff --check` | **PASS**. |
| Browser checks | **NOT RUN**: no frontend build executable or active browser session was available. |

### P1 re-verification

The server no longer exempts title-case/plain heading lines. In
`citationVerificationMessage`, every nonblank line is evaluated for citation
syntax, source filename, page validity, and citation-at-line-end; a line with no
citation receives “A claim is missing a source citation.” The integration
regression generates a candidate containing a valid cited claim followed by
`The Course Is Definitely About Nothing`, asserts that specific missing-citation
diagnostic, and asserts that `approveKnowledgeMap` returns 422. The full
Platform integration suite passed, providing actual evidence for both
assertions. The SoCLaaS prompt now also asks for citations on every nonblank
line, including topic headings.

**P1 status: resolved.** No citationless title-case line can bypass the
validator through the former heading heuristic.

### Criteria 15–20 focused review

| Criterion | Re-verification result |
| --- | --- |
| 15 — staff-only Resources upload action and overlay | Source review remains consistent: the authenticated role gates the action; the dialog includes existing PDF controls/guidance, close and Escape behavior, and keeps request errors in the dialog. Service authorization still requires course staff, and integration coverage denies student upload. Actual rendered role, keyboard/focus, dismissal, and upload-error interactions remain unverified without a browser session. |
| 16 — Knowledge Map naming and retained lifecycle | Source review confirms the tab/workspace name and retained source lifecycle, generation, retry, candidate review, and approval/rejection controls. Existing integration tests pass for upload lifecycle, authorization, generation, approval/rejection, and failed-update retention. |
| 17 — source support links | Source review confirms no separate clickable Source support link list under returned maps; generated text and citations remain visible. No rendered browser check. |
| 18 — viewport and scroll behavior | CSS and accessible markup remain `max-height: 70vh`, internal vertical overflow, labelled focusable region, and visible focus treatment; review actions remain outside the map content region. Actual long-content keyboard scrolling remains unverified. |
| 19 — diagnostics and approval block | Platform integration tests pass for missing citation, noncanonical syntax, mismatched filename, invalid/textless page, trailing punctuation, and the supplied Markdown/bulleted example. The new title-case regression also passes and the approval call is denied with 422. Unclassified-output fallback remains in place. |
| 20 — prompt and supplied example | Prompt source includes unformatted plain headings, one claim per line, canonical source/page pairs, citations on every nonblank line including headings, citation as each line's final characters, and no trailing punctuation; it prohibits invented citations and requires supplied PDFs to be cited. The integration fixture for the reported malformed Markdown/bulleted/trailing-period example remains unverified and unapprovable. Prompt assertions exist in `SoCLaaSClientTest`, but could not be executed because the loopback fake gateway could not bind. |

### Assessment and handoff

**Needs changes for completion evidence.** The P1 is resolved and the
server-side regression passes. Criteria 15–18 still have only source-level UI
evidence, and criterion 20's prompt assertions did not execute. The full script
also remains incomplete because the environment blocks its local fake gateway.
Obtain browser evidence for the overlay and long-map interactions, a frontend
build in an environment with `tsc`, and a SoCLaaS client test run in an
environment that permits loopback binding before marking the addendum Pass.

## UI and build evidence follow-up — 2026-10-21

The orchestrator performed these additional checks against the temporary local
Vite frontend and in-memory Java backend:

- Signed into the seeded staff account and observed **Upload file** in
  Resources; opened the modal and confirmed focus starts on **Choose PDF**.
- Pressed Escape; the dialog closed and focus returned to **Upload file**.
- Confirmed Tab wraps from **Cancel** to **Close** inside the dialog.
- Signed into the seeded student account; its course tabs were **Forum** and
  **Resources** only, and Resources did not show **Upload file**.
- No file was submitted. Upload error/validation interaction was not exercised.
- The browser remained on student Resources. Returned-map rendering/scrolling
  was not browser-tested because producing a map fixture requires PDF upload and
  a provider-backed generation path.

I independently compared the temporary frontend's `src/main.tsx` and
`src/styles.css` byte-for-byte with the current workspace files; both matched.
Then ran the requested build from that checkout:

```sh
cd /private/tmp/staff-features-frontend-verification
PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm run build
```

**Result: PASS.** `tsc -b && vite build` completed; Vite transformed 28
modules and produced the production bundle. `git diff --check` for the report
also passes.

**Updated assessment: Needs changes for remaining evidence.** The browser
checks now verify staff/student upload-control visibility and the dialog's
initial focus, Escape dismissal/focus restoration, and one Tab-wrap boundary.
The temporary build passes against current frontend source. The title-case
citation bypass remains resolved by the passing Platform integration suite.
Still unverified in a browser are upload validation/error recovery and the
returned Knowledge Map's rendered 70vh scrollbar/keyboard scrolling; the
SoCLaaS client assertions still cannot run in this sandbox because its fake
gateway cannot bind loopback. Keep the overall assessment at Needs changes
until the map viewport behavior and remaining relevant execution evidence are
covered.

## Approved layout follow-up verification — criteria 21–22 — 2026-10-22

**Scope:** Focused independent review of the approved Resources/Knowledge Map
layout follow-up. Reviewed the updated spec/design, current implementation diff,
server authorization path, and relevant integration tests. No implementation
changes were made.

### Criterion 21 — Resources delete controls

**Source review: conforms.** The Resources row renders **Download PDF** followed
immediately by **Delete file** in the same flex action group. Delete is inside
the `staff &&` conditional and uses the red `danger` style; student markup has
only the download action. Activating it invokes a confirmation that names the
filename and states that deletion immediately removes the item from Resources
and new AI requests and cannot be undone. Confirmation submits the existing
course-scoped DELETE route, refreshes Resources, and reports success. The
server's `deleteMaterial` requires course staff before looking up/deleting the
material.

`PlatformIntegrationTest` verifies that an unassigned staff actor cannot delete
a cross-course material, that an authorized staff deletion succeeds, that the
deleted PDF immediately disappears from an enrolled student's Resources list,
and that its download is denied afterward. The test source does not include a
direct student `deleteMaterial` call; the server-side staff-role guard is
visible in `PlatformService.deleteMaterial`. I did not perform a new browser
check of current staff/student row rendering or the confirmation interaction.

### Criterion 22 — Knowledge Map workspace

**Source review: conforms.** The staff tab mounts `KnowledgeMapWorkspace`, which
loads map state and renders the `KnowledgeMapsByPdf` Course Knowledge Map
section; it no longer loads or renders a separate material lifecycle list.
Generation/update, refresh, status, candidate, approve/reject, and failure
controls remain in the existing map panel components. The “AI-generated
supporting navigation material” tagline is absent. The replacement paragraph
explains topic organization, student AI search for relevant material and
supporting PDF pages, retrieval guidance rather than source replacement,
staff review/approval, and original-source citations in student answers.

### Checks and assessment

- Confirmed `/private/tmp/staff-features-frontend-verification/src/main.tsx`
  and `src/styles.css` match the current repository frontend files.
- `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm run build` in the
  temporary checkout — **PASS** (`tsc -b && vite build`; 28 modules transformed).
- The engineer's current-branch handoff records `sh scripts/test.sh` as
  **PASS** for Platform integration and SoCLaaS client tests. My attempt in
  this sandbox passed Platform integration but the client test could not bind
  its fake loopback gateway (`Operation not permitted`); this environment
  therefore did not independently reproduce the full-suite pass.
- No current browser check was completed for the new delete button visibility,
  its confirmation/cancel/success flow, or the revised Knowledge Map content.
  Earlier browser checks verified the upload action/modal and staff/student
  tabs before this follow-up implementation; they do not establish the new
  delete or Knowledge Map rendering behavior. Returned-map 70vh scrolling and
  keyboard scrolling also remain unverified in a rendered browser session.

**Focused verdict: Needs changes for UI evidence.** The implementation and
available server tests support criteria 21–22, with no source-level defect
identified. Close the remaining verification gap with staff and student
Resources browser checks, a staff delete-confirmation/cancel/success check, and
a Knowledge Map browser inspection. A temporary DOM fixture can verify the
viewport scroll CSS without uploading a course file; that fixture was not run
in this verification turn. Keep the overall addendum assessment at Needs
changes until those UI behaviors are exercised.

## Orchestrator test execution follow-up — 2026-10-21

The orchestrator reran `sh scripts/test.sh` on the current branch with local
loopback access enabled. **Both Platform integration tests and SoCLaaS client
tests passed.** The SoCLaaS prompt assertions therefore have executed against
the current source; the earlier loopback limitation is resolved. The
frontend build also passed in the temporary checkout described above, whose
`main.tsx` and `styles.css` were confirmed byte-for-byte identical to the
workspace files. `git diff --check` passes.

**Overall verification remains Needs changes for browser evidence only.**
Upload-error recovery and rendered long-map keyboard scrolling have not been
exercised in the browser. The implementation and build evidence are present,
but the report does not claim those interactions were observed.

## Approval and status-control follow-up verification — criteria 23–26 — 2026-10-22

**Scope:** Independent focused verification of the approved immediate map
approval and removal of the manual status-refresh control. Reviewed the current
specification, approved design, implementation, and existing integration-test
coverage. No implementation changes were made.

### Checks and actual results

| Check | Result |
| --- | --- |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm run build` from `src/frontend` | **PASS**. `tsc -b` and Vite production build completed; Vite transformed 28 modules. |
| `sh scripts/test.sh` | **PARTIAL**. `PlatformIntegrationTest` passed, including map approval authorization and source/page verification cases. `HttpApiIntegrationTest` could not start because the sandbox denied binding its local fake HTTP server (`java.net.SocketException: Operation not permitted`); the script stopped before `SoCLaaSClientTest`. |
| `git diff --check` | **PASS** at time of check. |
| Interactive browser check | **NOT RUN**. Browser inventory showed no open tabs; no app/dev server was running. Running the application would require starting/configuring services, outside this no-setup verification. |
| Frontend interaction tests | **NOT AVAILABLE**. No frontend test/spec files or test script are present in `src/frontend/package.json`. |

### Criteria coverage

| Criterion | Evidence and result |
| --- | --- |
| 23 — immediate approval | **Source-reviewed; browser interaction unverified.** `review()` prompts only when `decision === "reject"`; activating approval proceeds directly to the course/material-scoped POST. `setMapBusy(materialId)` disables both review buttons and changes their label to “Saving review…” while the request is pending. It only reports approval after the POST resolves, calls `load()` for refreshed status, and retains the prior UI plus safe error reporting when the POST fails. No approval confirmation string/dialog remains in the review path. A rendered click, duplicate-click, and failure-state check was unavailable. |
| 24 — status refresh control and refresh behavior | **Source-reviewed; browser interaction unverified.** `KnowledgeMapWorkspace` calls `load()` in its initial effect and after generate/update and review actions. Its existing poll refreshes queued/generating jobs. `KnowledgeMapsByPdf` no longer accepts or renders an `onRefresh` control; its heading action area still contains **Generate all eligible maps**. A case-insensitive source search found no “Refresh Map status” control. |
| 25 — rejection confirmation and eligibility/server rules | **Partial pass.** The rejection confirmation text and cancel-before-submit branch remain. `MaterialMap` only renders approval when `candidate.verificationMessage` is absent; `busy` disables both review controls. The passing `PlatformIntegrationTest` covers authorized approval, student/cross-course denials, and rejection of unverified/missing, foreign, stale, and invalid-page citation candidates. This run could not reach HTTP API tests because local socket binding was denied. UI confirmation/cancel behavior and safe failure feedback were source-reviewed, not exercised in a browser. |
| 26 — student authorization and original-PDF citations | **Contract-preservation review only.** This change only alters staff review controls. The map section continues to say student answers cite original PDFs and pages, and the server-side approval/currentness/source verification logic was not changed. Existing platform tests confirm approval is blocked for invalid or stale source/page support and unauthorized actors. This repository has no student AI answer-generation test or flow in the inspected test set, so this run does not independently demonstrate answer citation output or student retrieval authorization end to end. |

### Assessment and handoff

**Focused verdict: Needs changes for independent UI verification.** Source review
and the production build support the requested interaction, and the passing
platform suite supports the server-side approval boundary. There is no
source-level defect identified for criteria 23–25. However, the acceptance
criteria explicitly call for interactive checks of approval busy/success/error
states, rejection confirmation, and load/action refresh, and those could not be
exercised without starting the app and its backend. Criterion 26 also remains
an unchanged contract rather than end-to-end student answer evidence. Re-run
the focused checks in a running test app/browser and capture student citation
behavior when the student AI flow is available before treating this follow-up as
independently verified end to end.

### Orchestrator test execution follow-up — 2026-10-22

The orchestrator reran `sh scripts/test.sh` with approved loopback access after
the sandbox restriction encountered during my run. **Platform integration,
HTTP API integration, and SoCLaaS client tests all passed.** This supersedes the
partial execution result above for the full test script; its description is
retained as the actual result of my independent run.

**Updated focused verdict: Needs changes for browser interaction evidence
only.** The full automated suite now passes. The remaining verification gap is
that immediate approval, its busy/success/error states, rejection confirmation,
and page/action refresh behavior were not exercised in a running browser.
