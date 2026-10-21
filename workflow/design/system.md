# University TA Help Desk — Shared UI System

Status: Project defaults; feature designs may propose changes for human review.
Owner: Product owner and UI designer.
Last reviewed: 2026-10-22.

This document is the shared visual and layout reference for feature designs and
implementation. It resolves repeated choices across features; it does not
replace approved feature behavior or server-enforced authorization. When an
approved design intentionally differs, record the reason in that feature's
design and keep the exception local.

## Visual foundation

- Use a calm blue-and-white educational interface: very light neutral page
  surfaces, dark readable text, and blue for primary actions, navigation, focus,
  and informational accents.
- Use semantic success, warning, and error treatments. Pair color with labels,
  icons, or text so status is not communicated by color alone.
- Use the existing system font stack unless a feature design has an approved
  reason to change it. Preserve readable body text (16 px baseline) and a clear
  heading hierarchy.
- Use an 8 px base spacing rhythm. The current interface also uses 4 px for
  compact control gaps; document any other exception. Prefer responsive gutters
  over fixed page padding.
- Interactive targets should be at least 44 px high. Keep visible keyboard
  focus and semantic labels.

## Layout roles and width rules

Widths describe the intended role of a region, not a requirement to force every
child to fill it. Use one shared parent for regions that should align; constrain
inner content only where its reading or interaction role calls for it.

| Role | Maximum width | Use |
| --- | ---: | --- |
| Application shell | 1,200 px | Shared outer frame, centered; 24 px desktop and 16 px narrow-screen gutters. |
| Main management region | 1,120 px | Course management, resource lists, and other broad work areas, centered within the shell. |
| Reading region | 760 px | Forum thread and other long-form text, aligned to the main content start. |
| Form region | 640 px | Create/edit forms and focused data entry. |
| Authentication region | 460 px | Sign-in and registration card. |
| Dialog region | 720 px | Modal content; use `width: min(720px, 100%)` and constrain height to the viewport. |

The current shared-course-forum design names 760 px reading and 1,120 px
management regions; the current stylesheet uses a 1,200 px `.app` shell, a
640 px `.narrow` form, a 460 px `.auth` card, and a 720 px upload dialog. Treat
those as deliberate nested roles, not competing widths. New designs should
state which role each region uses and which elements share its alignment edge.

For a page with a sidebar, align the header and navigation to the shell. Treat
the sidebar and main column as one layout grid. Within the main column, align
section headings, list/card edges, and their related actions unless the design
states why an element is inset. Repeated cards in one list use the same width.
Use `minmax(0, 1fr)` or an equivalent shrinkable track for flexible columns so
long content does not force overflow.

At the shared 768 px breakpoint, collapse the two-column app layout, stack
header/action groups as needed, and change two-column form/member layouts to one
column. Designs may choose another breakpoint only when content behavior
justifies it and the design records it.

## Shared component behavior

- Buttons: primary, secondary, and danger variants have stable sizing and
  hierarchy. Reserve danger styling for destructive actions. Pending state
  prevents duplicate submission and gives a clear progress label.
- Fields: visible label, help text where needed, and inline validation linked
  programmatically to the field. Keep field widths consistent within a form.
- Cards and list rows: repeated items in the same context share padding, border,
  radius, and action placement. Let content wrap without changing neighboring
  item alignment.
- Banners and status badges: use consistent semantic treatment and plain-language
  labels. Errors explain recovery where possible.
- Staff role badges: use one shared yellow fill (`#FEF08A`) with dark brown text
  (`#713F12`) in the account header, course `Staff access` context, member list,
  and staff-authored forum content. Use a compact uppercase `STAFF` label beside
  a staff author's name; keep the badge text visible so role is not conveyed by
  color alone. Student role badges keep the neutral blue treatment.
- Empty states: explain why the region is empty and provide only an action the
  current role is allowed to take.
- Dialogs: identify the action and consequence; support keyboard focus,
  dismissal rules, and narrow-screen full-width behavior.
- AI regions: visibly label generated material, preserve source citations, and
  keep it visually separate from human-authored posts and approved source files.

Feature designs can add components, but must state whether they are local or
shared, their width/spacing rules, variants, responsive behavior, and states.
Do not create a second visual variant of an existing component without naming
the difference and its user need.

## Visual review evidence

For a UI review, inspect the running interface at a representative desktop
viewport and a narrow/mobile viewport where possible. Check shared alignment
edges, repeated component dimensions, text wrapping, overflow, responsive
reflow, keyboard focus, and the relevant loading/empty/error states. Record the
viewport and state with each screenshot or observation. If the environment
prevents a rendered review, state exactly what was inspected and leave visual
conformance unverified.
