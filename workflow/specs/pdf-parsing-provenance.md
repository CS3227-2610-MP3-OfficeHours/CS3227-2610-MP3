# Page-Preserving PDF Parsing and Provenance

Status: Approved and implemented — verification in progress

Approval: Human product-owner approved implementation and security review, 2026-10-21

Last handoff: Product owner → engineering, 2026-10-21

## Problem

The current dependency-free parser only scans raw PDF bytes for literal text.
It cannot reliably handle common compressed content streams, font encodings, or
object streams. Visually readable PowerPoint PDFs can consequently be marked
active but produce no page text for Knowledge Map generation and citation
validation.

## Outcome

Replace the literal-text scanner with a bounded, page-preserving parser so an
authorized active PDF produces a server-held sequence of text-bearing pages.
Every extracted fragment retains source material ID, immutable material
version/content digest, and one-based PDF page number. The parser is an
ingestion component only: browsers never receive parser internals or private
storage paths, and only minimal authorized excerpts reach the LLM.

## Users and scope

| Actor | Need | Behaviour |
| --- | --- | --- |
| Course staff | Upload normal lecture PDFs and generate reviewable maps. | See Processing, Active, or a specific safe extraction failure; active PDFs contribute page-aware text to manual map generation. |
| Course staff | Understand incomplete source support. | See which same-course PDF pages have no extractable text; may update/retry manually. |
| Enrolled student | Receive future grounded help. | No new parser UI or endpoint; future citations continue to resolve only against authorized active PDFs. |
| Unauthorized actor | Access another course’s material/provenance. | Denied without disclosing parser status, filename, text, or page metadata. |

In scope: text PDFs with compressed streams, object streams, common font
encodings, rotated pages, and page-by-page provenance for staff Knowledge Maps
and the future retrieval contract.

Out of scope: OCR for image-only/scanned PDFs; direct PDF/attachment submission
to an LLM; changing student academic-integrity policy; public downloads; and
automatic Knowledge Map generation.

## Proposed library and integration approach

Use Apache PDFBox 3.x (`org.apache.pdfbox:pdfbox`), pinning the exact reviewed
release and transitive dependency lock/checksum during implementation. It is a
mature Java library under Apache-2.0, supports text extraction, and its
official tooling supports bounded page-range text extraction. Run a dependency
compatibility/security scan and a Java 25 build spike before migration.

1. Replace the custom structural parser and literal extraction functions with
   a `PdfPageExtractor` server-side interface.
2. The PDFBox implementation opens a material from an in-memory bounded source
   or private server storage; it sets conservative memory/temp-file limits,
   page limit, and parser timeout/cancellation controls. It rejects encrypted,
   malformed, password-protected, or resource-exhausting documents.
3. Iterate pages one at a time. Extract normalized Unicode text with page
   boundaries preserved; retain the original page number even when a page has
   no usable text. Do not render text as HTML or execute embedded actions.
4. Store an immutable extraction record only after success: material ID,
   course ID, content digest/version, parser version, page number, normalized
   text, extraction state, and safe diagnostics. Delete/invalidate it with the
   source material and invalidate it whenever the source version changes.
5. Replace `sourcePacket` with bounded page-aware chunks derived from those
   records. Continue framing PDF text as quoted untrusted data; preserve the
   existing all-active-PDF snapshot and local provider bounds.
6. Validate generated Knowledge Map citations against stored page provenance,
   not a model-supplied filename alone. Prefer a structured internal citation
   representation (`materialId`, `page`) while accepting a documented staff
   display format at the API boundary.

## Data and API contract

Add a server-owned extraction/provenance model, not a public raw-text API:

| Field | Purpose |
| --- | --- |
| `material_id`, `course_id`, `material_digest` | Tie pages to the exact authorized source version. |
| `page_number`, `text`, `text_digest` | Preserve ordered, page-aware extracted data. |
| `parser_version`, `state`, `safe_failure_code` | Make retries and migrations diagnosable without leaking parser internals. |
| `created_at`, `invalidated_at` | Prevent stale pages from entering a new map/retrieval result. |

The existing staff material list may expose only a safe state such as “Text
available”, “No selectable text”, or “Could not extract text.” It must not
return stored extracted text, private object paths, parser stack traces, or
cross-course metadata. No student-facing extraction endpoint is added.

## Upload-page UI requirements

Extend the existing **Choose PDF** upload card for authorized course staff with
a reusable `PdfCompatibilityNotice` directly above the file chooser. Use the
existing calm blue-and-white card treatment; use an information icon plus text,
not colour alone. It must say:

> For Knowledge Maps and future AI help, choose a PDF with selectable text.
> Standard PDFs exported from PowerPoint, Word, or lecture-note tools are
> supported, including compressed text PDFs. Scanned or image-only PDFs can be
> shared in Resources but cannot yet be used for text-grounded AI help.

The notice also retains the existing PDF-only, 20 MB, 100-page, and 20-active-
PDF limits. It must not promise that browser-side checks prove compatibility;
the server remains authoritative.

After server processing, the `ResourceRow` displays one written `StatusBadge`
and concise staff-only detail:

