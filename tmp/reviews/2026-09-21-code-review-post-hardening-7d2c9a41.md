# Code Review Report

## Report Contract

- Report type: `code-review`
- Report ID: `cr-20260921-7d2c9a41`
- Review chain ID: `rc-20260921-7d2c9a41`
- Review generation: `0`
- Review trigger: `initial`
- Parent review report ID: `None`
- Parent review report path: `None`
- Parent resolution ID: `None`
- Parent resolution path: `None`
- Generated at: `2026-09-21T04:10:00+09:00`
- Report path: `~/Desktop/File Manager-worktrees/omnifile-saf-transfer-v1/tmp/reviews/2026-09-21-code-review-post-hardening-7d2c9a41.md`
- Source skill: `code-review`
- Status: `Review complete`
- Git mutation during review: `None`
- Scope fingerprint: `ifp-sha256:4f0e62cc07f9b8f4fd8003c0b49f7b182fcefba36d583a97a66748937a2ca78a`

## Scope

- Review date: `2026-09-21`
- Scope kind: `commit range plus working tree`
- Scope description: `Published VS03 through the current VS04 working tree, with primary attention on the post-runtime hardening delta that resolves the prior provider-disappearance and equal-depth restoration findings.`
- Scope mode: `full frozen scope`
- Baseline: `a95b3e28f47954b879b389ce6d5a5f17d4704407`
- Target: `development/core-v1-saf-transfer-v1 working tree; HEAD 293b48e91dec057387127d358392210363e2b55f plus uncommitted hardening`
- Changed paths: `VS04 storage/operations/UI/tests/docs and prior committed VS04 implementation`
- Diff size: `git diff --check passed; current uncommitted delta is 11 modified paths plus 1 new host test`
- Completion: `Complete within reviewed scope; final locked-device Compose rerun is an evidence caveat, not an unresolved code finding`
- Requirements consulted: `VS04 campaign, SAF durability invariants, architecture ADRs, debug, android-intent-security, code-review, regression-review requirements`
- Prior resolution consulted: `None`
- Assumptions: `SAF→Local Copy remains the only claimed SAF production route; SAF destination, SAF Move, and SAF→SAF remain Unsupported.`
- Excluded as unrelated: `VS05, directory/archive/background/cloud/root work`

## Review Orchestration

- Assessment subagent: `Coordinator assessment - cohesive hardening delta with independent SAF identity, durable-state, and UI checks; prior read-only audits supplied bounded evidence`
- Orchestration decision: `Single reviewer`
- Decision confidence: `high`
- Decision rationale: `The post-hardening delta is cohesive around two prior findings and one Move recovery invariant; one coordinated trace avoids reopening unrelated VS04 discovery while covering all affected callers.`
- Coordinator override: `None`
- Context or tool limits: `The Pixel was available for controlled instrumentation but locked/dreaming for Compose/UI interaction; no lock bypass was attempted.`

### Risk Dimensions

- `Provider error classification and distinction between unavailable, permission denied, true NotFound, and stale identity.`
- `Durable finalization/restart and destructive Move ordering under provider outage.`
- `Persisted SAF selection intent, exact grant validation, and equal-depth fallback.`
- `User-visible route gating and final evidence truthfulness.`

### Reviewer Assignments

| Reviewer | Angle | Owned surfaces | Mandatory cross-checks | Status |
| --- | --- | --- | --- | --- |
| `R1` | `coordinator correctness/security/reliability` | `SafStorageProvider.kt, SafTreeGrantStore.kt, OperationManager.kt, OperationStateMachine.kt, StorageModel.kt` | `controlled provider, NotFound/permission/stale mapping, Move pending, no duplicate replay` | `Complete` |
| `R2` | `coordinator UI/integration/evidence` | `MainActivity.kt, FilesScreen.kt, tests, production doc, runtime outputs` | `grant marker flow, capability gates, user-visible errors, locked-device limitation` | `Complete` |

### Synthesis Statement

`The coordinator independently re-read each prior F1/F2 path and the current hardening delta. Provider loss now remains ProviderUnavailable through containment/query boundaries, true missing in-tree documents are NotFound in the controlled harness, equal-depth selection uses an explicit validated marker and safe tie behavior, and SOURCE_DELETE_PENDING recovery is covered by host tests. No unresolved Blocker/Major/Minor code finding remains. The locked Pixel prevents only final Compose/UI interaction evidence for the latest APK.`

## Review Snapshot

