# Developer Guide

This guide describes the design of the University TA Help Desk and the
engineering process used to build it, with emphasis on the four additional
requirements of the assignment: an LLM feature per role, Spec-Driven
Development with multi-agent software engineering, explicit AI security, and a
deployed system with development and production separated.

It describes the system as it stands on `master`. Where something was planned
and not built, or built with a known weakness, it says so.

---

## 1. Architecture at a glance

```
browser ── HTTPS ──► Java server (one origin) ──► PostgreSQL
  React 19 SPA          com.sun.net.httpserver        users, courses, posts,
  built by Vite         HttpApi   (routing, JSON)     comments, materials,
                        PlatformService (authz,       pdf pages, knowledge maps
                          domain rules, state)
                        StudentAiService  ─┐
                        PlatformService    ├─► SoCLaaSClient ──► SoC LLM gateway
                          (knowledge maps) ─┘                    (chat models)
                        StaticSite (serves the built client)
```

Deliberate choices:

- **No web framework.** The server is the JDK's own `com.sun.net.httpserver`,
  compiled with `javac`. There is no Maven or Gradle; `scripts/test.sh` and the
  `Dockerfile` invoke the compiler directly. This keeps the dependency surface
  to two JARs (PDFBox, the PostgreSQL JDBC driver) and makes the whole
  authorization path readable in one file.
- **One origin.** In production the Java process also serves the built client
  (`StaticSite`), so there is no CORS surface and no second deployment.
- **Server-authoritative.** The client renders what it is told. Every
  authorization decision is made on the server; hiding a button is never the
  control.

### Source layout

| Path | Contents |
| --- | --- |
| `src/main/java/edu/nus/tahelpdesk/` | The whole server |
| `src/test/java/edu/nus/tahelpdesk/` | Four test suites, run by `scripts/test.sh` |
| `src/frontend/` | React 19 + TypeScript client, built by Vite |
| `db/migrations/` | Numbered SQL migrations, applied at startup |
| `scripts/` | Build, test, run and container entrypoint scripts |
| `workflow/` | The SDD and multi-agent process artefacts (section 8) |
| `docs/` | This guide, the User Guide, Reflections, and the site |
| `logs/` | Summaries of the AI interactions during development |

### Server classes

| Class | Responsibility |
| --- | --- |
| `Main` | Startup: config, database, migrations, AI clients, static site, logging of what is and is not configured |
| `AppConfig` | Environment-only configuration, validated at startup; refuses a half-configured production |
| `HttpApi` | Routing, JSON serialisation, session cookie, uniform error envelope |
| `PlatformService` | Authoritative state and every authorization rule; the Knowledge Map lifecycle |
| `PostgresPlatform` | Loads state at boot, persists after each mutation |
| `Domain` | Records and enums for every persisted entity |
| `SessionManager` | Opaque session tokens |
| `PasswordHasher` | Salted password hashing |
| `PdfPageExtractor` | Bounded, non-executing PDFBox adapter producing page-aware text |
| `SoCLaaSClient` | The only code that talks to the LLM gateway |
| `StudentAnswerPolicy` | The student answer scopes and the deterministic screen |
| `StudentAiService` | Retrieval, prompt assembly, citation validation, outcomes |
| `StaticSite` | Serves the built client with SPA fallback |
| `Markdown` | Escaping renderer for user-authored text |
| `Json` | Minimal dependency-free JSON parser and writer |
| `ApiException` | The small, deliberate error vocabulary |

---

## 2. Data model and storage

Entities: `User`, `Course`, `Membership`, `Post`, `Comment`, `Material`,
`PdfPage`, `KnowledgeMap`, `KnowledgeMapJob`. All are immutable records;
mutation replaces the record.

Migrations `V1`–`V5` in `db/migrations/` are applied in order at startup before
the server accepts traffic.

**How files are stored, stated plainly.** Uploaded PDFs are held as `BYTEA` in
`course_materials.content`. There is no object storage and nothing is written
to disk. Extracted page text lives in `course_material_pdf_pages`, one row per
page, with a digest of the source so a map can be detected as stale.

