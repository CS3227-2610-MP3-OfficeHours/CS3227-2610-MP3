# PDF parsing and provenance — independent test report

Status: Needs changes

Verified on: 2026-10-21

Scope: implementation review and local test execution for the approved
`workflow/specs/pdf-parsing-provenance.md` and
`workflow/security/pdf-parsing-provenance-plan-review.md`.

## Executed evidence

- `sh scripts/test.sh` (with local loopback enabled for the mock SoCLaaS
  server): **PASS** — `Platform integration tests: PASS`; `SoCLaaS client
  tests: PASS`.
- `cd src/frontend && pnpm build`: **PASS** — TypeScript and Vite production
  build completed. The package manager emitted its existing ignored-build
  warning for `esbuild`, but Vite completed successfully.
- Code review of `PdfPageExtractor`, `PlatformService`, PostgreSQL migration
  V4, API access paths, and the material-management UI.

The supplied integration suite demonstrates PDFBox-generated page extraction,
page-aware source packets, staff-only map access, cross-course denial, stale
map handling, and refusal to approve an unverified map. It also demonstrates
that malformed input is rejected, though PDFBox prints a parser stack trace to
stderr for the deliberately malformed test fixture.

## Initial verification defects

1. **[P1] Resource-only PDFs cannot be downloaded although the application
   advertises that they can.** `listResources` includes both `active` and
   `resource_only` materials, and the staff UI says that students can download
   a resource-only PDF. However, `PlatformService.downloadResource` permits
   only `active` material. The Resources page therefore renders a download
   link which returns a hidden/404 response for every scanned or image-only
   PDF. This violates acceptance criteria 2, 6, and 8. Make the download path
   allow `resource_only` for enrolled course members, preserving the existing
   course check, and add an integration assertion that downloads the scanned
   fixture.

2. **[P2] The required written status badges are not implemented.**
   `StatusBadge` displays raw enum values (`processing`, `active`,
   `resource only`, `failed`) rather than the approved visible labels
   **Processing text**, **Text ready**, **Resource only**, and **Text
   extraction failed**. The surrounding detail text is good, but does not
   satisfy the specified status indicator. Map enum states to those labels and
   add UI-level coverage or a focused rendered-component assertion.

3. **[P2] Parser security corpus and resource-bound evidence are missing.**
   The security review requires exercised cases for an indirect prompt
   injection, decompression bomb, over-page and over-text/parse-time limits,
   mixed image/text PDF, deletion/replacement while extraction is in flight,
   and forged/wrong-page citations. The current test uses a normal generated
   PDF, an empty-page PDF, malformed fixtures, and map lifecycle cases; it
   does not cover those required negative cases. Also, `MAX_PARSE_TIME` is
   checked only after `PDFParser.parse(...)` has returned, so the review does
   not establish a bound for time spent in initial parsing. Add bounded corpus
   tests and implement or explicitly document an enforceable parser-time
   limit before claiming criterion 3 or 7.

4. **[P2] Dependency delivery does not meet the approved build-tool slice.**
   `scripts/ensure-pdfbox.sh` downloads a SHA-512-checked jar at test/run
   time, but no real dependency build tool, lockfile, or dependency/security
   scan was added. This falls short of the approved implementation slice and
   makes clean/offline builds depend on Maven Central availability. Add the
   agreed dependency-management mechanism (or obtain and record a revised
   approved decision) with dependency provenance and scan evidence.

## Acceptance-criteria assessment

| Criterion | Evidence | Result |
| --- | --- | --- |
| 1. Compressed/selectable text with page citation | PDFBox page extraction and generated two-page fixture are exercised; source packet retains pages and a cited map can enter review. The supplied PowerPoint fixture was not independently exercised here. | Partial |
| 2. Image-only PDF stays resource-only/downloadable and excluded from AI | `resource_only` state and map exclusion are implemented, but the download path rejects it. | **Fail** |
| 3. Encrypted/malformed/bomb/page/byte bounds | Malformed, encrypted marker, page and byte checks reviewed; no bomb/over-page/timeout corpus evidence. | Partial |
| 4. Delete/replacement invalidates page records | Deletion removes in-memory records and map becomes stale; no persistence/replacement/in-flight extraction test. | Partial |
| 5. Invalid/wrong-page/cross-course citation cannot be approved | Course authorization and valid-page citation lookup are present; integration covers unverified approval denial, but not forged/wrong-page citation cases. | Partial |
| 6. Existing auth, resources, maps, stale behavior, SoCLaaS | Core tests pass, except the resource-only download regression above. | **Fail** |
| 7. PDF prompt injection stays untrusted | Source packet framing remains present; no direct or indirect injection test evidence. | Partial |
| 8. Upload guidance and accurate statuses | Compatibility notice is present and accessible; raw enum badge labels do not meet the approved wording. | **Fail** |

