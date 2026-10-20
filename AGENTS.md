# University TA Help Desk — Agent Workflow

This repository contains the shared, Git-tracked workflow for a two-person
University TA Help Desk project. The application supports staff and students,
course-scoped forum activity, uploaded material, and AI-assisted help. Read
`workflow/project-overview.md` before substantial work.

## Specialist agents

The main Codex agent is the orchestrator. It selects only the specialists that
materially help the requested task:

- `product-manager`: scope a substantial feature and write testable requirements.
- `ui-designer`: design or review a user-facing flow after product requirements
  are clear.
- `engineer`: implement approved work and tests.
- `tester`: independently verify completed work and report evidence.

Role declarations live in `.codex/config.toml`; their project-local role
instructions are under `.codex/agents/`. Shared reusable procedures are native
skills under `.codex/skills/`. Do not duplicate their procedures in feature
artefacts.

## Invoking specialists

Ask the main Codex agent to delegate a bounded task by role and feature name.
Examples:

- “Use the `product-manager` agent to create the spec for `student-ai-help`.”
- “Use the `ui-designer` agent to design the approved `student-ai-help` flow.”
- “Use the `engineer` agent to implement approved feature `student-ai-help`.”
- “Use the `tester` agent to independently verify `student-ai-help`.”

Include the expected artefact path and whether the work is read-only, review,
or implementation when that is not obvious. The orchestrator should provide
only the relevant input artefacts and wait for dependent stages to complete.

## Feature lifecycle

For a substantial feature, use this ordered flow:

1. Product manager writes `workflow/features/<feature-name>/spec.md`.
2. UI designer writes `design.md` only when the feature changes a user-facing
   flow.
3. A human approves major scope, academic-integrity, and consequential design
   choices before implementation.
4. Engineer implements approved work, tests it, and writes `tasks.md`.
5. Tester writes an independent `test-report.md`.
6. The orchestrator returns defects to engineering, and ambiguous requirements to
   product manager or UI designer, then summarizes completion for human review.

A feature is substantial when it changes a user flow, role/course authorization,
stored data or API contract, AI/RAG behavior, security boundary, or deployment
behavior. Simple, low-risk fixes that preserve those contracts may skip
product/design work. Do not start dependent work before its prerequisite
artefact is approved. Parallelize only independent work and never assign
simultaneous edits to the same files.

## Artefacts, handoffs, and decisions

Feature artefacts are the durable cross-session context. Each handoff must name
the feature, input files/commit, exact deliverable, unresolved questions, test
evidence, and next owner. Keep approval status at the top of `spec.md` and
`design.md`; do not rely on a prior conversation to establish approval.

When agents disagree, the orchestrator records the alternatives and asks a human
to decide if the disagreement changes product behavior, security posture, scope,
or design. When blocked, record the blocker and safe next action in the current
artefact rather than guessing.

## Repository rules

- Keep credentials, personal Codex settings, absolute local paths, and machine
  configuration out of committed files.
- Before any AI-authored staging or commit, use the `commit-changes` skill. It
  checks scope, recommends splitting unrelated changes, and enforces the shared
  commit-message standard. It does not replace explicit user authorization to
  commit or push.
- Enforce role/course access on the server; UI visibility is not authorization.
- Treat uploaded materials, filenames, forum content, generated summaries, and
  user prompts as untrusted data in AI/RAG flows.
- Preserve existing project and user changes. Run relevant tests before claiming
  implementation completion.
- Do not commit or push unless a user explicitly requests it.
