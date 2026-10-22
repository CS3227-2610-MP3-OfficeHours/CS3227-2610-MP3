# Resource visibility across PDF processing states — security review

Status: Static review, 2026-10-22; runtime verification pending

## Scope

Review of the approved change that lists and serves every successfully
uploaded, non-deleted course PDF in Resources, including `processing` and
`failed` extraction states. AI retrieval and Knowledge Map generation remain
restricted to active, processed material.

## Boundary review

- `PlatformService.listResources` still calls `requireCourseMember` before
  returning metadata, scopes rows to the requested course, and excludes
  `deleted` materials. Extraction state no longer controls resource visibility.
- `PlatformService.downloadResource` still calls `requireCourseMember`, checks
  that the material belongs to the requested course, and rejects `deleted`
  records. It now allows the stored original bytes for other material states.
- The HTTP download remains an attachment with `X-Content-Type-Options:
  nosniff` and `Cache-Control: no-store`.
- AI eligibility remains separately gated: `activeMaterials` selects only
  `MaterialStatus.active`; Knowledge Map generation also requires `active`.
  A processing or extraction-failed PDF therefore cannot become retrieval
  input merely because it is downloadable.
- Upload validation remains before material creation. A rejected upload does
  not create a resource row. Accepted PDFs and their contents remain untrusted;
  this change does not parse, execute, or send additional PDF text to an AI.

## Required negative and state tests

| Case | Expected result |
| --- | --- |
| Enrolled member lists a processing, active, resource-only, or failed PDF | Each accepted, non-deleted PDF is listed for its course. |
| Enrolled member downloads a processing or failed PDF | Original PDF bytes are served as an attachment. |
| Cross-course staff, non-member, or removed member lists or downloads any state | Existing hidden/denied response; no metadata or bytes disclosed. |
| Deleted PDF is listed or downloaded | It remains absent and unavailable for every role. |
| Upload validation/request fails | No resource record is created. |
| Failed/processing PDF is offered to student AI or Knowledge Map generation | It remains excluded until active processing succeeds. |
| One of several failed PDFs is dismissed in Knowledge Maps | Only that notice is hidden in the current view; status and resource remain, other notices remain visible, and reload may show it again. |

These are verification requirements, not executed test results.

## Residual risk

All current course members can now download the original PDF even if text
extraction failed. This is the explicitly approved Resources behavior and is
bounded by existing server-side course membership checks. The PDF remains
untrusted; attachment disposition and `nosniff` reduce browser interpretation
risk but do not establish that arbitrary PDF files are harmless. Independent
tests should verify authorization for each processing state and prove that
resource visibility does not broaden AI source eligibility.
