# Stage 3 AI/RAG Foundation Security Review

Status: Engineering security review complete — awaiting independent testing, 2026-10-21
Scope: Staff-only Knowledge Map adapter, source packaging, lifecycle, persistence, and API boundary.

## Findings

- The adapter is server-side code and reads only `SOCLAAS_STAFF_API_KEY`; no
  HTTP route or browser bundle receives the secret.
- It frames source material as untrusted data in a fixed system instruction.
  This reduces prompt-injection risk but does not solve prompt injection; map
  output must still be validated against server-side provenance before use.
- 401/403 map to a configuration failure, 429 to a neutral rate-limited state,
  and 5xx/network interruption to a temporary failure. No fallback provider or
  fabricated result exists.
- The server checks current course-staff membership before listing map state,
  selecting source PDFs, sending source data, or approving/rejecting a map.
  Students and staff from another course receive the same hidden response.
- A map source snapshot contains every active PDF in the authorized course;
  upload/delete/retry events immediately mark a map stale. A source change
  during a gateway call discards the returned map rather than approving it.
- PDF bytes and extracted literals are packaged only as quoted untrusted data.
  The server's fixed policy remains outside that data and no source text can
  self-approve a map or choose its course.
- Map output is withheld in `failed` unless it cites each snapshot PDF with an
  in-range page reference. Staff must explicitly approve `pending_review`.
- A four-slot local semaphore bounds queued/in-flight manual map requests; the
  provider adapter maps 429, 5xx/network timeout, credential, and malformed
  outputs to safe non-answer states. No fallback provider exists.
- No student AI or student Knowledge Map route has been introduced.

## Remaining release hardening

1. Replace the bounded literal-text extractor with a mature page-preserving PDF
   parser before student retrieval/release; current page provenance validates
   model references against the PDF page range but does not reconstruct page
   boundaries from arbitrary PDFs.
2. Add direct-jailbreak, indirect PDF-injection, stale-generation race,
   malformed-output, and provider-failure fixtures to the independent suite.
3. Add structured, secret-safe operational events and production deployment
   verification as required by the release specification.

Residual risk: a model can still produce misleading text. The approved map is
navigation context only; original PDF/page citations remain the source of truth.
