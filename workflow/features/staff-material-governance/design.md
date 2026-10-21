# Staff Material Governance — UI Design

Status: Approved for Stage 4A and the Stage 4B Knowledge Map UI; Stage 4B awaits Stage 3 completion
Approval: Human product-owner approval recorded in chat, 2026-10-20 through 2026-10-22 — approved Resources upload/deletion, the Knowledge Map-only workspace, one manually updated Knowledge Map per course, and staff-only display of returned unverified maps (2026-10-22).
Last handoff: Human product owner → UI designer, 2026-10-22 — refine staff Resources deletion and the Knowledge Map workspace per approved criteria 21–22; next owner is engineer.

## Scope and access boundary

This design implements the confirmed material-governance requirements in `spec.md`, product-foundation decisions, and the `ui-consistency` skill. It does not design student AI help or enable AI before the shared service and AI scope are approved.

Only assigned course staff see material management, job, consolidation, and review controls. Enrolled students see active PDFs only in Resources. A non-member, removed student, or unassigned staff member receives a generic access-denied/not-found result with no course, file, or job metadata. The server authorizes every route, list, download, mutation, and job.

## Shared blue-and-white system and pattern additions

Use the shared application shell: white/light-neutral surfaces, dark readable text, blue for primary actions, navigation, information, and focus, and semantic green/amber/red for success, processing/warning, and failure. Colour is never the sole status cue. The top bar has product, signed-in username/role, and sign-out; the course strip shows only the authorized course code/name; desktop uses a left rail and small screens use compact course navigation.

| Reusable pattern | Treatment |
| --- | --- |
| `CourseTabs` | **Forum**, **Resources**, and **Knowledge Map** only for assigned staff. Preserve the active tab in URL/history. |
| `UploadOverlay` | Centered, dismissible dialog containing the existing PDF upload form and its validation/capacity guidance; staff-only entry point lives in Resources. |
| `ResourceRow` | Responsive PDF row with safe filename, **Download PDF**, and a staff-only red **Delete file** button immediately to its right; never exposes storage URLs. |
| `StatusBadge` | Written label plus icon/shape for Processing, Active, Failed, Pending review, Approved, Rejected. |
| `KnowledgeMapPanel` | Labelled **AI-generated Knowledge Map — review required**, visibly distinct from human forum posts; textual citations remain in map content, with no separate Source support link list beneath it. |
| `DestructiveConfirmDialog` | Cancel-first focused dialog with exact object name and irreversible consequence. |
| `JobFailureNotice` | Safe error, retry eligibility, and retry action; no stack trace, secret, private path, or protected content. |

Use one primary blue action per region; outlined/text secondary actions; persistent labels; inline validation plus a top-of-form error summary; visible focus rings; 16 px base type; 8 px spacing scale; and at least 44 px targets. These are shared additions that parallel and later designs must reuse instead of creating variants.

## Information architecture

| Viewer | Visible tabs | Material destination |
| --- | --- | --- |
| Enrolled student | Forum, Resources | Active-PDF Resources list |
| Assigned course staff | Forum, Resources, Knowledge Map | Knowledge Map; Resources remains selectable |
| Non-member / removed student / unassigned staff | No course shell | Generic denied/not-found page |

`Resources` lists active PDFs for authorized members. Each row has **Download PDF**; assigned staff additionally see the red **Delete file** action immediately to its right. Assigned staff also see a top-right **Upload file** action that opens `UploadOverlay`. The staff **Knowledge Map** workspace contains only the existing **Course Knowledge Map** section and its generation, status, and review operations. It has no separate material lifecycle list. Staff-only processing/AI data and deletion controls cannot appear in student navigation or markup.

## Screens and interactions

### Resources — enrolled students and assigned staff