**Persistence model, and its consequences.** `PlatformService` holds all state
in memory and is the read path; PostgreSQL is a durable snapshot. At boot
`PostgresPlatform` loads every row, PDF bytes included, into memory. After each
mutation it writes the entire state back inside one transaction.

This is simple and consistent, and it has three costs that a reader should know
before extending the system:

1. Every PDF is resident in heap for the process's lifetime. On a 512 MB host
   that bounds total material at roughly fifteen 20 MB PDFs.
2. `listResources` copies each material record, which copies its bytes, so
   rendering a list of filenames allocates the size of the course's PDFs.
3. Every mutation rewrites every table. Posting a forum comment rewrites every
   stored PDF.

None of this is visible at demonstration scale. It is the first thing to change
if the system were taken further: move bytes to object storage and keep only
references in the database, and replace the snapshot with per-entity writes.
The Stage 6 plan in `workflow/implementation_plan.md` specifies that design; it
was not built, and this guide does not claim otherwise.

### Limits enforced on the server

| Limit | Value |
| --- | --- |
| PDF size | 20 MB |
| PDF pages | 100 |
| Extracted text per page | 24,000 characters |
| Active PDFs per course | 20 |
| Student AI requests | 5 per minute per student per course |
| Staff material retries | 3 per minute per staff member per course |
| Question length | 1,000 characters |
| Retrieval sent to the model | 8 pages, 3 materials, 24,000 characters |

---

## 3. API

JSON over HTTPS, session cookie `ta_session` (`HttpOnly`, `SameSite=Strict`,
`Secure` in production). Every response carries `X-Content-Type-Options:
nosniff` and `Cache-Control: no-store`.

```
POST   /api/auth/signup | signin | signout
GET    /api/me
GET    /api/courses                       POST /api/courses
GET    /api/courses/{c}                   PATCH /api/courses/{c}   DELETE /api/courses/{c}
GET    /api/courses/{c}/members           POST /api/courses/{c}/members
DELETE /api/courses/{c}/members/{u}
GET    /api/courses/{c}/posts             POST /api/courses/{c}/posts
GET    /api/courses/{c}/posts/{p}         PATCH, DELETE
POST   /api/courses/{c}/posts/{p}/comments
PATCH  /api/courses/{c}/posts/{p}/comments/{m}   DELETE
GET    /api/courses/{c}/materials         POST /api/courses/{c}/materials
DELETE /api/courses/{c}/materials/{m}     POST .../materials/{m}/retry
GET    /api/courses/{c}/resources         GET  .../resources/{m}/download
GET    /api/courses/{c}/knowledge-maps    POST .../knowledge-maps/generate-all
POST   /api/courses/{c}/materials/{m}/knowledge-map/generate | approve | reject
GET    /api/courses/{c}/ai-help           POST /api/courses/{c}/ai-help
GET    /health
```

Errors use one envelope, `{"error":{"code","message"}}`, drawn from a
deliberately small vocabulary: `401 authentication_required`,
`403 permission_denied`, `404 not_found`, `409 conflict`,
`422 validation_error`, `429 retry_limited`, `503 service_unavailable`.

**`404` is load-bearing.** A non-member, a member of another course, a staff
member reaching the student endpoint, and a course that does not exist all
receive the same `404`. Nothing in the API lets a caller distinguish "you may
not" from "it is not there", so course existence and material existence cannot
be probed.

---

## 4. The two AI features

Both reach the SoC LLM gateway through one class, `SoCLaaSClient`, and one
request primitive, `chatOnce`. Nothing else in the codebase makes an HTTP call
to a model. Keys are read from the environment at startup and never leave the
server: they are not in the client bundle, not in any response, and not logged.

The adapter maps every outcome to a typed result — `rate_limited`,
`temporarily_unavailable`, `configuration`, `rejected` — and retries exactly
once on a *different* model only for transient failures.

### Staff: per-PDF Knowledge Maps (`PlatformService`)

A staff member asks for a map of one active PDF. The server snapshots the
material digest, sends its page-aware text as quoted data, and requires a
structured topic list in which every line ends with a citation. The result is
stored `pending_review`. If its citations do not resolve to real pages of that
PDF it is marked unverified and cannot be approved. Approval is recorded with
reviewer and time. Changing or deleting the PDF drops the map.

