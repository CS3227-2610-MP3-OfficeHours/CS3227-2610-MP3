import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { api } from "../api/client";
import type { Course, Member } from "../api/types";
import { DeleteButton, EmptyState } from "../shared/ui";
export function Members({
  course,
  reportError,
  reportNotice,
}: {
  course: Course;
  reportError: (message: string) => void;
  reportNotice: (message: string) => void;
}) {
  const [members, setMembers] = useState<Member[]>([]);
  const [username, setUsername] = useState("");
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      setMembers(
        (
          await api<{
            members: Member[];
          }>(`/api/courses/${course.id}/members`)
        ).members,
      );
    } catch (e) {
      setMembers([]);
      setFailed(true);
      reportError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [course.id, reportError]);
  useEffect(() => {
    void load();
  }, [load]);
  const add = async (event: FormEvent) => {
    event.preventDefault();
    if (!username.trim()) return reportError("Enter an existing username.");
    setBusy(true);
    try {
      await api(`/api/courses/${course.id}/members`, {
        method: "POST",
        body: JSON.stringify({
          username,
        }),
      });
      setUsername("");
      await load();
      reportNotice("Member added.");
    } catch (e) {
      reportError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const remove = async (member: Member) => {
    if (
      !confirm(
        `Remove ${member.user.username} from this course? Their course-forum access will end immediately.`,
      )
    )
      return;
    try {
      await api(`/api/courses/${course.id}/members/${member.user.id}`, {
        method: "DELETE",
      });
      await load();
      reportNotice("Student removed from the course.");
    } catch (e) {
      reportError((e as Error).message);
      await load();
    }
  };
  const staff = members.filter((member) => member.membershipRole === "staff");
  const students = members.filter(
    (member) => member.membershipRole === "student",
  );
  return (
    <div>
      <h2>Members</h2>
      <p className="muted">
        Add an existing account by username. Students cannot enrol or withdraw
        themselves.
      </p>
      <form className="inline-form" onSubmit={add}>
        <label htmlFor="member-username">Username</label>
        <input
          id="member-username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          disabled={busy || failed}
          required
        />
        <button className="primary" disabled={busy || failed}>
          {busy ? "Adding…" : "Add member"}
        </button>
      </form>
      {loading ? (
        <p className="loading">Loading members…</p>
      ) : failed ? (
        <EmptyState
          title="Members unavailable"
          text="Your staff access may have changed. Try again to refresh access."
          action="Retry"
          onAction={() => void load()}
        />
      ) : (
        <div className="member-columns">
          <MemberList title="Course staff" members={staff} />
          <MemberList title="Students" members={students} onRemove={remove} />
        </div>
      )}
    </div>
  );
}
function MemberList({
  title,
  members,
  onRemove,
}: {
  title: string;
  members: Member[];
  onRemove?: (member: Member) => void;
}) {
  return (
    <section>
      <h3>{title}</h3>
      {members.length ? (
        <ul className="rows">
          {members.map((member) => (
            <li key={member.user.id}>
              <span>
                <b>{member.user.username}</b>
              </span>
              {onRemove && (
                <button className="danger" onClick={() => onRemove(member)}>
                  Remove
                </button>
              )}
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted">No {title.toLowerCase()} yet.</p>
      )}
    </section>
  );
}
