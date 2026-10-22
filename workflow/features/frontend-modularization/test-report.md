# Frontend modularization — independent test report

Status: Pass
Tester: QA / Tester
Date: 2026-10-22
Scope: Approved `frontend-modularization` specification, the current working-tree
frontend refactor, `tasks.md`, and the code-quality remediation recorded there.

## Result

The build, typecheck, backend integration suite, static contract review, and
manual workflows pass. The post-test palette-key remediation is present and
compiles, and the product owner has supplied the previously missing direct
backdrop-dismissal evidence. All acceptance criteria now have passing evidence.

## Commands and actual results

| Command | Actual result |
| --- | --- |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm run typecheck` from `src/frontend` | Passed before and after the palette-key remediation. TypeScript project build completed with no diagnostics. |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm run build` from `src/frontend` | Passed before and after the palette-key remediation. The latest build transformed 41 modules and produced the production bundle. |
| `sh scripts/test.sh` from the repository root | Passed. `Platform integration tests: PASS` and `HTTP API integration tests: PASS`; the expected malformed-PDF fixture logged a PDFBox parsing error during its negative-path test. The SoCLaaS client test also completed successfully because the script exited zero after running it. |
| `git diff --check` | Passed. |

The first sandboxed run of the HTTP integration test could not bind its
loopback port. The same command was rerun with local loopback permission and
passed; the result above is from that passing run.

## Manual browser scenarios

I ran the Vite client against the available localhost Java service, using a
fresh staff account and course created for this verification. The account,
course, post, reply, and tag are development test data.

| Scenario | Actual result |
| --- | --- |
| Authentication and course shell | Passed. Created a staff account and `QA101`; the course navigation, staff-only tabs, course heading, and success status rendered. |
| Forum, thread, and comments | Passed. Created a post and reply. Both success status messages and the expected post/comment text rendered. |
| Resources and upload dialog | Passed for dialog opening, initial file-input focus, Escape dismissal, focus restoration to `Upload file`, and Tab wrapping from the final enabled control to `Close upload dialog`. |
| Forum-tag keyboard interaction | Passed for ArrowRight navigation: focus moved from Blue to Green, Green became selected, and computed focus outline was solid and 2px. |
| Members, Knowledge Maps, settings, and tag management | Passed. The staff membership view, empty Knowledge Map state, course rename, and tag creation all rendered and completed their API calls. |
| Narrow viewport, 640 × 800 | Passed for the course shell, forum card, upload dialog, and settings controls. The shell became one column, forum cards became vertical, the upload actions stacked in reverse order, and settings controls remained visible. The viewport override was reset after testing. |

## Defects and limits

### Resolved post-test remediation — forum-tag palette key

**Location:** `src/frontend/src/forum/ForumTagsSettings.tsx:166-212`

The prior test found that the `TAG_PALETTE.map` children had no React key. The
remediation now supplies `key={swatch.color}` on every mapped button. The
palette colour is stable and unique in `TAG_PALETTE`; the updated frontend
typecheck and production build both pass. The current browser automation
surface was unavailable for a second runtime-console observation, so this is
static and compile-time confirmation rather than a fabricated browser result.

### User-provided manual verification — backdrop dismissal

The product owner confirmed that, while no upload is busy, clicking the exposed
upload-dialog backdrop closes the dialog and returns focus to the `Upload file`
trigger. This completes the direct observation that the prior browser automation
could not perform. The earlier automated keyboard, Escape, initial-focus, and
focus-trap evidence remains unchanged.

## Acceptance-criteria coverage

| Criterion | Result | Evidence |
| --- | --- | --- |
| 1. Minimal bootstrap entry point | Pass | `main.tsx` only imports the stylesheet and `App`, then creates the Strict Mode root. |
| 2. Cohesive domain modules | Pass | Reviewed `api`, `app`, `auth`, `courses`, `forum`, `resources`, `members`, `settings`, `knowledge-maps`, and `shared`; each has the responsibility documented in `tasks.md`. |
| 3. Single API and type sources | Pass | `api/client.ts` is the only `fetch` caller; `api/types.ts` owns the shared frontend models. |
| 4. Readable TypeScript without suppression shortcuts | Pass | Formatting and TypeScript compilation pass, no `any`, `@ts-ignore`, `@ts-expect-error`, or lint-disable shortcut was found, and the palette map now supplies its stable React key. |
| 5. Organized CSS and preserved cascade | Pass | `styles.css` has explicit base, shell/shared, domain, and responsive sections; `git diff --check` and narrow-layout checks pass. |
| 6. Same workflows, controls, and text | Pass | Auth, dashboard, forum, thread/comment, resources, membership, settings, tags, and Knowledge Map empty state rendered and worked in browser testing. The subsequent palette-key fix is behavior-preserving and compiles. |
| 7. Compatible API requests and authorization model | Pass | Static comparison found the prior endpoint set retained across domain modules; the helper keeps `credentials: "same-origin"`, request headers, and error extraction. The passing HTTP API integration suite confirms server contract behavior. Client visibility remains based only on the authenticated response, while the server integration suite covers authorization. |
| 8. Keyboard, focus, dialog, alerts/status, responsive checks | Pass | Tag keyboard and visible focus, dialog initial/restored/trapped focus, Escape, status messages, and all named narrow layouts passed in prior testing. The product owner manually confirmed idle backdrop dismissal and focus restoration to `Upload file`; no prior automated/manual result changed. |
| 9. Typecheck and production build | Pass | Exact passing commands recorded above. |
| 10. Engineering handoff evidence | Pass | `tasks.md` provides module map, changed responsibilities, command results, limitations, and code-quality remediation. |

## Next handoff

Acceptance auditor: review the approved specification, engineering handoff,
code-quality evidence, and this passing independent test report.
