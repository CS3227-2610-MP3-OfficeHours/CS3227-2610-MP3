# PDF parsing and provenance — engineering handoff

Status: Implemented; independent verification pending

Approval: The product owner approved the parser plan and security review on
2026-10-21.

## Delivered

- Added Apache PDFBox 3.0.8 as a pinned SHA-512-verified local build/runtime dependency via `scripts/ensure-pdfbox.sh`.
- Replaced raw-byte literal extraction with strict PDFBox page-by-page text extraction, bounded page/text limits, and safe failures.
- Added server-owned, SHA-256-tied `PdfPage` provenance records and PostgreSQL migration `V4__material_pdf_pages.sql`.
- Added the `resource_only` state: image-only PDFs remain downloadable but are excluded from Knowledge Map packets and citation approval.
- Changed maps and citation validation to consume only current persisted page records, not the PDF byte stream.
- Added the approved staff upload compatibility notice and clear processing, text-ready, resource-only, and failed status wording.

## Evidence recorded so far

- Platform integration tests pass; frontend production build passes.
- Strict PDFBox parsing of supplied `l0.pdf` yielded 26 pages and 101 characters on page 1.

## Remaining verification

- Independent tester report including UI, parser security corpus, and loopback-enabled server suite.
- Acceptance audit after the tester passes.
