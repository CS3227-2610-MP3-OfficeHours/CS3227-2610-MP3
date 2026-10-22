# Frontend modularization and stylesheet organization

Status: Approved
Approval: The human product owner approved this maintenance work in chat, 2026-10-22. The approved outcome is a domain-oriented React client and a readable, organized stylesheet that preserve current product behavior.
Last handoff: Product manager → Engineer, 2026-10-22 — implement this behavior-preserving frontend maintenance work, verify it, and record evidence in `tasks.md`.

## Purpose and outcome

Make the frontend safe to maintain by replacing the compressed single-file React client and stylesheet with readable modules organized by coherent product domains. The refactor must preserve the rendered UI, user interaction, accessibility behavior, backend API contract, and server-enforced authorization boundaries.

## Current technical context

The client is a React 19.1.1 application written in TypeScript 5.9.2 and built with Vite 7.1.3. Its source currently consists of one compressed `src/frontend/src/main.tsx` file and one compressed `src/frontend/src/styles.css` file. `main.tsx` combines startup, API calls, domain types, shared UI, authentication, course navigation, forum threads and tags, resources and upload, membership, course settings, and Knowledge Map workflows. The stylesheet mixes global tokens, layout, shared controls, responsive rules, and domain-specific rules.

## Scope

- Reformat all touched TypeScript/TSX and CSS into the repository's normal, readable style.
- Keep a minimal application entry point responsible only for bootstrapping the React root and importing the application-level stylesheet.
- Separate client code by cohesive domain, with a clear and shallow module layout. Domains include authentication, application/course navigation, forum and tags, resources and upload, members, settings, Knowledge Maps, API access, domain types, and shared UI/utilities.
- Put API request handling and shared frontend data types in dedicated modules so domain components do not duplicate transport logic or incompatible type definitions.
- Organize CSS into readable sections or CSS files with a documented import order: design tokens/base rules, shared layout and controls, then the applicable domain rules and responsive overrides.
- Preserve existing class names and DOM structure where practical. If a class or markup change is necessary to organize styles, preserve the visual result and semantic/accessibility behavior.
- Update only frontend source and frontend-maintenance documentation or tests that are necessary to verify the refactor. Build configuration changes are permitted only when strictly required by the new source-module imports; they must not change runtime behavior or tooling policy.

## Non-goals

- Adding, removing, or redesigning product features, screens, tabs, controls, copy, styling, navigation, or responsive breakpoints.
- Changing API paths, request or response shapes, authentication/session behavior, role/course authorization, server validation, persistence, or AI and RAG behavior.
- Changing the existing PDF upload encoding or validation flow.
- Introducing a component library, CSS framework, state-management library, router, test framework, dependency upgrade, or a new visual design system.
- Reworking the backend or changing build and deployment configuration for reasons unrelated to the frontend module boundary.

## Required module and stylesheet boundaries

The engineer may choose file names that fit the existing repository, provided the resulting layout keeps these responsibilities distinct and avoids a new catch-all file:

| Responsibility | Required ownership |
| --- | --- |
| React bootstrap | Entry point only: Strict Mode, root creation, and top-level stylesheet import. |
| API access | One shared typed request helper, including safe error handling and request options. |
| Shared types | Shared frontend representations of users, courses, forum content, materials, and Knowledge Maps. |
| Shared UI/utilities | Reusable presentational elements and non-domain helpers such as banners, empty states, status badges, date formatting, and authorization display helpers. |
| Authentication | Role selection, sign-in, and sign-up UI and its local state. |
| Course shell | Dashboard, course selection, tabs, page-level notices/errors, and composition of domain workspaces. |
| Forum | Post list, filtering, tag display/editing, post editor, thread, comments, and forum-specific helpers. |
| Resources | Resource list, upload dialog, and PDF compatibility messaging. |
| Members and settings | Membership management, course rename/delete, and their local state. |
| Knowledge Maps | Map loading, polling, generation, review controls, and presentation. |
| CSS | Base/tokens, shared components/layout, then domain styles; media queries stay adjacent to the rules they adapt or in a clearly labeled responsive section. |

This table describes ownership, not a requirement for exactly one file per row. A small domain may share a file with its tightly coupled child component; a domain must not be split merely to maximize file count.

## Behavior and compatibility requirements

