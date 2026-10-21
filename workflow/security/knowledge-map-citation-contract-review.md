# Knowledge Map citation-contract review

Status: Implemented; independent verification pending

## Finding

The LLM instruction requested a filename and page reference but gave no literal
citation grammar. The server accepted only `filename.pdf, p. N`, so semantically
equivalent output such as `[filename.pdf pN]` was wrongly classified as
unverified.

## Change reviewed

- The staff generation prompt now specifies `(filename.pdf, p. N)` and includes
  a concrete example.
- Server validation accepts the canonical form plus optional comma/brackets and
  `p`, `p.`, or `page` spelling.
- Validation still resolves only against the current active course material's
  exact filename and persisted, nonblank extracted page numbers.

## Security properties retained

- Citation syntax flexibility does not accept a wrong filename, a wrong course,
  inactive material, an unextracted page, or a nonexistent page.
- PDF text and model output remain untrusted; no citation changes authorization
  or approval state.
- A map with any active source lacking a valid material/page match remains
  staff-only and unapprovable.

## Tests

- Client adapter test asserts the canonical syntax and example are sent in the
  system instruction.
- Platform integration test accepts `[Week 2.pdf p1]` and
  `[Week 3.pdf page 1]` only when those pages exist in active provenance.

Residual risk: filename-based display citations can be ambiguous if a course
has duplicate filenames. The longer-term structured `materialId`/page citation
contract remains the preferred mitigation.
