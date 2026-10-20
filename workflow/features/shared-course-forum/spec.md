# Shared Identity, Course Membership, and Course-Scoped Forum

Status: Approved
Approval: Human product-owner approval recorded in chat, 2026-10-20
Last handoff: Human product owner → Engineer, 2026-10-20 — approved for shared platform/server implementation.

## Purpose and outcome

Provide the shared account, course-access, and forum capabilities needed for a
working TA Help Desk. A person can create either a student or staff account.
Staff create courses and enrol named student accounts. Authorized course
members can use a forum that is isolated from every other course, while the
server enforces all role and course checks.

This is a shared platform feature owned by **@p-xp**. @ziyang consumes its
approved contracts for the student dashboard and student forum experience.

## Confirmed requirements

1. The first release has only `student` and `staff` roles.
2. A user self-signs up by supplying a username and password and choosing
   either role. Staff self-sign-up grants staff authority without verification
   for this test project.
3. Staff create courses. The creator automatically becomes course staff. Only
   course staff may access that course as staff; course staff may add existing
   student or staff accounts to that course by entering a username. A student
   cannot self-enrol in or withdraw from a course.
4. Forum content is scoped to a course. Students within the same authorized
   course can see posts from other students in that course. Staff can create
   posts or replies.
5. There is no reporting or general moderation workflow.
6. Every user may edit or delete only their own posts and comments. Staff may
   additionally delete a student-authored post or comment. A staff member may
   not delete content authored by another staff member.
7. Course data must be server-authorized; hiding an interface control is not an
   access-control mechanism.
8. Removing a student from a course immediately revokes that student's access
   to that course forum.
9. Deleting a post or comment hard-deletes it from the backend. A deleted post
   hard-deletes its dependent comments as well.
10. A course has a mandatory code and name. Authorized course staff may rename
    or delete their course. Course deletion immediately removes it from normal
    course access; dependent-data cleanup must not leave its forum or resources
    accessible.
11. A forum post has a mandatory title of at most 120 characters and an
    optional description of at most 2,000 characters. Markdown is supported;
    attachments and search are not in the initial release.

## Scope

- Account sign-up and authenticated account identity sufficient to apply the
  role and authorship rules above.
- Staff course creation, rename, and deletion; course-staff and student
  membership management through username lookup; and student removal.
- Course-scoped post creation, listing/viewing, comment/reply creation,
  editing, and deletion.
- Clear user-facing empty, validation-error, permission-denied, and failed
  request states.
- Server-side authorization and negative tests for role, authorship, and
  cross-course access.

## Non-goals

- Account verification, password-reset/recovery, institutional SSO, additional
  roles, or production-grade staff vetting.
- Student self-enrolment, withdrawal, invitations, join codes, public course
  directories, reporting, flag queues, or a general moderation system.
- AI-generated forum responses or automatic publishing of any AI output.
- Defining the staff material or either AI feature; those are separate specs.
- A retention/recovery policy for deleted data; deletion is irreversible for
  this test project.

## Roles and user stories

| Role | User goal | Required behavior |
| --- | --- | --- |
| Visitor | Obtain an account for test use. | Create one student or staff account with a username and password; receive validation feedback if it cannot be created. |
| Student | Access course discussions I was added to. | View and participate only in forums for courses where the server confirms membership. |
| Student | Manage my contribution. | Edit or delete my own posts/comments, but not anyone else's. |
| Staff | Set up a course and control its membership. | Create a course (becoming its first course staff member), and add existing student or staff accounts by username. |
| Staff | Participate safely in course discussion. | Create and edit own posts/comments; delete a student-authored post/comment where the server permits it; never delete another staff member's content. |

## Functional behavior and states

### Accounts and authentication

- Sign-up requires a username, password, and selected role. The service must
  validate required fields and reject a username that is already in use without
  creating a second account.
- Password values must not be returned in responses, displayed, or included in
  logs. The implementation detail of credential storage is owned by engineering
  but must meet the shared security baseline.
- Unauthenticated requests to account-protected, course, or forum operations
  are denied. A signed-in user receives only their own identity/role context.
- The product must make the self-selected staff role clear during sign-up so a
  tester does not mistake it for verified institutional staff access.

### Courses and membership

- A staff actor may create a course. Course creation validates required code
  and name information, automatically grants the creator course-staff membership, and
  returns a clear failure state if it cannot be saved.
- Only authorized course staff may rename or delete their course. Once deleted,
  the course must no longer be readable or usable by any former member;
  engineering must make dependent record/job cleanup safe so stale access
  cannot survive deletion.
