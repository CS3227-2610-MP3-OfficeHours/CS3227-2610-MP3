# Forum Tags — Independent Test Report

Status: **Pass**
Tester: Independent verification
Date: 2026-10-22

## Summary

The initial four findings passed their recheck. The persistence-failure test confirms an injected exception during tag deletion restores the tag and its post association. The newest staff-revocation test first confirms a staff user can list, create, and edit course tags, then restores the same user/course/tag state into a fresh service without that course membership and verifies tag list/create/edit/delete all return the hidden-access response. No blocking defect remains in these behaviors.

## Verification run

| Command | Result |
| --- | --- |
| `sh scripts/test.sh` | `Platform integration tests: PASS`. The overall script exited 1 afterward because `SoCLaaSClientTest` could not bind its local HTTP server (`SocketException: Operation not permitted`). The PDFBox malformed-PDF diagnostic was expected by that suite. |
| `sh scripts/test-postgres.sh` | **PASS** against the local PostgreSQL container. PostgreSQL tag create/edit persistence, post association, seven-tag cap, delete-to-General, membership revocation, and course deletion checks passed. |
| `PATH=<bundled-node-dir> pnpm run typecheck` | **PASS** using the bundled Node runtime because `node` is not on the default shell PATH. |
| `PATH=<bundled-node-dir> pnpm run build` | **PASS**; Vite generated the production bundle. |
| `git diff --check` | **PASS**. |
| PostgreSQL legacy-schema scenario in `PostgresPlatformIntegrationTest.migrationBackfill` | **PASS**, as part of `sh scripts/test-postgres.sh`: seeded V1–V5 data survived V6; post/reply identifiers, bodies, authorship and timestamps were checked; the post received General; a second startup retained the same General and association. |
| Unicode whitespace cases in `PlatformIntegrationTest.forumTagLifecycle` | **PASS**, as part of the platform integration test: whitespace-only NBSP/EM SPACE/BOM is rejected, and those characters around a valid name are trimmed. |
| Persistence rollback and membership-revocation cases in `PlatformIntegrationTest` | **PASS**, as part of `sh scripts/test.sh`'s platform integration stage: injected `afterMutation` failure surfaces and restores the custom tag and original post tag ID; revoked student is denied tag reads, filtered posts, post detail, and create/edit/delete tag calls. |
| Staff membership revocation case in `PlatformIntegrationTest.forumTagStaffMembershipRevocation` | **PASS**, as part of the latest `sh scripts/test.sh` platform integration stage: staff successfully lists/creates/edits tags before revocation; `Snapshot` is restored into a fresh service with only that course membership filtered out; the same staff identity then receives 404 for list/create/edit/delete. |
| `sh scripts/test-postgres.sh` on this recheck | **PASS** against the local PostgreSQL container. |

No browser-driven UI or assistive-technology test was available/run. No source files were changed during this verification.

## Rechecked findings

1. **Unicode whitespace-only tag names — resolved.** The server uses Unicode-aware whitespace trimming before empty/length validation. The platform integration test rejects a name containing only NBSP, EM SPACE and BOM, and confirms trimming around a valid name.
2. **Stale tag choices after refresh failure — resolved by source review.** `PostEditor.loadTags` clears the tag list and selected ID in its error path. The submit handler and button both require a selected ID present in the current tag list, so a failed reload leaves publishing/saving disabled; a retry control is shown. No browser/network-failure injection test was run.
3. **Color radio group keyboard behavior — resolved by source review.** Only the selected swatch has `tabIndex=0`; the others have `-1`. Arrow keys move focus and update the selected color, and Home/End select the first/last swatch. Native button Space activation remains available. No browser/screen-reader interaction test was run.
4. **Legacy migration backfill and idempotence — resolved by PostgreSQL test.** An isolated schema is built through V1–V5, seeded with a course, post and reply, then V6 is applied through `PostgresPlatform.open`. The test verifies a General tag, post association, preserved post/reply fields and timestamps, and a repeated startup without a duplicate/replaced General. The test does not separately seed a course with zero posts; the migration's General insert selects from `courses` independently of posts, so that path is supported by source review.
5. **Persistence failure during tag deletion — pass.** The test injects an exception through the `afterMutation` persistence callback after the tag and posts are provisionally changed. It verifies the exception surfaces, the custom tag remains readable, the post's exact tag ID is restored, the post remains visible under that tag filter, and General remains intact. The fixture has one post assigned to the deleted tag; it does not inject a failure into a live PostgreSQL transaction.
6. **Membership revocation for tag/forum access — pass.** The student revocation test checks tag listing, filtered posts, post detail, and tag create/edit/delete denial. The newer staff-specific test closes the prior role ambiguity: it demonstrates successful tag listing/create/edit as course staff, then transfers the same state to a new `PlatformService` while omitting only the staff member's course-membership row. Tag list/create/edit/delete all return 404 afterward, despite the user's global staff role and the course/tag records still existing.

