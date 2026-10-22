import { useCallback, useEffect, useState } from "react";
import { api } from "../api/client";
import type { Course, Tab, User } from "../api/types";
import { AuthEntry } from "../auth/AuthEntry";
import { CourseForm } from "../courses/CourseForm";
import { EmptyDashboard } from "../shared/ui";
import { CourseWorkspace } from "./CourseWorkspace";
export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [courses, setCourses] = useState<Course[]>([]);
  const [course, setCourse] = useState<Course | null>(null);
  const [tab, setTab] = useState<Tab>("forum");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [creatingCourse, setCreatingCourse] = useState(false);
  const clearMessages = () => {
    setError("");
    setNotice("");
  };
  const loadCourses = useCallback(async () => {
    try {
      const next = (
        await api<{
          courses: Course[];
        }>("/api/courses")
      ).courses;
      setCourses(next);
      setCourse(
        (current) =>
          next.find((item) => item.id === current?.id) ?? next[0] ?? null,
      );
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);
  useEffect(() => {
    api<{
      user: User;
    }>("/api/me")
      .then((result) => setUser(result.user))
      .catch(() => undefined);
  }, []);
  useEffect(() => {
    if (user) void loadCourses();
  }, [user, loadCourses]);
  if (!user) return <AuthEntry onDone={setUser} />;
  const selectCourse = (next: Course) => {
    clearMessages();
    setCourse(next);
    setCreatingCourse(false);
    setTab("forum");
  };
  const signedOut = async () => {
    await api<void>("/api/auth/signout", {
      method: "POST",
    }).catch(() => undefined);
    location.reload();
  };
  const created = (next: Course) => {
    setCourses((previous) =>
      [...previous, next].sort((a, b) => a.code.localeCompare(b.code)),
    );
    setCourse(next);
    setCreatingCourse(false);
    setTab("forum");
    setNotice("Course created. Add members from the Members tab.");
  };
  return (
    <main className="app">
      <header className="topbar">
        <strong>University TA Help Desk</strong>
        <span className="account">
          <b>{user.username}</b>
          <span
            className={`badge ${user.role === "staff" ? "staff-badge" : ""}`}
          >
            {user.role}
          </span>
          <button onClick={() => void signedOut()}>Sign out</button>
        </span>
      </header>
      <div className="layout">
        <aside aria-label="Course navigation">
          <h2>My courses</h2>
          {user.role === "staff" && (
            <button
              className="primary full"
              onClick={() => {
                clearMessages();
                setCreatingCourse(true);
              }}
            >
              Create course
            </button>
          )}
          {courses.map((item) => (
            <button
              className={`course ${course?.id === item.id ? "selected" : ""}`}
              key={item.id}
              onClick={() => selectCourse(item)}
            >
              <b>{item.code}</b>
              <small>{item.name}</small>
            </button>
          ))}
          {!courses.length && !creatingCourse && (
            <p className="muted">
              {user.role === "staff"
                ? "Create a course to get started."
                : "You have not been added to a course yet."}
            </p>
          )}
        </aside>
        <section className="content">
          {creatingCourse ? (
            <CourseForm
              onCreated={created}
              onCancel={() => setCreatingCourse(false)}
            />
          ) : course ? (
            <CourseWorkspace
              user={user}
              course={course}
              tab={tab}
              setTab={setTab}
              onCourseChange={(next) => {
                setCourses((items) =>
                  items.map((item) => (item.id === next.id ? next : item)),
                );
                setCourse(next);
              }}
              onCourseDeleted={(id) => {
                setCourses((items) => items.filter((item) => item.id !== id));
                setCourse((items) => (items?.id === id ? null : items));
                setNotice("Course deleted.");
              }}
              error={error}
              notice={notice}
              clearMessages={clearMessages}
              reportError={setError}
              reportNotice={setNotice}
            />
          ) : (
            <EmptyDashboard role={user.role} />
          )}
        </section>
      </div>
    </main>
  );
}
