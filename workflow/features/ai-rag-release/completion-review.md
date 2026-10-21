# Stage 3 staff Knowledge Map foundation — acceptance review

Verdict: Ready for human review
Reviewed: 2026-10-21
Reviewer scope: Artefact-only final review of the approved Stage 3 staff-only
Knowledge Map foundation. This review did not inspect implementation or rerun
tests.

## Inputs and state reviewed

- `AGENTS.md`
- `workflow/project-overview.md`
- `workflow/development-process.md`
- `workflow/features/ai-rag-release/spec.md`
- `workflow/features/ai-rag-release/tasks.md`
- `workflow/features/ai-rag-release/test-report.md`
- `workflow/security/ai-rag-stage3-foundation-review.md`
- Related approved staff flow: `workflow/features/staff-material-governance/spec.md`
  and `workflow/features/staff-material-governance/design.md`

Review baseline: `653a671`; the working tree contains the documented Stage 3
implementation and artefact changes, alongside frontend-source relocation and
local configuration changes. No commit is represented as the completion
baseline.

## Approval and scope coherence

The product-owner approval is consistently recorded: Stage 3 implements only
the shared staff Knowledge Map foundation. It has manual generation from every
active PDF, staff-only review/approval, immediate staleness after material
change, server-side course authorization, and no student AI endpoint. The
staff UI remains distinct Stage 4B work.

## Acceptance evidence

The independent tester reports `Pass` with actual passing results for the
platform and loopback SoCLaaS suites, frontend typecheck/build, and whitespace
check. The report maps each applicable Stage 3 criterion to evidence:

- staff authorization and cross-course isolation before map work;
- all-active-PDF snapshots, page-linked source validation, manual review, and
  stale in-flight generation rejection;
- PDF/resource authorization and material eligibility controls;
- safe typed provider outcomes, bounded four-slot handling, and no fallback;
- no student AI route; and
- a hostile-PDF fixture that keeps prompt-injection text inside the untrusted
  source envelope under a fixed policy, with no staff-map user-prompt or
  tool-call channel.

The previously recorded evidence gaps for cross-course map access,
multi-source snapshots, in-flight staleness, queue saturation, timeout,
malformed provider output, and hostile PDF framing are all expressly covered
by the refreshed independent report.

## Residual scope and limitations

The report correctly preserves limitations for later work: a production-grade
page-preserving parser, broader operational/persistence verification, live
provider checks, student retrieval, and Railway release requirements. These do
not enable a student endpoint or weaken the Stage 3 boundary, and they are not
claimed complete here. Fixed policy framing and staff approval reduce but do
not promise elimination of general model-obedience risk; output validation and
the approved navigation-only role remain required controls.

## Handoff

**Next owner: Human product owner / orchestrator.** The approved Stage 3
staff-only foundation is ready for human review on the recorded evidence.
Stage 4B may proceed as its separately approved staff UI feature. Student AI
and production release work remain separately specified and must be tested
before any completion claim for those scopes.
