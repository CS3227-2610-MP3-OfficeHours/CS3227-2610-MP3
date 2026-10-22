# 20 October — multi-agent development workflow setup

**Kind: reconstructed.** Reconstructed from the Codex conversation records,
commits, and the workflow artefacts created during setup. The artefacts verify
the resulting process; this summary does not reproduce every prompt or claim
first-hand detail beyond the available records.

**Owner:** @p-xp.

---

## 1. From assignment plan to an agent workflow

The early planning conversations reviewed the assignment requirements, checked
whether the proposed project covered its required user and AI features, and
turned the plan into staged work with named owners and completion gates. A
key sequencing decision was to split the user-facing forum from the AI work:
forum features could proceed without waiting for the school's LLM service
details, while AI work stayed blocked until its operating constraints and
product policy were known.

## 2. Defining roles and durable handoffs

The team asked how Codex agents are created, how work reaches the right role,
and how skills differ from agents. The resulting workflow distinguishes:

- **Role instructions**, which define a specialist's responsibilities and
  boundaries.
- **Skills**, which preserve repeatable procedures for a task, such as feature
  specification, UI review, RAG security review, and verification.
- **Agent sessions**, which perform a bounded task and return a deliverable.

Handoffs were designed around named inputs, exact outputs, acceptance criteria,
constraints, test evidence, and unresolved decisions. This made feature files
the durable source of approval and implementation state rather than relying on
conversation memory.

## 3. Approval and independence rules

The repository workflow established a feature sequence of specification,
design when the user flow changes, human approval for consequential decisions,
implementation, code-quality review, independent testing, and (for substantial
or security-sensitive work) an acceptance audit.

Each specialist received a bounded role and could not spawn nested agents.
Reviewers were expected to work from artefacts and evidence rather than the
implementer's private reasoning. The orchestrator was required to stop a
completion claim when a test report remained **Needs changes** or an applicable
acceptance criterion lacked evidence.

## 4. Repository artefacts

The setup produced `AGENTS.md`, `.codex/config.toml`, specialist role
definitions, reusable skills under `.codex/skills/`,
`workflow/development-process.md`, the project overview, and the staged
implementation plan. Role configuration was later consolidated so each TOML
role file contains its own developer instructions and disallows nested
delegation; redundant role Markdown files were removed.

## 5. Why the process was refined

Later conversations considered making an independent code-quality reviewer
mandatory for every change. The workflow kept specialist invocation tied to
material value and task size, while making code-quality review mandatory after
engineering handoffs. This addresses review consistency without turning
trivial edits, read-only work, or one-file fixes into unnecessary multi-stage
processes.
