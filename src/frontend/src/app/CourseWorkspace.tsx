import { Forum } from "../forum/Forum";
import { KnowledgeMapWorkspace } from "../knowledge-maps/KnowledgeMapWorkspace";
import { Members } from "../members/Members";
import { Resources } from "../resources/Resources";
import { Settings } from "../settings/Settings";
import type { Course, Tab, User } from "../api/types";
import { Banner } from "../shared/ui";
export function CourseWorkspace({
  user,
  course,
  tab,
  setTab,
  onCourseChange,
  onCourseDeleted,
  error,
  notice,
  clearMessages,
  reportError,
  reportNotice,
}: {
  user: User;
  course: Course;
  tab: Tab;
  setTab: (tab: Tab) => void;
  onCourseChange: (course: Course) => void;
  onCourseDeleted: (id: string) => void;
  error: string;
  notice: string;
  clearMessages: () => void;
  reportError: (message: string) => void;
  reportNotice: (message: string) => void;
}) {
  const staff = user.role === "staff";
  const navigate = (next: Tab) => {
    clearMessages();
    setTab(next);
  };
  return (
    <>
      <div className="course-heading">
        <p className="eyebrow">My courses / {course.code}</p>
        <h1>{course.name}</h1>
        <span className={`badge ${staff ? "staff-badge" : ""}`}>
          {staff ? "Staff access" : "Enrolled"}
        </span>
      </div>
      <nav className="tabs" aria-label="Course sections">
        <button
          className={tab === "forum" ? "active" : ""}
          onClick={() => navigate("forum")}
        >
          Forum
        </button>
        <button
          className={tab === "resources" ? "active" : ""}
          onClick={() => navigate("resources")}
        >
          Resources
        </button>
        {staff && (
          <>
            <button
              className={tab === "materials" ? "active" : ""}
              onClick={() => navigate("materials")}
            >
              Knowledge Map
            </button>
            <button
              className={tab === "members" ? "active" : ""}
              onClick={() => navigate("members")}
            >
              Members
            </button>
            <button
              className={tab === "settings" ? "active" : ""}
              onClick={() => navigate("settings")}
            >
              Settings
            </button>
          </>
        )}
      </nav>
      {error && (
        <Banner kind="error" message={error} onDismiss={clearMessages} />
      )}
      {notice && (
        <Banner kind="success" message={notice} onDismiss={clearMessages} />
      )}
      {tab === "forum" && (
        <Forum
          user={user}
          course={course}
          reportError={reportError}
          reportNotice={reportNotice}
        />
      )}
      {tab === "resources" && (
        <Resources
          course={course}
          staff={staff}
          reportError={reportError}
          reportNotice={reportNotice}
        />
      )}
      {tab === "materials" && staff && (
        <KnowledgeMapWorkspace
          course={course}
          reportError={reportError}
          reportNotice={reportNotice}
        />
      )}
      {tab === "members" && staff && (
        <Members
          course={course}
          reportError={reportError}
          reportNotice={reportNotice}
        />
      )}
      {tab === "settings" && staff && (
        <Settings
          course={course}
          reportError={reportError}
          reportNotice={reportNotice}
          onChanged={onCourseChange}
          onDeleted={onCourseDeleted}
        />
      )}
    </>
  );
}
