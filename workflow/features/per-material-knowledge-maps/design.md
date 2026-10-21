# Per-material Knowledge Maps — staff UI design

Status: Approved for implementation

Approval: Human product-owner approval recorded in chat, 2026-10-21.

## Staff flow

Within the existing staff-only **Manage materials** page, replace the single
Course Knowledge Map panel with a **Knowledge Maps by PDF** section. Keep the
existing blue-and-white shell, resource rows, status badges, source-citation
links, and safe failure notice patterns.

The section header has **Generate all eligible maps**. Confirmation names the
count of text-ready PDFs and says that the system queues separate background
requests, skips ineligible PDFs, and retains current map content if an attempt
fails. It does not promise provider capacity or automatic retry.

Each active text-ready PDF shows an adjacent/expandable map region:

1. filename and PDF processing state;
2. latest job state or safe failure message;
3. the approved map (if present), status and reviewer/time;
4. a clearly labelled **AI-generated — staff review required** candidate (if
   present), its source citations and verification warning; and
5. contextual actions: **Generate map** or **Update map**, then candidate
   **Approve for student AI navigation** or **Reject** when allowed.

Generate/Update is disabled only for the PDF with an active job. A bulk run
shows per-PDF queued/generating/completed/skipped/failed feedback; one failure
does not hide or replace other PDFs' content. Processing, failed, and
resource-only PDFs are visibly skipped with a reason and have no map action.

## Review and retention interaction

If a generation attempt fails, show **Map generation unavailable** beside the
retained candidate/approved output, with a retry action. Never replace a map
body with the failure notice. An unverified candidate has the existing
staff-only warning and no approval button. Reject confirmation says that it
removes only this unapproved generated candidate and leaves an approved map,
if any, unchanged. Approval confirmation names the source PDF and reminds
staff that future student answers must still cite the original PDF/pages.

## States and accessibility

Use semantic buttons, text labels plus status colour, live status for
queued/completed updates, assertive alert only for action-required failures,
and focus restoration after confirmation. Long filenames and generated text
wrap. On narrow screens, rows and action groups stack. Generated text and
filenames render as text only; source links are generated from server-authorized
material IDs and validated page numbers, never model-provided URLs.
