# Platform hardening — acceptance review

Verdict: Ready for human review
Reviewed: 2026-10-22
Reviewer scope: Artefact-only completion review. This review did not inspect
implementation or rerun tests.

## Inputs and state reviewed

- `AGENTS.md`
- `workflow/project-overview.md`
- `workflow/development-process.md`
- `workflow/features/platform-hardening/spec.md`
- `workflow/features/platform-hardening/tasks.md`
- `workflow/features/platform-hardening/code-quality-review.md`
- `workflow/features/platform-hardening/test-report.md`
- `workflow/security/platform-hardening-upload-boundary-review.md`

No `design.md` is required: the approved specification records that this
maintenance feature has no user-facing flow change. Review baseline:
`3609882`; the working tree contains the uncommitted platform-hardening
migrations, API and test changes, this feature's artefacts, and unrelated
forum-tag, frontend, workflow, and local-store changes.

## Approval and scope coherence

The approved specification consistently records the product owner's decisions:
`resource_only` must be accepted by PostgreSQL through a forward migration;
V5 intentionally discards obsolete, disposable legacy course Knowledge Map test
data; and JSON request bodies have endpoint-specific pre-parse limits. The
security review addresses the resulting upload boundary. No product, security,
or design decision remains pending in the recorded scope.

## Acceptance evidence assessment

The latest independent test report is `Pass` and maps every acceptance
criterion to recorded evidence. It gives actual passing results for:

- `sh scripts/test.sh`, covering platform, HTTP API, and SoCLaaS client suites;
- `sh scripts/test-postgres.sh`, covering the PostgreSQL integration suite.

Its criterion mapping records forward migration from V1–V6 to V7, acceptance
of all five material statuses, image-only material persistence/reload and
authorized download, the V5 development-data comment, declared and
untrusted-length 413 behavior, exact-boundary processing, unchanged
authorization and AI eligibility, and the relevant Java/PostgreSQL suites.
The security review records safe error content, absence of parsing and
mutation for oversized bodies, and the residual deployment and parser limits.

## CI request-limit regression follow-up

The latest test report records a passing re-verification of the GitHub CI
failure caused by a live client streaming a declared-over-limit 28 MiB
material-upload request. The revised declared-limit case now uses a controlled
handler exchange with `Content-Length: 29,360,129` and a body stream that fails
if read. Its recorded assertions show the handler returns the safe 413 response
without reading the stream, parsing JSON, decoding Base64, or creating a
material. The exactly 28 MiB accepted upload remains a real loopback test and
passed.

The code-quality follow-up records and resolves the evidence mismatch between
the earlier documentation and this controlled handler-level test. The security
review has been updated to describe the same coverage accurately. No unresolved
defect or approval is recorded for this regression follow-up.

## Code-quality completion evidence

The completed code-quality review records two P2 findings: incomplete
handler-level coverage of missing and understated `Content-Length`, and absent
coverage for `resource_only` AI ineligibility. Engineering remediated both;
the final code-quality re-review reports no actionable P0–P3 findings.

The independent test report records the resulting handler-level 413/no-mutation
cases and the `resource_only` Knowledge Map rejection. This supplies the
required quality-review, remediation, and independent re-verification sequence
under `AGENTS.md`, including the CI regression remediation.

## Handoff

**Next owner: Human product owner / orchestrator.** The approved
platform-hardening scope has complete artefact evidence, a passing independent
test report, and a completed code-quality remediation cycle. It is ready for
human review.