## Security and authorization review

The server continues to enforce course-staff access for material state and map
operations, and course-member access for resource listing/downloads. Extracted
page text has no public API and map input is source-framed. The resource-only
download regression is not a cross-course disclosure, but it breaks the
intended same-course availability contract. No evidence yet demonstrates the
required hostile-PDF and prompt-injection corpus.

## Re-verification — 2026-10-21

Engineering remediated both initially reported user-visible defects:

- `PlatformService.downloadResource` now permits an enrolled course member to
  download either `active` or `resource_only` material. The integration suite
  now asserts a successful download of the image-only fixture.
- `MaterialStatusBadge` maps the material states to the approved labels:
  **Processing text**, **Text ready**, **Resource only**, and **Text
  extraction failed**.

I re-ran `sh scripts/test.sh` with local loopback enabled. It passed both the
platform integration and SoCLaaS client suites, including the new
resource-only download assertion. The malformed-fixture test still causes a
PDFBox parser stack trace on stderr while the test passes.

I also attempted `cd src/frontend && pnpm build`. The installed wrapper
refused its dependency synchronization because `esbuild` is configured as an
ignored build script, so no new frontend production-build result is available
from this re-run. The prior production build completed successfully during the
initial verification, and the remediation itself is straightforwardly visible
in the rendered-component source, but a clean frontend build should be
restored in CI/local tooling.

## Remaining acceptance blockers

The two prior functional P1/P2 defects are fixed. The report nevertheless
remains **Needs changes** because the approved security review explicitly
requires negative-test evidence that has not been produced:

1. no exercised corpus for indirect PDF prompt injection, decompression bomb,
   mixed image/text PDF, page/text limits, parse-time limit, deletion or
   replacement during extraction, and wrong-page/forged citations;
2. the configured ten-second deadline is not enforceable during the initial
   synchronous `PDFParser.parse(...)` call, so it does not yet prove the
   required resource bound; and
3. no approved real dependency build tool/lock/scan has replaced the runtime
   JAR download mechanism, contrary to the implementation slice.

These are not cosmetic follow-ups: acceptance criteria 3, 4, 5, and 7 require
their evidence, and the workflow requires every applicable criterion to pass
before acceptance.

## Verdict

**Needs changes.** The resource-only download and status-label fixes pass
re-verification. Complete the listed security/resource-bound and dependency
delivery work, then repeat independent verification (including a clean
frontend production build).

## Focused citation-contract re-verification — 2026-10-21

Scope: canonical LLM citation instruction and server acceptance of compatible
staff-visible citation formats only.

### Result: Pass for this correction

- `SoCLaaSClient` now instructs the model to use the unambiguous canonical
  form `(filename.pdf, p. N)`, including the comma, `p.`, and a one-based page
  number. `SoCLaaSClientTest` asserts this exact instruction is sent.
- `missingMapCitationSources` accepts the canonical form and compatible
  `[filename pN]` / `[filename page N]` forms. It does not accept a filename
  alone: each match is checked against a nonblank, non-invalidated `PdfPage`
  belonging to the current active material with that exact filename.
- The integration test now exercises `[Week 2.pdf p1]` and
  `[Week 3.pdf page 1]`; both validate without an unverified-source gap.
- A wrong filename cannot satisfy another source because validation compiles
  and checks a separate filename-bound pattern for every active material. A
  wrong page cannot satisfy a source because the parsed number must occur in
  that material's stored extracted-page set. Such a result remains unverified
  and cannot be approved under the existing approval guard.
- Re-ran `sh scripts/test.sh` with local loopback enabled: **PASS** —
  `Platform integration tests: PASS`; `SoCLaaS client tests: PASS`.

The suite does not yet include explicit wrong-filename and wrong-page fixture
assertions; add them with the broader security corpus to protect this contract
against future regex changes. This is not a regression in the corrected
implementation, but it remains part of the report's already-recorded
acceptance-evidence gap.