## Acceptance criteria

| # | Result | Evidence |
| --- | --- | --- |
| 1 | Pass | Service integration verifies new courses start with General and API-service attempts to rename/recolor/delete it are rejected. Service uses the default grey `#475569`. |
| 2 | Pass | The PostgreSQL test seeds legacy V1–V5 data, checks post/reply preservation and General association, then restarts to verify no duplicate/replaced tag. Migration source creates General from all courses, including courses with no posts. The zero-post course path was source-reviewed but not separately seeded. |
| 3 | Pass | Service integration verifies a course staff member can create tags while a student and a staff non-member are denied. Route handlers delegate to the service authorization checks. |
| 4 | Pass | Integration tests reject Unicode whitespace-only input and verify Unicode whitespace is trimmed around a valid name, in addition to duplicate, emoji, control, markup, and length checks. |
| 5 | Pass with a coverage note | Service integration verifies the seven-tag maximum and two simultaneous creates with one slot remaining. PostgreSQL verifies the database cap; it does not simulate competing database processes. The database trigger locks the course row before counting. |
| 6 | Pass | Service validation limits custom colors to the approved six values; the migration constrains stored colors; General has its separate grey value. The approved palette and selected tints are documented in `design.md`. |
| 7 | Pass by code review; no visual run | Posts retain tag identity and current tag data is joined into post responses via the current tag record; list cards and thread render that response. PostgreSQL verifies tag edit persistence, but no browser test checked every preview/thread rendering. |
| 8 | Pass | Successful deletion and the injected persistence-failure rollback are covered. The rollback assertion verifies the exact prior tag ID for the fixture post and tag visibility after the failure. The injected failure uses the service persistence callback, not an induced PostgreSQL transaction failure. |
| 9 | Pass by code review and service test | Service filter test returns matching posts. Forum pills are immediate single-select buttons with `aria-pressed`; selecting the active pill clears the filter. |
| 10 | Pass by code review; no browser run | Empty filtered results expose a Clear tag filter action. `refresh` reloads current tags, drops a stale deleted ID, announces removal, and requests the unfiltered feed. |
| 11 | Pass by source review | Filter pills remain native buttons with `aria-pressed`. The custom color group now exposes a single Tab stop, moves selection/focus with arrow keys, and supports button activation. No browser/screen-reader test was run. |
| 12 | Pass by source review | Create/edit selector placement/defaults and server validation remain intact. A failed tag refresh clears options and selected ID, and both the submit button and submit handler require a current tag. No browser/network-failure injection test was run. |
| 13 | Pass by code review | Post responses resolve the current tag record, and both preview and detail render its name as React text with the stored color. Service persistence keeps tag ID stable on rename/recolor. |
| 14 | Pass | Tests verify a removed student gets 404 for tag reads, filtered posts, post detail and tag mutations. Separately, the same staff identity that successfully listed/created/edited tags before revocation is restored without its course membership and receives 404 for tag list/create/edit/delete. This exercises current membership enforcement for staff management even though the public `removeStudent` operation cannot remove staff. |
| 15 | Pass by source review | Settings and forum loading/error/retry behavior remains; failed tag-list reload disables post submit and preserves title/description. No browser test checked focus/error announcements. |

## Next action

Both audit findings pass the available checks. The persistence failure injection restores the fixture's prior association. The staff-revocation test proves authorization both before and after membership removal through the state-load/restore path. Remaining evidence limits: failure was injected at the service persistence callback rather than PostgreSQL itself, and `scripts/test.sh`'s full run is blocked after platform tests by the sandbox's local HTTP bind restriction in `SoCLaaSClientTest`.
