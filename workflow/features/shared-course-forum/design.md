# Shared Identity, Course Membership, and Course-Scoped Forum — UI Design

Status: Approved
Approval: Human product-owner approval recorded in chat, 2026-10-20
Last handoff: Human product owner → Engineer, 2026-10-20 — approved for shared platform/server implementation.

## Scope and visual language

This design implements the approved shared-course-forum specification only: account creation, course/membership management, course-scoped forum activity, and the Resources entry point. Resource upload, processing, and management are owned by staff-material-governance.

Use a calm blue-and-white educational interface: white/light-neutral surfaces, dark readable text, and primary blue for action, navigation, and focus. Use separate labelled success, warning, and error colours. Work to an 8 px spacing grid, 24 px desktop/16 px mobile gutters, 16 px body text, a 760 px forum reading column, and a 1,120 px maximum management region.

## Shared shell and reusable components

| Component / pattern | Use |
| --- | --- |
| `AppShell` | Top bar with product name, role badge, account menu, and sign-out. Course pages add context/nav but never a different course's data or controls. |
| `CourseContextHeader` | Breadcrumb `My courses / {course code}`, code/name, and `Enrolled` or `Staff access` badge. |
| `CourseNav` | Desktop left rail; mobile horizontal scrollable tabs. `Forum`, `Resources`, plus `Members` and `Settings` only for course staff. Active state has text, icon, and blue indicator. |
| `PrimaryButton`, `SecondaryButton`, `DangerButton` | Minimum 44 px target. Primary is blue; danger is reserved for irreversible deletion. Pending text prevents double submit. |
| `Field` | Label, required marker, help text, input, counter where limited, and inline error via `aria-describedby`; validate on blur and submit. |
| `StatusBanner` | Icon, clear heading/text, semantic colour, and only meaningful retry/dismiss action. Do not leak protected resource existence. |
| `EmptyState` | Icon, brief explanation, and a permitted next action only. No self-enrol, withdrawal, attachment, search, reporting, or forbidden staff CTA. |
| `OverflowActions` | Accessible `More actions for {item}` menu with only authorized actions. |
| `ConfirmDialog` | Focus trap, stated irreversible consequence, Cancel default focus, danger-labelled confirm, and focus restoration. |
| `MarkdownEditor` / `MarkdownPreview` | Textarea, supported-Markdown hint, and Preview using the same sanitized renderer as published content. No supplied HTML/script/event runs. |
| `MemberList` | Separate labelled Course staff/Students lists, username and role badge. Only student rows offer removal. |
| `ForumPostCard` / `CommentCard` | Username/role badge, timestamp, sanitized text, and only permitted actions. No credentials/private profile data. |
| `ResourceEntryCard` | Course-scoped Resources label/link only; no material-management controls. |

These are reusable patterns for later designs. Human discussions are labelled **Discussion** and must not be styled as AI output.

## Information architecture and authorization visibility

The authenticated landing page is `My courses`, listing only courses returned for the actor. There is no public directory or cross-course feed. UI checks are defensive only: a fresh server decision governs every read and mutation.

| Context | Navigation and visible actions |
| --- | --- |
| Visitor | Sign-up only. Protected route shows generic sign-in-required feedback; sign-in itself is not specified here. |
| Student, course list | Course cards only; no create-course/membership/settings controls. |
| Enrolled student | `Forum`, `Resources`; create/edit/delete own content only. No join/withdraw/member/settings controls. |
| Course staff | `Forum`, `Resources`, `Members`, `Settings`; creation, member addition/student removal, course rename/delete, own content actions, and student-content delete. |
| Non-member staff, removed student, unauthenticated | No course shell or course identity after resolution. Show generic unavailable/revoked feedback and `My courses`. |

## Flows and layouts

### Account sign-up and role selection

Use a 440 px single-column card on a light-neutral page: product name, `Create your account`, username, password, role radio-cards, and `Create account`. Neither role is preselected. The Staff card says: `For this test project, staff accounts are self-selected and are not institutionally verified.`

Required-field, duplicate-username, invalid-password, and general failures appear in a focused summary and associated fields without echoing the password. States: initial, client validation, server validation, submitting, success (confirm role then route to `My courses`), and recoverable request failure.

### My courses and course creation

Show role context and a list/grid of course cards: code, name, role badge, and `Open course`. Staff alone get header action `Create course`. Student empty: `You have not been added to a course yet` with no join CTA. Staff empty adds `Create course`.

