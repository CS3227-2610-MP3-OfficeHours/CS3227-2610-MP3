import { useCallback, useEffect, useState } from "react";
import { api } from "../api/client";
import type { Course, Material, MaterialMapState } from "../api/types";
import { date, EmptyState } from "../shared/ui";
export function KnowledgeMapWorkspace({
  course,
  reportError,
  reportNotice,
}: {
  course: Course;
  reportError: (message: string) => void;
  reportNotice: (message: string) => void;
}) {
  const [maps, setMaps] = useState<MaterialMapState[]>([]);
  const [fallbackConfigured, setFallbackConfigured] = useState(false);
  const [loading, setLoading] = useState(true);
  const [mapBusy, setMapBusy] = useState<string | null>(null);
  const load = useCallback(async () => {
    try {
      const mapResult = await api<{
        maps: MaterialMapState[];
        fallbackConfigured: boolean;
      }>(`/api/courses/${course.id}/knowledge-maps`);
      setMaps(mapResult.maps);
      setFallbackConfigured(mapResult.fallbackConfigured);
    } catch (e) {
      reportError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [course.id, reportError]);
  useEffect(() => {
    void load();
  }, [load]);
  useEffect(() => {
    if (
      !maps.some(
        (state) =>
          state.job?.status === "queued" || state.job?.status === "generating",
      )
    )
      return;
    const timer = window.setInterval(() => void load(), 1800);
    return () => window.clearInterval(timer);
  }, [maps, load]);
  const generate = async (materialId?: string) => {
    const active = maps.filter((state) => state.material.status === "active");
    if (!active.length)
      return reportError(
        "Upload and process at least one text-ready PDF before generating a Knowledge Map.",
      );
    const label = materialId
      ? maps.find((state) => state.material.id === materialId)?.material
          .filename
      : `${active.length} eligible PDFs`;
    if (
      !confirm(
        `Generate separate Knowledge Map request${materialId ? "" : "s"} for ${label}? Existing approved and pending maps stay visible if an attempt fails.`,
      )
    )
      return;
    setMapBusy(materialId ?? "all");
    try {
      await api(
        materialId
          ? `/api/courses/${course.id}/materials/${materialId}/knowledge-map/generate`
          : `/api/courses/${course.id}/knowledge-maps/generate-all`,
        {
          method: "POST",
        },
      );
      await load();
      reportNotice(
        "Knowledge Map generation queued. Each PDF updates independently.",
      );
    } catch (e) {
      reportError((e as Error).message);
    } finally {
      setMapBusy(null);
    }
  };
  const review = async (materialId: string, decision: "approve" | "reject") => {
    const approved = decision === "approve";
    if (
      decision === "reject" &&
      !confirm(
        "Reject this unapproved generated map? This removes only the candidate; any approved map stays unchanged.",
      )
    )
      return;
    setMapBusy(materialId);
    try {
      await api(
        `/api/courses/${course.id}/materials/${materialId}/knowledge-map/${decision}`,
        {
          method: "POST",
        },
      );
      await load();
      reportNotice(
        approved
          ? "Knowledge Map approved for student AI navigation."
          : "Generated candidate rejected.",
      );
    } catch (e) {
      reportError((e as Error).message);
    } finally {
      setMapBusy(null);
    }
  };
  return (
    <KnowledgeMapsByPdf
      course={course}
      maps={maps}
      busy={mapBusy}
      fallbackConfigured={fallbackConfigured}
      loading={loading}
      onGenerate={(id) => void generate(id)}
      onReview={(id, decision) => void review(id, decision)}
    />
  );
}
function materialStatusDetail(material: Material) {
  if (material.status === "processing")
    return "Checking this PDF for page-aware text.";
  if (material.status === "active")
    return "Ready for Knowledge Maps and future AI help.";
  if (material.status === "resource_only")
    return "No selectable text was found. Students can download it, but it cannot support AI help yet.";
  return (
    material.failureMessage ??
    "This PDF could not be processed. It may be malformed, encrypted, password-protected, or too complex to process."
  );
}
function MaterialStatusBadge({ status }: { status: string }) {
  const label =
    status === "processing"
      ? "Processing text"
      : status === "active"
        ? "Text ready"
        : status === "resource_only"
          ? "Resource only"
          : status === "failed"
            ? "Text extraction failed"
            : status.replaceAll("_", " ");
  return <span className={`status-badge ${status}`}>{label}</span>;
}
function KnowledgeMapsByPdf({
  course,
  maps,
  busy,
  fallbackConfigured,
  loading,
  onGenerate,
  onReview,
}: {
  course: Course;
  maps: MaterialMapState[];
  busy: string | null;
  fallbackConfigured: boolean;
  loading: boolean;
  onGenerate: (id?: string) => void;
  onReview: (id: string, decision: "approve" | "reject") => void;
}) {
  const [dismissedFailureNotices, setDismissedFailureNotices] = useState<Set<string>>(
    () => new Set(),
  );
  const eligible = maps.filter((state) => state.material.status === "active");
  return (
    <section className="knowledge-map" aria-labelledby="knowledge-map-heading">
      <div className="section-heading">
        <div>
          <h2 id="knowledge-map-heading">Course Knowledge Map</h2>
          <p className="muted">
            The Course Knowledge Map indexes course topics to help student AI
            search for relevant material and find supporting source pages more
            efficiently. It guides retrieval; student answers still cite
            original PDFs and pages.
          </p>
        </div>
        <div className="actions">
          <button
            className="primary generate-all-maps"
            onClick={() => onGenerate()}
            disabled={busy !== null || !eligible.length}
          >
            Generate all eligible maps
          </button>
        </div>
      </div>
      {loading ? (
        <p className="loading">Loading Knowledge Map…</p>
      ) : maps.length ? (
        maps.map((state) => (
          <MaterialMap
            key={state.material.id}
            course={course}
            state={state}
            busy={busy === state.material.id || busy === "all"}
            failureNoticeDismissed={dismissedFailureNotices.has(state.material.id)}
            onDismissFailureNotice={() =>
              setDismissedFailureNotices((dismissed) =>
                new Set(dismissed).add(state.material.id),
              )
            }
            onGenerate={() => onGenerate(state.material.id)}
            onReview={(decision) => onReview(state.material.id, decision)}
          />
        ))
      ) : (
        <EmptyState
          title="No course PDFs yet"
          text="Upload a text-ready PDF from Resources to generate the course Knowledge Map."
        />
      )}
    </section>
  );
}
function MaterialMap({
  course: _course,
  state,
  busy,
  failureNoticeDismissed,
  onDismissFailureNotice,
  onGenerate,
  onReview,
}: {
  course: Course;
  state: MaterialMapState;
  busy: boolean;
  failureNoticeDismissed: boolean;
  onDismissFailureNotice: () => void;
  onGenerate: () => void;
  onReview: (decision: "approve" | "reject") => void;
}) {
  const { material, candidate, approved, job } = state;
  const running = job?.status === "queued" || job?.status === "generating";
  return (
    <article className="map-panel">
      <div className="section-heading">
        <div>
          <h3>{material.filename}</h3>
          <p className="meta">
            <MaterialStatusBadge status={material.status} />
            {job && (
              <>
                {" "}
                · <StatusBadge status={job.status} />
              </>
            )}
          </p>
        </div>
        {material.status === "active" && (
          <button onClick={onGenerate} disabled={busy || running}>
            {candidate || approved ? "Regenerate" : "Generate map"}
          </button>
        )}
      </div>
      {material.status === "failed" && !failureNoticeDismissed ? (
        <div className="job-failure extraction-failure-notice" role="alert">
          <p>This PDF could not be parsed. Try uploading it again.</p>
          <button
            type="button"
            aria-label={`Dismiss extraction failure notice for ${material.filename}`}
            onClick={onDismissFailureNotice}
          >
            Dismiss
          </button>
        </div>
      ) : material.status !== "active" && material.status !== "failed" ? (
        <p className="muted">Skipped: {materialStatusDetail(material)}</p>
      ) : null}
      {running && (
        <p role="status">
          Generating this PDF’s map. Existing maps remain available.
        </p>
      )}
      {job?.status === "failed" && (
        <div className="job-failure" role="alert">
          <b>Map generation unavailable</b>
          <p>{job.failureMessage}</p>
        </div>
      )}
      {approved && (
        <section>
          <h4>Approved for student AI navigation</h4>
          <div
            className="map-content"
            role="region"
            aria-label={`Approved Knowledge Map content for ${material.filename}`}
            tabIndex={0}
          >
            {approved.content}
          </div>
          <p className="muted">
            Approved {approved.reviewedAt && date(approved.reviewedAt)}.
          </p>
        </section>
      )}
      {candidate && (
        <section>
          <h4>AI-generated — staff review required</h4>
          {candidate.verificationMessage && (
            <div className="job-failure" role="alert">
              <b>Unverified Knowledge Map — staff review only</b>
              <p>{candidate.verificationMessage}</p>
            </div>
          )}
          <div
            className="map-content"
            role="region"
            aria-label={`Knowledge Map content for ${material.filename}`}
            tabIndex={0}
          >
            {candidate.content}
          </div>
          <div className="actions" aria-live="polite">
            {!candidate.verificationMessage && (
              <button
                className="primary"
                onClick={() => onReview("approve")}
                disabled={busy}
                aria-busy={busy}
              >
                {busy ? "Saving review…" : "Approve for student AI navigation"}
              </button>
            )}
            <button
              className="danger"
              onClick={() => onReview("reject")}
              disabled={busy}
              aria-busy={busy}
            >
              {busy ? "Saving review…" : "Reject"}
            </button>
          </div>
        </section>
      )}
    </article>
  );
}
function StatusBadge({ status }: { status: string }) {
  return (
    <span className={`status-badge ${status}`}>
      {status.replaceAll("_", " ")}
    </span>
  );
}
