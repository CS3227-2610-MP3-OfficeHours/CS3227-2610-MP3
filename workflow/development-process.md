# Development Process

## Purpose

This lightweight process lets either developer resume a feature without relying
on a previous Codex conversation. It uses repository artefacts, native Codex
specialist agents, and human approval for consequential decisions.

## Feature artefacts

Create `workflow/features/<feature-name>/` only when a feature begins. Its
files have these owners and purposes:

| File | Owner | Required contents |
| --- | --- | --- |
| `spec.md` | Product manager | status, confirmed requirements, assumptions, non-goals, acceptance criteria, approvals, open decisions |
| `design.md` | UI designer | user flow, layouts/components, interaction and state behavior, accessibility/responsive decisions, approvals |
| `tasks.md` | Engineer | implementation checklist, technical decisions, changed areas, test commands/results, QA handoff |
| `test-report.md` | Tester | scenarios, actual results, criteria coverage, defects/reproduction, limitations, final assessment |
| `completion-review.md` | Acceptance auditor | artefact-only completion verdict, evidence gaps/blockers, and next owner |

The product manager may omit `design.md` only for work with no user-facing
change. A missing required artefact is a blocker, not an invitation to infer
unapproved behavior.

Start `spec.md` and `design.md` with this compact status block so another
developer can resume without reading agent history:

```md
Status: Draft | Approved | Blocked | Superseded
Approval: <human decision/date, or Pending>
Last handoff: <from → to, date, and one-line purpose>
```

## Lifecycle and status

1. The orchestrator asks the product manager to produce/update `spec.md`.
2. For UI work, the orchestrator asks the UI designer to produce/update
   `design.md` after the specification is ready.
3. A human reviews and approves consequential product, security, and design
   decisions. Mark the relevant artefact `Status: Approved` with the decision
   date/summary; otherwise use `Draft` or `Blocked`.
4. The engineer implements the approved artefacts and records evidence in
   `tasks.md`.
5. The tester independently produces `test-report.md` with `Pass` or `Needs
   changes`.
6. If testing reports `Needs changes`, the orchestrator routes named defects to
   engineering and ambiguity to product/design, then obtains independent
   re-verification after the affected artefact is updated.
7. For substantial or security-sensitive work with a passing test report, the
   acceptance auditor writes `completion-review.md` from feature artefacts only.
8. The orchestrator gives the human a completion summary only when the test
   report and, when required, completion review pass their completion gates.

## Handoff convention

Every handoff begins with a compact summary containing:

- feature name and current status;
- exact input artefacts and commit/working-tree state;
- bounded task and expected output path;
- decisions already approved, assumptions, and blockers;
- applicable skills or security tests; and
- next owner and the condition for transfer.

Do not use a handoff to transfer hidden context, credentials, or authority to
make a new product decision. If source artefacts conflict, the orchestrator
records the conflict and requests human direction when it affects behavior,
scope, risk, or design.

## Selecting agents

- Use product manager for new or materially changed requirements. A feature is
  substantial when it changes a user flow, role/course authorization, stored
  data or API contract, AI/RAG behavior, security boundary, or deployment
  behavior. Simple low-risk fixes that preserve those contracts may proceed
  directly to engineering.
- Use UI designer only for user-facing behavior or design review.
- Use engineer only after required approvals; it may handle small, clearly scoped
  bug fixes directly.
- Use tester for independent acceptance checks, especially after a substantial
  feature or security-sensitive change.
- Use acceptance auditor after a passing tester report for substantial or
  security-sensitive work. It reviews only the approved scope and recorded
  artefact evidence; it does not inspect implementation or rerun tests.

For a simple, low-risk fix that preserves user flows, authorization, data/API,
AI/RAG, security, and deployment contracts, the orchestrator applies the
completion checklist directly: scope is met, relevant passing test evidence is
recorded, no blocker or required approval remains, and the final response does
not overstate completion. A `Needs changes` report, blocking defect, missing
test result, or missing acceptance-criteria coverage always blocks completion.

Parallel work is appropriate only for independent analysis/review. Agents must
not edit the same artefact or source area at the same time. The orchestrator
owns integration and may keep sequential work in one main session when a
subagent adds no separation of concerns.

## Shared skills and Git use

The native skills in `.codex/skills/` are the single source for reusable
procedures: `feature-spec`, `ui-consistency`, `rag-security-review`, and
`feature-verification`. They are intentionally distinct from the specialist
agents: skills define repeatable procedures; agents carry bounded assignments
and produce handoff artefacts. If a skill is temporarily unavailable, the
specialist follows its role instructions and records that limitation; missing
skill discovery alone does not block essential work.

After cloning or pulling, both developers use the project root as the Codex
workspace and trust the project so `.codex/config.toml` can load. They can ask
the main agent to delegate by role, for example: “Use the product-manager agent
to create the spec for `student-ai-help`.” No personal configuration needs to
be committed. Local credentials remain in each developer's own Codex setup.

## Recovery

To resume later, read `AGENTS.md`, this file, `workflow/project-overview.md`,
and the current feature directory. The current status and next handoff in the
artefacts take precedence over conversation summaries. Record failed tests,
blocked decisions, and known limitations rather than deleting them.
