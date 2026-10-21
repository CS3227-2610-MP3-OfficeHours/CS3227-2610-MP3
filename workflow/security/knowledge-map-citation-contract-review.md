# Knowledge Map citation-contract review

Status: Implemented; independent verification pending

## Finding

The LLM instruction requested a filename and page reference but gave no literal
citation grammar. The verifier's canonical grammar is `(filename.pdf, p. N)`.
The approved staff-dashboard addendum now explicitly asks the model for this
exact form and requires it at the end of each claim line.

## Change reviewed

- The staff generation prompt now specifies `(filename.pdf, p. N)` and includes
  a concrete example.
- Server validation accepts only the canonical form; alternative syntax is
  classified as noncanonical and remains unverified. This intentionally
  matches the approved prompt contract.
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

- Client adapter tests assert plain headings, one claim per line, exact
  canonical syntax, and no punctuation after the final citation.
- Platform integration tests cover missing citations, noncanonical syntax,
  mismatched filenames, invalid/unextracted pages, trailing text/punctuation,
  and the supplied Markdown/bullet-shaped failure example.

Residual risk: filename-based display citations can be ambiguous if a course
has duplicate filenames. The longer-term structured `materialId`/page citation
contract remains the preferred mitigation.
