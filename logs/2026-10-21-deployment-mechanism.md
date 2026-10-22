# 21 October — deployment mechanism (Stage 6, first pass)

**Kind: reconstructed.** This work ran in a separate agent session. The
orchestrator received its reports but did not observe the session directly, so
this summarises the reports and the three commits they produced.

**Owner:** @ziyang. Branch `claude/determined-shannon-362118`, merged via PR #1.

---

## Brief

Deliver Stage 6 of `workflow/implementation_plan.md`: a team-managed
deployment with development and production separated, automated through
GitHub Actions, using no Claude- or Codex-hosted build or hosting environment.

## Delivered

**Single-origin static serving** (`StaticSite`, `StaticSiteTest`). The Java
server now serves the built React client alongside the API, so production is
one container and one origin with no CORS surface.

**Container and platform config.** A multi-stage `Dockerfile` — Node builds
the client, a JDK image compiles the server, a JRE image runs it as an
unprivileged user — plus `scripts/docker-entrypoint.sh`, which translates the
platform's injected `PORT` and refuses to start a half-configured production.
`railway.json` declared the original target.

**CI and release workflow** (`.github/workflows/deploy.yml`): a quality gate
running `scripts/test.sh` plus a client type-check and build on every push and
pull request, then a deploy job gated to `master` pushes. Two security checks
were added to the gate: the build fails if a server-only value appears in the
client bundle, or if a `.env` file is ever tracked.

**Artefacts**: `workflow/features/deployment/spec.md` and `tasks.md`.

## Outcome and honesty about it

The session reported the mechanism built and locally verified, and
**explicitly reported that nothing was deployed**: the Railway CLI in its
environment was unauthenticated and Docker was unavailable, so every live
acceptance criterion was unverified. It stopped and asked rather than claiming
Stage 6 complete.

PR #1 ran the quality gate on a Linux runner — the first execution of
`scripts/test.sh` outside a Windows sandbox — and it passed in 41 seconds, with
the deploy job correctly skipped for a `pull_request` event.

## Note for the record

The branch was cut from `b98e95f` and therefore predates the student AI
feature. The CI run on PR #1 exercised `PlatformIntegrationTest`,
`SoCLaaSClientTest` and `StaticSiteTest`, but not `StudentAiHelpTest`, which
did not yet exist on that branch. That distinction mattered later: it meant the
green PR #1 run was not evidence for the student AI work.
