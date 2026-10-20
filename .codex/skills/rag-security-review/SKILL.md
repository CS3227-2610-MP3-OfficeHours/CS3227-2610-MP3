---
name: rag-security-review
description: Review course-material ingestion, retrieval, prompts, and AI output changes for authorization and prompt-injection risks.
---

# RAG Security Review

Use this skill for changes to material upload, PDF extraction, chunking,
embeddings, retrieval, prompts, AI responses, or SoC LLM configuration. It is a
review workflow; do not modify production behavior unless the user asks for a
fix.

Read the relevant feature specification, implementation, and existing security
artefacts. Write the review and test cases to `workflow/security/`.

Evaluate these trust boundaries:

- a requester is authorized for the course *before* retrieval, and retrieval
  returns only approved/current chunks for that course;
- PDFs, filenames, forum posts, generated consolidations, and user input are
  treated as untrusted data, never as instructions;
- citations identify original approved source material and page/section where
  the product promises them;
- uploads are bounded and safely processed, with provenance and review status;
- restricted-assessment policy has a defined, testable response rather than a
  prompt-only promise; and
- quota, timeout, retry, error, audit-log, and human-approval behavior cannot
  expose sensitive information or silently bypass policy.

Include direct jailbreak, indirect-injection, cross-course retrieval,
unapproved/stale-source, and malformed-upload negative tests when applicable.
State residual risk plainly; never claim prompt injection is fully solved.
