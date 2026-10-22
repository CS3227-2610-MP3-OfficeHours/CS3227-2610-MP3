# Student AI help — engineer handoff

Status: Verified `Pass` (test-report round 3). Acceptance audit returned
`Human decision required` on finding F1; awaiting the product owner's decision
on AC9 coverage. No engineering work outstanding.

Revision history: implemented 2026-10-21; `test-report.md` round 1 returned
`Needs changes` with D1-D8; remediated as R1-R6 below; round 2 confirmed all
six fixed with no engineering defect remaining, and raised N1, remediated as
R7. The human product owner confirmed D4 and R2/A1 on 2026-10-21, and the
guidelines were reissued as v2 (R8). Round 3 returned `Pass` and raised N2,
remediated as R9. The acceptance audit then returned `Human decision required`
on F1 (AC9 ties the unexecuted adapter coverage of D8 to acceptance, and no
human has waived it) with editorial findings F2-F4; F3 and F4 are fixed here.
No engineering work and no approval is outstanding other than the F1 decision.

Input artefacts: `workflow/specs/student-ai-help.md` (Approved),
`workflow/features/student-ai-help/spec.md` (Approved),
`answer-guidelines.md` version `student-answer-policy/v2` (Approved),
`design.md` (Approved). Working tree based on `b98e95f`.

Last handoff: Orchestrator → Human product owner, 2026-10-21 — testing and the
acceptance audit are complete; the audit's finding F1 needs a product-owner
decision on AC9's coverage condition before the feature can be reported
complete. See `completion-review.md`.

## Implementation checklist

- [x] Answer-scope policy: deterministic screen, scope resolution, classifier
      and composer prompts, refusal and insufficient-evidence copy, retrieval
      terms, and the post-generation prompt-disclosure filter.
- [x] Retrieval and composition: candidate eligibility, approved-map
      narrowing, bounded page selection, quoted source packet, deterministic
      citation validation, outcome mapping.
- [x] Course-scoped entry point with authorization, question validation, the
      local rate limit, and the eligible-evidence snapshot.
- [x] Student gateway path on the shared adapter, with a student key, bounded
      token budgets, and fallback-model reuse.
- [x] `POST`/`GET /api/courses/{courseId}/ai-help` and the response
      serializer.
- [x] Student-only **AI help** tab with scope labelling, citation links, and
      every approved state panel.
- [x] Automated verification of the approved acceptance evidence.
- [x] `503 service_unavailable` added to the shared error vocabulary.
- [x] Student model and key documented in `.env.example`.

## Changed areas

| File | Change |
| --- | --- |
| `src/main/java/edu/nus/tahelpdesk/StudentAnswerPolicy.java` | New. Scope enum, deterministic screen, scope resolution, label parsing, classifier/composer prompts, leak filter, query terms. |
| `src/main/java/edu/nus/tahelpdesk/StudentAiService.java` | New. Candidate/page selection, source packet, citation validation, outcome mapping, provider interface, neutral failure mapping. |
| `src/main/java/edu/nus/tahelpdesk/PlatformService.java` | Added `studentAiHelp`, `studentAiConfigured`, the enrolled-student check, question validation, the 5-per-minute limit, the eligible-evidence snapshot, and the provider setter. |
| `src/main/java/edu/nus/tahelpdesk/SoCLaaSClient.java` | Added `studentFromEnvironment`, `studentProvider`, and a shared `chatOnce`/`chat` primitive the staff Knowledge Map path now also uses. |
| `src/main/java/edu/nus/tahelpdesk/HttpApi.java` | Added the two `ai-help` routes and the citation serializer. |
| `src/main/java/edu/nus/tahelpdesk/Main.java` | Installs the student provider at bootstrap when the student key is configured. |
| `src/main/java/edu/nus/tahelpdesk/ApiException.java` | Added `unavailable` (503). |
| `src/frontend/src/main.tsx`, `styles.css` | Student-only AI help tab, scope badges, Sources block, state panels, responsive rules. |
| `src/test/java/edu/nus/tahelpdesk/StudentAiHelpTest.java` | New. 15 grouped scenarios over the approved acceptance evidence. |
| `scripts/test.sh` | Registered the new suite and made the classpath separator portable (see limitations). |
| `.env.example` | `SOCLAAS_STUDENT_CHAT_MODEL`, `SOCLAAS_STUDENT_FALLBACK_CHAT_MODEL`. |

No migration and no new table: the feature persists nothing, as the
specification requires.

## Technical decisions

