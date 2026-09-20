# User-Visible Regression Review

## Scope

- Review date: `2026-09-21`
- Requested outcome: `post-fix review`
- Continuation: `return post-fix findings; no implementation finding remains`
- Scope reviewed: `published VS03 a95b3e28f47954b879b389ce6d5a5f17d4704407 through current VS04 working tree, with focus on the post-hardening delta`
- Baseline: `a95b3e28f47954b879b389ce6d5a5f17d4704407`
- Completion: `Incomplete only for final locked-device Compose/UI interaction; static and controlled-runtime review complete`
- Assumptions: `SAF→Local Copy is the only supported SAF route; SAF destination, SAF Move, and SAF→SAF remain intentionally unsupported.`

## Gate Snapshot

- Recommendation: `Discuss`
- Completion: `Incomplete - final APK Compose/UI interaction could not run while Pixel was locked/dreaming`
- Why now: `The two prior user-visible concerns are addressed in code and controlled runtime; one physical UI gate remains unavailable without unlocking the device.`
- Must-review now:
  1. `F1` Final locked-device UI runtime gate
- Findings count: `Block 0 | Discuss 1 | Watch 0 | Intentional 3`
- Coverage confidence: `high for code/host/controlled runtime; medium for final physical UI`
- Behavior graph coverage: `Built/directly traced for Local, SAF source, picker, Copy/Move gates, provider errors, restart restoration, conflict, Operations, Back, and unsupported routes.`
- Biggest blind spot: `Final APK Compose hierarchy and tappability while the Pixel is locked/dreaming.`

## Complete Findings Index

| ID | Action | Surface | User-visible outcome | Confidence |
| --- | --- | --- | --- | --- |
| `F1` | `Discuss` | final APK Compose/provider-error UI | The latest UI-only message mapping could not be physically rerun because the Pixel was locked; no storage or route regression was found statically or in controlled runtime. | medium |

## Block

None.

## Discuss

### F1 Discuss - Final APK UI runtime gate is pending because the Pixel is locked

User impact: `The final APK's Compose hierarchy, provider-unavailable text, and Retry tappability were not observed on the physical Pixel after the last UI-only rendering change.`

Review reason: `The implementation change is narrow and statically consistent, but the campaign requires physical UI evidence where the device is available. The device reported mDreamingLockscreen=true, and unlocking or bypassing security is prohibited.`

Surface: `Files error rendering, provider-unavailable message, Retry action, final Compose UI`
Confidence: `medium`