A map is never a forum post, never shown to a student, and never student-usable
before a human approves it.

### Student: AI help (`StudentAnswerPolicy`, `StudentAiService`)

One question produces at most two gateway calls.

1. **Scope.** A deterministic screen over the student's own text runs first and
   is authoritative. A short classification call — which receives *only the
   question*, never retrieved material — is advisory. The effective scope is
   the stricter of the two. See section 7.
2. **Retrieval.** Candidates are the course's active, text-bearing PDFs.
   Approved, digest-current Knowledge Maps raise their material's score; map
   text is used for scoring only and is never sent to the model. Pages are
   selected to hard caps.
3. **Composition.** The selected pages and the question are sent as delimited,
   quoted data under a scope-specific instruction.
4. **Validation.** Every cited filename/page pair must be in this request's
   packet, every asserting sentence must end with its own citation, and no PDF
   may be named in any other form. Output echoing the policy or the source
   delimiters is discarded. Anything that fails becomes
   `insufficient_evidence`, and no partial text is shown.

Nothing is persisted: no question, no answer, no scope. The feature adds no
table.

**Token budgets.** The permitted models reason before answering and report that
reasoning separately, so a small completion budget returns an empty `content`
with `finish_reason: "length"`. Measured against the gateway, classification
needs roughly 600 tokens before it emits anything and a grounded answer used
984. The budgets are 768 and 2,000. The adapter never falls back to the
reasoning field: it is the model's private working and must not reach a student.

---

## 5. Spec-Driven Development

The process is spec-anchored rather than spec-first: specifications are
versioned artefacts that are amended when the implementation teaches us
something, and the amendment is recorded rather than quietly applied.

### The artefact chain

Every substantial feature has a directory under `workflow/features/<name>/`:

| File | Owner | Purpose |
| --- | --- | --- |
| `spec.md` | Product manager | Status, confirmed requirements, non-goals, acceptance criteria, approvals, open decisions |
| `design.md` | UI designer | Flow, layout, states, accessibility, approvals |
| `tasks.md` | Engineer | Checklist, technical decisions, changed areas, test commands and actual results, limitations |
| `test-report.md` | Tester | Scenarios, actual results, criteria coverage, defects, verdict |
| `completion-review.md` | Acceptance auditor | Artefact-only verdict on whether completion is supported |

Larger decisions get a normative record under `workflow/specs/`, which the
feature `spec.md` adopts. `spec.md` states that the record governs where the
two differ — a precedence rule that mattered in practice, see Reflections.

### The gates

1. Product manager writes `spec.md`.
2. UI designer writes `design.md` for user-facing work.
3. **A human approves** scope, academic-integrity and consequential design
   choices. Nothing is implemented before this.
4. Engineer implements and records `tasks.md`.
5. An **independent** tester writes `test-report.md` with `Pass` or
   `Needs changes`.
6. Defects return to engineering, ambiguity to product or design, then
   re-verification.
7. For security-sensitive work, the acceptance auditor writes
   `completion-review.md` from the artefacts only.
8. Completion is reported to the human only when those gates pass.

Approval status lives at the top of `spec.md` and `design.md`, never in a
conversation, so any developer or agent can resume without the history.

### Spec engineering in practice

A worked example is `workflow/features/student-ai-help/`. The specification
began with a single rule — guidance only, never answers. Building against it
exposed that the rule refused course administration, which a help desk exists
to answer. The resolution was not to loosen the prompt but to amend the
specification into three scopes, record the amendment with its reasoning, get
it re-approved, and reissue the answer policy as a new version
(`student-answer-policy/v2`) whose version string is carried in the prompt and
asserted by tests.

The approved-behaviour document, `answer-guidelines.md`, is deliberately
separate from the specification. It states what the assistant may say; the
server renders it into the prompt. Changing permitted behaviour therefore means
amending and re-approving a document, not editing a string.

---

## 6. Multi-agent orchestration

The work was carried out by specialist AI agents coordinated by an
orchestrator. Role definitions live in `.codex/agents/*.toml`, shared
procedures in `.codex/skills/`, and the rules binding them in `AGENTS.md`.

