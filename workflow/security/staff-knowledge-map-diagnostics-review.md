# Staff Knowledge Map diagnostics and dashboard security review

Date: 2026-10-21
Status: Implemented; independent verification pending

## Scope

Review of the staff Resources upload overlay, renamed Knowledge Map workspace,
generated-map display/scrolling, server-side citation diagnostics, and revised
Knowledge Map prompt for `staff-material-governance` criteria 15–20.

## Trust-boundary controls retained

- Browser role visibility is presentation only. Upload, material reads,
  Knowledge Map generation, and review remain protected by the existing
  server-side staff/course authorization checks.
- Citation classification is server-side and does not authorize a map. The
  existing approval path still rejects a candidate with any verification
  message, and student navigation remains limited to approved, current maps.
- Verification still requires the exact canonical citation grammar, a matching
  source filename, and an extracted nonblank page in the current active PDF.
  A missing citation, alternative syntax, mismatched filename, invalid or
  textless page, and citation followed by text/punctuation receive concise
  staff-facing reasons. Reasons are deduplicated; the prior general message is
  retained for output where no specific safe reason can be established.
- The prompt treats source data as untrusted, prohibits following embedded
  instructions, prohibits invented citations, and now asks for plain topic
  headings, one factual claim per line, and exact canonical citations as the
  final characters without trailing punctuation. Prompt conformance does not
  replace the verifier or staff review.
- Returned map content remains text-rendered. The additional source-download
  links were removed; citations remain visible in the generated text. The
  content viewport is keyboard-focusable and scrollable, while status and
  review actions remain outside it.
- The upload overlay is exposed only from staff Resources UI, uses the existing
  authorized upload endpoint and constraints, has Escape/close dismissal,
  focus trapping/restoration, and blocks backdrop dismissal during upload.

## Verification evidence

- `sh scripts/test.sh` — PASS for Platform integration tests, including student
  and cross-course denials, citation approval blocking, stale/foreign/page
  source rejection, and new reason-specific cases. The expected malformed-PDF
  fixture emits a PDFBox diagnostic during the run.
- SoCLaaS client tests — PASS when run with loopback binding permitted; the
  test binds a local HTTP server and was blocked by the default sandbox on the
  first attempt.
- Frontend build — PASS (`tsc -b && vite build`) after syncing the changed UI
  sources into an isolated temporary project copy with recovered offline
  dependencies. Repository `node_modules` and `.pnpm-store` were not changed.
- Existing tests cover hostile PDF text framing/prompt injection, malformed
  uploads, unapproved/stale/cross-course source policy, and service failure.
  The present change does not alter these boundaries.

## Required negative checks for independent verification

- Student and unrelated-course actors cannot use upload, material, map, or
  review operations directly, regardless of staff-only button visibility.
- A map with a missing citation, noncanonical syntax, wrong filename,
  out-of-range or textless page, or trailing content remains staff-only and
  cannot be approved.
- A map with otherwise valid citations remains unapprovable when stale or
  source-invalidated; students cannot read an unverified map.
- A filename/PDF injection remains data and cannot change model policy,
  authorization, source lifecycle, citation verification, or review outcome.
- Modal focus, Escape close, upload error retention, upload-in-progress backdrop
  behavior, and the 70vh keyboard-scroll region work at desktop and narrow
  viewport sizes.

## Residual risk

The verifier validates citation shape and page provenance, not the semantic
truth of a generated claim or whether each line contains only one claim.
Authorized staff can still read inaccurate or malicious generated output in
the review-only state. Prompt guidance, text-only rendering, explicit
unverified labeling, and server-side approval blocking reduce impact but do not
fully solve prompt injection or establish factual correctness.

## Follow-up remediation: heading-line bypass

The initial implementation treated title-case uncited lines as headings. An
independent review identified that factual title-case lines could use this
heuristic to pass citation verification. The exemption has been removed. The
prompt now requires every nonblank line, including any plain topic heading, to
end with a canonical citation, and integration coverage verifies that a valid
cited claim plus `The Course Is Definitely About Nothing` remains unverified
and cannot be approved. Independent re-verification of the frontend build and
full acceptance scope is pending.
