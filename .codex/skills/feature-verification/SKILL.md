---
name: feature-verification
description: Verify a completed University TA Help Desk feature against its specification, security constraints, UI guidance, and tests.
---

# Feature Verification

Use this skill after a feature is implemented and before it is accepted or
merged. It verifies evidence; it does not redefine product requirements. Read
the matching feature specification, relevant UI/security reviews, implementation
diff, and test results.

Write a concise verification report to `workflow/verifications/` that maps each
acceptance criterion to evidence or a failure. Include:

- functional behavior and relevant user-visible states;
- server-side role and course authorization, including at least one negative
  case when a trust boundary is crossed;
- applicable unit, integration, end-to-end, and AI-security tests;
- UI-review status for user-facing work;
- safe behavior for AI/provider failures where AI is involved; and
- unresolved defects, limitations, or evidence that is missing.

Use `pass` only when evidence supports every required criterion. Otherwise use
`needs changes` and name the specific blocking criterion. Do not mark a feature
passed merely because tests execute successfully if the tests do not demonstrate
the intended behavior.
