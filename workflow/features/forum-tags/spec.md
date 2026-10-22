Status: Approved
Approval: Human product-owner request and clarifications recorded in chat, 2026-10-22 — General is immutable grey; 7 total tags; names unique case-insensitively; reject emoji, controls, and markup; expand the fixed accessible custom-color palette from six to nine; authors can retag their own posts while editing.
Last handoff: Human product owner → UI designer, 2026-10-22 — design the approved forum tag settings, filter, and post create/edit flows.

# Course Forum Tags

## Purpose and outcome

Give each course a small, staff-managed set of colored tags so participants can
categorize discussion posts and filter a course forum by one tag. Tags belong
to a course; no tag or post metadata is shared across courses.

## Confirmed requirements

1. Every course has a `General` tag. It is grey, cannot be renamed or recolored,
   cannot be deleted, and counts toward the seven-tag course maximum.
2. A course may have at most seven tags total: General plus up to six custom
   tags.
3. Course staff can create, rename, recolor, and delete custom tags in course
   settings. Renames and color changes are reflected on every post referring
   to that tag. Deleting a custom tag reassigns its posts to General.
4. Tag names are at most 20 characters and unique within the course regardless
   of capitalization. They cannot contain emoji, control characters, or
   markup.
5. Custom tag colors come from a fixed set of nine accessible colors presented
   in a grid picker. General remains grey and immutable.
6. Under the Forum heading and its course-discussion subtitle, display each tag
   as an individual pill. A pill uses its tag's assigned color for its outline
   and text. Selecting a pill immediately filters posts to that tag; at most
   one tag may be selected. Clicking the selected pill clears the filter. No
   separate apply/confirm control is shown.
7. Post creation includes a tag selector below the title field and above the
   description field. General is selected by default. A post cannot be created
   without a valid tag. Authors can change the tag while editing their own post.
8. Forum post previews and the post detail/thread view show the post's tag
   using that tag's color.
9. Existing courses and posts are assigned General as part of migration.

## Roles and user goals

| Role | Goal | Required behavior |
| --- | --- | --- |
| Student course member | Categorize a question and find related discussions. | Create a post with a selected course tag (General by default), and filter the authorized course forum by one tag. |
| Course staff | Manage the course's tag vocabulary. | Create/edit/delete custom tags in that course, subject to naming, color, immutability, and count rules. |
| Any authorized course member | Understand a post's category. | See its current tag and color in list previews and the post detail view. |

Course staff means a staff-role user who is a staff member of that specific
course under the approved `shared-course-forum` contract. A global staff role
alone does not grant access to another course's settings or tags.

## Scope

- Course-scoped persistence and API behavior for tags and post-tag association.
- Server-side create, list, update, and delete behavior for custom tags.
- Course staff settings UI for tag management and fixed color selection.
- Forum tag pills, single-tag filtering, post create/edit selection, preview
  and thread display.
- Backfill/migration of every existing course and post to General.
- Automated validation, migration, membership authorization, and post/tag
  behavior tests, followed by independent verification.

## Non-goals

- Nested tags, multiple tags per post, tag search, tag ordering controls, or
  filtering by more than one tag at once.
- Global/shared tags, public tag browsing, or cross-course aggregation.
- User-created tags by students or non-course-staff members.
- Emoji, arbitrary CSS colors, custom hex entry, or a free-form color picker.
- Changes to forum authorship, moderation, deletion, Markdown, or course
  membership rules established by `shared-course-forum`.
- Changing another author's post tag or adding tag selection to staff
  moderation.

## Functional requirements

### Tag initialization and migration

- Creating a course creates exactly one General tag for that course. It uses
  the product's designated neutral grey token and is marked/treated as
  immutable by server rules, not only by hidden UI controls.
- A migration adds one General tag for each existing course and associates all
  existing posts in that course to it. Migration must preserve course/post
  identifiers, content, authorship, timestamps, replies, and unrelated data.
- Migration must be repeatable or safely transactional so a partial/retried
  migration cannot create duplicate General tags or leave posts without a tag.
