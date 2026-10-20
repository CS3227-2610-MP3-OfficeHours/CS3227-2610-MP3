---
name: ui-consistency
description: Define or review University TA Help Desk UI work for consistent components, states, and accessible role-specific flows.
---

# UI Consistency

Use this skill before a new user-facing flow is implemented, or to review a
completed student or staff interface. It applies to product UI—not to internal
scripts, schemas, or ordinary API-only work.

Read the relevant feature specification and any existing design guidance before
making recommendations. Preserve established design decisions unless the task
explicitly changes them.

For a new flow, document the required page structure, reusable components, and
role-specific navigation in `workflow/design/` or the relevant feature spec. For
a review, write a focused report in `workflow/reviews/`.

Check that the work:

- reuses existing page shells, controls, status indicators, and spacing/type
  conventions where they exist;
- makes loading, empty, success, validation-error, permission-denied, and
  network/AI-failure states intentional;
- clearly distinguishes AI-generated content, source citations, and human posts;
- avoids exposing controls or information outside the current role/course; and
- is keyboard-usable and uses labels, semantic controls, and readable feedback.

Report concrete mismatches and their user impact. Do not rewrite the visual
design merely for preference or edit unrelated feature code.
