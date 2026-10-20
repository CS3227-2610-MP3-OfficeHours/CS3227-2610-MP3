import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

/** Shared client entry point. Student/staff feature owners add routed views here. */
function App() {
  return <main><h1>University TA Help Desk</h1><p>The shared client foundation is ready for feature-owned views.</p></main>;
}

createRoot(document.getElementById("root")!).render(<StrictMode><App /></StrictMode>);
