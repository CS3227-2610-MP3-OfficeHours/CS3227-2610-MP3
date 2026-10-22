# Platform hardening — independent test report

Status: Pass
Tested: 2026-10-22
Last handoff: Tester → Acceptance auditor, 2026-10-22 — CI request-limit regression re-verified; all approved acceptance criteria retain recorded independent evidence.

## Scope and evidence reviewed

Reviewed the approved [specification](spec.md), engineering [handoff](tasks.md),
[upload-boundary security review](../../security/platform-hardening-upload-boundary-review.md),
implementation diff, V5 and V7 migrations, request-boundary implementation,
and related Java integration tests. This re-verification specifically assessed
the CI remediation that removed the live over-28 MiB material-upload request.
The working tree also contains unrelated forum-tag, frontend, and workflow
changes; this report assesses only the `platform-hardening` scope.

## Commands executed

| Command | Actual result |
| --- | --- |
| `sh scripts/test.sh` | Pass (re-run for this report). `Platform integration tests: PASS`; `HTTP API integration tests: PASS`; `Student AI help tests: PASS`; `SoCLaaS client tests: PASS`; `Static site tests: PASS`. PDFBox emitted the expected malformed-PDF parser diagnostic exercised by the existing negative test, then the suite completed successfully. |
| `sh scripts/test-postgres.sh` | Pass. The local Docker PostgreSQL integration test completed with `PostgreSQL platform integration tests: PASS`. |

The HTTP test required local loopback socket access and the PostgreSQL test
required the local Docker service. Both were rerun in their intended local
environment and passed.

## Scenario results and acceptance-criteria coverage

| Criterion | Independent verification and result |
| --- | --- |
| 1 | Pass. `PostgresPlatform` registers V7 after V6. The PostgreSQL backfill scenario starts at V1–V6, opens the platform, and succeeds, demonstrating the forward migration is applied. |
| 2 | Pass. V7 replaces the `course_materials` status constraint with the five required values. PostgreSQL integration inserts `processing`, `active`, `resource_only`, `failed`, and `deleted` rows successfully after migration. |
| 3 | Pass. PostgreSQL integration uploads an image-only PDF, waits for `resource_only`, reopens the platform, and verifies the material reloads with that status and remains downloadable to an enrolled student. |
| 4 | Pass. V5 explicitly records that its drop intentionally discards disposable legacy course Knowledge Map test data and requires no preservation. No retention or recovery code was added. |
| 5 | Pass. The loopback HTTP integration test sends a declared body over 1 MiB to signup and receives 413. Source inspection confirms the declared length check runs before obtaining the request body stream. |
| 6 | Pass. A controlled handler exchange sends the authorized material-upload route a declared `Content-Length` over 28 MiB and supplies a body stream that throws if read. The handler returns the safe 413 response without consuming the body, parsing JSON, decoding Base64, or creating material. The endpoint chooses the larger cap only for `POST /api/courses/{courseId}/materials`. |
| 7 | Pass. Handler-level tests use both absent and understated (`Content-Length: 0`) headers with bodies one byte over each cap. Each returns 413 with only `request_too_large` and `The request is too large.`; oversized signup creates no user and oversized uploads create no material. |
| 8 | Pass. Exactly 1 MiB of valid JSON reaches normal validation and returns 422 rather than 413. An exactly 28 MiB upload containing a valid image-only PDF returns 201, reaching the existing upload-processing path. |
| 9 | Pass. Platform integration verifies an image-only resource remains downloadable to an authorized member and a configured Knowledge Map generator rejects it with 422. Existing authorization tests cover cross-course and non-member access restrictions. |
| 10 | Pass. Both relevant Java suites and the PostgreSQL integration suite passed as recorded above. |

## CI request-limit regression re-verification

- Pass. The live loopback test no longer constructs or sends a body larger
  than 28 MiB to the material-upload route. Its only material-upload transport
  request is exactly 28 MiB and returned 201 in the passing suite.
- Pass. The declared-over-limit material case now uses a controlled
  `HttpExchange` with `Content-Length: 29,360,129` and a request stream that
  throws on any read. `HttpApi.handle` returned the safe 413 JSON response;
  the passing assertion confirms the stream was not consumed.
- Pass. After the declared, missing-header, and understated-header material
  cases, the controlled service's material list remains empty. The test also
  checks the same safe two-field `request_too_large` response shape for every
  boundary case.
- Pass. The existing exactly-at-limit material fixture remains loopback
  coverage of normal upload processing. It embeds a valid image-only PDF and
  is exactly 29,360,128 bytes, so the 201 result demonstrates the 28 MiB
  boundary still reaches the normal upload path.

## Security and boundary checks

- The size reader rejects an oversized declared `Content-Length` before
  consuming the request body. For absent, invalid, or understated headers, it reads at
  most the applicable limit plus one byte before returning 413.
- The safe error response contains only the generic code and message checked
  above. The over-limit handler tests demonstrate no JSON parsing that creates
  a signup account and no material persistence.
- The upload route retains session, role/course, decoded-PDF, encryption,
  structure, page-count, and 20 MiB decoded-content validation after the
  transport boundary. The reviewed security artefact identifies PDFs and their
  extracted content as untrusted and records remaining parser and deployment
  controls.

## Defects and limitations

No defects found in the approved scope.

The JDK loopback server's request framing makes a live declared-over-limit
material upload timing-dependent while the client streams 28 MiB. That case,
along with absent and understated `Content-Length`, is verified through the
controlled handler exchange instead. The declared case proves the body is not
consumed; the exactly-at-limit material upload remains real loopback coverage.
The request caps bound a single application request but do not replace
production connection, concurrency, reverse-proxy, or PDF-parser resource
limits, as recorded in the security review.

## Final assessment

**Pass.** The approved platform-hardening requirements have independent test
evidence. Next owner: acceptance auditor for the required artefact-only
completion review.