Show heading **Resources**, help text “Course PDFs shared by staff,” and a semantic list of `ResourceRow`s. Assigned staff see **Upload file** aligned at the top right of the heading region; students never see the button. Activating it opens a centered modal dialog with the existing PDF upload form and guidance. The member-facing list does not display job status, uploader identity, internal metadata, or AI eligibility. Assigned staff see a red **Delete file** button immediately to the right of **Download PDF** for each listed active PDF; students see no delete action. The server authorizes the list, every download, and every deletion.

- A download has button-local loading feedback; failed downloads announce an adjacent **Try download again** action.
- Display active PDFs only. Pagination is unnecessary at the confirmed maximum of 20.
- Upload dialog: label it **Upload course PDF**, provide a visible close button, and use the existing labelled file input, selected filename/size, remove-selection action, and **Upload PDF** submit control. Include “PDF only · maximum 20 MB · maximum 100 pages · up to 20 active PDFs per course.” Keep all existing validation and capacity messages inline with an error summary. On accepted upload, announce that processing has started, close the dialog, clear its selection, and refresh the staff list in Knowledge Map; on rejection or request failure keep the dialog and selection open so staff can recover.
- The dialog is opened only for authorized course staff. Direct upload authorization and PDF validation are server-enforced regardless of whether the button is visible.
- Empty state: “No resources have been shared yet. Check back after course staff upload a PDF.” Students see no upload action; authorized staff retain **Upload file** in the heading region.
- Revoked access/stale direct URL: “You no longer have access to this course’s resources,” then **Back to my courses**, with no named details.
- Refresh failure preserves safely loaded rows and says “Resources could not be refreshed. Try again.”

#### Upload form (staff overlay)

The overlay has a labelled file input, **Choose PDF**, selected safe filename/size, **Remove selection**, and primary **Upload PDF**. Helper: “PDF only · maximum 20 MB · maximum 100 pages · up to 20 active PDFs per course.” Upload is disabled without a selection. Browser constraints are guidance; server validation is authoritative.

| Condition | Safe message |
| --- | --- |
| Missing file | “Choose a PDF to upload.” |
| Non-PDF, malformed, or password-protected | “This file cannot be used. Upload an unprotected, valid PDF.” |
| Over 20 MB | “This PDF is larger than the 20 MB limit.” |
| Over 100 pages | “This PDF has more than the 100-page limit.” |
| Capacity reached | “This course already has 20 active PDFs. Delete one before uploading another.” |
| Network/request failure | “The PDF was not uploaded. Check your connection and try again.” |
| Permission lost | “You no longer have permission to manage materials for this course.” |

On acceptance, clear selection, announce “`<filename>` was accepted and is processing,” close the dialog, and refresh the staff source list in Knowledge Map. The source never becomes Active until server confirmation.

### Knowledge Map — assigned course staff only

Show only the **Course Knowledge Map** section in the staff Knowledge Map tab. Remove the “AI-generated supporting navigation material” tagline and do not show a separate material list/lifecycle section. Beneath the heading explain: “The Course Knowledge Map organizes course topics to help student AI search for relevant material and find supporting PDF pages. It guides retrieval but does not replace the original PDFs: student answers still cite those sources, and staff must review and approve a map before it can be used.” There is no upload form in this workspace; staff upload and delete files from Resources. Direct access by an unassigned actor is denied without existence leakage.

#### Staff delete action in Resources

The red **Delete file** button follows **Download PDF** in each active resource
row and is rendered only for assigned staff. It opens a destructive
confirmation with the exact filename and irreversible consequence. Cancel
preserves the row; successful deletion removes it from Resources and new AI
retrieval/generation inputs, announces success, and refreshes the list. Failure
preserves the row and reports the safe server error. The server remains the
authorization boundary. Student resource rows remain download-only.

### Course Knowledge Map — assigned course staff only

Until the shared guarded service and AI scope are approved, show the section with: “The course Knowledge Map is not available yet. Course PDFs can still be managed and shared in Resources.” Do not show enabled request controls, fabricated queues, or fabricated results.

