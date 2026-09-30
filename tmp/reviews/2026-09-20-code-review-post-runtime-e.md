# Code Review Report

## Report Contract

- Report type: `code-review`
- Report ID: `cr-20260920-postruntimee`
- Review chain ID: `rc-20260920-postruntimee`
- Review generation: `0`
- Review trigger: `initial`
- Parent review report ID: `None`
- Parent review report path: `None`
- Parent resolution ID: `None`
- Parent resolution path: `None`
- Generated at: `2026-09-20T23:20:00+09:00`
- Report path: `~/Desktop/File Manager-worktrees/omnifile-saf-transfer-v1/tmp/reviews/2026-09-20-code-review-post-runtime-e.md`
- Source skill: `code-review`
- Status: `Review complete`
- Git mutation during review: `None`
- Scope fingerprint: `Unavailable - runtime evidence was supplied in-thread`

## Scope

- Review date: `2026-09-20`
- Scope kind: `working tree plus runtime evidence`
- Scope description: `Post-runtime Review E of Files, destination picker, Operations presentation, grant flow, and user-visible regression surfaces.`
- Scope mode: `full frozen scope`
- Baseline: `a95b3e28f47954b879b389ce6d5a5f17d4704407`
- Target: `d6a82896b074cfbf2a5f958c086e462906157d0 plus uncommitted TestDocumentsProvider.java fix`
- Changed paths: `MainActivity.kt, SafTreeGrantStore.kt, FilesViewModel.kt, FilesScreen.kt, OperationsPanel.kt, SAF provider and test harness`
- Diff size: `reviewed by targeted trace`
- Completion: `Complete within reviewed scope; real destination routes remain intentionally disabled`
- Requirements consulted: `VS04 picker/grant/UI gating requirements, android-intent-security review, Pixel runtime evidence`
- Prior resolution consulted: `None`
- Assumptions: `Only disposable Pixel files/tree were used.`
- Excluded as unrelated: `unrelated UI redesign, VS05`

## Review Orchestration

- Assessment subagent: `Coordinator assessment - UI and grant paths were reviewed independently from durable engine paths`
- Orchestration decision: `Parallel specialists`
- Decision confidence: `high`
- Decision rationale: `Picker trust boundaries and retry/UI behavior have distinct user-visible failure modes.`
- Coordinator override: `None`
- Context or tool limits: `Destination error Retry and grant revocation were not exercised on Pixel.`

### Risk Dimensions

- `Picker result flags and persisted grant scope.`
- `Destination capability gating when finalization is unproven.`
- `Retry/Back/cancel behavior after provider failure.`
- `Operations state truth and Local regression.`

### Reviewer Assignments

| Reviewer | Angle | Owned surfaces | Mandatory cross-checks | Status |
| --- | --- | --- | --- | --- |
| `R1` | `picker/grant/security` | `MainActivity.kt, SafTreeGrantStore.kt, SafStorageProvider.kt` | `tree containment, flags, provider authority` | `Complete` |
| `R2` | `UI/retry/regression` | `FilesViewModel.kt, FilesScreen.kt, OperationsPanel.kt` | `Back, cancel, unsupported gate, Local behavior` | `Complete` |

### Synthesis Statement

`The coordinator independently checked the candidates against code and Pixel UI/XML/runtime evidence. The destination finalization gate is intentional; the Retry path needs correction.`

## Review Snapshot

- Recommendation: `Changes requested`
- Completion: `Complete within reviewed scope`
- Why now: `Pixel proves grant acquisition, SAF browsing, and SAF→Local terminal UI; destination retry and revocation paths remain actionable gaps.`
- Must-review now:
  1. `F1` Destination error Retry reloads source navigation
  2. `F2` Revoked/provider-unavailable grant classification is stale rather than permission/retry
- Findings count: `Blocker 0 | Major 2 | Minor 0 | Question 0`
- Standalone test gaps: `Blocker 0 | Major 3 | Minor 0`
- Coverage confidence: `medium`
- Biggest blind spot: `Destination provider failure/retry and grant revocation after restart`

## Complete Findings Index

| ID | Severity | Surface | Review risk | Confidence | Origin | Verification | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `F1` | `Major` | `FilesViewModel.retry` | `Destination failure Retry reloads source content instead of failed destination` | `high` | `R2` | `static trace` | `behavior; entry=destination provider error retry; contract=Retry reloads failed destination location; effect=source view/stale pending transfer after Retry` | `ifp-sha256:413775106c102f5635b01a1193217aef21aea6dfcc125fa533d1ff38dc93e558` | `kind:hard-invariant; strength:authoritative; evidence:destination picker recovery requirements` |
| `F2` | `Major` | `SafStorageProvider.isWithinSelectedTree` | `SecurityException is collapsed into StaleReference and can lose permission/retry classification` | `medium` | `R1` | `static trace; no revocation runtime` | `behavior; entry=revoked SAF grant; contract=permission loss is durable/recoverable and not stale data; effect=terminal stale failure or crash instead of permission retry` | `ifp-sha256:8a2ecbb9c12232491a8ffe17cfa8f58c6e6b3e5c6ab2ff6446008b05b5799dbf` | `kind:hard-invariant; strength:authoritative; evidence:grant revocation requirements` |

