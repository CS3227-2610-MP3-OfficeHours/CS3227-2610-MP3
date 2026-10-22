# Student AI help — acceptance audit

Status: Audited
Verdict: **Human decision required**
Audit date: 2026-10-21
Last handoff: Acceptance auditor → Orchestrator → Human product owner,
2026-10-21 — artefact evidence supports the delivered behaviour, but one
explicitly pre-acceptance evidence requirement has no executed coverage and no
artefact records a human waiver of it.

This is an artefact-only audit. I did not inspect implementation source, rerun
any command, or re-test any behaviour. Where this document says "confirmed", it
means the recorded evidence is present, internally consistent, and traceable to
an approved requirement — not that I independently proved the implementation or
the test execution correct.

## Reviewed artefacts and revision state

| Artefact | State as read |
| --- | --- |
| `AGENTS.md` | Committed |
| `workflow/project-overview.md` | Committed |
| `workflow/development-process.md` | Committed |
| `workflow/features/student-ai-help/spec.md` | Modified, uncommitted. `Status: Approved for implementation` |
| `workflow/specs/student-ai-help.md` | Untracked. `Status: Approved for implementation`. Adopted as normative by `spec.md` |
| `workflow/features/student-ai-help/answer-guidelines.md` | Untracked. `Status: Approved`, version `student-answer-policy/v2` |
| `workflow/features/student-ai-help/design.md` | Untracked. `Status: Approved for implementation` |
| `workflow/features/student-ai-help/tasks.md` | Untracked. `Status: Remediated after independent verification; awaiting re-verification` (stale — see F3) |
| `workflow/features/student-ai-help/test-report.md` | Untracked. `Status: Pass` (round 3 current; rounds 1–2 retained as labelled history) |

Revision state: working tree based on commit `b98e95f`, all feature work
uncommitted. `git status --short` shows 10 modified and 8 untracked paths;
`git diff --stat` shows 315 insertions and 38 deletions across 10 tracked
files. Recorded for traceability only; the change set was not audited.

## Approval chain

Confirmed complete. Every artefact that states permitted product behaviour
carries a status block with a human approval, and the two decisions taken
during implementation are stamped in every document that states them:

- The three-scope answer boundary, `answer-guidelines.md` v1 and v2, `design.md`,
  the two-tier deterministic screen, and the accepted lexical-retrieval
  limitation are all named in `spec.md`'s approval line, dated 2026-10-21.
- The two-tier screen amendment (round-1 D4) is stamped in the normative
  record's "Amendment note" as confirmed by the human product owner.
