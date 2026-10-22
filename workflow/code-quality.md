# University TA Help Desk — Code Quality Standards

Status: Project defaults; exceptions require a concrete reason and review.
Owner: Engineering team.
Last reviewed: 2026-10-22.

This document standardizes code review across the languages currently used by
the project. Apply it to new and changed code, and use judgment when reviewing
older code: report nearby legacy issues only when they affect the requested
change or present a material maintenance risk. It complements approved feature
specifications, `workflow/design/system.md`, security requirements, and tests;
it does not change product behavior.

## Review principles

- Prefer clear, direct code over cleverness, indirection, or abstraction for its
  own sake. A useful abstraction removes repeated policy or makes a boundary
  explicit; avoid wrappers that only rename one call.
- Keep related behavior together and unrelated responsibilities apart. Methods,
  components, and modules should have a clear reason to change.
- Make dependencies and data flow visible. Avoid hidden global state, unnecessary
  bidirectional dependencies, and cross-layer reach-through.
- Preserve established behavior and public contracts unless an approved change
  requires otherwise. Prefer a small change that fits the current architecture.
- Handle invalid input, expected failure, resource limits, and concurrency at
  the boundary where the application has enough context to respond safely.
- Optimize for maintainability before speculative performance. For expensive
  work, unbounded collections, or external calls, ensure cost and failure modes
  are explicit.
- Comments should explain a non-obvious constraint, invariant, or reason. Keep
  them accurate; do not narrate code that is already clear.

## Naming, formatting, and local craft

- Use domain terms consistently with `Domain.java`, API contracts, migrations,
  and approved feature artefacts. Names should communicate role, units, scope,
  and state where that would prevent confusion.
- Avoid unexplained abbreviations, ambiguous booleans, vague names such as
  `data`, `value`, `handle`, or `process` when a domain-specific name is
  available, and names that encode temporary implementation details.
- Keep formatting consistent with the surrounding language and file. Avoid
  compressed multi-statement lines when they make control flow, side effects,
  or error handling harder to scan. Do not reformat unrelated regions in a
  focused change.
- Remove dead code, stale comments, duplicated literals, and unused imports
  when touched. Extract constants for repeated policy values or values whose
  meaning would otherwise be unclear; do not create constants for every
  obvious literal.
- Keep branches and nesting understandable. Use early returns or small helpers
  when they make the main path clearer, not as mechanical rules.

## Java 25: API, service, and tests

- Follow the existing package layout under `edu.nus.tahelpdesk`; use Java
  `UpperCamelCase` for types, `lowerCamelCase` for methods and variables, and
  `UPPER_SNAKE_CASE` for constants. Keep one primary public type per file.
- Give types cohesive jobs. Keep HTTP parsing/serialization in the API layer,
  policy and domain behavior in services, persistence concerns in the platform
  adapters, and provider-specific request/response handling in the client
  boundary. Avoid growing `Main`, `HttpApi`, or a service into unrelated
  subsystems.
- Keep authorization checks server-side and close to protected operations.
  Require explicit course and role context where needed; do not rely on the UI,
  request-supplied role, or a caller's prior check as the security boundary.
- Prefer immutable records/value objects for data that should not change. Make
  ownership of mutable collections and byte arrays explicit; defensively copy
  at boundaries where callers could otherwise mutate stored state.
- Use precise exception handling. Translate expected domain failures into the
  established `ApiException` response contract; do not swallow failures or
  expose implementation details, secrets, provider payloads, or stack traces to
  clients. Preserve diagnostic context in safe server-side handling where
  available.
- Bound external work, queues, input sizes, retries, and timeouts. Avoid holding
  broad locks during slow I/O. Make executor lifecycle, shared state, and
  synchronization choices explicit and testable.
- Prefer standard library and existing dependencies unless a dependency solves
  a concrete need. Keep database statements parameterized; preserve transaction,
  referential integrity, and migration compatibility expectations.
- Tests use descriptive scenario names and assert observable outcomes, including
  authorization failures, provider failures, edge conditions, and persistence
  behavior where relevant. Avoid tests coupled to private implementation
  details.

## TypeScript and React

- Use TypeScript's declared types for API payloads, component props, and state.
  Avoid `any` and unsafe casts; when runtime data is not trusted, validate or
  narrow it at the boundary rather than claiming a type makes it safe.
- Use `PascalCase` for React components and type names, `camelCase` for values
  and functions, and constants in the surrounding project's established style.
  Name event handlers and async actions for their outcome, not merely `handle`.
