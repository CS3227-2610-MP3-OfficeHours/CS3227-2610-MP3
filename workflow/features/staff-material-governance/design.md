# Staff Material Governance — UI Design

Status: Approved for Stage 4A and the Stage 4B Knowledge Map UI; Stage 4B awaits Stage 3 completion
Approval: Human product-owner approval recorded in chat, 2026-10-20 and 2026-10-21 — approved Resources, non-AI Manage materials, and one manually updated Knowledge Map per course.
Last handoff: Human product owner → Engineer, 2026-10-21 — implement Stage 3, then enable the approved Knowledge Map controls.

## Scope and access boundary

This design implements the confirmed material-governance requirements in `spec.md`, product-foundation decisions, and the `ui-consistency` skill. It does not design student AI help or enable AI before the shared service and AI scope are approved.

Only assigned course staff see material management, job, consolidation, and review controls. Enrolled students see active PDFs only in Resources. A non-member, removed student, or unassigned staff member receives a generic access-denied/not-found result with no course, file, or job metadata. The server authorizes every route, list, download, mutation, and job.

## Shared blue-and-white system and pattern additions

Use the shared application shell: white/light-neutral surfaces, dark readable text, blue for primary actions, navigation, information, and focus, and semantic green/amber/red for success, processing/warning, and failure. Colour is never the sole status cue. The top bar has product, signed-in username/role, and sign-out; the course strip shows only the authorized course code/name; desktop uses a left rail and small screens use compact course navigation.

| Reusable pattern | Treatment |
| --- | --- |
| `CourseTabs` | **Forum**, **Resources**, and **Manage materials** only for assigned staff. Preserve the active tab in URL/history. |
| `ResourceRow` | Responsive PDF row with safe filename, status badge, metadata, and contextual actions; never exposes storage URLs. |
| `StatusBadge` | Written label plus icon/shape for Processing, Active, Failed, Pending review, Approved, Rejected. |
| `SourceCitationList` | Ordered `PDF filename — p. N` links to re-authorized server endpoints, not public object URLs. |
| `KnowledgeMapPanel` | Labelled **AI-generated Knowledge Map — review required**, visibly distinct from human forum posts. |
| `DestructiveConfirmDialog` | Cancel-first focused dialog with exact object name and irreversible consequence. |
| `JobFailureNotice` | Safe error, retry eligibility, and retry action; no stack trace, secret, private path, or protected content. |

Use one primary blue action per region; outlined/text secondary actions; persistent labels; inline validation plus a top-of-form error summary; visible focus rings; 16 px base type; 8 px spacing scale; and at least 44 px targets. These are shared additions that parallel and later designs must reuse instead of creating variants.

## Information architecture

| Viewer | Visible tabs | Material destination |
| --- | --- | --- |
| Enrolled student | Forum, Resources | Active-PDF Resources list |
| Assigned course staff | Forum, Resources, Manage materials | Manage materials; Resources remains selectable |
| Non-member / removed student / unassigned staff | No course shell | Generic denied/not-found page |

`Resources` is a read-only course-forum handoff. `Manage materials` is a separate staff workspace, so staff-only processing/AI data cannot appear in student navigation or markup.

## Screens and interactions

### Resources — enrolled students and assigned staff

Show heading **Resources**, help text “Course PDFs shared by staff,” and a semantic list of `ResourceRow`s. Each row has PDF icon, safe filename, and **Download PDF**. The member-facing list does not display job status, uploader identity, internal metadata, or AI eligibility. The server authorizes both the list and every download.

- A download has button-local loading feedback; failed downloads announce an adjacent **Try download again** action.
- Display active PDFs only. Pagination is unnecessary at the confirmed maximum of 20.
- Empty state: “No resources have been shared yet. Check back after course staff upload a PDF.” No upload action.
- Revoked access/stale direct URL: “You no longer have access to this course’s resources,” then **Back to my courses**, with no named details.
- Refresh failure preserves safely loaded rows and says “Resources could not be refreshed. Try again.”

### Manage materials — assigned course staff only

Heading **Manage materials** with text “Upload course PDFs, monitor processing, and maintain the course Knowledge Map.” Show `Active PDFs: N of 20` as informative summary only. Desktop has **Source PDFs** (upload card and list) then **Course Knowledge Map** (generation/review). Wide layouts may place upload beside the summary; small layouts stack every control. Long filenames wrap. Direct access by an unassigned actor is denied without existence leakage.

#### Upload card

The card has labelled file input, **Choose PDF**, selected safe filename/size, **Remove selection**, and primary **Upload PDF**. Helper: “PDF only · maximum 20 MB · maximum 100 pages · up to 20 active PDFs per course.” Upload is disabled without a selection. Browser constraints are guidance; server validation is authoritative.

| Condition | Safe message |
| --- | --- |
| Missing file | “Choose a PDF to upload.” |
| Non-PDF, malformed, or password-protected | “This file cannot be used. Upload an unprotected, valid PDF.” |
| Over 20 MB | “This PDF is larger than the 20 MB limit.” |
| Over 100 pages | “This PDF has more than the 100-page limit.” |
| Capacity reached | “This course already has 20 active PDFs. Delete one before uploading another.” |
| Network/request failure | “The PDF was not uploaded. Check your connection and try again.” |
| Permission lost | “You no longer have permission to manage materials for this course.” |

On acceptance, clear selection, announce “`<filename>` was accepted and is processing,” and put a Processing row at the top. It never becomes Active until server confirmation.