1. **The screen refuses explicit submission demands outright.** The approved
   normative record capped solution demands at `academic` while
   `answer-guidelines.md` lists them as refusal triggers. Implementing the
   record literally would have left the guidelines' promise dependent on the
   classifier. The screen therefore has two tiers: unambiguous submission
   demands (an explicit "give me the answer/solution/code" verb, or a
   possessive such as "do my assignment") force `refused`, while broader
   graded-work phrasing caps at `academic`. **This resolves a conflict between
   two approved artefacts; confirmed by the human product owner in chat on
   2026-10-21**. The
   normative record's "Answer-scope decision" section should be amended to
   match.

2. **Obfuscation handling added beyond the approved record.** Lecture 6's
   attack taxonomy lists obfuscation (spaced words, Unicode smuggling) and
   best-of-N sweeps, which plain substring matching misses. The screen now
   NFKC-folds the question, strips invisible characters, and matches a compact
   no-separator pattern set in addition to the readable one. The 5-per-minute
   limit is the best-of-N control. Both tighten the approved behaviour rather
   than widening it.

3. **A post-generation disclosure filter was added.** Output containing the
   policy version, a scope header, or a source delimiter is replaced by
   `insufficient_evidence` (or by the fixed refusal copy). Markers were chosen
   to be unique to the server's own prompt so legitimate course content about
   prompting is not caught.

4. **One `chatOnce` primitive now serves both role features.** The staff
   Knowledge Map path previously built its own request body. Both paths share
   request construction, status-class mapping, and fallback-model retry, which
   keeps the two features' provider behaviour identical (DRY). The staff
   request body is byte-identical to before: `max_tokens` is appended only
   when a caller supplies one, and the staff path supplies none.

5. **Refusals are not composed at all.** *Revised during remediation; see R2.*
   A refused request returns the fixed boundary sentence with no retrieval, no
   gateway call, and no citations. The earlier implementation composed a
   refusal from retrieved pages so it could offer cited guidance; that handed
   the model source material for exactly the request class that had just asked
   for submission-ready work.

6. **Narrowing is lexical, as approved.** Approved, digest-current map text
   contributes to a material's score (`1 + 0.4 x min(5, term hits)`) and is
   never sent to the gateway. Caps: 8 pages, 3 materials, 24,000 characters.

## Remediation of the independent test report

The tester's findings were reproduced in the code before acting on them. All
eight were accepted; none was disputed.

**R1 — D1, per-claim citation (fixed).** `StudentAiService.validate` now masks
each validated citation to a single marker character, splits the result into
sentences, and requires every asserting sentence to end with its own citation.
Questions, lines ending in a colon, and `#` headings assert nothing and are
exempt. Masking first means a citation's own full stops cannot be mistaken for
sentence boundaries. The composer prompt was rewritten to demand the same
thing explicitly, including that one citation does not cover a neighbouring
sentence. The tester's exact repro and a second variant (an uncited sentence
*after* a cited one) are now regression cases.

**R2 — D2, composed refusals (changed behaviour; confirmed 2026-10-21).**
Refusals no longer retrieve or compose. Accepted as the tester framed it: the
approved artefacts genuinely conflicted, and `spec.md`'s own precedence rule
("the normative record governs") resolves it toward no retrieval. The security
argument is decisive independently of precedence — a jailbreak that succeeded
against a composed refusal would have produced a solution that was grounded in
real course pages and therefore passed every citation check, so AC4 rested on
prompt compliance for the single highest-risk request class. `design.md` and
`answer-guidelines.md` (amendment A1) now record the change and flag it for
product-owner confirmation.

**R3 — D3, overstated coverage (fixed, and claims corrected).** Added tests
for rejected and unverified maps, for injection inside an *approved* Knowledge
Map, and for a cross-course citation naming a PDF that genuinely exists in
another course. Digest-stale and cross-course map states are unreachable
through the API — a material's bytes cannot change without a delete that
removes its maps, and maps are keyed by a material id guarded by
`requireMaterialInCourse` — so they are now recorded as unreachable rather
than claimed as tested. The coverage table below was corrected; the previous
version claimed PASS for evidence item 8 on the strength of two of its six
states.

**R4 — D5, vacuous ordering assertion (fixed).** The map-narrowing fixtures
were `Mapped.pdf` and `Rival.pdf`, which are not equally matching and where
the mapped file also won the alphabetical tie-break, so the assertion proved
nothing. They are now `Zeta.pdf` (mapped) and `Alpha.pdf`, with identical term
counts, so the map must overcome the tie-break. The test asserts the order in
four map states: none, pending, rejected, unverified, then approved.

