# Course Forum Tags — UI Design

Status: Approved
Approval: Human product-owner approval recorded in chat, 2026-10-22 — General is immutable grey; up to seven tags including General; names are unique without regard to capitalization and reject emoji, control characters, and markup; expand the fixed accessible custom-color palette to nine choices; authors may change the tag on their own posts while editing.
Last handoff: Human product owner → UI designer, 2026-10-22 — define staff tag management, forum filtering, and create/edit post flows.

## Scope and visual language

Add course-scoped tag management and display to the existing blue-and-white course shell and Forum. Keep the existing course context, navigation, 760 px forum reading column, 8 px spacing grid, fields, buttons, cards, status banners, and confirmation dialog patterns from `shared-course-forum/design.md`. Tags are text labels for human discussion; they do not imply moderation or AI-generated content.

## Fixed color palette

Use the following fixed set for custom tags. Each color is the foreground text and outline color on a white pill surface; each has at least 4.5:1 contrast against white for normal-size text. Render a very light tint of the same hue as the selected pill background while retaining the same foreground and outline. General uses the neutral grey shown here and cannot be changed.

| Tag color | Foreground / outline | Selected surface tint | Use |
| --- | --- | --- | --- |
| General grey | `#475569` | `#F1F5F9` | Reserved, immutable General tag |
| Blue | `#1D4ED8` | `#EFF6FF` | Custom |
| Green | `#166534` | `#F0FDF4` | Custom |
| Amber | `#854D0E` | `#FFFBEB` | Custom |
| Violet | `#6D28D9` | `#F5F3FF` | Custom |
| Teal | `#0F766E` | `#F0FDFA` | Custom |
| Red | `#B91C1C` | `#FEF2F2` | Custom |
| Light Blue | `#0369A1` | `#F0F9FF` | Custom |
| Magenta | `#A21CAF` | `#FDF4FF` | Custom |
| Charcoal | `#1F2937` | `#F3F4F6` | Custom |

The palette is a set of named choices, not a free-form color input. In the picker, each swatch is a labelled radio option with a visible color sample and color name; selected state is shown by a checkmark and outline, not hue alone. Keyboard arrows move among swatches, Space selects, and Tab exits the radio group.

## Tag pill dimensions

Use a shared minimum height of 32 px for every tag pill, including forum filters and display badges. Pills size their width to the label with 12 px horizontal padding on each side; do not force a fixed width, so longer tag names remain readable. Keep the same dimensions in selected and unselected states.

## Course Settings: Manage forum tags

Add a `Forum tags` section to staff-only course Settings, below Course details and before the Danger zone. Keep the course shell and staff context visible. Students do not see this section, and server authorization remains decisive. The expanded Add tag form spans the full settings content width, matching the Delete course danger-zone box. The Course name field and Save name action share one row beneath their label; the input takes the flexible width and the button sizes to its label plus horizontal padding.

Show the current tags as rows in stable order: General first, then custom tags in creation order. Each row shows a colored tag preview, the tag name, and authorized actions. General has a `Default tag` helper label and no edit/delete controls. Custom rows expose `Edit` and `Delete` actions; do not hide them behind hover.

The creation area states plainly: `Up to 7 tags per course, including General (up to 6 custom tags).` Display current usage, such as `3 of 7 tags`. When all six custom slots are used, disable the create action and explain that the limit has been reached. The limit helper remains visible when space is available.

`Add tag` expands an inline form with a required name field, a `0 / 20` character counter, and the grid color picker. General is not offered in the custom color picker. Trim whitespace before validation. Reject empty/overlong names, names that duplicate another course tag without regard to capitalization, and names containing emoji, control characters, or markup. Show field-level errors next to the name or picker without clearing entered values. The create action is disabled while submitting and has pending text; on success, announce the new tag and return the form to its collapsed state.

`Edit` opens the same compact inline form with current name and color selected. Save changes updates the row on success and the current name/color wherever that tag appears. Cancel discards local edits. Failed saves retain values and show retryable feedback. General never enters edit mode.

`Delete` opens the shared `ConfirmDialog`, naming the tag and stating that posts using it will move to General. Cancel is the default focus. The danger-labelled confirmation submits once and shows pending state. On success, remove the row and announce that its posts now use General. On failure, preserve the row and show a retryable error. General has no delete action and must remain intact.

Settings states include loading skeleton rows; retryable load failure; empty custom list with the General row and an invitation to add a tag; inline validation; mutation success; and permission-denied/unavailable state that clears protected course content and returns to safe course navigation per the shared shell.

