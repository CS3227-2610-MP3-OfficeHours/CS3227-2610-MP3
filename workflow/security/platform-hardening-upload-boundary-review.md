# Platform hardening upload-boundary review

Status: Reviewed for engineering handoff, 2026-10-22

Scope: The approved `platform-hardening` change adds encoded JSON request-size
limits before parsing and preserves the existing course-material upload path.

## Boundary review

`HttpApi` chooses the 28 MiB limit only for `POST /api/courses/{courseId}/materials`.
Every other JSON route uses the 1 MiB limit. A supplied `Content-Length` over
the applicable bound is rejected before the request body is consumed. Missing,
invalid, and understated values are not trusted: the reader accepts at most the
limit plus one byte and returns a safe 413 response before JSON parsing or
Base64 decoding.

The material route still requires a valid session before request handling and
the existing service retains staff membership, course, filename, PDF type,
encryption, parseability, page-count, and 20 MiB decoded-content checks. An
over-limit request returns only `request_too_large` and a generic message; it
does not disclose course existence, request content, or parser details.

The new size boundary reduces memory exhaustion exposure but does not make
untrusted PDFs safe by itself. PDF content, filenames, extracted text, and
later prompt inputs remain untrusted. Existing parser limits, authorization,
and review gates remain required, and prompt injection cannot be fully solved
by this change.

## Security test cases

| Case | Expected result | Evidence |
| --- | --- | --- |
| Direct oversized normal JSON request | 413 before JSON parsing or service mutation | `HttpApiIntegrationTest` sends a declared body over 1 MiB. |
| Direct oversized material upload | 413 before body consumption, parsing, Base64 decode, or material creation | `HttpApiIntegrationTest` invokes `HttpApi.handle` with an authorized material-upload request whose declared `Content-Length` exceeds 28 MiB. Its body stream throws if read, proving the safe 413 response does not consume it. |
| Missing or understated length | Handler returns only `request_too_large` and the exact safe message `The request is too large.`, parses no JSON, and leaves service state unchanged | `HttpApiIntegrationTest` invokes `HttpApi.handle` for both normal JSON and material uploads with absent and `0` lengths. It proves an oversized signup creates no user and oversized uploads create no material. |
| Boundary-size normal JSON | Passes the transport reader and reaches normal validation | `HttpApiIntegrationTest` uses an exactly 1 MiB signup body and receives 422 validation rather than 413. |
| Boundary-size material upload | Passes the transport reader and reaches existing upload processing | `HttpApiIntegrationTest` uses an exactly 28 MiB JSON payload containing a valid image-only PDF and receives 201. |
| Indirect injection in uploaded PDF | Treat PDF text as untrusted source material; it cannot alter authorization or model instructions | Existing ingestion/retrieval security tests remain applicable; no behavior changed here. |
| Cross-course retrieval/download | Unauthorized actors receive the existing hidden response and cannot access material | Existing `PlatformIntegrationTest` authorization coverage remains applicable. |
| Image-only resource source | A downloadable `resource_only` PDF cannot produce a Knowledge Map | `PlatformIntegrationTest` configures a map generator after the status transition and verifies generation receives 422. |
| Malformed upload | Existing decoded-PDF validation returns a safe validation error after the transport cap | Existing `PlatformIntegrationTest` malformed-PDF cases remain applicable. |

## Residual risk

The bounded reader still allocates up to the configured endpoint limit for a
single request. The API remains JSON/Base64 rather than streaming multipart,
and the parser is a separate resource boundary. Production deployment should
also apply connection, concurrency, and upstream request-size controls.
