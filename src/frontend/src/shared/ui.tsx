import type { Role, User } from "../api/types";
export const date = (value: string) =>
  new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
export const canDelete = (user: User, author: User) =>
  user.id === author.id || (user.role === "staff" && author.role === "student");
export function DeleteButton({
  label,
  action,
  reportError,
  onFailure,
}: {
  label: string;
  action: () => Promise<void>;
  reportError: (message: string) => void;
  onFailure?: () => void;
}) {
  const remove = async () => {
    if (!confirm(`Delete this ${label}? This cannot be undone.`)) return;
    try {
      await action();
    } catch (e) {
      reportError((e as Error).message);
      onFailure?.();
    }
  };
  return (
    <button className="danger" onClick={() => void remove()}>
      Delete
    </button>
  );
}
export function Banner({
  kind,
  message,
  onDismiss,
}: {
  kind: "error" | "success";
  message: string;
  onDismiss?: () => void;
}) {
  return (
    <p
      role={kind === "error" ? "alert" : "status"}
      className={`banner ${kind}`}
    >
      {message}
      {onDismiss && <button onClick={onDismiss}>Dismiss</button>}
    </p>
  );
}
export function EmptyState({
  title,
  text,
  action,
  onAction,
}: {
  title: string;
  text: string;
  action?: string;
  onAction?: () => void;
}) {
  return (
    <div className="empty-state">
      <h3>{title}</h3>
      <p>{text}</p>
      {action && (
        <button className="primary" onClick={onAction}>
          {action}
        </button>
      )}
    </div>
  );
}
export function EmptyDashboard({ role }: { role: Role }) {
  return (
    <div className="empty-state">
      <h1>My courses</h1>
      <p>
        {role === "staff"
          ? "Create your first course to start a course forum."
          : "You have not been added to a course yet. Ask your course staff to add your existing username."}
      </p>
    </div>
  );
}