## Forum tag filters

Place a `Filter by tag` labelled group directly below the Forum heading and course-discussion subtitle, before the post list. Show every current course tag as an individual pill, General first and custom tags in stable creation order. Initially no pill is selected and all authorized course posts are shown.

Each pill is a native button with its tag name as the accessible name and `aria-pressed` for selected state. Use a white surface, 1 px colored outline, and colored text. A selected pill uses its light tint, a stronger 2 px outline, and a visible checkmark; expose selection with `aria-pressed=true`. Do not rely on color alone. Clicking or keyboard-activating an unselected pill immediately filters to that tag. Activating another replaces the single selection. Activating the selected pill clears the filter and shows all posts; there is no apply button.

When a selected tag has no posts, show an explicit empty state such as `No posts tagged “Lab”.` and a `Clear tag filter` button. Activating it returns to all posts. If a selected tag disappears after staff deletion, clear the selection, load the unfiltered feed, and announce `That tag was removed. Showing all posts.` Do not display stale post data while the filter is being reconciled. If the tag or post service fails, retain the current authorized view only if still valid, show a retryable status banner, and do not claim that a filter succeeded.

The tag bar has a loading skeleton that preserves its approximate height to avoid layout jumps. For narrow viewports, pills wrap onto multiple lines; avoid horizontal scrolling for the filter controls. At 200% zoom, controls remain visible and reachable. Keyboard focus is conspicuous and not obscured by the sticky shell.

## Post preview and thread

Show the current tag in its assigned color in the post metadata row, on the same line as the author and timestamp. In the thread/detail view, use the same metadata layout beneath the post title. When the author is staff, place a compact uppercase `STAFF` badge immediately beside the username. Use the shared staff badge yellow (`#FEF08A`) with dark-brown text (`#713F12`), matching the account header, course `Staff access` context, and member list. The explicit label accompanies the color. Obtain current tag name/color and author role from the current course/post records so staff edits and role labels are reflected after refresh; render all values as text.

## Create and edit post

In the existing full-page post form, place a required `Tag` selector after the title field and before the Description editor. Use a native labelled select or accessible listbox consistent with existing Field patterns; options show tag names and a small named color swatch. The selection itself is identified by text, not color alone. On create, select General by default. On edit of the author's own post, initialize to that post's current tag. The selector contains only current tags for the post's course and is required; there is no untagged option. Staff moderation of another author's post does not expose tag editing.

If the selected custom tag is deleted while the form is open, refresh the choices. For a new post, select General and show a brief status that the removed tag is no longer available. For an edited post that was reassigned to General by deletion, update the selector to General and explain the change. If tag choices cannot be loaded or refreshed, disable publishing/saving and preserve all entered content until retry succeeds. Field validation and service errors keep the form values and focus the error summary. The server remains authoritative for required tag and course ownership.

## Responsive and accessibility behavior

- Preserve the shared shell's 768 px breakpoint: course navigation becomes horizontally scrollable; the tag pill group wraps; settings rows stack their actions beneath the tag identity.
- Keep targets at least 44 px, use explicit labels and helper/error associations, logical tab order, visible focus, and native button/select semantics where possible.
- Announce loading completion, successful create/edit/delete, filter changes, empty results, and stale-filter recovery with a polite live region. Focus an error summary after failed form submission; do not move focus for routine filter changes.
- Meet WCAG AA for text and focus indicators; selected state has checkmark, tint, and stronger outline in addition to color. Support screen readers and 200% zoom without clipped controls.
- Every async surface has intentional loading, empty, validation, success, permission-denied/unavailable, and network/service-failure states. Failed actions preserve user input and never imply a mutation succeeded.

## Handoff

**Feature:** `forum-tags` — Approved for implementation.

**Inputs:** `AGENTS.md`, `workflow/project-overview.md`, approved `workflow/features/forum-tags/spec.md`, `workflow/features/shared-course-forum/design.md`, and the `ui-consistency` skill.

**Design decisions:** The custom palette order is Blue, Green, Amber, Violet, Teal, Red, Light Blue, Magenta, and Charcoal with the exact accessible foregrounds and tints specified above. General uses fixed grey. Pills have text, outline, and explicit selected-state cues; filters apply immediately and are single-select. Staff settings state the 7-total/6-custom cap. Authors may change the tag on their own posts while editing.

**Next owner:** Engineer implements the approved `forum-tags` spec and this design, then records implementation tasks and verification evidence.