- Enrolment starts with a staff actor entering an existing username. The server
  must look up the account and refuse missing usernames; it must not create an
  account implicitly. Only course staff may add a student or staff account to
  their course.
- Adding a student grants forum access only to the identified course; adding a
  staff account grants that staff account course-staff access only to the
  identified course. Repeating the same membership addition must not create a
  duplicate membership.
- Students must not be offered or permitted endpoints to enrol themselves or
  withdraw.
- Course staff may remove a student from their course. The removal immediately
  takes effect on the server and revokes the student's forum access to that
  course. This specification does not authorize removing course staff.

### Forum

- A course forum lists only posts belonging to that course. An authorized
  member can open a post and see only its comments/replies in that same course.
- An authorized participant creates a post or comment within a course they are
  allowed to access. A comment/reply belongs to one post in the same course;
  the server rejects attempts to attach it across courses.
- A post requires a non-empty title of no more than 120 characters. Its
  description is optional and, when supplied, has no more than 2,000
  characters. The server validates these limits before persistence. Markdown
  is rendered through a safe allowlist/sanitizer: it is presentation text, not
  executable HTML, script, event handler, or AI instruction. Attachments and
  search endpoints are not provided in the initial release.
- The forum identifies an item's author sufficiently for users to understand
  who posted it, without exposing credential data.
- An author may edit their own post/comment. A user who is not the author may
  not edit it, including staff editing a student's content.
- An author may delete their own post/comment. Staff may delete
  student-authored content in an authorized course. Staff must be denied when
  attempting to delete another staff member's content. Students must be denied
  when attempting to delete any other user's content.
- Deleting a post or comment permanently removes it from the backend. Deleting
  a post also permanently removes its dependent comments; none may be returned
  through normal or direct forum reads.
- Empty courses, courses with no posts, and posts with no comments have
  explicit empty states. Invalid/missing content, failed saves, and denied
  actions have clear errors without revealing protected course data.

## Authorization rules

All rows below are server-enforced for every read and mutation. “Course staff”
means a staff-role account that created the course or was subsequently added to
that course by existing course staff.

| Action | Student | Staff | Required server check |
| --- | --- | --- | --- |
| Sign up as either role | Allowed | Allowed | Unique username; valid supplied fields; role explicitly selected. |
| Create course | Denied | Allowed | Authenticated staff role. |
| Rename/delete course | Denied | Allowed for course staff | Actor is course staff for the requested course; deletion immediately prevents future course access. |
| Add a student or staff account by username | Denied | Allowed for course staff | Actor is course staff; target exists; membership type follows target role. |
| Remove a student | Denied | Allowed for course staff | Actor is course staff; target is a current student member; removal immediately revokes forum access. |
| Read a course forum | Allowed when enrolled | Allowed for course staff | Actor is a student member or course staff for the requested course. |
| Create post/comment | Allowed when enrolled | Allowed for course staff | Actor may access the course; parent post, if any, is in that course. |
| Edit post/comment | Own only | Own only | Actor is the item's author. |
| Delete own post/comment | Allowed | Allowed | Actor is the item's author. |
| Delete student-authored post/comment | Denied unless own | Allowed for course staff | Actor is course staff and author role is student. |
| Delete staff-authored post/comment | Denied unless own | Own only | Actor is the item's author; never permit staff-to-staff deletion. |

## Data and API implications

Implementation must establish documented shared contracts, not a final schema
in this specification. At minimum, it needs:

- an account identity with unique username, selected role, and no retrievable
  password value;
- a course identity with mandatory code and name and a relationship recording
  which actor created it;
- distinct course memberships connecting student and staff accounts to a
  course, with the creator's course-staff membership recorded at creation;
- forum post and comment/reply identities, each associated with a course,
  author, title/description content and validated lengths; hard deletion must remove the record and any dependent
  comments when a post is deleted; and
- authenticated request context used to authorize every relevant read/mutation.

The shared API contract must carry course and item identifiers separately and
derive the actor from authenticated server context. It must not accept a
client-supplied role, author, or arbitrary course-membership claim as proof of
authorization. Error contracts must distinguish validation, authentication,
permission, not-found, and service-failure outcomes without leaking whether a
protected resource exists.

Backward-compatibility is not applicable before an implementation exists. Any
later API contract change that affects @ziyang's student-facing work requires a
documented contract update and review.

## Abuse cases and required safeguards

- A student calls a course/forum endpoint with another course's identifier:
  deny without returning posts, comments, membership, or material metadata.