- The uncomposed refusal (R2 / amendment A1) is stamped in
  `answer-guidelines.md` ("Status: in force as of v2. Approved by the human
  product owner, 2026-10-21"), in `design.md`'s refusal-copy section, and in the
  v2 change-log row with approver and date.
- The guidelines' own change rule — that widening or narrowing permitted answer
  behaviour requires a new version string, not a prompt edit — was followed:
  A1 produced `student-answer-policy/v2`, and R8 moved the code constant to
  match.

The stamping is adequate and consistent. No approval is outstanding.

## Criterion and evidence coverage

### `spec.md` acceptance criteria

All ten criteria (1, 1a, 2–9) have a verdict with named evidence in the
test report's coverage table, carried forward from round 2 with criterion 1's
`refused` row corrected in round 3 after R7. Round 3's `Pass` is the current
assessment and is supported by actual recorded command output, not a bare label:

```
Platform integration tests: PASS
Student AI help tests: PASS
Exception in thread "main" java.io.IOException: Unable to establish loopback connection
```

plus `tsc -b --pretty false` **PASS** with no diagnostics, and the V1–V14 probe
transcript for the R8 version-string change.

Criterion 6 is marked **Pass, static only**: the UI states are evidenced by
typecheck and review, not by exercising a running client. Criterion 9's own
wording makes automated coverage of the named cases a pre-acceptance condition;
see F1.

### Normative record "Required acceptance evidence" items 1–12

Ten of twelve items have an executed scenario group with a PASS result in
`tasks.md`, each independently re-verified in the test report. Two are partial,
and both are disclosed rather than claimed:

| Item | Recorded state | Audit view |
| --- | --- | --- |
| 8 (digest-stale, cross-course maps) | "Unreachable through the API, not tested. Asserted by code inspection, not execution." | Adequately recorded. The tester independently accepted the unreachability reasoning in round 2's R3 table rather than taking it on the claim. |
| 10 (request timeout, malformed provider JSON) | "NOT RUN — these map to adapter behaviour covered only by the suite this environment cannot run." | Disclosed accurately, but unresolved. See F1. |

I specifically checked `tasks.md`'s coverage table for the overstatement that
round 1 found as D3. It no longer overstates: the two partial items are labelled
as unreachable and not-run with reasons, the round-2 report verifies the
corrected claims line by line, and the table's "Additional coverage" paragraph
claims only scenarios the report also names. No claim in `tasks.md` asserts a
PASS the test report does not evidence.

## Findings

### F1 — D8 leaves an explicitly pre-acceptance evidence requirement unexecuted, and no human has waived it. **Blocking this audit.**

Both the engineer and the tester classify D8 as blocking for release but not for
acceptance. The artefacts do not support that distinction, and nothing in them
records a human making it.

- `spec.md` acceptance criterion 9 reads: *"Before implementation is accepted,
  automated tests cover the authorization, cross-course, restricted-answer,
  direct/indirect prompt-injection, citation-grounding, deleted/inactive-material,
  consolidation approval and pending/rejected/invalidated exclusion,
  malformed-output, and provider-failure cases."* It ties that coverage to
  acceptance, not to release.
- The normative record's required-evidence item 10 names timeout and malformed
  provider JSON among the cases automated tests must cover "at minimum".
- `SoCLaaSClientTest` has never executed in this environment, so gateway request
  construction, timeout handling, malformed-response handling, the
  `SOCLAAS_STUDENT_API_KEY` path, the bounded token budgets, the fallback-model
  retry, and the shared `chatOnce` primitive have no executed coverage. No live
  end-to-end request has been made.
- `chatOnce` is a refactor that the already-delivered staff Knowledge Map
  feature now also depends on (`tasks.md` changed-areas table and technical
  decision 4). `PlatformIntegrationTest` passing is evidence against regression
  in platform behaviour, but the artefacts record no executed coverage of the
  shared gateway primitive itself for either feature.

The gap is recorded honestly and in three places, the cause is environmental
rather than an engineering omission, and the tester correctly declined to
overstate it. Returning this to engineering would achieve nothing: no code
change closes it. But a specialist agreement between engineer and tester is not
the authority that an approved acceptance criterion requires. Deciding whether
to accept the feature with item 10 partly unexecuted — or to hold acceptance
until the suite runs on a loopback-capable runner — changes the project's
security posture and risk acceptance, which my role escalates rather than
decides.

Two safe resolutions, either of which closes F1:

1. The human product owner records, in `spec.md`, acceptance of the documented
   D8 gap as a waiver scoped to criterion 9 and evidence item 10, naming the
   release gate that still requires the suite to pass; or
2. `SoCLaaSClientTest` and one live end-to-end request are run on a runner that
   permits loopback binding and holds a real `SOCLAAS_STUDENT_API_KEY`, and the
   tester records the actual results in a short re-verification.

### F2 — the round-3 report and `tasks.md` disagree about N2. Non-blocking; reconcile the record.

The round-3 report lists N2 (a stale v1 sentence in `answer-guidelines.md`
Scope 3) as open and non-blocking. `tasks.md` records it fixed under R9. I read
the artefact: Scope 3's lead paragraph now reads *"gets a short fixed refusal
and nothing else. It is not retrieved for, no model call is made, and no
citations are returned"* — the v2 rule. The only surviving instance of the stale
sentence anywhere under `workflow/` is the test report's own quotation of it in
its N2 finding.

This is the sequencing artefact it was described as, not a concealed
contradiction: R9 landed after the round-3 report was written, R9 is
documentation-only, and `tasks.md` states plainly that no behaviour changed. The
artefact set is coherent enough to support a completion claim on this point, and
I am not blocking on it. Two caveats worth recording:

- R9 is the one remediation in this feature that no verification round has
  independently confirmed. It is a prose correction inside an approved policy
  document with no code or prompt reading it, so the risk is editorial rather
  than behavioural, and my own read of the file confirms the correction landed.
- The report's own closing guidance to the auditor still says to note N2 as
  deliberately open. A reader arriving at these artefacts cold would be told to
  look for a defect that no longer exists.

Lowest-cost fix: a one-line note on the round-3 N2 finding marking it closed by
R9, left to the tester as the report's owner. The engineer must not edit the
test report.

### F3 — stale status and handoff lines on `tasks.md`. Non-blocking; editorial.

`tasks.md` carries `Status: Remediated after independent verification; awaiting
re-verification` and `Last handoff: Engineer (@ziyang) → Tester`. Round 3 has
since returned `Pass`, and the file's own "Next handoff" section correctly
routes to the acceptance auditor. Its revision-history paragraph also stops at
R8 and does not mention round 3, N2, or R9, although R9 is written up in full
below it. `spec.md`'s `Last handoff` line likewise still reads *"Both need
approval before implementation"* while its own `Status` and approval line record
those approvals as granted.

Same class of defect as N1 and N2: requirement and status text trailing the
decision. None of it changes behaviour or coverage, but `development-process.md`
makes these status blocks the mechanism by which another developer resumes
without agent history, and all three currently mislead.

### F4 — scenario-group count disagrees across artefacts. Non-blocking; editorial.

`tasks.md` records `StudentAiHelpTest` as "14 grouped scenarios" and "all 14
scenario groups"; the round-2 report records "PASS (15 scenario groups,
including the new cases)" after R3 added tests, while round 1 recorded 14. The
count in `tasks.md` appears not to have been updated when R3 added cases. The
per-item coverage table is the load-bearing evidence and it is consistent, so
nothing turns on the number, but an engineer evidence file should not undercount
its own suite.

## Items assessed and found adequately recorded

- **Residual per-claim citation limits** (`tasks.md` limitation 6). Adequate.
  The three shapes are named with reproductions (A4–A8), the tester states
  independently that it did not class any of them as a defect and explains why
  the one-sentence-multiple-claims case matches both the approved example in
  `answer-guidelines.md` and the record's "claim *line*" wording, and the
  decision not to harden them is justified by the cost of invalidating round-2
  evidence for a minor safe-direction gap. The limitation discloses that the
  rule has never met real model output.
- **Lexical retrieval** (`tasks.md` limitation 3). Approved as a limitation in
  both the normative record and `spec.md`'s closed decision 3.
- **Over-inclusive screen** (limitation 4) and **strict per-claim enforcement**
  (limitation 5). Both fail in the safe direction and are disclosed, including
  that R2 raised the cost of a false positive.
- **Round-1 observations** that were not acted on (unpruned rate-limit keys,
  rate-limit budget consumed before the provider-null check, unreachable packet
  bound, `invalidatedAt` unreachable in the in-memory adapter, answers able to
  restate same-course page text). Retained in the report as observations rather
  than silently dropped, correctly not classed as defects.
- **Prompt injection is not claimed as solved.** Both the report and `spec.md`
  state that the academic-guidance boundary rests partly on prompt compliance.
  The report's claim is narrower and supportable: the server-side controls the
  specification promises are present and observed.

## Verdict

**Human decision required.**

The feature's artefact evidence is in good order in every respect my role
checks except one. The approval chain is complete and consistently stamped; the
latest test report is `Pass` with actual recorded results, not a label; all ten
acceptance criteria have mapped coverage; `tasks.md` no longer overstates what
the report evidences; and every open item is named with a reason rather than
omitted. Rounds 1 and 2 are retained as labelled history with superseded
verdicts, so the record of what was found and fixed survives.

What blocks is F1. `spec.md` criterion 9 and the adopted normative record's
evidence item 10 make automated coverage of the provider-failure cases a
condition of acceptance, two of those sub-cases have no executed coverage, and
the engineer-and-tester classification of D8 as release-blocking-only is not an
authority the approved artefacts grant. That is a risk-acceptance decision for
the human product owner, not a defect to return to engineering and not a call
for me to make.

F2, F3 and F4 are editorial and do not block. They should be corrected by their
artefact owners, but they do not need another verification round.

## Next handoff

**Next owner:** Orchestrator → Human product owner, for the F1 decision only.
Present the recorded D8 gap and the two resolutions above; do not present the
feature as complete while criterion 9's pre-acceptance coverage condition is
neither met nor waived.

**On a waiver (resolution 1):** the product manager records it in `spec.md`
against criterion 9 and evidence item 10, naming the surviving release gate.
No further testing round is needed for the current behaviour; this audit can
then be reissued as `Ready for human review` without re-auditing the rest.

**On running the suite (resolution 2):** the tester runs `SoCLaaSClientTest` and
one live end-to-end request on a loopback-capable runner holding a real
`SOCLAAS_STUDENT_API_KEY`, and records actual results. Any defect found there
goes to the engineer before this audit is reissued.

**Editorial, in parallel and blocking nothing:** tester closes N2 on the
round-3 finding (F2); engineer refreshes `tasks.md`'s status, handoff, revision
history and suite count (F3, F4); product manager refreshes `spec.md`'s
`Last handoff` line (F3).

**Not in scope for this audit:** the implementation, the test execution, and the
pending orchestrator question from the round-1 process note about whether a
separate security artefact under `workflow/security/` is wanted.