- A course with no posts still receives General.

### Managing custom tags

- Only course staff may read/manage tags through settings. Authorized course
  members may read the tag list needed to render the forum and create posts.
- The server always returns General and custom tags for the requested course
  only. Requests must derive actor identity and role from authenticated server
  context; client-supplied role, author, course membership, color, or tag
  ownership claims are not authorization evidence.
- Create validates the current course tag count atomically. If the course
  already has seven, creation fails without writing a tag, even when two
  requests race.
- Create and rename trim leading/trailing whitespace before validating and
  storing a name. An empty/whitespace-only name is invalid. A name of 1–20
  Unicode code points after trimming is permitted if it also satisfies the
  confirmed content restrictions. Length is measured consistently by server
  validation and UI counter; browser validation is supplementary.
- Tag names are stored and rendered as plain text. Reject HTML markup
  delimiters (`<` and `>`); do not parse tag names as HTML or Markdown.
- Name uniqueness is evaluated within the course after Unicode
  case-insensitive comparison. A case-only duplicate is rejected. The same
  name may exist in different courses.
- Color must be one of the fixed palette values delivered by the approved
  design/API contract. Arbitrary client color values are rejected. Palette
  colors must meet WCAG AA contrast for normal text against the pill surface;
  General is always grey.
- Editing a custom tag's name or color updates the tag record referenced by
  posts, so all existing post previews and details show the new name/color
  after refreshing. It must not rewrite post body/title or create a new tag.
- Delete is allowed only for a custom tag in the actor's course. The server
  must atomically reassign every post using the deleted tag to that same
  course's General tag and delete the custom tag. If reassignment or deletion
  fails, the operation leaves the prior state intact. No post becomes
  untagged, and no post is moved across courses.
- General cannot be renamed, recolored, or deleted, including by direct API
  calls. Attempts return a safe validation/permission response and preserve
  state.

### Forum filtering and display

- The forum shows all course tag pills in a stable order; default behavior
  displays posts across all tags. The specific order is a UI design choice and
  must remain stable within a rendered list.
- Clicking an unselected tag pill makes it the only active filter and refreshes
  the list automatically. Clicking the active pill removes the filter and
  returns to all course posts. Selecting a different pill replaces the active
  filter; multiple selected tags are impossible.
- The active state is visually distinct and programmatically exposed. Pills
  are keyboard operable and have accessible names. Text/outline color and
  selected state must remain perceivable without relying on color alone.
- Filtered results contain only posts from the authorized course whose
  current tag is the selected tag. An empty filtered result has an explicit
  no-posts-for-this-tag state and a way to clear the filter.
- Tag labels and colors are sourced from the current course tag records, not
  cached post snapshots that can become stale after a staff edit.
- A stale filter whose tag was deleted clears to all posts and shows an
  understandable status; no inaccessible data is exposed.
- Preview cards and thread/detail display the post's current tag and color.
  Rendering treats names as plain text, never executable markup.

### Creating and editing posts

- An authorized course member's post form shows a required tag selector
  between title and description. It lists only that course's tags and defaults
  to General.
- The server rejects a missing, invalid, or cross-course tag association and
  does not create the post. It must validate course membership and tag
  ownership on every request.
- If a selected custom tag is deleted before the create request is persisted,
  the server either completes against its atomic reassignment to General or
  returns a recoverable validation response; the post must not reference a
  deleted tag.
- On successful create, the post preview and thread show the persisted tag.
- When editing their own post, the author sees a required tag selector between
  title and description, initialized to the post's current tag. The server
  validates that the selected tag belongs to the post's course.
- Existing post visibility remains governed by the course membership rules in
  `shared-course-forum`.

### Loading, errors, and unavailable states

- Settings tag list and forum tag bar provide loading and retryable service
  failure states. Failed mutations preserve the current form values and show
  inline feedback; they do not imply success.
- Invalid names, duplicates, invalid colors, tag-limit violations, and attempts
  to alter General produce clear field/action feedback. On limit, the creation
  area states that a course can have at most 7 tags, including General, and
  that 6 custom tags is the maximum.