1. The client must continue to call the same backend endpoints with the same HTTP methods, request payloads, credentials behavior, and response assumptions as before the refactor.
2. The client must continue to render the same role-specific course capabilities. Client-side visibility must remain supplementary to existing server authorization and must not introduce client-supplied authorization decisions.
3. Existing state transitions, loading indicators, error/notice messaging, confirmation dialogs, polling, retry behavior, and post-mutation refreshes must remain equivalent.
4. Existing accessibility semantics and interactions must remain intact, including accessible labels, keyboard navigation, focus restoration and trapping in the upload dialog, visible focus, alert/status roles, and responsive behavior.
5. Existing visible text, element ordering, colors, spacing, and breakpoint behavior must not deliberately change. Incidental rendering changes must be corrected rather than accepted as part of the refactor.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Moving components changes imports, prop wiring, or state ownership. | Move one coherent domain at a time, retain TypeScript checking, and exercise each existing workflow after the build passes. |
| CSS import ordering changes cascade or responsive behavior. | Establish and preserve explicit base, shared, domain, and responsive order; inspect desktop and narrow layouts for every affected workspace. |
| Shared types drift from API payloads while being moved. | Move the current types without altering their shapes; TypeScript build must pass and API calls must retain exact paths/methods/payload construction. |
| Accessibility regressions arise from markup cleanup. | Preserve semantic elements, ARIA attributes, dialog keyboard handling, and focus behavior; manually verify keyboard flows. |
| Refactor scope expands into product redesign. | Treat any copy, layout, behavior, API, authorization, or dependency change as out of scope unless separately approved. |

## Dependencies and assumptions

- The existing Java backend and its API contract remain the authoritative behavior; this feature does not modify them.
- The current Vite TypeScript build command (`npm run build` from `src/frontend`) remains available for verification.
- Existing browser/manual test procedures and the frontend build are the available verification baseline. Adding a frontend test framework is outside this feature.
- The current repository contains only development data; the refactor itself must not change data or migrations.
- No `design.md` is required because this work preserves the existing user interface rather than changing a user-facing flow.

## Acceptance criteria

1. `src/frontend/src/main.tsx` is a small, readable bootstrap module that creates the React root, renders the application, and imports the global stylesheet. It does not own domain workflows, API logic, or large type declarations.
2. The former application code is reorganized into readable TypeScript/TSX modules whose responsibilities satisfy the required ownership table. No new monolithic application or catch-all component file contains unrelated domains.
3. Shared API access and shared frontend types each have one clear source of truth. Domain modules import them rather than redeclaring or copying them.
4. All modified TypeScript and TSX use conventional readable formatting, named components/helpers, and imports with no behavior-changing type-suppression or lint-disabling shortcuts.
5. CSS is readable and organized into explicit base/tokens, shared, domain-specific, and responsive groupings or files. Selector order is intentional and sufficient to preserve the existing cascade; there are no duplicate conflicting rules introduced by the split.
6. The application renders the same authentication, dashboard, course tabs, forum/tag, thread/comment, resource/upload, membership, settings, and Knowledge Map workflows as before, with the same user-visible controls and text.
7. The refactor preserves current API calls: endpoint paths, methods, payload fields, handling of authenticated requests, and error extraction remain compatible with the backend. No frontend code treats a user role or course membership supplied by the client as authorization evidence.
8. Manual verification covers keyboard operation and visible focus for tag controls, the upload dialog's focus behavior and Escape/backdrop handling, alert/status messages, and a narrow viewport for the course shell, forum, upload dialog, and settings controls. Results are recorded in `tasks.md`.
9. `npm run typecheck` and `npm run build` run successfully from `src/frontend`; the engineer records the exact commands and results in `workflow/features/frontend-modularization/tasks.md`.
10. The engineer records the old-to-new module responsibility map, files changed, verification evidence, and any confirmed behavior-equivalence limitations in `tasks.md` for code-quality review.

## Design decisions requiring human approval

None. The requested work is a behavior-preserving maintenance refactor. The module layout and CSS-file grouping are implementation decisions bounded by this specification. Any proposed change to UI appearance, interaction, accessibility behavior, API contract, authorization, dependency set, or build policy requires a separate human approval before implementation.

## Next handoff

The engineer should implement only this specification and create `workflow/features/frontend-modularization/tasks.md` with the module map and verification results. The code-quality agent must then perform its required read-only review before the tester starts independent verification.
