# Staff Material Governance — Stage 4A independent verification

Status: Pass — final independent re-verification recorded, 2026-10-20
Approval: Verification evidence recorded, 2026-10-20
Last handoff: Tester → Human reviewer / next feature owner, 2026-10-20 — approved Stage 4A non-AI scope is verified; Stage 4B remains blocked on the SoC operating envelope.

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
