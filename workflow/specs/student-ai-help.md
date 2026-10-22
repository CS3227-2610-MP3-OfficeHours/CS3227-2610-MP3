# Student AI help: answer-scope classification and map-narrowed retrieval

Status: Approved for implementation

Approval: Human product-owner approval recorded in chat, 2026-10-21 — the
three-scope boundary, `answer-guidelines.md` (v1, and v2's narrowed refusal),
and the accepted lexical-retrieval limitation.

This document is the normative decision record for the student-role LLM
feature owned by **@ziyang**. It resolves the two decisions that
`workflow/features/student-ai-help/spec.md` left open: the academic-integrity
boundary, and how student retrieval uses the staff-generated Knowledge Maps.
It does not restate the shared platform, ingestion, or Knowledge Map contracts
already approved in `workflow/specs/per-material-knowledge-maps.md` and
`workflow/specs/pdf-parsing-provenance.md`.

## Decision summary and critique

1. **Two answer scopes, not one.** The earlier specification allowed only
   guidance. That is wrong for course administration: a student asking "when
   is the midterm" is not asking for assessed work, and refusing it makes the
   assistant useless for the help-desk role the product exists to serve.
   This document therefore defines two permitted answer scopes —
   `administrative` (direct factual answer) and `academic` (guidance only) —
   plus a `refused` scope for attempts to obtain submission-ready work.

   *Critique accepted:* a scope decision is a security control, so it must not
   be left to a single model judgement. The chosen design makes the scope the
   **more restrictive** of a model classification and a deterministic pattern
   screen, and the classifier never sees retrieved material. See
   "Answer-scope decision" below.

2. **Retrieval narrowing is deterministic, not a second generative call.**
   The staff Knowledge Maps exist so the student assistant does not have to
   read every PDF per question. Using a further LLM call to pick materials
   would raise gateway usage per question against an unquantified SoCLaaS
   envelope, and would place untrusted map text in a control-flow position.
   Narrowing is therefore a server-side lexical scoring pass over
   **approved, source-current** Knowledge Maps and active page text. The LLM
   is used for the two things only a model can do: scope classification and
   answer composition.

   *Known limitation, accepted:* lexical narrowing is weaker than embedding
   retrieval for paraphrased questions. `bge-m3` embeddings are available on
   the gateway, but embedding ingestion is not implemented and adding it now
   would change the approved ingestion contract. Recorded as a documented
   limitation rather than a silent gap.

3. **Grounding and citation rules are unchanged and still binding.** Both
   permitted scopes must cite original PDF filename and page. An uncited or
   wrongly cited result is not an answer; it becomes
   `insufficient_evidence`. Administrative answers are not exempt.

4. **No new persistence.** Student questions, answers, and resolved scopes are
   not stored. The feature adds no table and no migration. This keeps the
   out-of-scope ruling on request history from the feature specification and
   avoids retaining student prompts.

## Goals

- An enrolled student can ask a course-scoped question and receive either a
  direct administrative answer or labelled study guidance, each grounded in
  active course PDFs with filename/page citations.
- The assistant narrows its search using approved Knowledge Maps before
  reading page text, so a question does not require every course PDF.
- Attempts to obtain submission-ready coursework answers, to override policy,
  or to reach another course's material fail safely and observably.
- Gateway usage per student question is bounded and locally rate-limited.

## Non-goals

- Embedding-based or vector retrieval.
- Persisted chat history, follow-up turns, analytics, or feedback capture.
- Automatic forum posting, replying, editing, or moderation.
- Any student-visible access to Knowledge Map text, raw page text, prompts,
  model identifiers, or gateway diagnostics.
- A precise, graded threshold between "enough guidance" and "too much".
  The rule stays the no-submission-ready-content rule.

## Roles and authorization

| Actor | Permitted |
| --- | --- |
| Signed-in user with account role `student` **and** a current `student` membership in the requested course | May call the student AI endpoint for that course |
| Course staff | **Not permitted.** Staff have the Knowledge Map flow; the student flow is rejected for them |
| Non-member, wrong-course, unauthenticated | Rejected before retrieval, with an indistinguishable not-found response |

Authorization is server-side and is checked before classification, retrieval,
and any gateway call. A rejected request must not reveal whether the course
exists or whether it holds material.

## Answer-scope decision

The server decides one scope per request, before composing an answer.

### Scopes

| Scope | Permitted output |
| --- | --- |
| `administrative` | A direct factual answer about course logistics — schedule, deadlines, assessment dates and weightage, venues, policies, where material lives — with citations |
| `academic` | Direction, the next step in a line of reasoning, a conceptual explanation, or a clarifying question, with citations. No complete solution, worked answer, or copy-pasteable submission content |
| `refused` | A short fixed boundary-preserving redirect, with no guidance and no citations. *Amended 2026-10-21:* this scope performs no retrieval and makes no composition call, so the model is never handed course material for a request that asked for work to submit. See `answer-guidelines.md` amendment A1 |

### How the scope is decided

1. **Deterministic screen (authoritative, runs first).** *Amended 2026-10-21
   during implementation; see amendment note below.* The server first folds
   obfuscation out of the student's own text — NFKC normalization, removal of
   invisible formatting characters, whitespace collapsing — and then matches
   it against three fixed pattern sets, in this order:
   - *override markers* — policy-attack phrasing (for example
     `ignore previous`, `system prompt`, `developer mode`, `reveal your
     instructions`, `pretend you are`), matched both in readable form and
     against a compact no-separator form of the question, so spaced-out,
     punctuated, and zero-width-smuggled variants are caught. Forces
     `refused`.
   - *submission-demand markers* — unambiguous demands for work the student
     would submit, requiring either an explicit verb (`give me the
     solution`, `show me the code`, `what is the answer`) or a possessive
     (`do my assignment`, `write our proof`). Forces `refused`.
   - *assessment markers* — broader graded-work phrasing (`final answer`,
     `the answer to`, `solve this`, `write the code`, question labels such as
     `q3a`, `part (b)`). Forces at most `academic`.

   Bare `assignment`, `homework`, and `exam` are deliberately **not** markers:
   "when is assignment 2 due" is course administration, and matching the noun
   alone would make the feature refuse the questions it exists to answer.
2. **Model classification (advisory).** A short, separate gateway call sees
   **only the student's question** — never retrieved page text, filenames,
   Knowledge Map content, or forum text — and must return exactly one of
   `administrative`, `academic`, `refused`. Any other output, a malformed
   response, or a provider failure yields `academic`.
3. **Resolution.** The effective scope is the more restrictive of the two
   results, ordered `administrative` < `academic` < `refused`. The screen can
   only tighten the model's answer, never loosen it.

Because step 2 sees no retrieved content, indirect prompt injection inside a
PDF, a filename, a Knowledge Map, or a forum post cannot change the scope.
Because step 1 is authoritative and fails closed, a compromised or jailbroken
classifier cannot widen it either.

**Amendment note, 2026-10-21 — confirmed by the human product owner in chat,
2026-10-21.** As first
approved, this section capped every solution demand at `academic` and left
`refused` to the override markers and the classifier. That contradicted
`answer-guidelines.md`, which lists "an explicit demand for the solution, the
final answer, or the code to submit" as a refusal trigger: the guidelines'
promise would have depended on the classifier, which is advisory by design.
The screen was therefore split into the two tiers above so an unambiguous
submission demand is refused deterministically, while broader graded-work
phrasing still only caps at `academic`. The obfuscation folding and the
compact pattern set were added at the same time, from the attack taxonomy in
the course's agent-security material. All three changes tighten behaviour and
none widens it, but they change approved product behaviour and are recorded
here for confirmation. See `workflow/features/student-ai-help/tasks.md`,
technical decisions 1 and 2.

### Answer guidelines

`workflow/features/student-ai-help/answer-guidelines.md` is the versioned,
human-approved statement of what each scope may and may not say. It is the
single source for the scope rules rendered into the composition prompt, and
its version string is recorded in the prompt template. Changing permitted
answer behaviour means changing that document and re-approving it; it is not
a prompt-tuning exercise.

## Retrieval contract

Retrieval runs only after authorization and scope resolution, and only for
`administrative` and `academic`. A `refused` scope composes from the
guidelines alone and performs no retrieval.

1. **Candidate set.** Materials in the requested course with status `active`
   whose extracted pages are present and not invalidated. Nothing else is
   eligible: `processing`, `resource_only`, `failed`, and `deleted` materials,
   and any material from another course, are excluded before scoring.
2. **Map narrowing.** For each candidate material, if an **approved**
   Knowledge Map exists whose stored material digest still matches the current
   PDF bytes, the map text contributes to that material's relevance score.
   A `pending_review`, rejected, absent, unverified, or digest-stale map
   contributes nothing and never makes a material eligible on its own.
3. **Page selection.** Score each eligible page by overlap between the
   question's content terms and the page text, boosted by its material's map
   score. Select the highest-scoring pages subject to hard caps: at most
   **8 pages**, at most **3 materials**, at most **24,000 characters** of
   page text per request.
4. **Empty outcomes.** No eligible material yields `no_material`. Eligible
   material that scores nothing for the question yields
   `insufficient_evidence`. Neither performs a composition call.
5. **Packet form.** Selected pages are sent as explicitly quoted, delimited
   data carrying only filename and page number, in the same
   `[SOURCE filename=... page=...]` form the staff flow already uses. Page
   text is never returned to the client.

Eligibility is re-read at the moment of retrieval, so a material deleted or
invalidated mid-request is not newly retrieved.

## Citation and grounding contract

- Every material claim ends with a citation in the exact form
  `(filename.pdf, p. N)` — comma, `p.`, one-based page number.
- Validation is deterministic and runs on the returned text before anything
  is shown: every cited filename/page pair must appear in the packet for
  **this** request, and a line that names any `.pdf` must cite it in the
  canonical form. A claim line with no citation fails.
- A result that fails validation is never presented as an answer. It becomes
  `insufficient_evidence` with no partial text shown.
- Citations are returned as structured records (`materialId`, `filename`,
  `page`, `downloadPath`) pointing at the existing, re-authorizing Resources
  download path. A stale citation cannot outlive membership revocation or
  material deletion, because that endpoint rechecks both.

## Prompt and injection controls

- One versioned composition template. The system message states the scope
  rules from the approved guidelines, the citation format, and that every
  string inside the source and question delimiters is untrusted data.
- The student's question is delimited and quoted, never concatenated into the
  instruction text.
- Approved Knowledge Map text is used **only** for server-side scoring. It is
  never sent to the gateway and never shown to the student, so map content
  cannot carry instructions into either model call.
- The server never echoes the system prompt, template version, model id, or
  gateway status text to the client. Failure states are fixed, neutral
  strings.
- `temperature` is 0 and the response token budget is bounded for both calls.

## Operational controls

- Per-student, per-course rate limit: **5 requests per 60 seconds**, enforced
  in-process before any gateway call, returning a neutral rate-limited state.
- Request timeout on both gateway calls. Provider outcomes map as the shared
  adapter already defines them: 400 to rejected, 401/403 to configuration,
  429 to rate-limited, 5xx and timeout to temporarily unavailable.
- The student path uses `SOCLAAS_STUDENT_API_KEY` and its own configured chat
  model, separate from the staff key. When the student key is absent the
  feature reports `ai_unavailable` and the rest of the course stays usable.
- Structured server logs record correlation id, course id, resolved scope,
  page/material counts, status class, and latency. They never record the
  question text, page text, map text, citations, or the key.

## API contract

`POST /api/courses/{courseId}/ai-help`

Request: `{ "question": "<1-1000 characters>" }`

`200` response:

```json
{
  "result": {
    "outcome": "administrative | guidance | refused | insufficient_evidence",
    "answer": "...",
    "citations": [
      { "materialId": "...", "filename": "week7.pdf", "page": 18,
        "downloadPath": "/api/courses/.../resources/.../download" }
    ]
  }
}
```

`answer` is a fixed, neutral string for both `refused` and
`insufficient_evidence`, and `citations` is empty for both.

Failure responses reuse the existing error envelope:

Statuses and codes reuse the platform's existing error vocabulary in
`ApiException` rather than introducing a parallel one. *Corrected 2026-10-21:
an earlier draft of this table invented codes the server does not emit.*

| Status | Code | Condition |
| --- | --- | --- |
| 401 | `authentication_required` | no or expired session; raised by the shared session check before routing |
| 404 | `not_found` | non-member, wrong course, staff actor, unknown course — all indistinguishable |
| 409 | `conflict` | course has no eligible active text-ready PDF |
| 422 | `validation_error` | blank, over-length, or non-string question; rejected before any gateway call |
| 429 | `retry_limited` | local per-student limit, or gateway rate limit |
| 503 | `service_unavailable` | student key unconfigured, provider outage, or malformed provider output |

No endpoint returns page text, map text, prompts, model ids, or another
course's data in any state.

## Student UI plan

The student course view gains an **AI help** tab, visible only to students.
`workflow/features/student-ai-help/design.md` is normative for layout. The
tab holds a question field with a visible character limit, a submit control,
and one result card. The card always carries a scope label so a direct
administrative answer is never mistaken for a graded-work answer and
guidance is never mistaken for a solution. Citations render as named
filename/page links into Resources. Distinct, non-technical panels cover
loading, invalid input, no material, insufficient evidence, rate limited, and
AI unavailable. There is no control anywhere in this tab that posts to the
forum.

## Implementation sequence after approval

1. Add the student gateway path to `SoCLaaSClient`: student-key factory, a
   bounded classification call, and a bounded composition call.
2. Add `StudentAiService` holding the deterministic screen, scope resolution,
   candidate/narrowing/page-selection logic, citation validation, rate limit,
   and the neutral outcome mapping. No new domain record is persisted.
3. Wire `PlatformService` accessors for eligible pages and approved,
   digest-current maps, keeping authorization inside the service.
4. Add the route to `HttpApi` and the student-only tab to the frontend.
5. Add the test suite below, then record `tasks.md`.

## Required acceptance evidence

Automated tests must cover, at minimum:

1. Enrolled student, administrative question, direct answer with a valid
   citation.
2. Enrolled student, conceptual question, guidance labelled as such, with a
   valid citation and no complete solution.
3. Assessment-marker question that the classifier calls `administrative`,
   still answered at `academic` or tighter.
4. Override-marker question, `refused`, with no system prompt, no secret,
   and no solution disclosed.
5. Staff actor, non-member, wrong-course id, unauthenticated: identical
   not-found response and no gateway call made.
6. Indirect injection in page text, filename, and an approved Knowledge Map:
   scope, authorization, and citation rules unchanged; no forum action.
7. Material deleted or invalidated between upload and request: not retrieved.
8. `pending_review`, rejected, unverified, digest-stale, and cross-course
   Knowledge Maps: do not influence retrieval and cannot make a material
   eligible.
9. Provider returns a citation to an uncited page, another course's filename,
   or no citation at all: `insufficient_evidence`, no partial text shown.
10. Provider 429, 503, timeout, malformed JSON, and absent student key:
    the matching neutral state; forum endpoints keep working.
11. Blank and over-length questions: `validation` with no gateway call.
12. Sixth request inside a minute: `rate_limited` with no gateway call.

An independent tester maps actual results to the feature acceptance criteria
in `workflow/features/student-ai-help/test-report.md`.

## Recorded approval

Human product owner approved the three-scope boundary, the answer guidelines
document, and the accepted lexical-retrieval limitation in chat on 2026-10-21.
Implementation is authorized.