## Blocker

None.

## Major

### F1 Major - Destination Retry must reload destination navigation

Impact: `After a SAF/provider destination error, Retry can take the user back to source content while the pending transfer remains active.`

Review reason: `The destination picker must recover the failed destination location, not silently change the active browsing context.`

Surface: `FilesViewModel.retry and destination error states`
Issue key: `behavior; entry=destination provider error retry; contract=Retry reloads failed destination location; effect=source view/stale pending transfer after Retry`
Issue fingerprint: `ifp-sha256:413775106c102f5635b01a1193217aef21aea6dfcc125fa533d1ff38dc93e558`
Expected basis: `kind:hard-invariant; strength:authoritative; evidence:destination picker recovery requirements`
Confidence: `medium`
Origin: `R2`
Coordinator verification: `Independent code-path trace and supplied Pixel evidence.`

Look here first:
- [`FilesViewModel.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/files/FilesViewModel.kt#L234)
- [`FilesViewModel.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/files/FilesViewModel.kt#L326)

Failure mode:
- Expected: `Retry calls loadDestinationRoot or loadDestinationDirectory using preserved destination state.`
- Current: `retry() always uses source navigation when navigation is non-empty.`

Reviewer action: `request fix and destination error/retry test`

### F2 Major - Revoked SAF grants must remain permission-classified

Impact: `A revoked or unavailable grant can be reported as stale instead of permission denied/retryable, making regrant recovery opaque.`

Review reason: `The provider adapter catches every containment exception and returns false, losing the distinction between out-of-tree identity and missing permission.`

Surface: `SafStorageProvider.isWithinSelectedTree, locator resolution`
Issue key: `behavior; entry=revoked SAF grant; contract=permission loss is durable/recoverable and not stale data; effect=terminal stale failure or crash instead of permission retry`
Issue fingerprint: `ifp-sha256:8a2ecbb9c12232491a8ffe17cfa8f58c6e6b3e5c6ab2ff6446008b05b5799dbf`
Expected basis: `kind:hard-invariant; strength:authoritative; evidence:grant revocation requirements`
Confidence: `medium`
Origin: `R1`
Coordinator verification: `Independent code-path trace and supplied Pixel evidence.`

Look here first:
- [`SafStorageProvider.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/storage/SafStorageProvider.kt#L566)
- [`OperationManager.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/operations/OperationManager.kt#L387)

Failure mode:
- Expected: `Missing/revoked access maps to PermissionDenied or an explicit retryable interruption without source deletion.`
- Current: `SecurityException during containment becomes false, then StaleReference/NOT_FOUND.`

Reviewer action: `request permission-classification fix and deterministic revocation test`

## Minor

None.

## Questions

None.

## Test Gaps

| ID | Severity | Surface | Missing coverage | Risk | Origin | Evidence | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `T1` | `Major` | `destination provider error Retry` | `No UI/runtime test exercises destination listing failure followed by Retry` | `User can be stranded in a stale source view` | `R2` | `static-only` | `test-gap; entry=destination Retry; contract=failed destination reloads; gap=runtime retry path` | `ifp-sha256:37653d6ae4637c6869f1b5133388e066c0f027341e8fbc275d2499f962e9c276` | `kind:requirement; strength:authoritative; evidence:picker recovery requirements` |
| `T2` | `Major` | `grant revocation` | `No test covers grant revoked before execution or after process recreation` | `Permission loss can be misclassified or crash` | `R1` | `mode=0x3 persisted=0x3 proves acquisition only` | `test-gap; entry=grant revocation; contract=truthful permission/retry state; gap=revocation lifecycle` | `ifp-sha256:840e760cd3314b0335cfc7d67153494af47aff1cbca834f181cec74dbc26bfec` | `kind:requirement; strength:authoritative; evidence:grant revocation requirements` |
| `T3` | `Minor` | `Operations error states` | `No real Pixel rendering test for permission revoked/provider unavailable/source changed` | `User-visible error copy can drift from durable state` | `R2` | `static UI trace only` | `test-gap; entry=Operations SAF errors; contract=truthful terminal/intermediate presentation; gap=runtime error rendering` | `ifp-sha256:ec492f94ca9bca9c3431e10375fae57e67678fbabd3fbe5114fc10f5c527039e` | `kind:requirement; strength:authoritative; evidence:Operations UI requirements` |

## Review Coverage Ledger

| Area ID | Area / path | Touched files or entry points | Owner | Depth | Status | Result | Evidence / next step |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `A1` | `picker Intent/callback` | `MainActivity.kt` | `R1` | `runtime verified` | `Reviewed - no issue found` | `ACTION_OPEN_DOCUMENT_TREE, tree-only callback, read/write masking and persisted checks are correct.` | `Re-run missing/revoked grant case.` |
| `A2` | `persisted grants` | `SafTreeGrantStore.kt` | `R1` | `runtime verified` | `Reviewed - no issue found` | `Pixel diagnostics show mode=0x3 persisted=0x3 for root and destination, with provider authority scoped.` | `Exercise revocation.` |
| `A3` | `SAF containment` | `SafStorageProvider.kt` | `R1` | `contract trace` | `Finding F2` | `Authority/tree checks exist; exception classification needs refinement.` | `Add typed permission path.` |
| `A4` | `destination capability gate` | `FilesViewModel.kt, FilesScreen.kt, SafStorageProvider.kt` | `R2` | `runtime verified` | `Reviewed - no issue found` | `Use this folder is disabled on Pixel while finalizationProven=false.` | `Keep generic destination unsupported.` |
| `A5` | `destination Retry/Back` | `FilesViewModel.kt, FilesScreen.kt` | `R2` | `dependency trace` | `Finding F1` | `Back/cancel exists; retry chooses source navigation.` | `Fix and add UI test.` |
| `A6` | `Operations` | `OperationsPanel.kt, OperationsViewModel.kt` | `R2` | `runtime verified` | `Reviewed - no issue found` | `Real SAF→Local Copy/Move rows show COMPLETE with exact bytes.` | `Add error rendering runtime tests.` |
| `A7` | `Local regression` | `LocalStorageProvider.kt, FilesScreen.kt` | `R2` | `contract trace` | `Reviewed - no issue found` | `No Local regression observed in code path; Local output showed copied/moved files.` | `Fresh final full instrumentation.` |

## Subagent Candidate Adjudication

| Candidate ID | Proposed by | Decision | Final ID | Coordinator evidence | Reason |
| --- | --- | --- | --- | --- | --- |
| `R2-C1` | `R2` | `accepted` | `F1` | `retry()` path trace | `Destination error has no destination-aware retry branch.` |
| `R1-C1` | `R1` | `accepted` | `F2` | `isWithinSelectedTree` catch trace | `SecurityException is not preserved as permission.` |
| `R1-C2` | `R1` | `dismissed` | `None` | `mode/persisted diagnostics` | `Grant acquisition and picker flag policy were verified; revocation remains a gap, not a current acquisition defect.` |

## Evidence Appendix

### Diff Inventory

| File or area | Classification | Semantic review area considered |
| --- | --- | --- |
| `app/src/main/java/com/omnifile/MainActivity.kt` | `surface` | `picker/grant trust boundary` |
| `app/src/main/java/com/omnifile/storage/SafTreeGrantStore.kt` | `surface` | `permission persistence` |
| `app/src/main/java/com/omnifile/storage/SafStorageProvider.kt` | `surface` | `containment/capability` |
| `app/src/main/java/com/omnifile/files/FilesViewModel.kt` | `surface` | `destination retry/back` |
| `app/src/main/java/com/omnifile/ui/files/FilesScreen.kt` | `surface` | `gating/user journey` |
| `app/src/main/java/com/omnifile/ui/operations/OperationsPanel.kt` | `surface` | `terminal/error presentation` |

### Verification Commands

- `adb devices -l` -> `Pixel 7a API 36 device`
- `dumpsys activity permissions` -> `authority com.android.externalstorage.documents; root and destination mode=0x3 persisted=0x3`
- `uiautomator` -> `SAF source rows, Copy enabled, destination Use this folder disabled under conservative gate`
- `real production UI` -> `SAF→Local Copy 41 B COMPLETE; Move 35 B COMPLETE; source absent after Move`

### Dismissed Coordinator Candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| `Global SAF destination should be enabled because ExternalStorageProvider grants write` | `dismissed` | `finalizationProven=false and destination button disabled` |
| `Move disabled for source with read-only grant` | `dismissed as route-dependent` | `actual root grant was mode=0x3; final production decision remains subject to source-version proof`

## Prior Resolution Reconciliation

None - frozen post-runtime generation-0 checkpoint.

## Receiving Handoff

- Handoff status: `Ready for receiving-code-review`
- Automatic receiving permitted: `Yes`
- Source report ID: `cr-20260920-postruntimee`
- Scope fingerprint to recheck: `Unavailable - runtime evidence was supplied in-thread`
- Actionable finding IDs: `F1,F2`
- Deferred finding IDs: `None`
- Actionable test-gap IDs: `T1,T2,T3`
- Deferred test-gap IDs: `None`
- Open question IDs: `None`
- Open coverage area IDs: `None`
- Highest-risk verification to repeat: `destination Retry, revoked grant, final unsupported gating`
- Suggested implementation boundaries: `Fix FilesViewModel destination retry and preserve conservative SAF destination gate.`
- Re-review note: `Run generation-1 post-runtime UI review after fixes.`
- Chain rule: `Generation 1 is terminal.`

## Report Self-Check

- `yes` Scope and runtime evidence are recorded.
- `yes` Findings and test gaps are frozen before implementation.
- `yes` Coverage ledger and handoff are present.
- `pending` Validator must be run before closure.
