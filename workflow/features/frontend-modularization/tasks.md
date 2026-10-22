# Frontend modularization — engineering handoff

Status: Ready for follow-up code-quality review
Last handoff: Engineer → Code-quality, 2026-10-22 — remediated the five named review findings.

## Implemented work

- Replaced `src/frontend/src/main.tsx` with a bootstrap-only entry point that
  imports the application stylesheet, creates the React root, and renders
  `App` in `StrictMode`.
- Moved the existing frontend types and typed request helper into dedicated
  API modules.
- Moved each existing workflow into a coherent domain module without changing
  endpoint paths, HTTP methods, payload construction, authorization display,
  labels, interaction handlers, or accessibility attributes.
- Reformatted `styles.css` and added ordered sections for base rules,
  application layout, shared controls, authentication, Knowledge Maps,
  upload UI, forum/tags, and settings. Existing selector and declaration order
  remains intact within the prior cascade.

## Module responsibility map

| Previous responsibility in `main.tsx` | Current module |
| --- | --- |
| Bootstrap | `src/frontend/src/main.tsx` |
| Shared API types | `src/frontend/src/api/types.ts` |
| Typed HTTP helper and error extraction | `src/frontend/src/api/client.ts` |
| Shared date, authorization-display, banners, empty states, and delete control | `src/frontend/src/shared/ui.tsx` |
| Role selection, sign-in, and sign-up | `src/frontend/src/auth/AuthEntry.tsx` |
| Dashboard and application state | `src/frontend/src/app/App.tsx` |
| Course tabs and domain-workspace composition | `src/frontend/src/app/CourseWorkspace.tsx` |
| Course creation | `src/frontend/src/courses/CourseForm.tsx` |
| Posts, thread, comments, tag picker, and forum helpers | `src/frontend/src/forum/Forum.tsx` |
| Staff tag-management controls | `src/frontend/src/forum/ForumTagsSettings.tsx` |
| Resource listing and upload dialog | `src/frontend/src/resources/Resources.tsx` |
| Course membership | `src/frontend/src/members/Members.tsx` |
| Course settings | `src/frontend/src/settings/Settings.tsx` |
| Knowledge Map loading, polling, generation, review, and display | `src/frontend/src/knowledge-maps/KnowledgeMapWorkspace.tsx` |

## Verification

| Command | Result |
| --- | --- |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm run typecheck` from `src/frontend` | Passed |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm run build` from `src/frontend` | Passed; Vite transformed 40 modules and generated the production bundle. |
| Static comparison of moved declarations | Completed; workflow components retain their prior JSX, endpoint strings, payload construction, state transitions, dialogs, polling, and ARIA attributes. |

## Limitations and next owner

- The repository has no frontend test framework, and adding one is outside the
  approved scope. No automated frontend behavior tests were added.
- Browser-based manual verification of tag keyboard navigation, upload-dialog
  focus trapping/restoration and Escape/backdrop behavior, alert/status
  messaging, and narrow layouts has not been run in this handoff. Those checks
  remain for independent testing against a running application.
- No dependency, backend, API, authorization, or build-policy changes were
  made. The pre-existing untracked `.pnpm-store/` remains untouched.

Code quality should review the module boundaries, imports, formatting, and
stylesheet organization before independent testing begins.

## Code-quality remediation

- Reformatted all touched TSX files so their component markup is represented
  over readable nested lines while preserving the same elements, attributes,
  child ordering, labels, and handlers.
- Moved shared tag presentation and helpers into
  `src/frontend/src/forum/tagPresentation.tsx`; both forum workflows now use
  that module.
- Consolidated `.forum-tag-settings`, `.post-link`, and `:root` declarations
  in `styles.css`, retaining their final effective visual declarations.
- Added safe error-payload handling to `src/frontend/src/api/client.ts`. The
  follow-up remediation below extends this with route-aware success decoders.
- Replaced namespace-qualified CSS property assertions with imported
  `CSSProperties` types and removed stale tag-helper exports from `Forum.tsx`.

The remediation passed the same frontend checks again:

| Command | Result |
| --- | --- |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm run typecheck` from `src/frontend` | Passed |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm run build` from `src/frontend` | Passed; Vite transformed 41 modules and generated the production bundle. |

Formatting verification preserved explicit inline whitespace in labels and
inline text around dynamic values, including course labels, tag counts, course
navigation, sign-up role text, and comment metadata.

## Upload-dialog manual verification

Status: Blocked by the available browser automation surface.

The local Java service returned its expected authentication response at
`http://localhost:8080/api/me`, and the Vite development server started at
`http://127.0.0.1:5173/`. The available in-app and Chrome browser surfaces
reported unavailable. Safari automation did not return a controllable window
before the verification turn was interrupted. As a result, no direct browser
evidence can be recorded for clicking the backdrop and confirming focus
restoration to the upload trigger.

Static implementation note: `UploadDialog` retains its existing backdrop
`onMouseDown` handler, which calls `onClose()` only when the backdrop itself
is clicked and upload work is not busy; its effect cleanup restores focus to
the captured opener. A human or independently configured browser test should
exercise this path before acceptance.

## Post-test remediation

- Added `key={swatch.color}` to each tag-palette button mapped in
  `src/frontend/src/forum/ForumTagsSettings.tsx`, removing the React missing-key
  warning identified during independent testing.
- Available verification evidence remains the earlier successful frontend
  `npm run typecheck` and `npm run build` results. No additional checks were
  run for this documentation-only handoff update.

## Follow-up remediation

- Applied Prettier formatting to every frontend TypeScript and TSX module. It
  expands the remaining nested JSX, conditional rendering, callbacks, and
  multiline component props without changing DOM structure or behavior. The
  formatter was used transiently and did not modify package manifests or add a
  runtime dependency.
- Replaced the generic successful-response cast in `api/client.ts` with
  route-aware structural decoders. They verify every consumed response shape:
  users, courses, forum tags, posts, comments, members, resources, and
  Knowledge Map data. Requests whose response body is not consumed retain the
  existing object-response guard; `204` responses still return `undefined`.
- Removed the unused React type imports from `shared/ui.tsx`.

Follow-up verification:

| Command | Result |
| --- | --- |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm run typecheck` from `src/frontend` | Passed |
| `PATH=/Users/xp/.nvm/versions/node/v24.22.0/bin:$PATH npm run build` from `src/frontend` | Passed; Vite transformed 41 modules and generated the production bundle. |
