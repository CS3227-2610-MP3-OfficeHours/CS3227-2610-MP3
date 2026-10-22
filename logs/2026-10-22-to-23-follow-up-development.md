# 22–23 October — follow-up feature work and production fixes

**Kind: reconstructed.** Compiled from relevant Codex conversation records,
Git history, and the feature task and test artefacts. Commit subjects, dates,
and recorded test outcomes are verifiable. This summary omits unrelated chats
and does not infer outcomes where the artefacts leave them unresolved.

**Owner:** @p-xp and @ziyang.

---

## 1. Staff Resources and Knowledge Map refinements — 22 Oct

The staff workflow was refined around the Resources screen: upload moved behind
a staff-only dialog, Knowledge Map work was separated from material listing,
and long map content received an internal scroll region. The team checked
keyboard dismissal and focus restoration, student visibility, and the
generation prompt's citation format. The prompt was tightened to one claim per
line with a citation on every line after generated maps were being marked
unverified. Assertions were added to keep the required wording from regressing.

## 2. Frontend modularization and Forum Tags — 22 Oct

The large React entry module was divided into API, shared UI, authentication,
course, forum, resource, membership, settings, and Knowledge Map modules. The
refactor preserved routes and interaction behavior; follow-up code-quality
reviews led to clearer component formatting, shared tag presentation,
route-aware API response decoding, and stylesheet cleanup. Type-check, build,
backend integration, and manual workflow checks were recorded as passing in
the final test report.

Forum Tags added course-owned tags, a default General tag, migration/backfill,
forum filtering, post labels, and staff management. The work included
server-side membership and validation rules, a fixed color palette, a seven-tag
limit, persistence rollback on failed mutations, and post reassignment to
General when deleting a tag. Independent checks covered legacy migration,
authorization after membership revocation, persistence, keyboard behavior, and
frontend build. The current test report is **Pass**; the acceptance artefact
records the applicable audit state.

## 3. Staff LLM fallback and reasoning effort

The staff Knowledge Map integration gained a bounded two-model fallback. The
server validates configured model IDs against the authenticated service
catalog, retries once only after transient primary failures, and exposes a
fixed staff-facing disclosure only when fallback configuration is validated.
The adapter tests cover ordering, request consistency, status classes, and
failure behavior. The independent report records unresolved evidence gaps for
the full fallback-to-map validation path and live provider configuration; this
work is therefore not described here as fully accepted.

A separate staff request change set `reasoning_effort` to `none` while
preserving the prompt and source-message framing. Tests asserted the request
field and the unchanged prompt. The feature's test report records the Java
suite passing and notes that live provider behavior remains diagnostic rather
than guaranteed.

## 4. PDF upload behavior in deployment

After deployment, PDF processing timed out for some uploads. The investigation
checked the difference between local in-memory processing and deployed
execution, then removed the parsing time limit that was causing accepted PDFs
to be skipped. A follow-up preserved accepted PDFs as downloadable resources
when text extraction fails, with a dismissible failure state. These changes
were made in focused commits and covered by the PDF provenance and staff
material tests; deployment configuration also needed a Blueprint sync for its
settings to take effect.

## 5. Render deploy gate — 23 Oct

To make deployment wait for linked-branch CI checks, `render.yaml` was updated
to use Render's `checksPass` auto-deploy trigger. The change was committed and
pushed to the repository's default branch after confirming the actual branch
name. The conversation established that a Blueprint sync is needed to apply
the setting to an existing Render service. No claim is made here that the
dashboard sync itself was completed.

## 6. Evidence and limits

The feature-specific `tasks.md` and `test-report.md` files remain the detailed
records for each implementation and verification. In particular, a passing
server suite or configuration commit does not establish live provider behavior,
Render deployment, or acceptance when the corresponding report still records
unverified criteria.
