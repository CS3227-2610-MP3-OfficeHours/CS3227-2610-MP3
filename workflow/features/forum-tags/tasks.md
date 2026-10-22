# Forum Tags — Engineering Tasks

Status: Final acceptance evidence finding addressed; engineering verification recorded, ready for re-audit
Approval: Scope and UI approved in `spec.md` and `design.md` before implementation.
Last handoff: Engineer → acceptance auditor, 2026-10-22 — added persistence-failure rollback and post-revocation tag-access evidence, including a staff actor whose membership is absent after state restore; automated evidence recorded.

## Implementation checklist

- [x] Add durable, per-course tag records and associate every forum post with exactly one tag.
- [x] Initialize General on new courses and transactionally backfill existing courses/posts in V6.
- [x] Enforce course membership/staff authorization, input validation, fixed colors, unique names, and the seven-tag cap in the service/API.
- [x] Add tag list/create/update/delete routes, single-tag forum filtering, and current tag data in post responses.
- [x] Add author-only tag selection for create/edit, forum filter pills, post/thread badges, and staff course-settings management.
- [x] Add service and PostgreSQL integration coverage for validation, access boundaries, limits, persistence, filtering, and General reassignment.
- [x] Verify delete/reassignment rollback when persistence fails, and verify tag reads/management after membership revocation.
- [x] Verify course staff access before and after reloading state without that staff membership.
- [x] Record implementation decisions and test results; hand off for independent acceptance verification.

## Technical decisions

- `ForumTag` is a course-owned domain record. `Post.tagId` refers to its durable ID, so renaming/recoloring updates every preview/detail through the current tag record without rewriting posts.
- `PlatformService` creates one grey, immutable General tag when creating a course. `V6__forum_tags.sql` adds General for every existing course, backfills every existing post, then makes `posts.tag_id` required. A composite `(course_id, tag_id)` foreign key prevents cross-course associations; migration bookkeeping and DDL run in one transaction.
- Custom tag names are trimmed, limited to 20 Unicode code points, and reject `<`, `>`, ISO control characters, and emoji code points. NFC plus Unicode upper/lower normalization is used for case-folded uniqueness and the durable normalized-name index. Duplicate names within a course return conflict; the same label is valid in another course.
- Name trimming and whitespace-only rejection use Unicode whitespace code points (including non-breaking/em spaces and BOM) on both server and client. The UI now rejects whitespace-only input after matching the server's trim behavior.
- General uses `#475569`. Custom colors are limited to the nine approved palette values in service validation and the database constraint. Migration V8 expands the stored palette without changing existing tags. Per-course insertion is synchronized in the service and guarded by a database trigger that locks the course row and counts all tags, including General.
- Postgres persistence currently rewrites a full snapshot. Tags are therefore saved with General first so the seven-tag trigger sees the correct running count. The migration runner now splits SQL while respecting quoted strings and PostgreSQL dollar-quoted function bodies. Default-tag immutability is enforced at the service/API boundary; no database delete trigger is used because the snapshot store must delete/reinsert rows for every durable save.
- Tag create/edit/delete operations restore their in-memory state if durable persistence fails. Delete reassigns all affected posts to the same course's General tag before persistence and restores the prior state on failure.
- Tag management routes are `/api/courses/{courseId}/forum-tags` and `/api/courses/{courseId}/forum-tags/{tagId}`. The existing post-list route accepts `?tagId=...`; create/edit require a `tagId` and responses embed the current tag object.
- The post selector displays the tag name plus its named color, preserving a text label in native controls. Color swatches are labelled radio buttons with a single Tab stop; arrow keys move and focus selection, while Home/End jump to the first/last color. Filter pills are native buttons with `aria-pressed`; the active pill can be toggled off. Filter requests use a sequence guard so an older response cannot overwrite a newer selection.
- A failed tag reload clears stale options and selected tag state, keeps the user's title and description, exposes retry, and prevents submission until fresh tag choices load successfully.
- Updated `scripts/test-postgres.sh` to include the pinned PDFBox dependency required to compile the full source tree; this fixes the repository's prior PostgreSQL test-script classpath omission.

