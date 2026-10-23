# Shared Course/Forum API Contract (v1)

All non-auth routes require the `ta_session` HttpOnly cookie issued on sign-up
or sign-in, or `Authorization: Bearer <token>`. The server derives the actor,
role, and course membership from that session: clients must never send an actor
ID, author ID, or claimed role as authority.

Responses are JSON. Failures use
`{ "error": { "code": "…", "message": "…" } }`: `401` for no valid
session, `403` for a role prohibition, `404` for unavailable/protected course
resources, `409` for unique conflicts, and `422` for malformed input.

Staff Knowledge Map generation can also return `429` with
`{ "error": { "code": "generation_rate_limited", "message": "Generation limit reached…", "retryAt": "ISO-8601 timestamp" } }`
and a rounded-up `Retry-After` response header. `retryAt` and `Retry-After` are
calculated by the server. The response deliberately omits the exhausted scope,
remaining quota, jobs in other courses, and provider details.

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

## Stage 4A course materials

These endpoints keep uploaded bytes private to the server and authorize every
read again. `contentBase64` is only the temporary JSON transport used by the
dependency-free local adapter; it is not a public object-storage URL. The
server accepts `application/pdf` payloads up to 20 MiB after a conservative
structural PDF gate (classic xref table or direct xref stream with matching
object offsets and terminal `startxref`, resolved catalog/page-tree/page
references, object graph, terminal EOF), encryption,
and page-limit validation. Accepted PDFs begin in `processing`; the local
non-AI processor later marks them `active` or `failed`.

Stage 4A supports classic xref tables plus direct xref streams with unfiltered
or safely capped FlateDecode entry data. Other filters and object-stream entry
types are rejected safely rather than being treated as parseable.

| Method and path | Body | Success | Server rule |
| --- | --- | --- | --- |
| `GET/POST /api/courses/{courseId}/materials` | POST: `filename`, `contentType`, `contentBase64` | staff materials / `201 {material}` | Assigned course staff only; POST validates bounded PDF and 20-item capacity. |
| `DELETE /api/courses/{courseId}/materials/{materialId}` | none | `204` | Assigned course staff only; removed immediately from Resources and future consumers. |
| `POST /api/courses/{courseId}/materials/{materialId}/retry` | none | processing material | Assigned course staff only; only a failed non-AI processing item can retry, at most three submissions/minute/staff/course. |
| `GET /api/courses/{courseId}/resources` | none | active resource metadata | Current course members only; returns active PDFs only. |
| `GET /api/courses/{courseId}/resources/{materialId}/download` | none | `application/pdf` attachment | Current course members only; active PDF and membership are checked for every download. |

There are no consolidation, embedding, RAG, or public storage endpoints in
Stage 4A.
