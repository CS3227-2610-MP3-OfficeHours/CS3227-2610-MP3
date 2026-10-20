# Project Overview

## Product

The planned product is a University TA / assignment-help desk: a course-scoped
forum where staff create courses and manage materials, while enrolled students
ask and answer questions. Staff and students are distinct roles.

Students use an AI help flow that retrieves authorized course material and
returns hints, explanations, or clarifying questions with references to the
original material. Staff use an AI material-consolidation flow to organize
uploaded lecture PDFs into reviewable course knowledge artefacts.

## Product constraints

- Each course's data and material must remain accessible only to authorized
  members.
- AI help is grounded in approved, current source material; generated summaries
  are not the sole source of truth.
- AI output must not be automatically published as a forum response.
- Uploaded documents and all retrieved/user-authored content are untrusted data,
  including possible indirect prompt injections.
- The project must use the SoC LLM service and handle its rate limits and
  failures safely.

## Workflow context

`workflow/implementation_plan.md` is the current high-level task breakdown and
ownership plan. Feature-specific, approved requirements and evidence live under
`workflow/features/<feature-name>/` once feature work starts. This overview is a
shared context document, not a substitute for a feature specification.