Look here first:
- [`FilesScreen.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/ui/files/FilesScreen.kt#L186)
- [`SafStorageProvider.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/storage/SafStorageProvider.kt#L655)

Behavior delta:
- Before: `The prior runtime evidence covered normal UI journeys, but provider-unavailable rendering was not a distinct final route.`
- After: `ProviderUnavailable has a user-facing retry message and remains retryable in durable operations; final physical Compose interaction is not yet observed.`

Evidence:
- `Controlled provider final class passed 17/17 and controlled plus SafTransferRuntime passed 20/20 on Pixel API 36.`
- `Host suite passed 69/69; lint/builds passed; static path maps ProviderUnavailable to the new text and Retry remains wired.`
- `Direct Compose class execution was blocked in all 4 tests with No compose hierarchies because dumpsys reported mDreamingLockscreen=true. No unlock, root, grant fabrication, or bypass was used.`

Reviewer action:
`Request a normal-user final Pixel run after unlock; do not treat this physical gate as evidence to enable unsupported SAF routes.`

## Watch

None.

## Intentional Changes

### I1 - Provider-unavailable truth is now user-visible and retry-oriented

- `ProviderUnavailable` no longer collapses into stale/not-found in the SAF provider/operation path.
- Files displays a stable retry-oriented message instead of the raw enum name.

### I2 - Explicit selected SAF tree restoration

- A source picker result records an exact selected tree URI only after current persisted read access is confirmed.
- Equal-depth fallback does not use platform grant enumeration order; an unresolved tie returns no automatic source.

### I3 - Conservative route matrix remains unchanged

- SAF→Local Copy remains the only supported SAF route.
- SAF Move, Local→SAF, and SAF→SAF remain gated Unsupported; `finalizationProven=false` and `sourceVersionProven=false` remain unchanged.

## Coverage Ledger

| Surface / path | Touched files or entry points | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| Local browse | `LocalStorageProvider.kt`, Files UI | Reviewed - no user-visible regression found | Pass | Host/controlled suites and preserved VS03 path |
| Local selection/rename/delete | Files UI and repository | Reviewed - no user-visible regression found | Pass | Existing host/instrumentation coverage; no changed guard in delta |
| Local→Local Copy/Move | `OperationManager.kt`, local provider | Reviewed - no user-visible regression found | Pass | Host Move outage/recovery and prior VS03 instrumentation |
| SAF picker/grant flow | `MainActivity.kt`, `SafTreeGrantStore.kt` | Reviewed - no user-visible regression found | Pass with physical caveat | Static role split, exact current grant validation, prior real DocumentsUI evidence |
| SAF source browse | `SafStorageProvider.kt`, Files UI | Reviewed - no user-visible regression found | Pass | Controlled 17/17 including outage/recovery/NotFound/permission/stale |
| SAF equal-depth restart selection | `SafTreeGrantStore.kt` | Reviewed - no user-visible regression found | Pass | Host 4/4 selected-tree policy; no arbitrary equal-depth restore |
| SAF→Local Copy | `OperationManager.kt`, `SafTransferRuntimeInstrumentedTest.kt` | Reviewed - no user-visible regression found | Pass | Final controlled 20/20 subset and prior real Pixel `41 B COMPLETE`, source retained |
| SAF→Local Move | Files capability gate | Intentional I3 | Unsupported safely | `MOVE_SOURCE` remains unavailable in production |
| Local→SAF | destination picker and capability gate | Intentional I3 | Unsupported safely | SAF `Use this folder` remains disabled |
| SAF→SAF | destination/source capability gates | Intentional I3 | Unsupported safely | No generic finalization trust introduced |
| conflicts | `OperationManager.kt`, Operations UI | Reviewed - no user-visible regression found | Pass | Prior Pixel conflict evidence and current durable path unchanged |
| cancellation/restart | `OperationManager.kt`, controlled provider | Reviewed - no user-visible regression found | Pass | Host recovery tests and controlled runtime; no source deletion on outage |
| Operations UI | `OperationsPanel.kt`, operation messages | Reviewed with physical gap | Durable text is mapped; final Compose display pending | Static mapping plus host/controlled runtime; Pixel locked for UI rerun |
| Back/navigation | Files ViewModel/UI | Reviewed - no user-visible regression found | Pass | No changed navigation guards in hardening delta |
| provider switching/permission errors | SAF provider and Files error | Reviewed with physical gap | Typed error path pass; final physical message/tap pending | Controlled permission/outage tests; no final physical fault interaction |

## Evidence Appendix

### Behavior Graph Deltas

| ID | Surface | Baseline path | After-change path | Delta | Ledger / finding link |
| --- | --- | --- | --- | --- | --- |
| `B1` | Provider outage | SAF locator → containment false → stale/not-found | SAF locator → typed ProviderUnavailable → retryable operation/UI | Error truth and retryability repaired | Reviewed |
| `B2` | Tree restoration | persisted grants → minimum depth/platform order | selected marker + current grant validation → unique shallowest fallback or no auto-selection | Equal-depth user intent preserved | Reviewed |
| `B3` | Move recovery | destination complete → source delete outage could fail recovery transition | destination complete → SOURCE_DELETE_PENDING → retryable outage → pending recovery without recopy | Durable ordering preserved | Reviewed |
| `B4` | Route gating | capability checks | same gates plus new error message only | No unsupported route enabled | Intentional I3 |

### Diff Inventory

| File or area | Classification | User-visible path considered |
| --- | --- | --- |
| `SafStorageProvider.kt`, `StorageModel.kt` | user-visible dependency | SAF browse/transfer errors |
| `SafTreeGrantStore.kt`, `MainActivity.kt` | user-visible dependency | picker result and restart source |
| `OperationManager.kt`, `OperationStateMachine.kt` | user-visible dependency | Operations/retry/source-delete state |
| `FilesScreen.kt` | surface | provider error message and Retry |
| controlled/provider and host tests | test-only | evidence for all above |
| production documentation | docs-only | route/support claims |

### Candidate Sweep Log

| Candidate | Decision | Reason |
| --- | --- | --- |
| `ProviderUnavailable could still cause Move COMPLETE` | dismissed | ProviderUnavailable never enters NotFound branches; source-delete recovery remains pending/retryable and host test proves source retained. |
| `ProviderUnavailable could cause duplicate destination` | dismissed | finishDestination enters finalization reconciliation rather than TRANSFERRING; source-delete outage test observes one partial creation. |
| `Equal-depth marker could regain access after revocation` | dismissed | Marker is matched only against current persisted readable grants; absent grant falls back safely or returns no source. |
| `SAF destination should be enabled after controlled runtime green` | intentional | `finalizationProven=false` remains hard gate. |

### Verification Commands

- `:app:testDebugUnitTest` -> `69 tests; 0 failures; 0 errors; 0 skipped`
- `:app:lintDebug` -> `PASS; 0 errors`
- `:app:assembleDebug` -> `PASS; strict dependency verification`
- `:app:assembleDebugAndroidTest` -> `PASS; strict dependency verification`
- final controlled `SafStorageProviderInstrumentedTest` -> `17/17 OK`
- final controlled + `SafTransferRuntimeInstrumentedTest` -> `20/20 OK`
- full direct instrumentation while locked -> `31 total; 27 passed; 4 Compose failures: No compose hierarchies`
- final APK install -> `Success` on stable Pixel serial

### Blind Spots

| Area | Risk introduced by the blind spot | What would resolve it |
| --- | --- | --- |
| Final APK Compose/provider-error UI | Text/tappability after the final UI-only change was not physically observed | Unlock normally, rerun Compose class and final UI error path; no bypass |

### Report Self-Check

- `yes` Every touched user-visible or unknown-impact surface appears in Coverage Ledger.
- `yes` Every finding in an action section appears in Complete Findings Index.
- `yes` Every Finding F1 ledger row has a matching card.
- `yes` Every Not covered/physical gap has a reason and next verification step.
- `yes` Every user-visible or unknown-impact surface has direct path or runtime evidence, or is explicitly marked with the blind spot.
- `yes` Recommendation follows the mapping rules from the regression-review skill.
