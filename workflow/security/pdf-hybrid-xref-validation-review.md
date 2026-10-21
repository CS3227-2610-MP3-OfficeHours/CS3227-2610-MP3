# PDF hybrid-xref validation review

Date: 2026-10-21

Scope: The upload-time structural PDF gate in `PlatformService` now accepts
standard hybrid xref layouts and compressed object-stream references used by
normal PowerPoint PDFs. This is an ingestion-boundary compatibility fix; it
does not change course authorization, storage visibility, processing,
retrieval, prompt construction, or generated-output behavior.

## Security checks retained

- Upload authorization remains server-side and course-staff-only before PDF
  inspection.
- Declared MIME type, 20 MiB limit, 100-page limit, PDF header, terminal EOF,
  catalog/page-tree validation, and encryption rejection remain required.
- Xref offsets must still match actual physical object headers.
- Xref-stream decompression remains capped at 1 MiB. The accepted `/Size` is
  now bounded by that cap and its declared entry width, rather than by the
  smaller count of physical objects. This supports compressed logical objects
  without allowing an unbounded xref table.
- Only xref entry types 0, 1, and 2 are accepted. Type 1 entries are still
  checked against physical offsets; type 2 permits a compressed logical object
  but cannot redirect the catalog/page-tree checks away from required physical
  root objects.
- PDF content and filenames remain untrusted data. This change does not treat
  any PDF text or object data as an instruction.

## Verification

- Added an integration regression fixture with a hybrid classic-xref wrapper,
  a referenced xref stream, and an xref stream that omits its own object.
- Existing negative tests continue to reject renamed non-PDF bytes, malformed
  PDFs, missing xref/startxref, incorrect xref offsets, dangling page trees,
  and encrypted PDFs.
- The supplied `l0.pdf` was exercised through the actual server upload method:
  it is accepted as a 26-page PDF and begins processing.
- `sh scripts/test.sh` passed with its isolated local fake gateway.

## Residual risk

The application still uses a deliberately bounded, dependency-free PDF
structural parser rather than a complete PDF implementation. Less common but
valid PDF constructs may still be rejected. The parser must continue to fail
closed for malformed structures; future compatibility expansion should add a
specific regression fixture and retain malformed-upload tests.