- Recommendation: `Pass with caveat`
- Completion: `Complete within reviewed scope; final locked-device Compose/UI rerun remains a physical evidence caveat`
- Why now: `The two prior approval findings are closed by typed error propagation, explicit selected-tree persistence, deterministic fallback, and regression coverage.`
- Must-review now: `No unresolved code findings; see the verification caveat in Evidence Appendix.`
- Findings count: `Blocker 0 | Major 0 | Minor 0 | Question 0`
- Standalone test gaps: `Blocker 0 | Major 0 | Minor 1`
- Coverage confidence: `high for code/controlled runtime; medium for final locked-device UI`
- Biggest blind spot: `Final APK Compose rendering after the last UI-only message-mapping change could not be exercised while the Pixel was locked.`

## Complete Findings Index

No code-review findings identified in the reviewed scope. One non-blocking physical verification gap is recorded below.

## Blocker

None.

## Major

None.

## Minor

None.

## Questions

None.

## Test Gaps

| ID | Severity | Surface | Missing coverage | Risk | Origin | Evidence | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `T1` | `Minor` | `final APK Compose/provider-error rendering` | `The latest APK's Compose UI could not be rerun because the Pixel was locked/dreaming; controlled provider and host coverage passed.` | `A final UI-only text rendering mismatch could remain unobserved, without affecting storage safety or route claims.` | `Coordinator` | `Pixel reported mDreamingLockscreen=true; direct Compose class 4/4 was blocked by no hierarchy; no unlock/bypass was attempted.` | `test-gap; entry=final APK Compose error rendering; contract=provider-unavailable user text is rendered and tappable; gap=locked-device final UI run` | `ifp-sha256:bc217ed1f9dde430a4427b9d26c660e606f2ae8b62ea1d403e22857ff5390997` | `kind:requirement; strength:authoritative; evidence:VS04 runtime closure policy and UI gating requirements` |

## Review Coverage Ledger

| Area ID | Area / path | Touched files or entry points | Owner | Depth | Status | Result | Evidence / next step |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `A1` | `SAF containment and locator resolution` | `SafStorageProvider.kt, SafDurableLocatorCodec.kt` | `R1` | `deep trace + runtime` | `Reviewed - no issue found` | `ProviderUnavailable is distinct from stale/NotFound; locator query now establishes true absence before containment interpretation.` | `Controlled 17/17 final runtime; host tests.` |
| `A2` | `Provider error to durable operation state` | `StorageModel.kt, OperationModels.kt, OperationManager.kt` | `R1` | `deep trace + host` | `Reviewed - no issue found` | `ProviderUnavailable is retryable; finalization observation outages do not re-enter TRANSFERRING; Move source is not deleted.` | `69 host tests; outage and source-delete recovery tests.` |
| `A3` | `SOURCE_DELETE_PENDING recovery` | `OperationManager.kt, OperationStateMachine.kt` | `R1` | `deep trace + host` | `Reviewed - no issue found` | `Retryable SOURCE_DELETING resumes pending with explicit destination-completion evidence; no recopy/duplicate destination.` | `OperationManagerTest providerUnavailableDuringSourceDelete... PASS.` |
| `A4` | `Persisted selected tree and grant validation` | `SafTreeGrantStore.kt, MainActivity.kt` | `R1` | `deep trace + host` | `Reviewed - no issue found` | `Exact marker is validated against current readable platform grants; equal-depth ties do not depend on enumeration order; revoked marker falls back safely.` | `SafTreeGrantStoreTest 4/4; production grant flags unchanged.` |
| `A5` | `Picker/UI/error integration` | `MainActivity.kt, FilesScreen.kt, FilesViewModel.kt` | `R2` | `static + prior Pixel` | `Reviewed - no issue found` | `Source picker alone updates marker; destination picker does not; ProviderUnavailable has user-facing retry text; unsupported gates remain disabled.` | `Prior real Pixel Copy and current static trace; final UI rerun caveat T1.` |
| `A6` | `Controlled provider/test harness` | `TestDocumentsProvider.java, SafStorageProviderInstrumentedTest.kt` | `R1` | `runtime` | `Reviewed - no issue found` | `Provider outage, recovery, true missing, permission, stale, pipe, unknown-size, finalization/conflict coverage passes.` | `17/17 final class; 20/20 with SafTransferRuntime.` |
| `A7` | `Local/VS03 regression and route matrix` | `OperationManagerTest.kt, prior VS03 paths, production doc` | `R2` | `host + prior Pixel + static` | `Reviewed - no issue found` | `Local routes and SAF→Local Copy claim remain bounded; SAF destination/Move/SAF→SAF remain Unsupported.` | `69 host; prior real Pixel Copy; final gates unchanged.` |

## Subagent Candidate Adjudication