### The roles

| Agent | Responsibility | May write |
| --- | --- | --- |
| Orchestrator | Selects specialists, routes defects, enforces the completion gate | Nothing directly |
| `product-manager` | Scope and testable requirements | `spec.md` |
| `ui-designer` | User-facing flow and states | `design.md` |
| `engineer` | Implementation and tests | source, tests, `tasks.md` |
| `tester` | Independent verification | `test-report.md` |
| `acceptance-auditor` | Read-only artefact audit | `completion-review.md` |

### How the roles are constrained

The design assumes agents can be wrong, so authority is bounded:

- **One writable artefact each.** A tester cannot edit the implementation it is
  judging; an auditor cannot edit the test report it is reading. A defect must
  be reported, not silently repaired, so every correction is visible.
- **No nesting.** `.codex/agents/*.toml` sets `agents.enabled = false`, so a
  specialist cannot spawn its own sub-agents. The orchestrator remains the only
  coordinator and there is no hidden chain of delegation.
- **Independence by fresh context.** The tester is given the artefacts and the
  diff, not the engineer's reasoning, so it re-derives rather than inheriting
  conclusions.
- **Artefacts, not conversation.** A handoff names the feature, the input
  files, the deliverable, the unresolved questions and the next owner. Nothing
  depends on a previous chat, which is also what lets a human audit the chain.
- **A human holds the approvals.** Agents may not approve their own work, and
  no agent message is treated as a human decision.

### The orchestrator's completion gate

The rule in `AGENTS.md` is explicit: completion may not be reported while the
latest `test-report.md` is `Needs changes`, has a blocking defect, lacks actual
results, or lacks coverage for an applicable acceptance criterion. For
security-sensitive work the auditor runs only after a tester `Pass`, and any
verdict other than `Ready for human review` blocks completion. The same section
caps remediation at two cycles before a human must decide, so an agent loop
cannot grind indefinitely.

This gate did real work. `student-ai-help` went through three verification
rounds and an acceptance audit; the first round returned eight defects, and the
audit then refused to pass the feature on a point both the engineer and the
tester had agreed on. Section 4 of `docs/Reflections.md` gives the details.

### Where it is weaker than it looks

The roles were played by one model family, so they share blind spots: three
rounds of verification passed a feature that could not answer a single real
question, because every suite stubbed the gateway. Independence of context is
not independence of judgement. The strongest check in the process turned out to
be the one that read only artefacts and re-read the acceptance criterion
literally.

---

## 7. Security

### Platform

- Passwords salted and hashed; never logged or returned.
- Opaque session tokens in an `HttpOnly`, `SameSite=Strict` cookie, `Secure` in
  production. Revoked on sign-out.
- Role and course membership re-checked on every request, before any read.
- Uniform `404` so permission and existence are indistinguishable.
- User text escaped on render; a `<script>` in a post is displayed, not run.
- Uploads validated for real PDF structure before storage; encrypted and
  malformed files rejected with safe messages.
- PDF parsing is bounded and non-executing, in a separate worker with a
  10-second timeout and page, per-page and total text limits.
- Downloads re-authorize on every request. No public or signed object URLs, so
  a stale link cannot outlive a revoked enrolment.
- Secrets come only from the environment; `AppConfig` refuses to start a
  production server without a database URL and a 32-character session secret.
- CI fails the build if a server-only value appears in the client bundle, or if
  a `.env` file is ever tracked.
- Deployed demo accounts are created only when an operator supplies a password;
  no credential in this repository can sign in to a deployment.

### AI-specific

Organised around the attack surfaces taught in the course's agent-security
material: direct injection, obfuscation, best-of-N, indirect and RAG poisoning.