- A removed member or non-member receives the same course access denial as
  other forum operations and cannot infer another course's tags or posts.
- A stale/deleted tag or unavailable tag service must not cause untagged post
  creation or silently assign a different custom tag.

## Authorization rules

All rules are enforced server-side for each read and mutation.

| Action | Student member | Course staff | Non-member/unauthenticated |
| --- | --- | --- | --- |
| Read tags for course forum/post form | Allowed for own course | Allowed for own course | Denied; no tag/course data returned |
| Create/edit/delete custom tag | Denied | Allowed only in own course | Denied |
| Mutate General | Denied | Denied | Denied |
| Filter/list posts by tag | Allowed in own course | Allowed in own course | Denied; no post data returned |
| Create post with a course tag | Allowed in own course | Allowed in own course | Denied |

The server must validate that the requested post, tag, and course identifiers
refer to the same course. Any removed membership revokes tag and filtered-post
access immediately, consistent with `shared-course-forum`.

## Data/API and compatibility implications

- Add course-owned tag identity, normalized name, fixed palette color value,
  and immutable-General identity/state. Enforce one General per course,
  case-insensitive per-course name uniqueness, and the seven-tag limit under
  concurrent writes.
- Add a required tag association to every post, with a course-consistent
  reference. Database integrity and service validation must prevent null or
  cross-course relationships after migration.
- Provide course-authorized tag listing and staff-only create/update/delete
  operations, plus a tag filter on the existing course post-list contract.
- Post create and edit accept a tag identifier. Response/list/thread contracts include
  enough current tag identity, name, and color to render the pill. Never trust
  client-supplied display name/color instead of the stored tag record.
- Error outcomes distinguish invalid/duplicate name, tag limit, invalid color,
  immutable General, missing/deleted tag, permission denial, and service
  failure without leaking protected resource existence.
- Existing clients that omit a tag must be updated or have a documented safe
  compatibility path. After migration, the server must still guarantee every
  post has a valid tag. Compatibility may not weaken the required-tag rule.
- Course deletion and tag/post deletion behavior remain consistent with
  existing course/forum cleanup contracts.

## Abuse cases and safeguards

- A user supplies another course's tag identifier when creating a post:
  reject server-side; create no post.
- A non-course-staff user calls management endpoints directly or changes a
  course/tag identifier: deny and expose no protected course details.
- A student or staff member attempts to mutate General directly: reject and
  leave General unchanged.
- Concurrent tag creation attempts exceed the seven-tag cap: at most one
  succeeds when only one slot remained; total stays at seven.
- Case-only or Unicode case-equivalent duplicate names are submitted: reject
  within that course; allow identical names in separate courses.
- A caller supplies markup, control characters, emoji, invalid palette values,
  or overlong names: reject before persistence and render no active content.
- A tag is renamed, recolored, or deleted while clients hold stale forum data:
  the server's current tag assignment/record governs results and rendering;
  no stale reference exposes data or leaves a post untagged.
- A removed student retries a cached filter or tag-management request: server
  rechecks current membership and denies access.

## Dependencies and assumptions

- Depends on the approved `workflow/features/shared-course-forum/spec.md` and
  its course staff/member authorization contract.
- A user-facing `design.md` is required after this specification is approved.
  It must define the concrete fixed accessible palette, pill states, keyboard
  interaction, creation form, settings management, and stale-filter behavior.
- Existing courses/posts are assumed to be present in persisted storage and
  must be backfilled transactionally before requiring non-null post tags.
- Tag-filtered feeds continue to use the existing forum visibility and
  pagination semantics; no additional post visibility policy is introduced.

## Open decisions requiring human review

No product decisions remain open. The UI design selects named swatches for the
fixed palette, all of which must meet WCAG AA contrast for normal text.

## Acceptance criteria

1. A newly created course has exactly one grey General tag. Direct API attempts
   to rename, recolor, or delete General fail and leave it unchanged.
