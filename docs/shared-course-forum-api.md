# Shared Course/Forum API Contract (v1)

All non-auth routes require the `ta_session` HttpOnly cookie issued on sign-up
or sign-in, or `Authorization: Bearer <token>`. The server derives the actor,
role, and course membership from that session: clients must never send an actor
ID, author ID, or claimed role as authority.

Responses are JSON. Failures use
`{ "error": { "code": "…", "message": "…" } }`: `401` for no valid
session, `403` for a role prohibition, `404` for unavailable/protected course
resources, `409` for unique conflicts, and `422` for malformed input.

## Client boundary

The React + TypeScript client consumes this API with its session cookie (or a
development bearer token). It must not receive PostgreSQL credentials, send an
actor/author identifier, or make authorization decisions. The Java server
continues to resolve identity and current course membership for every request.
Database-backed and in-memory local runtimes expose the same contract.

| Method and path | Body | Success | Server rule |
| --- | --- | --- | --- |
| `POST /api/auth/signup` | `username`, `password`, `role` | `201 {user}` and session cookie | Explicit `student` or `staff`; username unique |
| `POST /api/auth/signin` | `username`, `password` | `200 {user}` and session cookie | Password is never returned |
| `POST /api/auth/signout` | none | `204` | Revokes current session |
| `GET /api/me` | none | `200 {user}` | Current identity only |
| `GET/POST /api/courses` | POST: `code`, `name` | `200 {courses}` / `201 {course}` | POST requires staff role; creator becomes course staff |
| `GET/PATCH/DELETE /api/courses/{courseId}` | PATCH: `name` | course / `204` | Read requires current membership; changes require course staff |
| `GET/POST /api/courses/{courseId}/members` | POST: `username` | members / membership | Course staff only; target role controls membership role |
| `DELETE /api/courses/{courseId}/members/{userId}` | none | `204` | Course staff only; only a student membership can be removed |
| `GET/POST /api/courses/{courseId}/posts` | POST: `title`, optional `description` | posts / post | Current course member; title 1–120, description 0–2000 |
| `GET/PATCH/DELETE /api/courses/{courseId}/posts/{postId}` | PATCH: title/description | post / `204` | Read member; edit author only; delete author or course staff deleting student author |
| `POST /api/courses/{courseId}/posts/{postId}/comments` | `body` | `201 {comment}` | Current course member and in-course parent post |
| `PATCH/DELETE /api/courses/{courseId}/posts/{postId}/comments/{commentId}` | PATCH: `body` | comment / `204` | Author edit; author delete or course staff deleting student author |

`post` and `comment` responses include source Markdown and a `rendered…` field.
The renderer escapes supplied HTML before applying its small Markdown allowlist;
clients should use the rendered field for display. There are intentionally no
attachment or search endpoints. A course or post deletion is hard deletion;
post deletion cascades its comments.