## Changed areas

- `db/migrations/V6__forum_tags.sql`
- `src/main/java/edu/nus/tahelpdesk/Domain.java`
- `src/main/java/edu/nus/tahelpdesk/PlatformService.java`
- `src/main/java/edu/nus/tahelpdesk/HttpApi.java`
- `src/main/java/edu/nus/tahelpdesk/PostgresPlatform.java`
- `src/frontend/src/main.tsx`
- `src/frontend/src/styles.css`
- `src/test/java/edu/nus/tahelpdesk/PlatformIntegrationTest.java`
- `src/test/java/edu/nus/tahelpdesk/PostgresPlatformIntegrationTest.java`
- `scripts/test-postgres.sh`

## Verification evidence

- `sh scripts/test.sh` — **PASS** in this engineering environment. Platform integration tests passed, including tag authorization, denied General mutation, emoji/control/markup/length/color and Unicode whitespace validation, Unicode case-fold duplicate handling, cross-course denial, filter behavior, tag edit identity, delete-to-General behavior, seven-tag maximum, and simultaneous competing creates. SoCLaaS client tests passed. This command required the local execution permission that permits the test's ephemeral loopback bind; the independent tester's separate environment blocked that bind after `PlatformIntegrationTest`, so its full-script run was environmentally blocked. PDFBox emitted its malformed-fixture diagnostic; suite exited successfully.
- `sh scripts/test-postgres.sh` — **PASS** after correcting its PDFBox classpath. PostgreSQL integration verifies tag create/edit persistence, post-tag persistence, deletion/reassignment, membership revocation, and course deletion.
- PostgreSQL legacy migration fixture — **PASS**. The test creates a legacy V1–V5 schema with existing course, post, and reply rows and explicit content/timestamps, applies V6 via `PostgresPlatform.open`, and verifies General assignment plus preservation of post/reply IDs, authors, content, and timestamps. A second startup preserves those rows and does not duplicate General.
- Frontend `pnpm run typecheck` — **PASS**.
- Frontend `pnpm run build` — **PASS** (`vite v7.1.3`, production bundle generated).
- `git diff --check` — **PASS** after remediation.
- Persistence failure injection — **PASS** in `PlatformIntegrationTest`: the mutation hook throws during tag deletion after the in-memory move; after failure, the custom tag exists, the post still references it, the tag filter still returns the post, and General remains intact.
- Membership revocation authorization — **PASS** in `PlatformIntegrationTest`: after removing a student from a course, tag listing, filtered post listing, and direct access to a previously visible tagged post all return 404; tag create/edit/delete also return 404.
- `sh scripts/test.sh` — **PASS** after these additions when run with local loopback permission. `PlatformIntegrationTest` and `SoCLaaSClientTest` passed. PDFBox logged its malformed-PDF fixture diagnostic; suite exited successfully. An initial sandbox-restricted run passed PlatformIntegrationTest but SoCLaaSClientTest could not bind loopback; the permitted run passed both.
- `sh scripts/test-postgres.sh` — **PASS**; PostgreSQL integration suite passed (local Docker socket access permitted for its database container).
- `git diff --check` — **PASS** after the additional tests and task-record update.
- Staff membership revocation state-load test — **PASS** in `PlatformIntegrationTest`: a staff actor first lists, creates, and edits course tags successfully; state is then restored without that actor's course membership while retaining the user, course, and tags; tag listing and create/edit/delete each return 404.
- `sh scripts/test.sh` — **PASS** after the staff state-load test with local loopback permission. Platform integration and SoCLaaS client tests passed; PDFBox logged its malformed-PDF fixture diagnostic.
- `git diff --check` — **PASS** after the state-load test and task-record update.

## Known limits / next owner

- No browser-driven visual/accessibility test was run; the tester should independently review the approved UI states and keyboard behavior.
- The postgres runner uses the existing whole-state snapshot strategy; tag write rollback and database constraints were exercised, but a multi-process PostgreSQL tag-creation race was not directly simulated.

**Next owner:** Acceptance auditor for `forum-tags` re-audit.