| Candidate ID | Proposed by | Decision | Final ID | Coordinator evidence | Reason |
| --- | --- | --- | --- | --- | --- |
| `Prior-F1` | `prior final review` | `resolved` | `None` | `SafStorageProvider typed ProviderUnavailable path and 17/17 runtime` | `Blanket containment false conversion removed.` |
| `Prior-F2` | `prior final review` | `resolved` | `None` | `selected-read-tree-uri-v1 marker plus 4 host tests` | `Equal-depth platform order no longer controls valid marker restore.` |
| `Candidate-duplicate-destination` | `Coordinator` | `dismissed` | `None` | `finishDestination interruption path and source-delete recovery count partialCreates=1` | `No recopy path remains after destination truth is durable.` |
| `Candidate-generic-finalization-trust` | `Coordinator` | `dismissed` | `None` | `finalizationProven=false and SAF destination UI gate unchanged` | `No broad trust was introduced.` |

## Evidence Appendix

### Diff Inventory

| File or area | Classification | Semantic review area considered |
| --- | --- | --- |
| `SafStorageProvider.kt` | `surface` | `provider classification, containment, locator resolution` |
| `SafTreeGrantStore.kt` | `surface` | `selected-tree intent and grant validation` |
| `OperationManager.kt, OperationStateMachine.kt` | `surface` | `retry, finalization, source deletion ordering` |
| `StorageModel.kt, OperationModels.kt` | `contract` | `durable error taxonomy and compatibility` |
| `MainActivity.kt, FilesScreen.kt` | `surface` | `picker roles and user-visible error/gating` |
| `TestDocumentsProvider.java, tests` | `test-only` | `controlled runtime evidence` |
| `VERTICAL_SLICE_04_SAF_DURABLE_TRANSFER.md` | `docs-only` | `supported claim accuracy` |

### Verification Commands

- `:app:testDebugUnitTest` -> `69 tests; 0 failures; 0 errors; 0 skipped`
- `:app:lintDebug` -> `PASS; 0 errors; warnings retained from baseline/toolchain`
- `:app:assembleDebug` -> `PASS; strict dependency verification`
- `:app:assembleDebugAndroidTest` -> `PASS; strict dependency verification`
- final controlled direct runner -> `17/17 OK`
- final controlled + SafTransferRuntime direct runner -> `20/20 OK`
- earlier full final-ish direct runner -> `31 total; 27 passed; 4 Compose failures because device was locked/dreaming`
- current device state -> `mDreamingLockscreen=true; no unlock/bypass attempted`
- host Move outage recovery -> `destination retained, source retained until deletion recovery, partialCreates=1`

### Dismissed Coordinator Candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| `Enable SAF destination because controlled provider passes` | `dismissed` | `finalizationProven=false remains false and SAF destination action remains disabled` |
| `Enable SAF Move because delete flag exists` | `dismissed` | `sourceVersionProven=false remains false and Move requires MOVE_SOURCE` |
| `Treat provider outage as NotFound because query can be empty` | `dismissed` | `ProviderUnavailable typed path; empty in-tree query is covered as true NotFound separately` |

## Prior Resolution Reconciliation

- `F1 provider disappearance classification`: `resolved by adding StorageError.ProviderUnavailable, preserving it through SAF containment/query and mapping it to retryable durable state.`
- `F2 equal-depth tree restoration`: `resolved by selected-read-tree-uri-v1, current-grant validation, unique shallowest fallback, and no auto-selection on equal-depth ties.`
- `Prior T1 physical revocation gap`: `remains a non-blocking physical evidence caveat; deterministic controlled provider coverage is green.`

## Receiving Handoff

- Handoff status: `Ready for receiving-code-review`
- Automatic receiving permitted: `No`
- Source report ID: `cr-20260921-7d2c9a41`
- Scope fingerprint to recheck: `ifp-sha256:4f0e62cc07f9b8f4fd8003c0b49f7b182fcefba36d583a97a66748937a2ca78a`
- Actionable finding IDs: `None`
- Deferred finding IDs: `None`
- Actionable test-gap IDs: `T1`
- Deferred test-gap IDs: `None`
- Open question IDs: `None`
- Open coverage area IDs: `None`
- Highest-risk verification to repeat: `Unlock only under normal user control, then rerun final Compose/UI and real SAF picker evidence; do not bypass security.`
- Suggested implementation boundaries: `No further code change required by this review; preserve all Unsupported gates.`
- Re-review note: `Generation 1 is available after any further implementation change.`
- Chain rule: `Generation 0 may be consumed only by an explicit receiving-code-review resolution.`

## Report Self-Check

- `yes` Assessment mode and rationale recorded.
- `yes` Changed review-relevant areas mapped in coverage ledger.
- `yes` No unresolved F/T code finding is omitted from the index/cards/ledger.
- `yes` Prior F1/F2 dispositions are reconciled with current code/evidence.
- `yes` Recommendation follows the mapping: no code findings, one Minor physical test gap -> Pass with caveat.
- `yes` Git state was not mutated during review.
