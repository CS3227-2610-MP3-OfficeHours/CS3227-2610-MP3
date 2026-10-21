# Stage 3 staff Knowledge Map foundation — independent test report

Status: Pass
Reviewed: 2026-10-21
Tester scope: Approved Stage 3 server-side staff Knowledge Map foundation only.
Excluded by approved scope: student AI behavior and endpoint, the later staff
UI flow, and the separate production/Railway release implementation. This
report does not mark those later feature/release obligations complete.

Re-verification: 2026-10-21 — acceptance-audit remediation coverage and the
explicit hostile-PDF fixture were independently rerun. The expanded platform
and adapter suites pass.

## Inputs reviewed

- `AGENTS.md`, `workflow/project-overview.md`, and
  `workflow/development-process.md`.
- Approved `workflow/features/ai-rag-release/spec.md`, engineering handoff in
  `tasks.md`, and `workflow/security/ai-rag-stage3-foundation-review.md`.
- The changed Java service, HTTP routes, SoCLaaS adapter, V3 migration, local
  launch scripts, and test sources. The implementation was not modified during
  this review.

## Commands and actual results

| Command | Result |
| --- | --- |
| `sh scripts/test.sh` with permission for its isolated loopback fake gateway | **PASS** — `Platform integration tests: PASS`; `SoCLaaS client tests: PASS`. This run includes cross-course map denial, multi-PDF snapshot, stale in-flight generation, four-slot queue, malformed-response, timeout, and hostile-PDF source framing assertions. No real SoCLaaS credential or course material was sent. |
| `npm --prefix src/frontend run typecheck` | **PASS** — TypeScript build completed without diagnostics. |
| `npm --prefix src/frontend run build` | **PASS** — Vite production bundle completed. |
| `git diff --check` | **PASS** — no whitespace errors. |
| Static inspection of `scripts/run-local.sh`, `scripts/run-postgres-local.sh`, `Main.java`, and `SoCLaaSClient.java` | **PASS** — both documented local runners export `.env` values before the Java process starts; the staff client reads only `SOCLAAS_STAFF_API_KEY` from the process environment. |
| Static inspection of `src/frontend/` and HTTP routes | **PASS** — no browser-side SoCLaaS key reference and no student AI/Knowledge Map endpoint. Only staff map routes exist. |

The restricted sandbox cannot bind the loopback server without permission; the
unrestricted test result above is the recorded application result rather than
a test failure.

## Remediation re-verification

### KM-01 — local key loading

**Resolved.** Both documented local launch scripts now conditionally source
the ignored project `.env` with `set -a` before launching Java. Consequently a
locally configured `SOCLAAS_STAFF_API_KEY` becomes a process environment value
for `SoCLaaSClient.staffFromEnvironment`; no key is printed or placed in the
browser bundle.

### KM-02 — page-aware source provenance

**Resolved for the Stage 3 supported PDF extraction path.** The source packet
now contains one quoted `[SOURCE filename=... page=N]` segment per extracted
page. The new two-page integration fixture asserts that page 1 carries
`Hello` and page 2 carries `Second page`. On output, the server accepts a
filename/page citation only when that exact page produced nonblank extracted
text; an in-range but unextracted page no longer satisfies validation.

The map remains pending staff review after generation and cannot become an
approved navigation aid without explicit course-staff approval.

## Acceptance-criteria coverage for this Stage 3 increment

| Criterion | Evidence | Assessment |
| --- | --- | --- |
| 1–2. Server authorization and course isolation before map work | `requireCourseStaff` precedes status, generation, approval, and rejection. Tests deny student generation/read and existing platform tests deny unassigned staff cross-course operations. | Pass for the staff map boundary. |
| 3. Source-linked staff artefact; ineligible sources cannot succeed | Every active PDF is snapshotted; page-tagged text is supplied; citations must name every PDF and an extracted page; uncited output test fails. | Pass for Stage 3’s supported extractor. |
| 4. Untrusted source/prompt-injection boundary | The loopback fixture supplies hostile PDF text requesting policy override, prompt disclosure, and external tool calls. It verifies that text remains within `<course_sources>` and that the fixed system policy labels all sources untrusted. The Stage 3 map API has no separate user-prompt field, and the adapter exposes no tool-call capability. | Pass for the applicable Stage 3 input channels; residual model-obedience risk recorded below. |
| 5. No student AI endpoint | Route/client inspection found only staff Knowledge Map routes. | Pass. |
| 6–7. Material limits and authorized resources | Passing platform suite covers PDF validation, staff-only mutation, resource eligibility, membership/cross-course/deleted-source denial. | Pass. |
| 8. Safe provider failures/no fallback | Adapter maps 429, 503/network interruption, 401/403, malformed/empty results to typed safe outcomes; no alternate provider exists. Loopback tests cover 429/503/401/empty input. | Pass for tested outcomes. |
| 11. Model and bounded behavior | Default `qwen3.5:9b`, 45-second request timeout, and four local slots are explicit in code/tasks. The adapter timeout and fifth-job local-rate-limit tests pass. | Pass. |
| 12. One manual, reviewable, current map | One-map-per-course migration; manual generation; pending review before approval; material mutation marks stale; source-set recheck prevents accepting an in-flight obsolete result. Tests cover two active PDFs and a source change during a blocked provider call. | Pass. |

## Residual limitations / follow-up evidence

- Add an explicit regression fixture for a citation to a page with no extracted
  text, plus service-level assertions that every typed adapter failure becomes
  the intended persisted map status/message.
- The hostile-PDF framing fixture covers the applicable indirect input channel.
  No separate user-prompt channel exists in the staff-map API. A fixed prompt
  and absence of tool execution reduce risk but cannot guarantee a model will
  ignore hostile source text; retain output validation and staff approval.
- The extractor is intentionally bounded and dependency-free. A mature
  page-preserving parser remains advisable before student retrieval/release.
- PostgreSQL persistence was inspected but not executed here because this
  environment has no configured Docker/JDBC database test setup.
- No live SoCLaaS request was made during QA; the loopback gateway protects
  the local staff secret and protected course content.

## Verdict and handoff

**Pass for the approved Stage 3 staff-only Knowledge Map foundation.** The
previous blockers are resolved and independently re-verified. Next, the
orchestrator may request the required artefact-only acceptance audit; the
Stage 4B staff UI must remain separately implemented and verified before it is
claimed complete.
