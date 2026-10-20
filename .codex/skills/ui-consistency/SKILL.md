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

## University TA Help Desk visual system

For this product, use a calm blue-and-white educational visual language unless
an approved feature design says otherwise. Treat the following as shared
defaults, not a reason to bypass accessibility or role/course authorization:

- Use white or very-light neutral page surfaces, dark readable text, and blue as
  the primary action, navigation, focus, and informational accent. Reserve
  success, warning, and error colors for their semantic states; do not encode
  status in blue alone.
- Keep a consistent application shell: top product/course context, left or
  compact responsive navigation, a constrained readable content column, and
  persistent course/role context. Do not expose another course's name, content,
  or controls in that shell.
- Reuse the same component families across staff and student flows: primary and
  secondary buttons, form fields with labels and inline validation, status
  banners, cards, empty states, confirmation dialogs, tabs, paginated lists,
  and source-resource links. New feature designs must name additions rather
  than silently creating visual variants.
- Present human forum content, staff-managed source materials, and AI-generated
  assistance as visibly distinct labelled regions. AI panels must show source
  citation links and never resemble a published forum reply.
- Specify responsive, keyboard, focus, contrast, loading, empty, success,
  validation-error, permission-denied, and network/service-failure behavior in
  every new user-facing design.

When designing a feature, add or refine its `design.md` using these defaults.
If a feature introduces a reusable component or pattern, record it in that
design so later feature designers use the same treatment.

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
