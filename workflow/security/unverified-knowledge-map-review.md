# Unverified Knowledge Map staff-review policy

Date: 2026-10-21

## Approved policy change

When the SoC service returns Knowledge Map content but citation validation does
not verify every active source PDF, retain that content for authorized
course-staff review. The artefact is explicitly unverified; it cannot be
approved or used for student AI navigation.

## Trust-boundary controls

- The existing staff-only `getKnowledgeMap` endpoint remains the only read
  path. Students have no Knowledge Map endpoint and cannot access this state.
- The unverified output is rendered as text, not executable markup. Provider
  output and source filenames remain untrusted data.
- Server-side `approveKnowledgeMap` now refuses a pending-review map that has
  unresolved source-support gaps. Hiding the browser approval control is only
  a usability measure, not the authorization/security control.
- The map source snapshot and lifecycle checks remain unchanged. Deleted,
  stale, rejected, or unapproved maps remain ineligible for student use.
- The staff-facing message names only the affected filenames for the same
  authorized course. It exposes no storage paths, source text, API key,
  prompt, or cross-course metadata.

## Verification coverage

- The integration suite now confirms that uncited provider output is preserved
  as `pending_review`, carries an unverified explanation, and cannot be
  approved through the server API.
- Existing tests retain student map-read denial, cross-course generation denial,
  valid citation approval, source-change staleness, malformed provider output,
  and hostile-PDF framing coverage.

## Residual risk

Authorized staff can read inaccurate or malicious generated text in this review
state. The explicit label, text-only rendering, and approval block reduce its
impact but do not make untrusted output safe or correct. Staff must not rely on
it as source material; only verified, approved, current maps may ever guide
student AI navigation.
