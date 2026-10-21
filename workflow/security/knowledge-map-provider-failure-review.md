# Knowledge Map provider-failure state review

Status: Implemented change; independent verification pending

## Finding

A provider 5xx is mapped to `temporarily_unavailable`, which correctly creates
a failed Knowledge Map. The staff UI incorrectly displayed any map
`failureMessage` as an unverified returned map, implying missing PDF/page
support even when the provider returned no content.

## Change reviewed

- Failed generation records now contain no map content.
- The staff UI shows source-support review guidance only for a
  `pending_review` map with an unverified returned result.
- A `failed` map shows an outage-specific notice and no approval/rejection
  controls or source-support links.

## Boundary checks

- The provider failure remains course-staff-only through the existing endpoint.
- No PDF text, provider response body, credentials, or transport diagnostics is
  returned to the browser.
- A failure cannot be approved for student navigation because it is not in
  `pending_review` state.
- A prior map is not presented as the output of the failed generation.

## Regression evidence

`PlatformIntegrationTest` now asserts a temporary provider failure transitions
to `failed`, retains only the safe failure message, and has null content.
The full server test suite and frontend production build pass locally.

Residual risk: the public-safe failure category intentionally cannot distinguish
provider capacity, gateway health, or an upstream timeout. Operators need
server-side correlation logging to diagnose those conditions without exposing
them to staff or students.