Creation opens a dialog, or a full-screen mobile form, with required Course code/name, Cancel, and `Create course`. Success announces creator staff access and routes to that course Forum. Validation stays in place; request failure preserves non-sensitive text.

### Course shell, Forum list, and Resources entry

Every course view uses `CourseContextHeader`, `CourseNav`, then main content. Forum header: `Discussion`, course-scoped visibility text, and `Create post` for an authorized member. A paginated `ForumPostCard` list shows title, sanitized preview, author/role, timestamp, comment count, and `Open discussion`.

Resources opens with course-scoped copy. Until the material feature implements its body, display `Resources will appear here when staff add course materials`; do not create upload controls. Forum states: semantic skeletons; empty `Start the course discussion` plus `Create post`; retryable failure; generic unavailable state after clearing cached content.

### Create/edit post

Use a full-page course-shell form: breadcrumb, required Post title (`n / 120`), optional Description (`n / 2000`), Editor/Preview tabs, Cancel, and `Publish post`/`Save changes`. No attachment, AI, search, or report control. Dirty cancel asks for confirmation. Success creates/routes to the thread or announces saved changes. Missing/over-limit values show inline and summary errors; failed save retains text. Lost access clears content and returns safely.

### Thread, comments, and content actions

A thread has Forum return link, complete post card, Comments heading/list, and authorized `Add a comment` Markdown editor. Comment edit replaces its card with the same editor/Preview. Comments have no title; this design adds no length constraint.

| Viewed item | Actions |
| --- | --- |
| Own post/comment | `Edit`, `Delete` |
| Student-authored item, viewed by non-author course staff | `Delete student post/comment` only |
| Another staff member's item | No edit/delete action |
| Another student's item, viewed by a student | No edit/delete action |

Delete uses `ConfirmDialog`; a post dialog explicitly says it permanently removes the post and all comments. On success route deleted post to Forum list or remove a comment in place and announce it. Stale/denied response refreshes with safe feedback and never claims success. Include loading, no-comments, comment validation/save failure preserving input, unavailable, and retry states.

### Staff Members management

Course staff only. `Members` has an add card: username `Field`, then `Add to course`. There is no role selector; target account role determines membership. Successful addition announces the role-specific result. Required, unknown-user, already-member, and failed-request states are clear and refresh only the list.

`MemberList` separates Course staff and Students. Staff rows have no removal. Student `OverflowActions` exposes `Remove student`. Confirmation states immediate loss of Forum/Resources access and irreversible removal for this test project. Success removes row; stale/failure refreshes list and shows banner.

### Staff Settings: rename/delete course

Course staff only. `Course details` displays code as read-only context and edits course name; no code-edit control. Save uses normal pending/success/validation/failure states and updates context header.

`Danger zone` provides `Delete course`. Dialog names course code/name, states Forum, Resources, and dependent content immediately become inaccessible, and requires typing course code to enable confirmation. This safeguards the UI, not authorization. Success returns to `My courses`; failure/stale state clears cache and shows generic unavailable feedback.

## Responsive, accessibility, and feedback

- Under 768 px, replace rail with scrollable `CourseNav`, stack controls, and use full-screen mobile dialogs only with focus trap and explicit close.
- Use landmarks, one H1/view, native labelled controls, logical keyboard order, visible focus, no hover-only action, Escape and focus restore for overlays.
- Meet WCAG AA contrast; support 200% zoom without clipped actions/page scroll.
- Live-announce successful mutations; focus error summary after failed submit.
- Every async view has loading, empty, success, validation-error, unavailable/permission-denied, and network/service-failure treatments. Retry repeats only a failed operation, never a successful deletion.

## Design-to-implementation notes

- Server authorization applies regardless of UI visibility. On unavailable, clear cached course/thread/member data before rendering feedback.
- Use the same sanitized Markdown renderer for Preview and published content.
- Use accessible labelled pagination, not infinite scrolling; page size is an engineering decision.

## Open questions

1. Sign-up is specified but the sign-in/session-entry screen is not. Engineering should implement the minimum protected-route entry without extending policy.
2. No maximum comment length is approved. This design introduces none; any later limit must be approved and consistent between editor and service.

## Handoff

**Feature:** `shared-course-forum` — Draft / Pending human design approval.

**Inputs:** `AGENTS.md`, project/development documents, product-foundation and shared-course-forum specifications, and `ui-consistency` skill.

**Deliverable:** This design only. It establishes the blue-and-white shared shell and reusable patterns without changing requirements or code.

**Next owner:** Human product owner approves design; then @p-xp implements the shared foundation/contracts and @ziyang consumes them for student-facing work.