#### Source list, deletion, and retry

`ResourceRow` regions: filename, status, staff-facing detail, date, actions. Initial loading uses five text-line skeleton rows plus “Loading materials.” Empty state says “No PDFs yet” and points at upload. A list refresh error preserves known rows and provides **Try again**.

| State | Presentation and action |
| --- | --- |
| Processing | Amber Processing badge; “Preparing this PDF for course use”; last-updated time; **Delete** available. |
| Active | Green Active badge; “Available in Resources and eligible for authorized course use”; **Delete**. |
| Failed | Red Failed badge; `JobFailureNotice`; **Retry** if eligible; **Delete**. |
| Retry limited | Failed badge; “You can submit up to 3 retries per minute for this course. Try again in `<time>`.” Retry disabled until server eligibility. |
| Deleted/stale | Remove after confirmed deletion; concurrent action says “This PDF is no longer available” and refreshes. |

**Delete** opens a `DestructiveConfirmDialog`: **Delete `<filename>`?** “This removes the PDF from Resources and from new AI retrieval and consolidation requests immediately. Deletion cannot be undone. To replace it, delete this PDF, then upload the new PDF.” Actions: **Cancel**, red **Delete PDF**. Pending deletion disables duplicates. Success removes the row, updates count, announces success, and returns focus to the list. Failure retains the row and explains that deletion did not complete. There is no replace, revision, undo, recovery, or restoration control.

For failed processing/Knowledge Map generation, `JobFailureNotice` provides a safe reason and **Retry** only when the server allows it. Retry becomes Processing and announces “Retry submitted.” A server rate-limit result becomes Retry limited; an unknown error remains Failed. Automatic retry/backoff is a bounded shared-service implementation decision, not a user control.

### Course Knowledge Map — assigned course staff only

Until the shared guarded service and AI scope are approved, show the section with: “The course Knowledge Map is not available yet. Course PDFs can still be managed and shared in Resources.” Do not show enabled request controls, fabricated queues, or fabricated results.

After shared guarded service approval, show **Generate Knowledge Map** when no map exists and **Update Knowledge Map** otherwise. The confirmation identifies that every active PDF in this course will be included; there is no source selector. It says: “The Knowledge Map helps student AI find relevant course resources. It requires staff review and approval before use, and student answers will still cite original PDFs and pages.” Do not promise capacity or display numeric provider limits; show neutral server-confirmed rate-limited/retry-later states. Source upload, deletion, or replacement marks the map **Stale** and shows an enabled **Update Knowledge Map** action; regeneration is never automatic.

The `KnowledgeMapPanel` includes a generated label; Current, Stale, Generating, Failed, Pending review, Approved, or Rejected `StatusBadge`; generated time; source-snapshot summary; topic/subtopic navigation entries; **Source support** `SourceCitationList` entries; and server-confirmed reviewer/time after decision. It has no forum-post or automatic-publishing affordance. Learning-objective and FAQ panels are not shown.

For Pending review, display primary **Approve for student AI navigation** and destructive secondary **Reject**. Approval confirmation: “Approve this Knowledge Map? It may help student AI find relevant course resources, but responses must still cite original active PDFs and pages.” Rejection confirmation: “Reject this Knowledge Map? It will not guide student AI retrieval.” A successful immediate decision becomes read-only summary. No reversal/re-review is designed because the spec does not authorize it.

Missing provenance, inactive/deleted sources, failed grounding, or service/request failure withholds the Knowledge Map and shows safe unavailable/failed/rate-limited/retry-later `JobFailureNotice`. Never invent output, use a substitute provider, or permit approval to override invalid sources.

## Accessibility, responsive behaviour, and states

All tabs, file input, actions, dialogs, and citations are keyboard usable. Use correct tab semantics/`aria-current`, concise polite live status, assertive alerts only when action is needed, focus-trapped Escape-close dialogs, and focus restoration. Icons have text labels/tooltips. Render untrusted filename/content as text, never HTML.

Below 768 px, use labelled menu/scrollable tabs, stacked content, labelled row groups instead of dense tables, and stacked dialog actions with Cancel first in reading order. Maintain WCAG AA contrast for text, controls, focus, and badges.

| State | Required response |
| --- | --- |
| Initial load | Shell/course-strip skeleton and concise loading; no placeholder course/resource data. |
| Empty | Role-specific explanation and only authorized action. |
| Validation error | Inline field error and summary; selection remains removable/reselectable. |
| Permission denied | Generic denied/not-found; no protected metadata. |
| Network failure | Preserve safe loaded data where possible; retry; never infer mutation success. |
| Processing | Explicit Processing, never optimistic Active. |
| AI failure | Explicit unavailable/failed/rate-limited/retry-later; no substitute provider, leaked diagnostic, or invented result. |

## Open decisions and handoff

1. The 2026-10-21 product-owner decision accepts normal SoCLaaS use without published numeric quota/budget/concurrency limits; this is not a blocker. The service retains safe failure behavior.
2. Confirm whether Resources should expose upload date/uploader. This defaults to filename + download only.
3. Confirm final authorized citation behaviour when a PDF page cannot be deep-linked; this design preserves page number without assuming a custom viewer.

**Next owner:** Human product owner approves the AI-dependent design after the shared AI/RAG service is approved. After approval, @p-xp implements staff UI and server-enforced controls. @ziyang may reuse `CourseTabs`, `ResourceRow`, `StatusBadge`, and Resources in student flows but must not show staff controls.
