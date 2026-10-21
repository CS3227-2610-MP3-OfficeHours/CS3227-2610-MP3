# Staff Material Governance — Engineering Handoff

Status: Stage 4A independently verified; Stage 4B staff UI implemented locally and awaiting independent verification

Feature: `staff-material-governance` (approved non-AI Stage 4A only)

## Delivered

- Added server-authoritative source-material records, course ownership, safe
  display filename, lifecycle status, page count, timestamps, private content,
  and PostgreSQL migration `V2__course_materials.sql`.
- Added staff-only material list/upload/delete/retry routes and authorized
  member-only Resources list/download routes. Direct material reads and every
  download re-check current course membership server-side.
- Enforced the interim retry guardrail in the server model: no more than three
  retry submissions in one minute for the same staff member and course; a
  limited request receives a safe `429 retry_limited` response.
- Validated the Stage 4A local upload transport: `application/pdf`, 20 MiB,
  a conservative bounded structural PDF gate: classic xref tables or direct
  xref streams with a valid terminal `startxref` offset, xref offsets that
  match declared object headers, catalog root, unique object boundaries,
  resolved catalog-to-page-tree reference, resolved/directly consistent page
  child, terminal EOF, encryption rejection, and a 100-page marker count.
  Direct xref streams support unfiltered bytes and capped FlateDecode streams;
  malformed, filtered otherwise, or object-stream variants fail closed. The
  server never creates a filesystem path or returns a storage URL.
- Added React Resources and Manage materials views. The latter offers PDF
  upload, active count, lifecycle label, deletion confirmation, and clearly
  marks AI consolidation as unavailable pending the SoC operating envelope.
- Added integration coverage for student/staff authorization, declared and
  actual non-PDF rejection, structurally malformed PDF-like bytes including a
  dangling catalog `/Pages` reference, missing xref/startxref, a Flate xref
  stream with an incorrect object offset, encrypted PDF rejection, and a valid
  Flate xref-stream PDF,
  processing-to-active, processing-to-failed, manual retry,
  same-course download, cross-course denial, deletion, and immediate Resources
  removal.

## Intentional Stage 4A boundary

The dependency-free foundation first validates PDF structure synchronously,
then uses a small local asynchronous processor. It moves an accepted item from
`processing` to `active` only when the conservative non-AI text extraction
check succeeds; otherwise it moves to `failed` with a safe message, and staff
can submit a manual retry. It is intentionally not a production document
parser or a provenance/indexing worker: robust extraction, page provenance,
and every AI/RAG concern remain later-stage work. No SoC calls, embeddings,
consolidation, generated artefacts, or student AI retrieval have been added.

## Test evidence

- `sh scripts/test.sh` — PASS (`Platform integration tests: PASS`), 2026-10-20.
- `git diff --check` — PASS, 2026-10-20.
- `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH /Users/xp/.nvm/versions/node/v24.22.0/bin/npm run build`
  — PASS (`tsc -b && vite build`), 2026-10-20. The interactive login shell
  lacks npm on `PATH`; the installed Node runtime was used explicitly.

## Next owner

Tester: independently exercise the HTTP routes and browser UI, including an
enrollment revocation between Resources listing and direct download. Stage 4B
staff UI now needs independent browser verification against the completed Stage
3 map routes: no-map, generating/polling, failed, pending-review,
approve/reject, stale, source citations, and staff-only access.

## Stage 4B — Knowledge Map staff UI

### Delivered

- Replaced the Stage 4A-only consolidation placeholder with the approved
  staff-only Course Knowledge Map panel in Manage materials.
- Added manual Generate/Update confirmation using every active PDF; disabled
  it where no active source exists; and poll the already-authorized staff map
  endpoint while a job is generating.
- Added labelled semantic statuses, safe server failure feedback, an explicit
  pending-review approval/rejection flow, recorded-decision time, and a
  visibly distinct AI-generated content region.
- Rendered generated content strictly as text and showed source PDF/page
  citations as re-authorized Resources download links. No browser code stores
  a SoCLaaS key or calls the provider directly.

### Local test evidence

- `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm run build` in
  `src/frontend` — PASS (`tsc -b && vite build`), 2026-10-21.
- `sh scripts/test.sh` with the isolated local loopback fake gateway — PASS
  (`Platform integration tests: PASS`; `SoCLaaS client tests: PASS`),
  2026-10-21.
- `git diff --check` — PASS, 2026-10-21.

## Follow-up — hybrid PDF compatibility fix

- Corrected the server upload gate so it accepts standard hybrid xref layouts,
  compressed object references, and compact `/Type/Name` token syntax emitted
  by Microsoft PowerPoint while retaining bounded xref decompression and the
  existing malformed/encrypted-PDF rejections.
- Added a hybrid-xref integration regression fixture. The supplied `l0.pdf`
  was accepted through the same server upload method as a 26-page processing
  material after the fix.
- Security review: `workflow/security/pdf-hybrid-xref-validation-review.md`.

## Follow-up — staff review of unverified Knowledge Maps

- A returned Knowledge Map that lacks verified PDF/page support is now retained
  as an explicitly unverified, staff-only pending-review artefact rather than
  discarded. Its safe message identifies the affected source filenames.
- The server refuses approval while source-support gaps remain; the UI hides
  approval and offers Reject/Update instead. Students cannot read, navigate
  with, or retrieve from this state.
- Added integration coverage for retained unverified output and server-side
  approval denial. Security review:
  `workflow/security/unverified-knowledge-map-review.md`.