**R5 — D6, parenthesised filename evasion (fixed).** `ANY_PDF_MENTION` was
`[^\s()]{1,200}\.pdf`, which excluded parentheses and so missed
`week(9).pdf`. It is now `\S{0,200}\.pdf`, applied to the masked text after
validated citations are removed. Regression case added.

**R6 — D7, API contract mismatch (fixed in the spec).** The normative record's
status table had invented codes (`validation`, `no_material`, `rate_limited`,
`ai_unavailable`) and a 400 status. The implementation deliberately reuses the
platform's existing `ApiException` vocabulary; inventing a parallel one for a
single endpoint would have been the worse choice. The record's table was
corrected to the emitted statuses and codes, including the 401 the shared
session check raises before routing.

**R7 — N1, requirement text trailing the decision (fixed).** The second
verification round found that R2 had been recorded in `design.md` and
`answer-guidelines.md` but not in the two documents that state what a
`refused` request may return, so user story 2 in `spec.md` was not met as
written. Corrected: the `refused` row in `spec.md` and in the normative
record, and user story 2, now describe a fixed redirect with no guidance and
no citations, each cross-referencing amendment A1. No behaviour changed; the
requirement text now matches the behaviour acceptance is measured against.

**R8 — guidelines version bumped to v2 (after the confirmations).** Because
A1 changed what the assistant may say, the guidelines' own change rule
required a new version string rather than an amendment note on v1. Scope 3 was
rewritten so the document states the delivered behaviour directly, A1 was kept
as the historical rationale, a v2 row was added to the change log, and
`StudentAnswerPolicy.GUIDELINES_VERSION` was updated to match. The constant is
a label carried in the composer prompt and checked by `leaksPolicy`; no
behaviour depends on its value, and the full suite was re-run after the change.

**R9 — N2, stale v1 sentence inside the v2 document (fixed).** Round 3 found
that the v2 rewrite had missed Scope 3's lead paragraph in
`answer-guidelines.md`, which still said a refusal "gets a short refusal and
then whatever `academic` guidance can be given safely" — the v1 rule inside
the v2 document, three lines above the corrected "How to answer" block. The
tester classed it as non-blocking because nothing in the server, the prompts,
or any acceptance criterion reads it. Corrected anyway: an approved policy
document that contradicts itself is a hazard to the next person who reads it.
No behaviour changed.

**D4 and D8 are not remediated here.** D4 (confirmation of the two-tier screen
amendment) is a product-owner decision. D8 (`SoCLaaSClientTest` and a live
request) is blocked by the environment and remains blocking for release.

## Test commands and results

```
sh scripts/test.sh
```

Run 2026-10-21 on Windows (Git Bash), JDK 25, PDFBox 3.0.8:

| Suite | Result |
| --- | --- |
| `PlatformIntegrationTest` | **PASS** — no regression from the shared-service changes. |
| `StudentAiHelpTest` | **PASS** — all 15 scenario groups. |
| `SoCLaaSClientTest` | **NOT RUN** — environment cannot bind a loopback listener (`IOException: Unable to establish loopback connection`). See limitations. |

`scripts/test.sh` runs under `set -e`, so this suite's failure aborts the
script. The listener-free suites were therefore moved ahead of it, and their
results above come from that ordering. The two suites were also confirmed
individually:

```
java --add-modules jdk.httpserver -cp "build/classes;build/test-classes;.local-lib/pdfbox-app-3.0.8.jar" edu.nus.tahelpdesk.PlatformIntegrationTest
java --add-modules jdk.httpserver -cp "build/classes;build/test-classes;.local-lib/pdfbox-app-3.0.8.jar" edu.nus.tahelpdesk.StudentAiHelpTest
```

```
cd src/frontend && npm ci && npm run typecheck
```

**PASS**, no diagnostics.

### Scenario coverage in `StudentAiHelpTest`

