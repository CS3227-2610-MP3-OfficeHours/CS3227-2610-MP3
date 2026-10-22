import { useState } from "react";
import type { FormEvent } from "react";
import { api } from "../api/client";
import type { Role, User } from "../api/types";
import { Banner } from "../shared/ui";
export function AuthEntry({ onDone }: { onDone: (user: User) => void }) {
  const [role, setRole] = useState<Role | null>(null);
  if (!role)
    return (
      <main className="auth">
        <h1>University TA Help Desk</h1>
        <p>Choose your role to continue.</p>
        <div className="choice-actions">
          <button className="role-card" onClick={() => setRole("student")}>
            <b>Student</b>
            <span>Access your enrolled course forums</span>
          </button>
          <button className="role-card" onClick={() => setRole("staff")}>
            <b>Staff</b>
            <span>Manage courses and participate in forums</span>
          </button>
        </div>
      </main>
    );
  return <RoleAccess role={role} onDone={onDone} back={() => setRole(null)} />;
}
function RoleAccess({
  role,
  onDone,
  back,
}: {
  role: Role;
  onDone: (user: User) => void;
  back: () => void;
}) {
  const [mode, setMode] = useState<"choose" | "signin" | "signup">("choose");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const result = await api<{
        user: User;
      }>(mode === "signup" ? "/api/auth/signup" : "/api/auth/signin", {
        method: "POST",
        body: JSON.stringify(
          mode === "signup"
            ? {
                username,
                password,
                role,
              }
            : {
                username,
                password,
              },
        ),
      });
      if (result.user.role !== role)
        throw new Error(`This is not a ${role} account.`);
      onDone(result.user);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  if (mode === "choose")
    return (
      <main className="auth">
        <h1>{role === "student" ? "Student access" : "Staff access"}</h1>
        <p>
          {role === "student"
            ? "Sign in or create a student account to access forums where course staff add you."
            : "Choose how you would like to continue."}
        </p>
        <div className="choice-actions">
          <button className="primary" onClick={() => setMode("signin")}>
            Log in
          </button>
          <button onClick={() => setMode("signup")}>
            Create {role} account
          </button>
        </div>
        <button
          className="back-to-role"
          style={{
            marginTop: "1.5rem",
          }}
          onClick={back}
        >
          ← Back to role selection
        </button>
      </main>
    );
  return (
    <main className="auth">
      <h1>{mode === "signup" ? `Create ${role} account` : `${role} log in`}</h1>
      {role === "staff" && mode === "signup" && (
        <p className="notice">
          For this test project, staff accounts are self-selected and are not
          institutionally verified.
        </p>
      )}
      <form onSubmit={submit}>
        <label>
          Username
          <input
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            disabled={busy}
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            autoComplete={
              mode === "signup" ? "new-password" : "current-password"
            }
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={busy}
            required
          />
        </label>
        {error && <Banner kind="error" message={error} />}
        <button className="primary" disabled={busy}>
          {busy
            ? "Please wait…"
            : mode === "signup"
              ? "Create account"
              : "Log in"}
        </button>
        <button
          type="button"
          onClick={() => {
            setError("");
            setMode("choose");
          }}
          disabled={busy}
        >
          Back
        </button>
      </form>
    </main>
  );
}
