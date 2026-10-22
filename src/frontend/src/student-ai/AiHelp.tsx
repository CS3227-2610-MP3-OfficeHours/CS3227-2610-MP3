import { useEffect, useRef, useState, type FormEvent } from "react";
import { api } from "../api/client";
import type { AiOutcome, AiResult, Course } from "../api/types";

const MAX_QUESTION_CHARS = 1000;
const labels: Record<AiOutcome, { text: string; tone: string }> = {
  administrative: { text: "Course info", tone: "info" },
  guidance: { text: "Study guidance — not an answer to submit", tone: "accent" },
  refused: { text: "Guidance only", tone: "accent" },
  insufficient_evidence: { text: "Not enough in the material", tone: "warn" },
};

export function AiHelp({ course }: { course: Course }) {
  const [question, setQuestion] = useState("");
  const [result, setResult] = useState<AiResult | null>(null);
  const [panel, setPanel] = useState("");
  const [busy, setBusy] = useState(false);
  const [checking, setChecking] = useState(true);
  const resultHeading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    let active = true;
    setChecking(true);
    api<{ configured: boolean }>(`/api/courses/${course.id}/ai-help`)
      .then((state) => {
        if (active && !state.configured) setPanel(unavailableMessage);
      })
      .catch(() => {
        if (active) setPanel(unavailableMessage);
      })
      .finally(() => {
        if (active) setChecking(false);
      });
    return () => {
      active = false;
    };
  }, [course.id]);

  useEffect(() => {
    if (!busy && (result || panel)) resultHeading.current?.focus();
  }, [busy, result, panel]);

  const ask = async (event: FormEvent) => {
    event.preventDefault();
    const asked = question.trim();
    if (!asked) {
      setResult(null);
      setPanel("Enter a question first.");
      return;
    }
    setBusy(true);
    setResult(null);
    setPanel("");
    try {
      const response = await api<{ result: AiResult }>(
        `/api/courses/${course.id}/ai-help`,
        { method: "POST", body: JSON.stringify({ question: asked }) },
      );
      setResult(response.result);
    } catch (error) {
      const failure = error as Error & { status?: number };
      setPanel(
        failure.status === 404 ? "This course is not available." : failure.message,
      );
    } finally {
      setBusy(false);
    }
  };

  const label = result ? labels[result.outcome] : null;
  return (
    <div>
      <h2>AI help</h2>
      <p className="ai-standing">
        Grounded in this course&apos;s PDFs. It gives direct answers about how
        the course runs, and study guidance — not answers to submit. Nothing
        here is posted to the forum.
      </p>
      <form className="ai-ask" onSubmit={ask}>
        <label htmlFor="ai-question">Your question</label>
        <textarea
          id="ai-question"
          rows={4}
          value={question}
          maxLength={MAX_QUESTION_CHARS}
          aria-describedby="ai-counter"
          placeholder="Ask about this course…"
          disabled={busy || checking}
          onChange={(event) => setQuestion(event.target.value)}
        />
        <div className="ai-ask-footer">
          <small
            id="ai-counter"
            className={question.length >= 900 ? "ai-counter warn" : "ai-counter"}
          >
            {question.length} / {MAX_QUESTION_CHARS}
          </small>
          <button className="primary" disabled={busy || checking || !question.trim()}>
            {busy ? "Asking…" : "Ask"}
          </button>
        </div>
      </form>
      <div className="ai-result" aria-live="polite" aria-busy={busy}>
        <h3 className="sr-only" tabIndex={-1} ref={resultHeading}>
          Result
        </h3>
        {busy ? (
          <p className="loading">Reading this course&apos;s material…</p>
        ) : result && label ? (
          <article className="ai-card">
            <span className={`ai-badge ${label.tone}`}>{label.text}</span>
            <p className="ai-answer">{result.answer}</p>
            {result.citations.length > 0 && (
              <div className="ai-sources">
                <h4>Sources</h4>
                <ul className="rows">
                  {result.citations.map((citation) => (
                    <li key={`${citation.materialId}-${citation.page}`}>
                      <span>📄 {citation.filename} — p. {citation.page}</span>
                      <a href={citation.downloadPath}>Open</a>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </article>
        ) : panel ? (
          <p className="ai-panel">{panel}</p>
        ) : null}
      </div>
    </div>
  );
}

const unavailableMessage =
  "AI help is unavailable right now. The forum and course resources still work — try again later.";