| Control | Where | What it stops |
| --- | --- | --- |
| Authorization before anything | `PlatformService.studentAiHelp` | Cross-course retrieval; a staff member using the student path. No gateway call is made for a refused request |
| Deterministic screen, authoritative | `StudentAnswerPolicy.screen` | Jailbreaks and submission demands. It can only tighten the model's classification, never loosen it |
| Unicode folding and compact matching | same | Obfuscated payloads: spaced, punctuated, zero-width-smuggled |
| Classifier sees only the question | `StudentAiService.classify` | Indirect injection changing the answer scope |
| Fail closed | `parseLabel`, failure paths | A manipulated, malformed or failed classification yields the stricter scope |
| Quoted, delimited source packets | `userPrompt` | PDF text and filenames acting as instructions |
| Map text never sent to the gateway | retrieval scoring | A staff-published artefact as an injection vector |
| Per-claim citation validation | `everyClaimCited` | Fabrication beside a real citation |
| Packet-bound citation checking | `validate` | Invented sources, cross-course filenames, out-of-range pages |
| Post-generation disclosure filter | `leaksPolicy` | Prompt and policy leakage |
| Refusals never retrieve | `StudentAiService.answer` | A successful jailbreak producing a *grounded* solution |
| Rate limit, 5/min | `enforceStudentAiLimit` | Best-of-N jailbreak sweeps; runaway spend |
| Bounded packets and timeouts | throughout | Resource exhaustion |
| Human approval gate | Knowledge Map review | Unreviewed AI output reaching students |
| Neutral failure copy | `providerFailure` | Disclosure of provider, model or configuration through errors |

Every row above has at least one automated test. The suites are the security
evidence, not prose: `StudentAiHelpTest` alone covers authorization, scope
resolution, obfuscated payloads, injection in page text, filenames and approved
maps, citation grounding, map eligibility, provider failures, validation and
rate limiting.

**What is not claimed.** Prompt injection is not solved. The screen is
deliberately over-inclusive and will refuse some legitimate questions. Two
narrow gaps are known and recorded in
`workflow/features/student-ai-help/tasks.md`: a fact phrased as a rhetorical
question, and one ending in a colon, are exempt from per-claim citation.

---

## 8. The `workflow/` folder

| Path | What it is |
| --- | --- |
| `project-overview.md` | Shared product context: what the product is and the constraints it must respect |
| `development-process.md` | The lightweight process: artefacts, owners, lifecycle, handoff convention, agent selection |
| `implementation_plan.md` | Staged delivery plan, ownership, per-stage gates, and an evidence-based status table |
| `features/<name>/` | Per-feature artefact chain (section 5). Features: `product-foundation`, `shared-course-forum`, `staff-material-governance`, `pdf-parsing-provenance`, `per-material-knowledge-maps`, `soclaas-model-fallback`, `ai-rag-release`, `student-ai-help`, `deployment` |
| `features/student-ai-help/answer-guidelines.md` | The versioned, approved statement of what the student assistant may say |
| `specs/` | Normative decision records adopted by a feature spec: `student-ai-help`, `per-material-knowledge-maps`, `pdf-parsing-provenance`, `soclaas-model-fallback` |
| `security/` | Nine standalone security reviews of specific AI and parsing decisions |
| `soc-llm-information.md`, `soc-llm-quickstart.md`, `soc-llm-api-reference.md` | Captured reference for the SoC LLM gateway |

---

## 9. Testing

```sh
sh scripts/test.sh
```

Four suites, no test framework — each is a `main` with assertions, run in
sequence.

| Suite | Covers |
| --- | --- |
| `PlatformIntegrationTest` | Authentication, role and course authorization, forum CRUD boundaries, material lifecycle, PDF validation including malformed and encrypted files, Knowledge Map lifecycle and review, queue limits |
| `StudentAiHelpTest` | 15 scenario groups over the student AI acceptance criteria |
| `SoCLaaSClientTest` | The gateway adapter against an injected `HttpClient`: request construction, every status class, malformed and starved responses, timeouts, the bounded fallback, both role paths |
| `StaticSiteTest` | Static serving and SPA fallback |

`SoCLaaSClientTest` was rewritten to inject an `HttpClient` rather than bind a
local listener, so it runs in sandboxes that forbid loopback binding. Only
`StaticSiteTest` still needs a listener, so it runs last and the others report
first where that is blocked.

GitHub Actions runs the whole suite plus a client type-check and build on every
push and pull request, and additionally fails the build if a secret appears in
the bundle or a `.env` file is tracked.