After shared guarded service approval, show **Generate Knowledge Map** when no map exists and **Update Knowledge Map** otherwise. The confirmation identifies that every active PDF in this course will be included; there is no source selector. It says: “The Knowledge Map helps student AI find relevant course resources. It requires staff review and approval before use, and student answers will still cite original PDFs and pages.” Do not promise capacity or display numeric provider limits; show neutral server-confirmed rate-limited/retry-later states. Source upload, deletion, or replacement marks the map **Stale** and shows an enabled **Update Knowledge Map** action; regeneration is never automatic.

The generated map is expected to use plain unformatted topic headings and one factual claim per line, with exact canonical filename/page citations as the final characters of every claim line and no trailing punctuation. The interface must present server verification as authoritative: prompt-conforming output can still be unverified, and output shaped like the reported example must show specific citation/format reasons when detected and remain approval-ineligible.

The `KnowledgeMapPanel` includes a generated label; Current, Stale, Generating, Failed, Pending review, Approved, or Rejected `StatusBadge`; generated time; source-snapshot summary; topic/subtopic navigation entries; the generated content's own textual filename/page citations; and server-confirmed reviewer/time after decision. Do not render the separate clickable **Source support** download links beneath the returned map. It has no forum-post or automatic-publishing affordance. Learning-objective and FAQ panels are not shown.

Bound the returned map content region to `max-height: 70vh`; when it exceeds that height, scroll vertically inside the region. Keep status, verification notice, and review controls outside the scrolling content region so they remain available. Give the scroll region an accessible name and keyboard focusability (`tabindex="0"` where required by the implementation), preserve a visible focus indicator, and ensure keyboard users can scroll through the entire map without focus being trapped or lost. On short screens, the 70vh cap still applies and the review actions remain reachable in the surrounding page layout.

For Pending review, display primary **Approve for student AI navigation** and destructive secondary **Reject**. Approval confirmation: “Approve this Knowledge Map? It may help student AI find relevant course resources, but responses must still cite original active PDFs and pages.” Rejection confirmation: “Reject this Knowledge Map? It will not guide student AI retrieval.” A successful immediate decision becomes read-only summary. No reversal/re-review is designed because the spec does not authorize it.

When the provider returns map content but source support cannot be verified for
every active PDF, retain the content in `KnowledgeMapPanel` for assigned staff
only. Precede it with an assertive **Unverified Knowledge Map — staff review
only** notice that names missing source-support filenames when the server can
determine them. Show concise, actionable reason(s) whenever established by
validation: “A claim is missing a source citation”; “A citation is not in the
required format”; “The cited filename does not match an uploaded course PDF”;
“The cited page is outside the PDF or has no extracted text”; or “Put the
citation at the end of its claim line, with no text or punctuation after it.”
If several failures are identified, show the applicable reasons as a short
list, deduplicated. Use the existing general unverified-map message only when
validation cannot determine a more specific safe reason. Keep
**Approve for student AI navigation** hidden/disabled;
show **Reject** and **Update Knowledge Map** instead. Students never receive,
retrieve from, or navigate with this state. Missing returned content,
inactive/deleted sources, or service/request failure still withholds the map
and shows safe unavailable/failed/rate-limited/retry-later `JobFailureNotice`.
Never invent output, use a substitute provider, or permit approval to override
an unverified map.

## Accessibility, responsive behaviour, and states

All tabs, file input, actions, dialogs, and citations are keyboard usable. Use correct tab semantics/`aria-current`, concise polite live status, assertive alerts only when action is needed, focus-trapped Escape-close upload dialogs, and focus restoration to **Upload file**. Returned-map scrolling is keyboard accessible and does not contain or hide the review actions. Icons have text labels/tooltips. Render untrusted filename/content as text, never HTML.

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

**Next owner:** Engineer implements the approved addendum after design review and Stage 3 completion, preserving server-side authorization and verifier authority. @ziyang may reuse `CourseTabs`, `ResourceRow`, `StatusBadge`, and Resources in student flows but must not show staff controls.
