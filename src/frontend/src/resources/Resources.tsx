import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { api } from "../api/client";
import type { Course, Resource } from "../api/types";
import { EmptyState } from "../shared/ui";
export function Resources({
  course,
  staff,
  reportError,
  reportNotice,
}: {
  course: Course;
  staff: boolean;
  reportError: (message: string) => void;
  reportNotice: (message: string) => void;
}) {
  const [resources, setResources] = useState<Resource[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploadOpen, setUploadOpen] = useState(false);
  const closeUpload = useCallback(() => setUploadOpen(false), []);
  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setResources(
        (
          await api<{
            resources: Resource[];
          }>(`/api/courses/${course.id}/resources`)
        ).resources,
      );
    } catch (e) {
      reportError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [course.id, reportError]);
  useEffect(() => {
    void refresh();
  }, [refresh]);
  const remove = async (resource: Resource) => {
    if (
      !confirm(
        `Delete ${resource.filename}? This immediately removes it from Resources and new AI requests. This cannot be undone.`,
      )
    )
      return;
    try {
      await api(`/api/courses/${course.id}/materials/${resource.id}`, {
        method: "DELETE",
      });
      await refresh();
      reportNotice(`${resource.filename} was deleted.`);
    } catch (e) {
      reportError((e as Error).message);
    }
  };
  return (
    <div>
      <div className="section-heading">
        <div>
          <h2>Resources</h2>
          <p className="muted">Course PDFs shared by staff.</p>
        </div>
        {staff && (
          <button className="primary" onClick={() => setUploadOpen(true)}>
            Upload file
          </button>
        )}
      </div>
      {loading ? (
        <p className="loading">Loading resources…</p>
      ) : resources.length ? (
        <ul className="rows">
          {resources.map((resource) => (
            <li key={resource.id}>
              <span>📄 {resource.filename}</span>
              <span className="card-actions">
                <a href={resource.downloadPath}>Download PDF</a>
                {staff && (
                  <button
                    className="danger"
                    aria-label={`Delete ${resource.filename}`}
                    onClick={() => void remove(resource)}
                  >
                    Delete file
                  </button>
                )}
              </span>
            </li>
          ))}
        </ul>
      ) : (
        <EmptyState
          title="No resources have been shared yet"
          text="Check back after course staff upload a PDF."
        />
      )}
      {uploadOpen && (
        <UploadDialog
          course={course}
          onClose={closeUpload}
          onUploaded={async (filename) => {
            closeUpload();
            reportNotice(`${filename} was accepted and is processing.`);
            await refresh();
          }}
        />
      )}
    </div>
  );
}
function UploadDialog({
  course,
  onClose,
  onUploaded,
}: {
  course: Course;
  onClose: () => void;
  onUploaded: (filename: string) => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const dialog = useRef<HTMLElement>(null);
  const opener = useRef<HTMLElement | null>(
    document.activeElement as HTMLElement | null,
  );
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => {
    input.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
      }
      if (event.key === "Tab" && dialog.current) {
        const items = [
          ...dialog.current.querySelectorAll<HTMLElement>(
            "button:not(:disabled), input:not(:disabled)",
          ),
        ];
        if (!items.length) return;
        const first = items[0],
          last = items[items.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      opener.current?.focus();
    };
  }, [onClose]);
  const upload = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    if (!file) return setError("Choose a PDF to upload.");
    if (file.type !== "application/pdf")
      return setError(
        "This file cannot be used. Upload an unprotected, valid PDF.",
      );
    setBusy(true);
    try {
      const bytes = new Uint8Array(await file.arrayBuffer());
      let binary = "";
      bytes.forEach((byte) => (binary += String.fromCharCode(byte)));
      await api(`/api/courses/${course.id}/materials`, {
        method: "POST",
        body: JSON.stringify({
          filename: file.name,
          contentType: file.type,
          contentBase64: btoa(binary),
        }),
      });
      onUploaded(file.name);
    } catch (e) {
      setError(
        (e as Error).message ||
          "The PDF was not uploaded. Check your connection and try again.",
      );
    } finally {
      setBusy(false);
    }
  };
  return (
    <div
      className="modal-backdrop"
      onMouseDown={(event) => {
        if (!busy && event.target === event.currentTarget) onClose();
      }}
    >
      <section
        className="upload-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="upload-title"
        ref={dialog}
      >
        <div className="section-heading">
          <h2 id="upload-title">Upload course PDF</h2>
          <button
            type="button"
            aria-label="Close upload dialog"
            onClick={onClose}
            disabled={busy}
          >
            Close
          </button>
        </div>
        <form className="editor" onSubmit={(event) => void upload(event)}>
          <PdfCompatibilityNotice />
          <label htmlFor="upload-pdf">Choose PDF</label>
          <input
            ref={input}
            id="upload-pdf"
            type="file"
            accept="application/pdf,.pdf"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            disabled={busy}
          />
          {file && (
            <div>
              <span>
                {file.name} · {(file.size / (1024 * 1024)).toFixed(2)} MB
              </span>{" "}
              <button
                type="button"
                onClick={() => setFile(null)}
                disabled={busy}
              >
                Remove selection
              </button>
            </div>
          )}
          <small>
            PDF only · maximum 20 MB · maximum 100 pages · up to 20 active PDFs
            per course.
          </small>
          {error && (
            <p role="alert" className="job-failure">
              {error}
            </p>
          )}
          <div className="actions">
            <button type="button" onClick={onClose} disabled={busy}>
              Cancel
            </button>
            <button className="primary" disabled={!file || busy}>
              {busy ? "Uploading…" : "Upload PDF"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
function PdfCompatibilityNotice() {
  return (
    <aside
      className="pdf-compatibility"
      aria-label="PDF compatibility information"
    >
      <b>ⓘ PDF compatibility</b>
      <p>
        For Knowledge Maps and future AI help, choose a PDF with selectable
        text. Standard PDFs exported from PowerPoint, Word, or lecture-note
        tools are supported, including compressed text PDFs.
      </p>
      <p>
        Scanned or image-only PDFs can be shared in Resources but cannot yet be
        used for text-grounded AI help.
      </p>
    </aside>
  );
}
