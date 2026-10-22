---
name: ui-consistency
description: Design or review University TA Help Desk interfaces for shared layout, component, responsive, and accessibility consistency.
---

# UI Consistency

Use this skill before implementing a new user-facing flow or when reviewing an
existing student or staff interface. It applies to product UI, not internal
scripts, schemas, or API-only work.

Read the relevant approved feature specification, its design, existing related
UI source, and [the shared design system](../../../workflow/design/system.md)
before making recommendations. Preserve approved behavior and established
patterns. Do not change product requirements or authorization rules as a
visual-design choice.

## Design work

For a new or changed flow, document the page structure, information hierarchy,
layout containers, reusable components, role-specific navigation, interaction
states, and responsive behavior in the feature's `design.md`. State which
elements share a left/right alignment boundary and which are intentionally
narrower. Use the shared width and spacing roles; if a new role or component
variant is needed, name it, explain its purpose, and identify its relationship
to existing tokens. Avoid vague directions such as “make it consistent” without
stating the measurable relationship.

## Review work

Review the rendered interface when it can be run in the available environment.
Inspect representative desktop and narrow/mobile widths, including at least one
view with dense content or controls where applicable. Compare the rendered UI
with the approved design and shared system. Source inspection is useful for
finding causes, but a build passing or CSS appearing plausible does not prove
that the layout is visually consistent. If rendering is unavailable, state that
limitation and ground findings in source evidence without claiming visual
verification.

Report each actionable finding with:

- page/component and affected viewport or state;
- expected shared rule and observed mismatch;
- user impact and evidence (screenshot, rendered observation, or file/line);
- a focused correction, preferably at the shared container, token, or component
  level when multiple instances have the same cause.

Separate blocking usability/accessibility defects from polish suggestions. Do
not rewrite a design for preference, propose local overrides for a systemic
problem, or edit unrelated feature code. Write review reports in
`workflow/reviews/` when the orchestrator requests a report; otherwise return
findings to the orchestrator.

## University TA Help Desk defaults

Use a calm blue-and-white educational visual language unless an approved design
sets another direction. Treat the values and roles in the shared design-system
reference as the project defaults. Use semantic success, warning, and error
colors; status must not rely on color alone.

Keep a consistent application shell with product/course context, responsive
navigation, a constrained content region, and persistent role/course context.
Never expose another course's name, content, or controls in that shell. Reuse
component families for buttons, labelled form fields, status banners, cards,
empty states, confirmation dialogs, tabs, paginated lists, and source-resource
links. Name and document any new reusable pattern in the feature design and
propose adding it to the shared system when it applies across features.

Clearly distinguish human forum content, staff-managed source material, and
AI-generated assistance. AI content must be labelled, cite sources where
relevant, and never look like a published forum reply. Specify loading, empty,
success, validation-error, permission-denied, network/service-failure, keyboard,
focus, contrast, and responsive behavior for each applicable flow.

Before handoff, check:

- related page regions align to the same named container, except where the
  design documents an intentional narrower region;
- repeated instances of one component family share width, spacing, typography,
  and interaction treatment;
- spacing and dimensions use shared tokens or a documented exception;
- layouts reflow at the documented breakpoint without overflow or clipped
  controls;
- controls remain keyboard usable, labelled, visibly focused, and readable;
- required states and role/course visibility are represented.

Report concrete mismatches and their user impact. Do not treat this checklist as
a substitute for inspecting the actual rendered result during a visual review.
