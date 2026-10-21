# SoCLaaS model fallback — independent test report

**Verdict: Needs changes**

## Executed checks

- Final `sh scripts/test.sh` — **PASS** (server compilation, `PlatformIntegrationTest`, and `SoCLaaSClientTest`). The integration test emits a PDFBox parser stack trace for its malformed-PDF case, then completes with `Platform integration tests: PASS`; the client suite completes with `SoCLaaS client tests: PASS`.
- Tried `pnpm run build` in `src/frontend` — **not completed**. pnpm attempted to fetch dependencies from `registry.npmjs.org`; name resolution failed (`ENOTFOUND`). The run was stopped after retries began. `npm` was unavailable.
- Live authenticated SoCLaaS catalog validation — **not run** because this workspace has no deployment staff API key. The mock catalog tests do not establish live deployment catalog availability.

## Acceptance coverage

| Criterion | Evidence and result |
| --- | --- |
| 1. Startup enables fallback only when the exact configured pair is present; invalid IDs/catalog failure do not claim fallback. | **Pass for mock configuration.** `SoCLaaSClientTest` validates the selected pair, rejects a missing fallback ID, missing primary ID, 503 catalog response, and malformed catalog JSON, and accepts a validated primary-only client. Live deployment catalog availability remains unverified because no staff key is configured here. |
| 2. Transient primary failures call primary then fallback once with the same request snapshot; fallback success follows normal validation. | **Partial.** Client tests prove ordered calls for 502/503, I/O disconnect, and timeout; compare serialized bodies after replacing the model ID; and observe fallback success. Normal map validation is tested separately through the platform generator seam, but the fallback client result is not passed through that lifecycle in one integration test. |
| 3. 429, 401/403, 400, malformed/empty response, and source-validation failure each stop after one call. | **Partial.** With fallback configured, client tests assert one call for 429, 401, 403, 400, malformed JSON, and empty content. The platform integration tests demonstrate citation validation and approval denial, but do not assert provider call count for a citation-validation failure using the configured fallback client. |
| 4. Both transient attempts failing preserve candidate and approved maps, including per-material lifecycle. | **Pass across the tested boundaries.** `SoCLaaSClientTest` asserts two transient failures stop after exactly two calls and return temporary-unavailability. `PlatformIntegrationTest` separately asserts temporary-unavailability retains an existing candidate, then retains an approved map for that material. |
| 5. Invalid, foreign, stale, or missing fallback citations remain unverified and cannot be approved. | **Partial.** `PlatformIntegrationTest` now demonstrates missing, foreign, and out-of-range page citations remain unverified and cannot be approved. The cases use the platform generator seam rather than output produced by the configured fallback client, so the full fallback-to-review path is not exercised end to end. |
| 6. No provider IDs/catalog/errors/source/credentials leak; authorized staff see fixed disclosure and students/non-members cannot access new status. | **Partial.** Inspection confirms the response adds only `fallbackConfigured`, the UI uses the fixed disclosure conditionally, provider errors map to generic messages, and the existing staff-only Knowledge Maps route calls `requireCourseStaff`. Integration tests cover an authorized staff capability query and student/cross-course generation denials. No HTTP-level disclosure/response-secret checks or non-member access test for the new status field were run. |
| 7. Server suite and frontend typecheck/build pass. | **Server: PASS** as above. **Frontend: unverified** because dependency fetch failed with `ENOTFOUND`. |

## Blocking evidence gaps

The current Java suites pass, but criteria 2, 3, 5, 6, and 7 remain partially demonstrated. Add an integration test that exercises a successful fallback result through normal map citation validation, and checks that source-validation rejection does not trigger another provider call; add HTTP-level checks for capability authorization and absence of provider diagnostics/secrets. Rerun the frontend build where dependencies are available. No live-key behavior can be confirmed in this workspace.
