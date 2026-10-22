# Platform hardening: material-status persistence and bounded request bodies

Status: Approved
Approval: Human product owner approved all three changes in chat, 2026-10-22. The project remains in development; all existing data is disposable test data, and V5 may intentionally discard legacy course Knowledge Maps.
Last handoff: Product manager → Engineer, 2026-10-22 — implement the approved migration, explicit development-data note, and request-body limits; record results in `tasks.md`.

## Purpose and outcome

Ensure the PostgreSQL schema represents every supported material lifecycle
state, make the existing development-only Knowledge Map data-loss decision
unambiguous, and establish server-side request-size boundaries before JSON
parsing allocates an unbounded request body.

## Confirmed requirements

1. PostgreSQL must accept the existing `resource_only` material status. This
   status represents a valid, downloadable PDF with no selectable text; it is
   excluded from AI processing but remains available through the authorized
   Resources flow.
2. The change must be a new, forward-only PostgreSQL migration. Earlier
   migrations must not be edited or reinterpreted.
3. V5 intentionally drops the obsolete `course_knowledge_maps` table. Because
   all current data is disposable development test data, no migration,
   retention, export, backup, or recovery mechanism is required for those
   legacy course-level maps.
4. Every endpoint that accepts a JSON request body must enforce a maximum body
   size before parsing JSON. The normal JSON limit is 1 MiB (1,048,576 bytes).
5. `POST /api/courses/{courseId}/materials` has a separate maximum encoded
   JSON body size of 28 MiB (29,360,128 bytes). This accommodates a 20 MiB
   PDF after Base64 expansion (up to 27,962,028 Base64 bytes) and its JSON
   envelope, while preserving a bounded allocation before the existing decoded
   PDF validation runs.
6. For a supplied `Content-Length` greater than the applicable limit, the
   server must reject the request before reading its body. If `Content-Length`
   is absent, invalid, or understated, the server must read no more than the
   applicable limit plus one byte and reject once the limit is exceeded.
7. An over-limit body must return the existing safe JSON error shape with HTTP
   413 and a non-sensitive message that states the request is too large. The
   server must not parse JSON, Base64-decode content, invoke a service method,
   write data, or disclose protected course information for that request.
8. Existing authorization and decoded-PDF validation remain in force. The
   28 MiB transport limit does not raise the 20 MiB decoded PDF limit or alter
   PDF type, encryption, structure, page-count, role, or course checks.

## Scope

- A new forward PostgreSQL migration that permits `resource_only` in
  `course_materials.status`.
- An explicit V5 migration comment documenting deliberate disposal of legacy
  course-level Knowledge Map test data.
- A request-body reader with endpoint-specific limits, early `Content-Length`
  rejection, and bounded reads when the header cannot be trusted.
- Automated coverage for the migration and body-size boundary behavior.

## Non-goals

- Preserving, converting, exporting, or recovering old course-wide Knowledge
  Maps.
- Changing material status semantics, Resources authorization, or AI
  eligibility.
- Supporting streaming or multipart uploads, changing the JSON upload API, or
  increasing the 20 MiB decoded-PDF maximum.
- Introducing rate limits, quotas, persistence refactors, or frontend behavior
  changes beyond rendering the existing API error.

## Roles and behavior

| Actor or system | Required behavior |
| --- | --- |
| Authorized course staff | Can continue uploading a valid PDF whose decoded content is at most 20 MiB; an image-only valid PDF may persist as `resource_only`. |
| Enrolled course member | Continues to access `resource_only` PDFs only through the existing server-authorized Resources flow. |
| Any HTTP client | Receives a safe 413 JSON error before parsing when a request body exceeds the relevant endpoint limit. |
| PostgreSQL migration runner | Applies the new migration after existing versions without modifying historical migration records. |

## Data and compatibility implications

The forward migration updates the PostgreSQL constraint or its equivalent so
that `processing`, `active`, `resource_only`, `failed`, and `deleted` are all
valid `course_materials.status` values. Existing rows and existing permitted
statuses remain valid.

V5's table drop remains destructive by design in this development project. A
comment in that migration must state that it discards only obsolete legacy
course-level Knowledge Map test data and that no preservation is required under
the recorded product-owner decision.

The HTTP API request format and successful responses do not change. Oversized
JSON requests gain a documented HTTP 413 response. Existing malformed JSON and
decoded-PDF validation errors retain their current behavior after a body is
within the applicable transport limit.

## Failure and abuse handling

- A request with a missing, non-numeric, negative, or understated
  `Content-Length` is bounded by the reader and cannot bypass its endpoint
  limit.
- A body exactly at the applicable limit is passed to normal JSON parsing and
  validation; a body larger by one byte is rejected with 413.
- A normal endpoint cannot use the material-upload allowance by sending a
  Base64-like field or manipulating its path.
- A material upload within 28 MiB is still rejected if its decoded content is
  over 20 MiB or otherwise invalid.
- Error responses must not echo request content, Base64 data, session tokens,
  course details, stack traces, or internal parsing information.

## Acceptance criteria

1. A new numbered migration is registered in the PostgreSQL migration runner
   after V6 and applies successfully to a database previously migrated through
   V6.
2. After the new migration, inserting or persisting a `course_materials` row
   with `status = 'resource_only'` succeeds in PostgreSQL; each previously
   allowed status remains accepted.
3. PostgreSQL integration coverage exercises a valid image-only material
   lifecycle through `resource_only` persistence and reload, rather than only
   asserting the in-memory enum value.
4. V5 contains a clear development-stage comment that dropping
   `course_knowledge_maps` intentionally discards disposable legacy test data;
   no code or migration is added to retain or recover it.
5. A JSON endpoint other than material upload rejects a declared body larger
   than 1 MiB with HTTP 413 before the request stream is read or JSON is
   parsed.
6. The material-upload endpoint rejects a declared body larger than 28 MiB
   with HTTP 413 before reading, parsing, decoding, service invocation, or
   persistence.
7. For both a normal JSON endpoint and the material-upload endpoint, tests use
   an absent or understated `Content-Length` body one byte over the relevant
   limit and verify HTTP 413 with no service-side mutation.
8. Tests verify that a body exactly at each transport boundary reaches normal
   request processing. For the upload route, valid encoded fixture content
   within the body limit continues to reach the existing decoded PDF validation
   path.
9. Tests verify that a valid `resource_only` material remains downloadable
   only for authorized course members and remains ineligible for AI processing,
   preserving the existing status contract.
10. All relevant Java and PostgreSQL integration tests pass and the engineer
    records commands and actual results in
    `workflow/features/platform-hardening/tasks.md`.

## Dependencies and assumptions

- The existing 20 MiB decoded-PDF maximum remains the authoritative material
  content limit.
- The current Java HTTP server exposes request headers and a readable request
  stream sufficient to implement both early rejection and bounded reads.
- This maintenance feature adds no user-facing flow, so a `design.md` is not
  required.

## Open decisions and next handoff

No further human approval is required: the product owner authorized the
request-boundary change and explicitly approved loss of all current test data.

The engineer should implement only this approved scope, add the required tests,
and write `workflow/features/platform-hardening/tasks.md`. Code quality must
review the engineering handoff before independent testing, followed by the
tester and acceptance auditor because the work changes persistence and a
security boundary.