2. A migration gives every existing course exactly one General tag and assigns
   every existing post in that course to it while preserving all post content,
   authorship, replies, and timestamps. A retry/partial-failure recovery does
   not duplicate tags or leave null/cross-course post references.
3. An authorized course staff member can create a custom tag with a valid
   unique name and allowed palette color. A student member and a staff
   non-member are denied by direct server request.
4. Names are trimmed, nonempty, at most 20 Unicode code points, and rejected
   when containing emoji, control characters, or the approved markup patterns.
   Case-insensitive duplicate names within one course are rejected, while an
   identical name in another course is accepted.
5. The interface clearly states the seven-total/six-custom limit. The server
   rejects an eighth tag; under concurrent creation with one slot left, no
   more than one request succeeds and the course never exceeds seven tags.
6. Custom tags accept only palette values. Arbitrary colors and attempts to
   change General's grey color are rejected server-side. Approved palette text
   meets WCAG AA normal-text contrast against the pill background.
7. Renaming or recoloring a tag updates every referencing post's preview and
   thread display after refresh, with no change to post content or identity.
8. Deleting a custom tag moves all its posts to that course's General and
   removes the custom tag atomically. If the operation fails, posts and tag
   remain in their pre-operation state. No post crosses course boundaries or
   becomes untagged.
9. The course Forum displays each current tag as an individual pill using its
   stored text/outline color. All posts are shown initially. Selecting a tag
   immediately displays only that tag's posts; selecting another replaces the
   filter; selecting the active pill clears it. No apply button is needed.
10. A filtered tag with no posts displays a clear empty result and a way to
    clear the filter. A tag deleted while selected is cleared safely without
    an error crash or stale data display.
11. Authorized users can operate tag pills by keyboard, identify the selected
    state with assistive technology, and perceive tag identity/selection
    without color as the only cue.
12. The post form shows its required tag selector after title and before
    description, defaults to General on create/current tag on edit, and offers
    only tags for that course. Authors can change tags on their own posts. A
    missing, deleted, or cross-course tag causes server rejection and no post
    is persisted.
13. Post previews and detail/thread show the current tag name and color. Tag
    names render as text and cannot execute markup.
14. Direct requests to read tags, filtered posts, or mutate tags after course
    membership is removed are denied and return no protected course data.
15. Loading, empty, validation, permission-denied, and service-failure states
    are clear for tag settings, filtering, and post creation; failed mutations
    do not claim success or discard entered values.

## Verification evidence expected

Engineering records automated unit/integration tests for name normalization,
length and prohibited-character validation, case-insensitive uniqueness,
concurrent seven-tag enforcement, fixed palette enforcement, General
immutability, atomic post reassignment on delete, course-scope mismatch denial,
membership revocation, migration/backfill/idempotence, filtered-list behavior,
stale-tag handling, and required tag creation. Tests cover staff, student,
non-member staff, and unauthenticated contexts using separate identities. The
independent tester records results against every acceptance criterion and
verifies the management, post creation, filter, preview, and thread flows.

## Handoff

**Feature:** `forum-tags` — Approved; implementation may begin after UI design.

**Inputs:** `AGENTS.md`, `workflow/development-process.md`, the `feature-spec`
skill, `workflow/project-overview.md`, `workflow/implementation_plan.md`, and
approved `workflow/features/shared-course-forum/spec.md` and `design.md`.

**Confirmed decisions:** General is grey/immutable/undeletable; seven total
tags including General; name uniqueness is case-insensitive per course;
emoji, control characters, and markup are prohibited; colors use a fixed
accessible grid; deleting a custom tag moves its posts to General; forum pills
select/clear a single automatic filter; General is the default required post
tag; existing courses/posts are migrated to General; authors can change the tag
on their own posts while editing.

**Design choices:** UI design chooses named colors for a fixed accessible
grid. Tag labels reject `<` and `>` as markup delimiters and are rendered as
plain text; deleted active filters clear to all posts.

**Next owner:** UI designer writes `workflow/features/forum-tags/design.md`;
engineering begins after that design handoff.
