# 22 October — student AI help, specification to acceptance

**Kind: observed.** Written by the orchestrating agent that ran this work.

**Owner:** @ziyang. Feature `student-ai-help`.

---

## 1. Inputs

The session began from the MP3 brief, the repository state at `b98e95f`, the
CS3227 lecture material (lectures 6 and 7, read directly from Canvas), and a
team chat in which the two decisions below had already been made informally:

- the student assistant should give hints rather than answers, **except** for
  administrative questions like "when are midterms", which it should answer;
- the staff Knowledge Maps exist so the student assistant does not have to read
  every PDF per question.

Reading `workflow/` established that the feature's `spec.md` was `Draft —
blocked`, with two open blockers: the SoC operating envelope, and the
academic-integrity boundary.

## 2. Specification (product manager, UI designer)

Both blockers were closed. The envelope was already recorded in
`implementation_plan.md`; the boundary was resolved into three scopes —
`administrative`, `academic`, `refused` — written up in
`workflow/specs/student-ai-help.md` as the normative decision record.

Two artefacts were created alongside:

- `answer-guidelines.md`, a **versioned** statement of what each scope may say,
  rendered into the prompt by the server so that changing permitted behaviour
  means amending an approved document rather than editing a string.
- `design.md`, covering the tab, the scope badge, citation display and every
  failure panel.

A key design decision recorded here: the scope is the **stricter** of a
deterministic server-side screen and an advisory model classification, and the
classifier never sees retrieved material.

**Human approval gate.** Work stopped. The product owner approved the
three-scope boundary, `student-answer-policy/v1`, and the accepted limitation
that retrieval narrowing is keyword-based rather than embedding-based.

## 3. Implementation (engineer)

`StudentAnswerPolicy`, `StudentAiService`, the `studentAiHelp` entry point on
`PlatformService`, the student path on `SoCLaaSClient`, two routes, and the
student-only AI help tab. 15 scenario groups in `StudentAiHelpTest`.

Two hardenings came directly from lecture 6 rather than from the specification:
Unicode folding plus compact pattern matching for obfuscated payloads, and a
post-generation disclosure filter. Both tighten behaviour, and both were
recorded as deviations needing confirmation rather than applied silently.

An incidental fix: `scripts/test.sh` hardcoded the POSIX classpath separator,
so **no suite could run on Windows at all**. Made portable in its own commit.

## 4. Independent verification — round 1: **Needs changes**, 8 defects

A tester agent with fresh context, the repo's own `tester` role instructions,
and no access to the engineer's reasoning. It was explicitly told not to trust
the new test suite.

The two serious findings:

- **D1** — citation validation required only one valid citation anywhere, so a
  reply could carry fabricated facts beside one real citation and render under
  the **Course info** badge. Reproduced with an invented midterm date and
  weightage.
- **D2** — refusals retrieved course material and composed through the model,
  so a successful jailbreak would have produced a *grounded, citation-valid*
  solution. The academic-integrity boundary rested on prompt compliance for the
  highest-risk request class.

Also: coverage overstated in `tasks.md` (**D3**); a map-ordering assertion that
would pass with the feature removed (**D5**); a parenthesised filename evading
the loose-reference check (**D6**); API status codes in the spec that the
server never emits (**D7**).

## 5. Remediation

All eight accepted, none disputed. D1 became per-sentence citation checking.
D2 became: refusals perform no retrieval and make no gateway call —
resolved using the precedence rule already written into `spec.md`, that the
normative record governs where artefacts conflict. D3, D5, D6, D7 fixed.

## 6. Rounds 2 and 3

Round 2 re-ran the round-1 reproductions rather than trusting the new
regression tests, confirmed all six remediations, and raised **N1**: the
requirement text in two spec documents and user story 2 still described the old
refusal behaviour. Acceptance is measured against requirement text, so that was
a real gap. Fixed.

The product owner then confirmed both outstanding decisions, and the answer
policy was reissued as `student-answer-policy/v2` — the guidelines' own change
rule requires a new version when permitted behaviour changes.

Round 3 verified the version bump independently and returned **Pass**, raising
one non-blocking item (**N2**, a stale sentence inside the v2 document), which
was fixed.

## 7. Acceptance audit — **Human decision required**

The auditor read only the artefacts. It accepted the approval chain, the
criteria coverage, and that `tasks.md` no longer overstated. It then blocked on
one point both the engineer and the tester had agreed on: acceptance criterion
9 reads *"Before implementation is accepted, automated tests cover … the
malformed-output and provider-failure cases"*, and that coverage was not
executed anywhere. Two agents reclassifying it as release-blocking was not the
authority to override an approved criterion.

The escalation was correct and was not disputed. It is reflected on in
`docs/Reflections.md` §3.4.

## 8. Closing the gap

Rather than waive the criterion, the adapter suite was rewritten to inject an
`HttpClient` instead of binding a local listener, so it runs everywhere —
covering request construction, every status class, malformed responses,
timeouts, the bounded fallback, and the student path, which had none.

## 9. Evidence

`sh scripts/test.sh`: `PlatformIntegrationTest` PASS, `StudentAiHelpTest` PASS
(15 groups), `SoCLaaSClientTest` PASS. `StaticSiteTest` NOT RUN locally — it
binds a listener this sandbox forbids — and PASS on the Linux CI runner.
Client type-check clean.

Artefacts: `spec.md`, `design.md`, `answer-guidelines.md`, `tasks.md`
(remediations R1–R9), `test-report.md` (three rounds), `completion-review.md`.
