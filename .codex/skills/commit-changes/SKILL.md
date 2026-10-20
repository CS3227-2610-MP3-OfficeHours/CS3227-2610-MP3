---
name: commit-changes
description: "Prepare focused, reviewable Git commits with consistent messages; use whenever an AI stages or commits repository changes."
---

# Commit Changes

Use this skill for every AI-authored Git commit in this repository, including
when the user asks to “commit”, “commit and push”, or requests a commit as part
of another change. This skill does not authorize a commit or push: obtain the
user's explicit authorization for each external Git mutation as required.

## Review scope before staging

1. Inspect `git status --short`, unstaged changes, and staged changes. Preserve
   unrelated, pre-existing, and user-authored changes.
2. Group changes by one independently reviewable purpose. A good commit can be
   understood, tested, and reverted without relying on another commit in the
   same change set.
3. Recommend a split before staging when changes mix independent features,
   unrelated fixes, generated/format-only churn with behavior changes, or
   distinct documentation/process work with implementation. State the proposed
   commit groups and ask for direction if the requested scope is ambiguous.
4. Do not split a cohesive implementation merely because it touches several
   layers (for example, one feature's API, UI, and tests).

Stage only the explicitly selected paths; avoid broad staging commands that can
capture unrelated work. Inspect the staged diff and run `git diff --cached
--check`. Run relevant tests when practical, and record tests that were not run.

## Commit message standard

Every AI-authored commit uses this format:

```text
<type>: <concise imperative outcome>

Changes:
- <material change or scope>

Verification:
- <test/check result, or "Not run: <reason>">
```

Use one of these types: `feat`, `fix`, `docs`, `test`, `refactor`, `security`,
`build`, or `chore`. Keep the subject specific, imperative, and no longer than
72 characters. The body explains *what changed* and *how it was verified*;
omit redundant detail rather than repeating the diff. Do not claim tests passed
unless their results were observed.

## Completion

After committing, report the commit hash, subject, files included, verification
status, and any remaining unstaged/untracked changes. Push only when explicitly
requested, and report its result separately.
