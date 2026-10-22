# Student AI help — student UI design

Status: Approved for implementation

Approval: Human product-owner approval recorded in chat, 2026-10-21.

Last handoff: UI designer → Human product owner, 2026-10-21 — layout, scope
labelling, citation display, refusal copy, and every failure panel for the
approved three-scope behaviour in `workflow/specs/student-ai-help.md`.

## Scope of this design

The student course view gains one new tab. Nothing else in the product
changes. This design adds no control that writes to the forum, no control that
reaches another course, and no surface that displays Knowledge Map text, page
text, prompts, model names, or gateway diagnostics.

It reuses the existing shell: the same course header, the same tab strip, the
same card, button, badge, link, and inline-notice patterns already used by
Forum, Resources, and the staff Materials tab. No new colour, font, or
component family is introduced.

## Placement and visibility

- A tab labelled **AI help** is appended to the student tab strip, after
  **Resources**.
- The tab renders only for a signed-in user whose account role is `student`
  and who holds a current student membership in the open course. Staff never
  see it.
- Tab visibility is presentation only. The server rejects an unauthorized
  request regardless, and the UI must not imply otherwise.

## Layout

Single column, matching the width of the Forum tab.

1. **Heading and standing notice.** `AI help` as the tab heading. Directly
   under it, one short standing line, always visible, never dismissible:

   > Grounded in this course's PDFs. It gives direct answers about how the
   > course runs, and study guidance — not answers to submit. Nothing here is
   > posted to the forum.

2. **Ask panel.** A labelled multi-line field, four rows, placeholder
   `Ask about this course…`. Below it, left-aligned, a live counter
   `0 / 1000`. Right-aligned, the primary button **Ask**.

3. **Result region.** Empty before the first request. Holds exactly one
   result card or one state panel at a time — never both, never a history.

## Ask panel behaviour

- **Ask** is disabled while the field is empty or whitespace-only, and while a
  request is in flight.
- The field hard-caps input at 1000 characters. The counter turns to the
  warning colour from 900.
- Submitting with Enter is not bound; the field is multi-line, so Enter adds a
  newline. The button is the only submit.
- The question stays in the field after a result returns, so the student can
  refine it. It is cleared only by the student.
- Each submit replaces the previous result. There is no stored history,
  matching the specification's no-persistence rule.

## Result card

One card. Its first row is the **scope label** — a badge, left-aligned, in the
existing badge style:

| Outcome | Badge text | Badge tone | Body |
| --- | --- | --- | --- |
| `administrative` | `Course info` | neutral/info | The direct answer |
| `guidance` | `Study guidance — not an answer to submit` | accent | The guidance text |
| `refused` | `Guidance only` | accent | A fixed boundary sentence, with no citations (see the refusal note below) |
| `insufficient_evidence` | `Not enough in the material` | warning | Fixed neutral sentence (see below) |

The badge is never omitted and never truncated. It is the control that stops a
direct administrative answer being read as an answer to graded work, and
guidance being read as a solution, so it sits above the text rather than
beside or after it.

Body text renders as plain paragraphs through the existing safe renderer. No
raw HTML, no code execution, no auto-linking of anything the model returned.

### Citations

Under the body, a `Sources` block, shown only when the result carries
citations:

```
Sources
  week7_admin.pdf — p. 2      [Open]
  week3_complexity.pdf — p. 11 [Open]
```

- One row per cited filename/page pair, in the order returned, de-duplicated.
- `Open` is a link to that material's existing Resources download path. It is
  not a public URL; the server rechecks membership and material status on the
  click, so a stale citation fails there rather than here.
- The filename is rendered as text, never as markup. A filename is untrusted
  input.
- A result with an empty `citations` array shows no Sources block. `refused`
  never has any.

### Refusal copy, amended 2026-10-21

A `refused` result shows one fixed sentence and no Sources block:

> I give study guidance for this course, not answers to submit. Tell me which
> concept you are stuck on and I will point you to the material that covers it.

As first approved, this card was to show a redirect followed by cited study
guidance. Independent verification found that composing a refusal required
handing the model the course's source pages for exactly the request class that
had just asked for submission-ready work — so the academic-integrity boundary
would have rested on the model obeying its prompt, and a jailbreak that
succeeded would have produced a *grounded, citation-valid* solution. The
normative record, which `spec.md` makes authoritative on conflicts, already
specified no retrieval for this scope. The refusal is therefore composed
server-side with no retrieval and no gateway call. **Confirmed by the human
product owner in chat, 2026-10-21**, and carried into
`answer-guidelines.md` version `student-answer-policy/v2`; see `tasks.md`
remediation R2.

## State panels

Exactly one shows at a time, in the result region, in the existing inline
notice style. Every message is plain, non-technical, and discloses nothing
about another course, the provider, the model, or configuration.

| State | Trigger | Copy |
| --- | --- | --- |
| Loading | Request in flight | Spinner plus `Reading this course's material…`. **Ask** disabled. |
| Invalid request | Blank or over-length, caught client-side and server-side | `Enter a question first.` / `Shorten your question to 1000 characters or fewer.` |
| No material | `409 no_material` | `This course has no readable PDFs yet, so there is nothing to answer from. Ask on the forum instead.` |
| Insufficient evidence | `insufficient_evidence` | `The course material does not cover this well enough for a grounded answer. Try naming the topic or week, or ask on the forum.` |
| Rate limited | `429` | `You have asked a few questions in quick succession. Wait a moment and try again.` |
| AI unavailable | `503` | `AI help is unavailable right now. The forum and course resources still work — try again later.` |
| Permission denied | `404` | The tab is not reachable for an unauthorized user; if a request still returns 404, show `This course is not available.` and nothing else. |

No panel offers an automatic retry, a provider name, a status code, or a
"contact support" path. Rate-limited and unavailable states never show a
partial or cached answer.

## Accessibility

- The ask field has a visible `<label>`; the counter is referenced by
  `aria-describedby` on the field.
- The result region is `aria-live="polite"` so a returned result or state
  panel is announced once. The loading panel sets `aria-busy`.
- The scope badge carries its text content, not colour alone; tone is
  reinforcement. Badge and body text meet the existing contrast baseline in
  both the default and warning tones.
- Focus moves to the result region heading after a result returns, so a
  keyboard or screen-reader user is not left at the button.
- `Open` links are real links with discernible text including filename and
  page, not icon-only controls.

## Responsive behaviour

- At narrow widths the card and panels fill the column; the **Ask** button
  becomes full width below the counter.
- Source rows wrap filename and page onto two lines rather than truncating a
  filename, so a cited source is always identifiable.
- Nothing in this tab requires horizontal scrolling.

## Deliberate omissions

- No copy-to-clipboard control on the body, and no "post this to the forum"
  control. Both would make it easier to treat the output as submittable or
  publishable, which the specification forbids.
- No thumbs-up/down or feedback control: request persistence and analytics are
  out of scope.
- No display of which PDFs were searched beyond the citations actually used,
  and no display of Knowledge Map state. Students have no Knowledge Map
  surface.
- No streaming of partial output. A result is shown only after citation
  validation passes, so partial text can never appear and then be retracted.
