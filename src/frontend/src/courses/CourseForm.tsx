import { useState } from "react";
import type { FormEvent } from "react";
import { api } from "../api/client";
import type { Course } from "../api/types";
import { Banner } from "../shared/ui";
export function CourseForm({
  onCreated,
  onCancel,
}: {
  onCreated: (course: Course) => void;
  onCancel: () => void;
}) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      onCreated(
        (
          await api<{
            course: Course;
          }>("/api/courses", {
            method: "POST",
            body: JSON.stringify({
              code,
              name,
            }),
          })
        ).course,
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <form className="editor narrow" onSubmit={submit}>
      <h1>Create course</h1>
      <p className="muted">You will become its first course staff member.</p>
      <label>
        Course code{" "}
        <input
          value={code}
          maxLength={32}
          onChange={(event) => setCode(event.target.value)}
          disabled={busy}
          required
        />
      </label>
      <label>
        Course name{" "}
        <input
          value={name}
          maxLength={160}
          onChange={(event) => setName(event.target.value)}
          disabled={busy}
          required
        />
      </label>
      {error && <Banner kind="error" message={error} />}
      <div className="actions">
        <button type="button" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
        <button className="primary" disabled={busy}>
          {busy ? "Creating…" : "Create course"}
        </button>
      </div>
    </form>
  );
}
