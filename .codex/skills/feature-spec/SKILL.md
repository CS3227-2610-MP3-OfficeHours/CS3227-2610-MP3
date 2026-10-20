---
name: feature-spec
description: Turn a proposed University TA Help Desk feature into an implementation-ready specification before implementation begins.
---

# Feature Specification

Use this skill when a feature is new, materially changing, or lacks clear
acceptance criteria. Do not begin implementation unless the user also requests
implementation.

Read the current planning and relevant specifications in `workflow/`. Write or
update one feature specification in `workflow/specs/`.

The specification must make these decisions testable:

- intended role(s), user goal, and explicit non-goals;
- server-enforced authorization and course/data scope;
- user-visible behavior, including loading, empty, validation-error,
  permission-denied, and service-failure states where relevant;
- data/API changes and backward-compatibility implications;
- AI behavior, grounding/citation requirements, and safe fallback when AI is
  involved;
- abuse cases and measurable acceptance criteria; and
- test evidence required before acceptance.

Identify ambiguity or decisions that require product-owner input. Do not invent
policy choices such as academic-integrity boundaries or access rules. Record
approved changes as updates to the same specification, with their impact on
implementation and tests.
