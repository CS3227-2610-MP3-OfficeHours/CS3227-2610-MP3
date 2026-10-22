import { useCallback, useEffect, useState } from "react";
import type { CSSProperties, FormEvent } from "react";
import { api } from "../api/client";
import type { Course, ForumTag } from "../api/types";
import { DeleteButton, EmptyState } from "../shared/ui";
import {
  TAG_PALETTE,
  TagBadge,
  tagColorName,
  trimTagWhitespace,
} from "./tagPresentation";
export function ForumTagsSettings({
  course,
  reportError,
  reportNotice,
}: {
  course: Course;
  reportError: (m: string) => void;
  reportNotice: (m: string) => void;
}) {
  const [tags, setTags] = useState<ForumTag[]>([]),
    [loading, setLoading] = useState(true),
    [failed, setFailed] = useState(false),
    [name, setName] = useState(""),
    [color, setColor] = useState(TAG_PALETTE[0].color),
    [editing, setEditing] = useState<string | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      setTags(
        (
          await api<{
            tags: ForumTag[];
          }>(`/api/courses/${course.id}/forum-tags`)
        ).tags,
      );
    } catch (e) {
      setFailed(true);
      reportError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [course.id, reportError]);
  useEffect(() => {
    void load();
  }, [load]);
  const save = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const trimmedName = trimTagWhitespace(name);
      if (!trimmedName) {
        setError("Tag name is required.");
        setBusy(false);
        return;
      }
      const data = {
        name: trimmedName,
        color,
      };
      if (editing) {
        await api(`/api/courses/${course.id}/forum-tags/${editing}`, {
          method: "PATCH",
          body: JSON.stringify(data),
        });
        reportNotice("Forum tag updated.");
      } else {
        await api(`/api/courses/${course.id}/forum-tags`, {
          method: "POST",
          body: JSON.stringify(data),
        });
        reportNotice("Forum tag added.");
      }
      setName("");
      setEditing(null);
      await load();
    } catch (ex) {
      setError((ex as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const startEdit = (tag: ForumTag) => {
    setEditing(tag.id);
    setName(tag.name);
    setColor(tag.color);
    setError("");
  };
  const remove = async (tag: ForumTag) => {
    if (!confirm(`Delete “${tag.name}”? Posts using it will move to General.`))
      return;
    setBusy(true);
    try {
      await api(`/api/courses/${course.id}/forum-tags/${tag.id}`, {
        method: "DELETE",
      });
      await load();
      reportNotice(`“${tag.name}” was deleted. Its posts now use General.`);
    } catch (e) {
      reportError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <section className="forum-tag-settings">
      <h3>Forum tags</h3>
      <p>Up to 7 tags per course, including General (the default tag)</p>
      {loading ? (
        <p className="loading">Loading forum tags…</p>
      ) : failed ? (
        <EmptyState
          title="Forum tags unavailable"
          text="Try again to load course tags."
          action="Retry"
          onAction={() => void load()}
        />
      ) : (
        <>
          <ul className="rows">
            {tags.map((tag) => (
              <li key={tag.id}>
                <span>
                  <TagBadge tag={tag} />{" "}
                </span>
                {!tag.isDefault && (
                  <span className="card-actions">
                    <button onClick={() => startEdit(tag)}>Edit</button>
                    <button
                      className="danger"
                      onClick={() => void remove(tag)}
                      disabled={busy}
                    >
                      Delete
                    </button>
                  </span>
                )}
              </li>
            ))}
          </ul>
          {(tags.length < 7 || editing) && (
            <form className="editor tag-editor" onSubmit={save}>
              <h4>{editing ? "Edit tag" : "Add tag"}</h4>
              <label htmlFor="tag-name">
                Tag name <small>{[...name].length}/20</small>
              </label>
              <input
                id="tag-name"
                maxLength={40}
                value={name}
                onChange={(e) => setName(e.target.value)}
                disabled={busy}
                required
              />
              <fieldset>
                <legend>Color</legend>
                <div
                  className="swatch-grid"
                  role="radiogroup"
                  aria-label="Tag color"
                >
                  {TAG_PALETTE.map((swatch) => (
                    <button
                      key={swatch.color}
                      type="button"
                      role="radio"
                      aria-checked={color === swatch.color}
                      aria-label={swatch.name}
                      tabIndex={color === swatch.color ? 0 : -1}
                      className={`swatch ${color === swatch.color ? "chosen" : ""}`}
                      style={{ "--tag-color": swatch.color } as CSSProperties}
                      onKeyDown={(event) => {
                        const buttons = Array.from(
                          event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>(
                            "button",
                          ) ?? [],
                        );
                        const index = buttons.indexOf(event.currentTarget);
                        let nextIndex = -1;
                        if (
                          event.key === "ArrowRight" ||
                          event.key === "ArrowDown"
                        )
                          nextIndex = (index + 1) % buttons.length;
                        else if (
                          event.key === "ArrowLeft" ||
                          event.key === "ArrowUp"
                        )
                          nextIndex =
                            (index - 1 + buttons.length) % buttons.length;
                        else if (event.key === "Home") nextIndex = 0;
                        else if (event.key === "End")
                          nextIndex = buttons.length - 1;
                        if (nextIndex >= 0) {
                          event.preventDefault();
                          const next = buttons[nextIndex];
                          const colorValue = next.getAttribute("data-color");
                          if (colorValue) setColor(colorValue);
                          next.focus();
                        }
                      }}
                      data-color={swatch.color}
                      onClick={() => setColor(swatch.color)}
                      disabled={busy}
                    >
                      {color === swatch.color ? "✓" : ""}
                    </button>
                  ))}
                </div>
              </fieldset>
              {error && (
                <p role="alert" className="job-failure">
                  {error}
                </p>
              )}
              <div className="actions">
                {editing && (
                  <button
                    type="button"
                    onClick={() => {
                      setEditing(null);
                      setName("");
                      setError("");
                    }}
                  >
                    Cancel
                  </button>
                )}
                <button
                  className="primary"
                  disabled={busy || (tags.length >= 7 && !editing)}
                >
                  {busy
                    ? editing
                      ? "Saving…"
                      : "Adding…"
                    : editing
                      ? "Save tag"
                      : "Add tag"}
                </button>
              </div>
            </form>
          )}
          {tags.length >= 7 && (
            <p role="status">The tag limit has been reached.</p>
          )}
        </>
      )}
    </section>
  );
}