- A client changes a request role/author/item identifier to edit or delete
  another user's content: derive actor/role server-side and deny unless the
  matrix above permits it.
- A staff actor tries to delete a staff-authored item they do not own: deny.
- A user submits a duplicate/unknown username for sign-up or enrolment: return
  validation/not-found feedback without creating a partial account/membership.
- Forum titles/descriptions and usernames are untrusted input: validate at the service
  boundary and render Markdown through a sanitizer as data, not executable markup. Forum text must not be
  treated as instructions by either AI feature.
- Repeated requests or stale UI state must not bypass current membership or
  deletion state; recheck authorization server-side per request.

## Dependencies and assumptions

- Depends on the confirmed Phase 0 role and course decisions in
  `workflow/features/product-foundation/spec.md`.
- The detailed UI flow requires a `design.md` after human approval of this spec.
- @p-xp owns the shared platform/API contract. @ziyang must not independently
  redefine shared authorization behavior while implementing student flows.
- This feature does not depend on the pending SoC operating envelope, but it
  must support the course authorization boundary consumed later by material and
  AI services.

## Open decisions requiring human review

No unresolved product decisions remain for this feature. UI design must specify
the course-delete confirmation and visual Markdown-rendering/error states
without changing the approved authorization or content limits.

## Acceptance criteria

1. A visitor can create a student account and a staff account by supplying a
   valid, unique username, password, and selected role; a duplicate username or
   missing/invalid required field is rejected without creating a usable account.
2. The service never returns or logs a plaintext password in normal responses
   or structured application logs, and protected operations reject an
   unauthenticated actor.
3. A signed-in staff user can create a course only with a mandatory code and
   name; a signed-in student receives a server-enforced denial for the
   equivalent request. Only course staff can rename or delete that course, and
   a deleted course cannot subsequently be read or used by any former member.
4. A course creator automatically gains course-staff access. A course staff
   actor can add an existing student or staff account by username; unknown
   usernames are rejected, duplicate membership produces no duplicate record,
   and the operation never creates an account.
5. A student cannot self-enrol in or withdraw from a course through either the
   UI or a direct server request.
6. An enrolled student can list/read/create posts and comments only in their
   enrolled course. A direct request using another course's identifiers returns
   a denial/not-found result and returns no protected forum data.
7. A course staff actor can create and read forum content only for courses
   where they are course staff. A staff user who is not course staff is denied
   from that course, including by a direct request.
8. An author can edit and delete their own post/comment; no other user can edit
   it, including a staff user editing a student's item.
9. A course staff actor can delete a student-authored post/comment only in
   their course. A student cannot delete another
   user's item, and a staff actor cannot delete another staff actor's item.
10. Removing a student immediately denies that student's forum reads and
    mutations for the course. A deleted post/comment is hard-deleted; deleting
    a post hard-deletes its dependent comments, and direct requests cannot
    retrieve any deleted record.
11. The service rejects a missing/over-120-character post title and an
    over-2,000-character description before persistence. Supported Markdown is
    safely rendered without executing supplied HTML/script content; attachment
    upload and search are unavailable in the initial release.
12. The UI/API provides explicit empty, validation-error, permission-denied,
    not-found, and failed-request states without revealing data from a course
    the actor is not authorized to access.
13. Forum titles/descriptions and usernames are treated as untrusted data: malicious markup
    is not executed/rendered as active content, and forum content does not
    bypass server authorization or become trusted AI instructions.

## Verification evidence expected

Engineering must record automated tests for every acceptance criterion, with
particular evidence for cross-course denial, direct endpoint authorization,
authorship edits/deletes, staff-to-staff deletion denial, duplicate/unknown
username handling, course rename/deletion access revocation, title/description
validation, safe Markdown rendering, and deletion visibility. The independent
tester must test the approved course-staff scope and all role/course boundary
cases using separate accounts.

## Handoff

**Feature:** `shared-course-forum` — Draft, ready for human approval and then
UI design.

**Input artefacts:** `AGENTS.md`, `Instructions_and_Planning.md`,
`workflow/project-overview.md`, `workflow/development-process.md`,
`workflow/implementation_plan.md`, and
`workflow/features/product-foundation/spec.md` (Phase 0 decisions).

**Deliverable:** This specification only; no UI design, source code, schema,
or tests were created.

**Next owner:** Human product owner approves the behavioral boundary. Then the
UI designer creates `design.md`; after that, @p-xp may implement the shared
foundation and publish its contract for @ziyang.