| Server state | Badge/detail | Available action |
| --- | --- | --- |
| Processing | **Processing text** — “Checking this PDF for page-aware text.” | Delete |
| Active with text | **Text ready** — “Ready for Knowledge Maps and future AI help.” | Delete |
| Active without text | **Resource only** — “No selectable text was found. Students can download it, but it cannot support AI help yet.” | Delete |
| Extraction failure | **Text extraction failed** — safe reason and “This may be malformed, encrypted, password-protected, or too complex to process.” | Retry if eligible; Delete |
| Limit/validation failure | Existing error summary, with a specific server-provided reason. | Choose another PDF |

Do not expose raw extracted text, parser names/versions, stack traces, storage
paths, or source-support details to students. The status must be announced via
a polite live region after upload/processing completion, retain keyboard focus
behaviour already used by the upload form, wrap long filenames, and stack
cleanly below 768 px.

## Behaviour and failure states

- **Processing:** extraction job is running; not retrieval/map eligible.
- **Active with text:** every page record is ready; map generation may use it.
- **Active with no text:** PDF remains downloadable as a resource but is not
  eligible for text-grounded map/retrieval until an approved OCR policy exists.
- **Failed:** malformed/encrypted/password-protected/limit/timeout failure;
  show a safe action-oriented message and existing retry control where allowed.
- **Partial extraction:** default policy is fail closed for AI eligibility. The
  PDF is not included in a Knowledge Map snapshot unless every page operation
  completed; a product owner may approve a later partial-text policy.
- **Deletion or source change:** remove/mark provenance invalid before any new
  map or retrieval request; discard in-flight output if the snapshot changes.

## Security and abuse safeguards

- Authenticate and enforce course-staff authorization before upload,
  extraction-state reads, extraction retry, and map generation.
- Treat PDF bytes, filenames, extracted text, metadata, annotations, links,
  and embedded instructions as untrusted data. Do not follow links, run
  JavaScript, launch embedded files, or execute PDF actions.
- Apply byte, page, text-per-page, total-text, parse-time, and concurrent-job
  limits before persisting/sending extracted text. The exact values require an
  implementation benchmark and approval if they change product upload limits.
- Do not send whole PDFs to the LLM. Send only bounded, authorized,
  provenance-labelled text chunks needed for the manual operation.
- Log correlation ID, material ID (or safe hash), parser outcome, duration,
  page count, and bounded text counts; never log PDF bytes, full extracted
  content, credentials, prompts, vectors, or private storage locations.

## Acceptance criteria

1. A representative PowerPoint PDF with Flate-compressed text is accepted,
   yields ordered page text, and supports a valid `filename, p. N` map
   citation.
2. A scanned/image-only PDF is safely identified as no selectable text and is
   excluded from new text-grounded map/retrieval requests without being exposed
   cross-course.
3. Encrypted, malformed, decompression-bomb, over-page, and over-byte inputs
   fail safely within configured resource bounds.
4. Upload/delete/replacement invalidates old page records before a later map
   generation or retrieval request can use them.
5. A model citation to an inactive, wrong-course, nonexistent, or wrong page
   source cannot be approved or used for student navigation.
6. Existing staff/student authorization, Resources download behavior, manual
   map generation, stale-map exclusion, and SoCLaaS safe-failure behavior
   remain passing.
7. Direct and indirect prompt-injection text embedded in a PDF remains quoted
   untrusted data and cannot alter authorization, parser settings, prompts, or
   approval state.
8. The upload page explains selectable-text expectations before selection and
   accurately distinguishes **Text ready**, **Resource only**, and failed
   extraction after server processing without exposing internal diagnostics.

## Implementation slices after approval

1. Dependency spike: add pinned PDFBox 3.x through a real build tool, verify
   Java 25 compatibility, license, dependency scan, and the supplied PowerPoint
   fixtures. No behaviour switch yet.
2. Extraction service: introduce page records, bounded PDFBox extraction, safe
   states, migration/backfill strategy, and lifecycle invalidation.
3. Map integration: consume only current page records, move citation validation
   to structured material/page provenance, and improve staff diagnostics.
4. Verification: security corpus, resource-limit tests, fixture suite,
   regression tests, independent testing, and acceptance audit.

## Decisions required before implementation

1. Approve Apache PDFBox 3.x as the parser dependency after the version/license
   and Java 25 compatibility spike, or choose another library.
2. Confirm whether image-only PDFs remain resource-only until a separate OCR
   feature is specified (recommended), or authorize OCR scope/provider/cost.
3. Approve proposed bounded parse limits after the implementation benchmark;
   the current 20 MiB/100-page upload contract remains unchanged.
4. Confirm migration behaviour for already-active PDFs: background reprocess
   all at deployment versus mark maps stale and require staff manual updates.

## Required evidence before completion

Engineering records dependency provenance, migration/API details, test output,
and resource-limit benchmark results in the relevant feature handoff. An
independent tester verifies the acceptance criteria and security corpus; an
acceptance auditor reviews the artefacts only after a passing report.