| Approved evidence item | Scenario group | Result |
| --- | --- | --- |
| 1 administrative direct answer | `administrativeScope` | PASS |
| 2 guidance with citation | `guidanceScope` | PASS |
| 3 screen tightens classifier | `screenOverridesClassifier` | PASS |
| 4 override refused, no disclosure | `refusalBoundary`, `obfuscatedOverrides` | PASS |
| 5 staff/non-member/wrong-course/unauthenticated, no gateway call | `authorizationBoundary` | PASS |
| 6 injection in page text, filename, approved Knowledge Map, model echo | `indirectInjection` | PASS |
| 7 deleted/invalidated material not retrieved | `materialEligibility` | PASS |
| 8 pending, rejected, and unverified maps do not reorder retrieval and cannot become eligible; map text never leaves the server | `mapNarrowing` | PASS |
| 8 (digest-stale, cross-course maps) | — | **Unreachable through the API, not tested.** A material's bytes cannot change without a delete that removes its maps, and maps are keyed by a material id guarded by `requireMaterialInCourse`. Asserted by code inspection, not execution. |
| 9 absent, out-of-range, non-existent, cross-course, loose, and per-claim-missing citations; leaked output | `citationGrounding` | PASS |
| 10 429 / 503 / configuration / rejected / absent key | `providerFailures`, `unconfiguredService` | PASS |
| 10 (request timeout, malformed provider JSON) | `SoCLaaSClientTest` | **NOT RUN** — these map to adapter behaviour covered only by the suite this environment cannot run. |
| 11 blank and over-length questions, no gateway call | `requestValidation` | PASS |
| 12 sixth request in a minute | `rateLimit` | PASS |

Additional coverage beyond the approved list: classifier fails closed on
outage and on an unparsable label; revoked membership loses access
immediately; image-only PDFs are excluded from retrieval; citations are
de-duplicated; a question matching no page makes no composition call; the
rate limit is per student; obfuscated override payloads (spaced, punctuated,
zero-width) are refused without a classification call; headings, list
lead-ins, and clarifying questions are exempt from per-claim citation; a
refused request makes no gateway call at all.

## Limitations and residual risk

1. **`SoCLaaSClientTest` did not run in this environment.** The sandbox cannot
   bind the local Java HTTP listener the suite needs, the same limitation
   already recorded in `workflow/features/shared-course-forum/test-report.md`.
   The `chatOnce` refactor in decision 4 is therefore **unverified against a
   live gateway here**, and the student provider has never made a real HTTP
   call. This suite must pass on a machine or CI runner that allows loopback
   binding before release. Until then, treat gateway request construction and
   the student key path as untested.
2. **No live end-to-end run.** The student AI tab has not been exercised
   against a running server with a real SoCLaaS key, for the same reason plus
   the absence of a configured student key. UI behaviour is verified only by
   typecheck and code review.
3. **Lexical retrieval**, as approved: a paraphrased question that shares no
   content term with any page yields `insufficient_evidence` even when the
   material covers it. Embedding retrieval is the documented follow-up.
4. **The screen is deliberately over-inclusive.** A content question that
   mentions solutions or code may be capped at `academic` or refused. This
   fails in the safe direction but can be brusque, and since R2 a refused
   request no longer offers any guidance at all, so the cost of a false
   positive here is higher than it was.
5. **Per-claim citation is strict.** Any asserting sentence without its own
   trailing citation sends the whole answer to `insufficient_evidence`, so a
   model that cites loosely will fail often even when its content is sound.
   This is the approved grounding rule and it fails safe, but it is the most
   likely cause of a confusing `insufficient_evidence` in live use, and it has
   never been exercised against real model output.
6. **Per-claim enforcement has two narrow, accepted holes**, found by the
   second verification round. A sentence ending in `?` and a sentence ending
   in `:` are exempt, so a fact smuggled as a rhetorical question
   ("Isn't the midterm on 1 January?") or as a lead-in
   ("The midterm is on 1 January:") passes with one trailing citation
   elsewhere. Neither is being hardened: the question exemption is what lets
   the assistant ask the clarifying questions the guidelines call for, and the
   tester did not class either as a defect. Several claims joined inside one
   sentence also share a citation, which is deliberate — the approved example
   in `answer-guidelines.md` is itself one sentence carrying three facts under
   one citation, and the record's wording is "a claim *line*", which sentence
   granularity already exceeds. Recorded as residual risk rather than fixed,
   because a third code change would invalidate the round-2 evidence that
   covers the current behaviour.
7. **Both product-owner decisions are now closed.** The human product owner
   confirmed the two-tier screen (decision 1) and the uncomposed refusal
   (R2 / amendment A1) in chat on 2026-10-21. No approval remains outstanding.

## Next handoff

**Next owner:** Tester. Produce
`workflow/features/student-ai-help/test-report.md` mapping actual results to
the acceptance criteria in `spec.md`, independently of this file. Please
record explicitly whether `SoCLaaSClientTest` could be run in your
environment.

**Then:** Acceptance auditor, since this is security-sensitive work, once the
tester reports `Pass`.

**Blocking for release, not for testing:** a run of `SoCLaaSClientTest` and
one live end-to-end request on an environment that permits loopback binding
and holds a real `SOCLAAS_STUDENT_API_KEY`.