**Not covered.** No live end-to-end test runs in CI: the gateway needs a real
key, so the adapter is verified against a stub and the live path was verified
by hand. Multi-user behaviour on the deployment was likewise checked manually.

---

## 10. Deployment, and development/production separation

Production is a single container on Render, built from `Dockerfile`, serving
the API and client on one origin behind the platform's HTTPS edge, with managed
PostgreSQL. `render.yaml` declares both as infrastructure as code. Migrations
apply at startup before traffic is accepted. `/health` is the readiness probe.

Release is gated by GitHub Actions: tests run, then the workflow triggers the
host through a `DEPLOY_HOOK_URL` secret, then re-checks `/health` from outside.
The hook is host-agnostic deliberately, so changing provider does not change
the workflow. No Claude- or Codex-hosted build or hosting environment is used
at any point.

| | Development | Production |
| --- | --- | --- |
| Host | Developer machine | Render |
| Data | In-memory, or local Docker PostgreSQL | Managed PostgreSQL, private network only |
| Config | `.env`, gitignored | Platform-managed environment variables |
| Secrets | Developer's own | Separate values, set in the dashboard, never in the repo |
| Accounts | Seed accounts with a development password | Created only if an operator sets `TA_HELP_DESK_SEED_PASSWORD` |
| Client | Vite dev server or a local build | Built into the image |

The container accepts either the application's three database variables or a
platform `DATABASE_URL`, which the entrypoint translates, so the image is not
tied to one provider.

**Known constraints of the free tier**, stated rather than discovered: the
service sleeps after about 15 minutes idle and takes up to a minute to wake;
the free PostgreSQL expires 30 days after creation; free services share a
750 instance-hour monthly pool.

---

## 11. Acknowledgements

**Third-party software**

- [Apache PDFBox](https://pdfbox.apache.org/) 3.0.8 — PDF parsing and text
  extraction, pinned and fetched by `scripts/ensure-pdfbox.sh`
- [PostgreSQL JDBC Driver](https://jdbc.postgresql.org/) 42.7.8 — pinned and
  fetched by `scripts/ensure-jdbc.sh`
- [React](https://react.dev/) 19.1.1 and
  [React DOM](https://react.dev/) — client UI
- [Vite](https://vite.dev/) 7.1.3 and
  [TypeScript](https://www.typescriptlang.org/) 5.9.2 — client build
- [Eclipse Temurin](https://adoptium.net/) JDK/JRE 25 — container base images
- [PostgreSQL](https://www.postgresql.org/) — database

**Services**

- NUS School of Computing **SoCLaaS** LLM gateway, used for both AI features,
  via its OpenAI-compatible API. Models: Gemma 4 26B with a Qwen 3.5 9B
  fallback.
- **Render** — production hosting and managed PostgreSQL.
- **GitHub Actions** — CI and the gated release.

**Ideas and documentation**

- The CS3227 lecture material shaped the process and the threat model directly.
  Lecture 6 (Agent Safety & Security) supplied the attack taxonomy this
  system's AI controls are organised around — direct injection, obfuscation,
  best-of-N jailbreaking, indirect and RAG poisoning — and its mitigation list
  (delimiters separating instruction from input, filtering known dangerous
  phrasing, restricting input length and format, post-generation filtering,
  human oversight and approval gates) maps onto the controls in section 7.
  Lecture 7 (SDD and Specialized Agents) supplied the spec-anchored model, the
  "a good specification must contain" checklist used to review our specs, and
  the one-agent-per-task role split in section 6.
- The SoCLaaS reference material captured in `workflow/soc-llm-*.md` is the
  school's own documentation, retained for offline reference.

**Reuse from MP2**

The product *setting* follows MP2's brief — a multi-role application in a
formal setting. No MP2 source code, documentation or configuration was carried
into this repository; the implementation here was written from scratch, as the
commit history shows.

**AI assistance**

This project was built with AI agents as a required part of the assignment.
Specification, design, implementation, testing and audit artefacts under
`workflow/` were produced by the specialist agents described in section 6, with
human approval at the recorded gates. `logs/` summarises those interactions.
Commits authored with AI assistance carry a `Co-Authored-By` trailer.
