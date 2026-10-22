# 20–21 October — workflow, shared platform, staff features

**Kind: reconstructed.** Assembled from the commit history and the artefacts
those commits added. The ordering, the outputs and the decisions recorded in
`workflow/` are verifiable; the prompt-by-prompt detail of these sessions was
not observed by the agent writing this summary and is therefore not described.

**Owner:** @p-xp.

---

## Session 1 — the process itself

Before any application code, the multi-agent workflow was established.

Produced `AGENTS.md`, `.codex/agents/*.toml` (product-manager, ui-designer,
engineer, tester, acceptance-auditor), `.codex/skills/` (commit-changes,
feature-spec, feature-verification, rag-security-review, ui-consistency), and
`workflow/development-process.md`.

The decisions visible in those artefacts:

- One writable artefact per role, so no agent can edit what it is judging.
- `agents.enabled = false` for every specialist, so no nested delegation.
- Approval status lives at the top of `spec.md` and `design.md`, never in a
  conversation.
- An orchestrator completion gate that forbids reporting completion while a
  test report says `Needs changes`.

Then the product context: `workflow/project-overview.md`,
`workflow/implementation_plan.md` (eight stages with owners and gates), and the
first feature specifications.

## Session 2 — shared platform (20 Oct)

`shared-course-forum` taken through the full chain: spec → design → human
approval → implementation → tests.

Delivered accounts and sessions, courses, memberships, the course-scoped forum,
server-enforced role and course authorization, and the PostgreSQL migration
path. `PlatformIntegrationTest` was written alongside, covering cross-course
denial, author-edit boundaries, staff moderation limits, and immediate loss of
access on membership removal.

A deliberate choice recorded here: a uniform `404` for non-member,
wrong-course, and non-existent, so the API cannot be used to probe what exists.

## Session 3 — staff material governance, non-AI first (21 Oct)

The staff feature was split so the non-LLM half could proceed while the SoC LLM
operating envelope was still unknown — recorded as a human-approved sequencing
decision in `implementation_plan.md`.

Delivered PDF upload with validation (type, size, page count, real PDF
structure, encrypted-file rejection), material lifecycle, and enrolled-student
access through Resources. `workflow/features/staff-material-governance/`
carries the spec, design, tasks and an independent test report.

## Session 4 — SoCLaaS reference (21 Oct)

The school's gateway documentation was captured into the repository as
`workflow/soc-llm-information.md`, `soc-llm-quickstart.md` and
`soc-llm-api-reference.md`, so the integration could be specified offline.

## Session 5 — Knowledge Maps and PDF provenance (21 Oct)

The staff LLM feature, built across several passes and specified in
`workflow/specs/pdf-parsing-provenance.md` and
`workflow/specs/per-material-knowledge-maps.md`.

- Page-aware PDF text extraction with provenance: digests, parser version, and
  per-page state, bounded and non-executing.
- `SoCLaaSClient`: the single gateway adapter, with typed outcomes for each
  status class and one fallback attempt on a different model for transient
  failures only (`workflow/specs/soclaas-model-fallback.md`).
- Per-material Knowledge Maps replacing a single course-wide map, each bound to
  one PDF by digest, generated as `pending_review`, and approvable only by
  course staff.
- Citation verification: a map whose citations do not resolve to real pages of
  its own source is marked unverified and cannot be approved.

Nine standalone security reviews under `workflow/security/` were produced
during this work, covering the citation contract, provider failure handling,
hybrid xref validation, and the unverified-map state.

The acceptance-auditor role and its completion gate were added during this
period and applied to `staff-material-governance` and `ai-rag-release`.

## Session 6 — staff UI refinement (22 Oct)

`feat: streamline staff Resources and Knowledge Map` (PR #2) moved uploads into
a modal dialog within Resources, split the Knowledge Map workspace out of the
materials screen, and **hardened the staff generation prompt**: one factual
claim per line, every line ending in a citation, no trailing punctuation after
it. Three assertions pinning that prompt text were added to the adapter suite.

That change was a response to real output — generated maps were being marked
unverified because their formatting defeated the citation check.

---

## What a reader should take from this period

The process artefacts came first and the application second, which is visible
in the commit order. By the time any LLM code existed there was already a
specification chain, an approval gate, and an independent tester role to put it
through.
