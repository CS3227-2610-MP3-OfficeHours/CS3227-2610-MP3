# PDF parsing/provenance plan — security review

Status: Approved by the product owner, 2026-10-21; implementation verification pending

The proposed PDFBox-based page extractor preserves the current course/role
authorization boundary and improves provenance reliability. It does not
authorize direct PDF submission to an LLM.

Required negative tests after approval:

- PDF text or metadata containing indirect prompt-injection instructions;
- a decompression bomb, malformed xref/object stream, encrypted PDF, and a
  document exceeding parse-time/text limits;
- an image-only PDF and a mixed text/image PDF;
- deletion/replacement while extraction or map generation is in flight;
- cross-course material/page IDs and forged citation data;
- stale/unapproved map use and an LLM citation to an inactive or wrong page.

Residual risk: PDF parsing is complex and extraction quality cannot establish
semantic correctness. The implementation must retain bounded resources,
server-side authorization, untrusted-text framing, and human review; it must
not treat parser success as proof that an AI-generated claim is correct.
