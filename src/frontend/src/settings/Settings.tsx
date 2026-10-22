import { useState } from "react";
import type { FormEvent } from "react";
import { api } from "../api/client";
import type { Course } from "../api/types";
import { Banner, DeleteButton } from "../shared/ui";
import { ForumTagsSettings } from "../forum/ForumTagsSettings";
export function Settings({
  course,
  reportError,
  reportNotice,
  onChanged,
  onDeleted,
}: {
  course: Course;
  reportError: (message: string) => void;
  reportNotice: (message: string) => void;
  onChanged: (course: Course) => void;
  onDeleted: (id: string) => void;
}) {
  const [name, setName] = useState(course.name);
  const [busy, setBusy] = useState(false);
  const rename = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    try {
      const result = await api<{
        course: Course;
      }>(`/api/courses/${course.id}`, {
        method: "PATCH",
        body: JSON.stringify({
          name,
        }),
      });
      onChanged(result.course);
      reportNotice("Course name updated.");
    } catch (e) {
      reportError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const remove = async () => {
    if (
      !confirm(
        `Delete ${course.code} permanently? Its forum posts, replies, memberships, and resources will no longer be accessible.`,
      )
    )
      return;
    setBusy(true);
    try {
      await api(`/api/courses/${course.id}`, {
        method: "DELETE",
      });
      onDeleted(course.id);
    } catch (e) {
      reportError((e as Error).message);
      setBusy(false);
    }
  };
  return (
    <div>
      <h2>Course settings</h2>
      <form className="editor narrow settings-course-details" onSubmit={rename}>
        <label htmlFor="course-name">Course name</label>
        <input
          id="course-name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          disabled={busy}
          required
        />
        <button className="primary" disabled={busy}>
          {busy ? "Saving…" : "Save name"}
        </button>
      </form>
      <ForumTagsSettings
        course={course}
        reportError={reportError}
        reportNotice={reportNotice}
      />
      <section className="danger-zone">
        <h3>Delete course</h3>
        <p>
          This permanently removes the course and makes its forum data
          unavailable.
        </p>
        <button
          className="danger"
          onClick={() => void remove()}
          disabled={busy}
        >
          Delete course
        </button>
      </section>
    </div>
  );
}