- Keep components focused on one UI responsibility. Extract a component or hook
  when it clarifies reuse, state ownership, or a complex interaction; avoid
  fragmenting simple markup into layers of pass-through components.
- Keep server state distinct from transient UI state. Make loading, empty,
  validation, permission, and service-failure states explicit. Prevent duplicate
  submissions where an operation is pending, and avoid stale async responses
  overwriting newer state.
- Use semantic HTML, associated labels, accessible names, keyboard-operable
  controls, visible focus, and status/error semantics. Follow
  `workflow/design/system.md` for shared UI dimensions and responsive behavior.
- Keep API calls and response/error handling consistent with the existing
  client helper. Do not treat UI checks as authorization or expose secrets in
  browser code. Render user-authored or AI-generated content safely; do not
  interpret untrusted strings as HTML or executable content.
- Keep CSS selectors and component styles understandable and scoped to their
  purpose. Prefer shared tokens/patterns for repeated values; avoid accumulating
  one-off overrides that conceal a shared layout issue.

## SQL migrations and persistence

- Use explicit, descriptive, consistent `snake_case` names for tables, columns,
  indexes, and constraints. Match existing PostgreSQL types and conventions.
- Make constraints express invariants in the database where appropriate:
  `NOT NULL`, foreign keys, uniqueness, checks, and indexes for demonstrated
  query patterns. Do not rely only on application validation for persisted
  integrity.
- Treat migrations as append-only deployment history. Do not rewrite an applied
  migration; add a forward migration for schema changes. Consider existing rows,
  lock/availability impact, rollback implications, and adapter compatibility.
- Use foreign keys and deletion behavior deliberately. Avoid cascading or
  retaining records without considering course authorization, provenance, and
  lifecycle semantics.
- Keep queries parameterized. Select only needed data, use stable ordering when
  callers depend on it, and avoid N+1 or unbounded reads for growing datasets.

## Shell scripts and repository tooling

- Keep scripts POSIX `sh` compatible when they declare `#!/bin/sh`; use `set -eu`
  where appropriate and quote expansions that represent paths or user values.
- Check command failures and prerequisites. Avoid destructive cleanup outside
  the script's clearly scoped generated output. Do not log credentials or
  environment secrets.
- Keep scripts small and purpose-specific. Document non-obvious environment,
  dependency, or lifecycle requirements in the README or workflow docs.

## Architecture and cross-cutting requirements

- Preserve layer boundaries and keep public contracts explicit. Avoid circular
  dependencies, duplicated policy, service locators, and shared mutable state
  without a clear ownership and concurrency model.
- Keep changes proportional. Split a large responsibility when it has distinct
  policy, lifecycle, dependency, or testing needs; do not introduce a framework
  or generalized plugin system for one current use case.
- Keep configuration separate from code and never commit credentials, personal
  settings, or machine-specific paths. Validate configuration at startup and
  fail with an actionable, non-secret diagnostic.
- Enforce role and course access on the server and in persistence queries where
  appropriate. Avoid authorization check/use gaps and ensure identifiers from a
  request cannot cross course boundaries.
- Treat uploaded files, filenames, forum content, retrieved passages, summaries,
  and user prompts as untrusted. Keep data separate from instructions in AI
  workflows; validate provider output before it becomes structured or approved
  application state.
- Ground AI help in authorized, current source material, preserve source
  provenance/citations, and keep generated suggestions distinct from human
  posts. Fail safely on missing sources, stale material, rate limits, and
  provider errors. Do not automatically publish generated content.
- Keep secrets and internal diagnostics out of API responses and browser logs.
  Validate inputs, cap resource use, and use safe output encoding at trust
  boundaries.

## Code review reporting

A useful finding identifies a concrete behavior or maintenance cost, gives an
exact file and line, explains the impact, and suggests a focused correction.
Use severity consistently:

- **P0**: critical security, data-loss, or service-wide failure risk.
- **P1**: serious defect likely to affect normal operation, access control, or
  safe handling of user/provider data.
- **P2**: meaningful maintainability, correctness, or reliability issue with
  bounded impact or likely future cost.
- **P3**: small clarity or consistency improvement with low impact.

Report findings rather than preferences. Group multiple symptoms caused by one
root issue. Distinguish verified behavior from inference, and state when a
review is static or its scope excludes runtime behavior. A clean review means
no actionable findings were found in the reviewed scope; it is not a claim that
unreviewed code is defect-free.
