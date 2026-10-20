# Shared Identity, Course Membership, and Course-Scoped Forum — UI Design

Status: Approved
Approval: Human product-owner approval recorded in chat, 2026-10-20. The authentication entry-flow amendment (role choice; student placeholder; staff sign-up/sign-in) was directly approved by the human product owner, 2026-10-20.
Last handoff: Human product owner → Engineer, 2026-10-20 — approved for shared platform/server implementation, including the authentication entry-flow amendment.

Stage 5A amendment: The human request to implement Stage 5A fully (2026-10-21)
authorizes functional student authentication in addition to the originally
documented placeholder. The public entry remains a Student/Staff role choice;
each role has its own sign-in and sign-up path. Staff self-sign-up retains the
explicit unverified-account disclosure. This amendment does not change server
authorization, course membership, or LLM scope.

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
| `RoleChoiceCard` | A whole-card, semantic button for choosing `Student` or `Staff` before authentication. It includes a short path description and visible keyboard focus; it is not a radio group because choosing it navigates to the corresponding route. |

These are reusable patterns for later designs. Human discussions are labelled **Discussion** and must not be styled as AI output.

## Information architecture and authorization visibility

The authenticated landing page is `My courses`, listing only courses returned for the actor. There is no public directory or cross-course feed. UI checks are defensive only: a fresh server decision governs every read and mutation.

| Context | Navigation and visible actions |
| --- | --- |
| Visitor | Home page provides Student and Staff paths. The Student path is an explicit placeholder. The Staff path provides separate `Log in` and `Sign up` actions. Protected route shows generic sign-in-required feedback and a `Go to sign in` action without exposing protected course information. |
| Student, course list | Course cards only; no create-course/membership/settings controls. |
| Enrolled student | `Forum`, `Resources`; create/edit/delete own content only. No join/withdraw/member/settings controls. |
| Course staff | `Forum`, `Resources`, `Members`, `Settings`; creation, member addition/student removal, course rename/delete, own content actions, and student-content delete. |
| Non-member staff, removed student, unauthenticated | No course shell or course identity after resolution. Show generic unavailable/revoked feedback and `My courses`. |

## Flows and layouts

### Public home and authentication entry

The unauthenticated root page is the first decision point, not a sign-in form. Use a light-neutral page with the product name and one H1: `How would you like to continue?` Below it, place two equal-width `RoleChoiceCard`s in a two-column layout (one column below 768 px):

| Choice | Card content | Result |
| --- | --- | --- |
| `Student` | `Access student course help` and `Student features are being prepared.` | Navigate to the Student placeholder. This path must make no authentication or course request and must not resemble a failed sign-in. |
| `Staff` | `Manage courses and materials` and `Create an account or sign in to continue.` | Navigate to the Staff authentication page. |

The Student placeholder keeps the public shell and gives an intentional, plain-language state: H1 `Student access is coming soon`, short explanation that the student experience is not available in this release, and `Back to role selection`. It has no form, disabled credential controls, fabricated course data, sign-up action, or claim that an account was created. It is keyboard reachable and the return action restores focus to the Student choice on the home page.

The Staff authentication page uses a 440 px single-column card on the same background. Its H1 is `Staff access`; supporting text says `Sign in to an existing staff account, or create one for this test project.` Show two peer actions: primary `Log in` and secondary `Sign up`. Selecting an action replaces the action chooser with that action's form in place, retaining a visible `Back to staff options` action. Browser back returns to the prior public view. Do not submit an empty authentication form merely because a visitor entered account details intended for registration.

### Staff sign-up and log-in

`Sign up` presents product name, H1 `Create staff account`, username, password, the test-project disclosure, and `Create account`. This entry flow already selected Staff, so it does not present a role picker or silently create a Student account. The disclosure reads: `For this test project, staff accounts are self-selected and are not institutionally verified.` This preserves the approved self-selected-role policy without implying institutional authorization.

`Log in` presents product name, H1 `Log in to staff access`, username, password, and `Log in`. It does not imply that arbitrary new details will create an account. Required-field, duplicate-username (sign-up only), invalid-password, invalid credentials (log-in only), and general failures appear in a focused summary and associated fields without echoing the password or revealing whether an account exists beyond the approved error contract. States: initial, client validation, server validation, submitting, success (route to `My courses`), and recoverable request failure. Each pending action disables only its own submit control, announces progress, and preserves non-sensitive username values on failure.

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
- `RoleChoiceCard` is operable with Tab and Enter/Space, has an accessible name including its role and purpose, and does not use colour or position alone to distinguish the paths. Sign-up and log-in inputs use explicit labels, appropriate `autocomplete` values, and password fields never retain or announce their value after validation/request failure.
- Meet WCAG AA contrast; support 200% zoom without clipped actions/page scroll.
- Live-announce successful mutations; focus error summary after failed submit.
- Every async view has loading, empty, success, validation-error, unavailable/permission-denied, and network/service-failure treatments. Retry repeats only a failed operation, never a successful deletion.

## Design-to-implementation notes

- Server authorization applies regardless of UI visibility. On unavailable, clear cached course/thread/member data before rendering feedback.
- Use the same sanitized Markdown renderer for Preview and published content.
- Use accessible labelled pagination, not infinite scrolling; page size is an engineering decision.

## Open questions

1. No maximum comment length is approved. This design introduces none; any later limit must be approved and consistent between editor and service.

## Handoff

**Feature:** `shared-course-forum` — Approved.

**Inputs:** `AGENTS.md`, project/development documents, product-foundation and shared-course-forum specifications, and `ui-consistency` skill.

**Deliverable:** This design only. It establishes the blue-and-white shared shell and reusable patterns, including the approved public role choice, intentional Student placeholder, and Staff sign-up/log-in entry flow, without changing requirements or code.

**Next owner:** Engineer implements the approved public authentication entry flow alongside the shared foundation/contracts. The Student card remains a non-authenticated placeholder until its dedicated student experience is approved.
