# Staff Material Governance — Stage 4A Engineering Handoff

Status: Implemented locally; awaiting independent verification

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
remains blocked on the SoC operating-envelope decision.
