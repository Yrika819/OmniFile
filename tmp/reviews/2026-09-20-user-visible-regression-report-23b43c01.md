# VS02 -> VS03 User-Visible Regression Review

## Scope

- Review date: `2026-09-20`
- Requested outcome: `post-fix review`
- Continuation: `return post-fix findings`
- Scope reviewed: `published VS02 base through current VS03 HEAD and current closure edits`
- Baseline: `14732fd8f120b93380e624143e534cceff6d09c4`
- Target: `8d4c188cf5f4a5347009e1fc2dbb6b7a13368789 plus current working tree`
- Completion: `Complete within reviewed scope; runtime coverage is explicitly partial for successful Copy/Move completion.`
- Assumptions: `Local regular-file Copy/Move is the intentional VS03 boundary; SAF transfer, directories, and background continuation remain intentionally unsupported.`

## Gate Snapshot

- Recommendation: `Discuss`
- Completion: `Incomplete - successful Copy/Move completion, conflict/cancellation UI, and terminal Operations update were not observed end-to-end.`
- Why now: `Static behavior and partial Pixel UI/runtime evidence show no confirmed regression, but the new primary Copy/Move completion journey remains an approval-affecting coverage gap.`
- Must-review now: `No confirmed regression; see the coverage ledger and the explicit blind spot for Copy/Move terminal behavior.`
- Findings count: `Block 0 | Discuss 0 | Watch 0 | Intentional 4`
- Coverage confidence: `medium`
- Behavior graph coverage: `built for selection/picker and mutation gating; partial for operation execution because no successful runtime completion was available`
- Biggest blind spot: `User-visible successful Local Copy/Move completion and Operations terminal/history refresh`

## Complete Findings Index

No user-visible regression findings identified in the reviewed scope.

## Block

None.

## Discuss

None as a confirmed regression. The incomplete coverage state keeps the gate at `Discuss` under the regression-review contract.

## Watch

None.

## Intentional Changes

### I1 — Copy/Move actions for supported regular files

The selection action surface intentionally adds Copy and Move for regular files while retaining capability gating. [FilesScreen.kt](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/ui/files/FilesScreen.kt#L77)

### I2 — Explicit destination picker

Copy/Move intentionally enters a provider-neutral destination-picker mode and requires explicit current-directory confirmation before durable enqueue. [FilesViewModel.kt](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/files/FilesViewModel.kt#L239)

### I3 — SAF and directory transfer gating

SAF transfer and directory Copy/Move remain visibly unsupported rather than exposing an apparently working route. This matches the frozen VS03 scope and avoids misleading users.

### I4 — Operations panel

A minimal Operations surface intentionally adds durable operation history, progress, stages, terminal state, error text, and cancellation affordance. The final terminal update was not runtime-observed in a successful Copy/Move flow.

## Coverage Ledger

| Surface / path | Touched files or entry points | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| Normal Files browsing | `FilesViewModel.kt`, `FilesScreen.kt`, `LocalStorageProvider.kt` | Reviewed - no user-visible regression found | Local browse launched on Pixel and displayed an app-owned file; host browse tests passed. | Pixel startup/browse evidence; 57 host tests. |
| Local source selection | `FilesScreen.kt`, `FilesViewModel.kt` | Reviewed - no user-visible regression found | Long-press selection worked on Pixel and showed `1 selected`. | Pixel UI dump; Compose instrumentation included selection tests. |
| Multi-selection and close | `FilesScreen.kt`, `FilesViewModel.kt` | Reviewed - no user-visible regression found | Host/Compose selection paths passed; actual Pixel single-selection Back was observed. | 57 host tests; Pixel direct runner 20/20; manual Pixel long-press. |
| Android Back from selection | `MainActivity.kt`, `FilesViewModel.kt` | Intentional I1 / Reviewed - no user-visible regression found | Actual Android Back exited selection without leaving the directory. | Pixel actual `input keyevent 4` and UI dump. |
| Rename | Existing VS02 mutation path | Reviewed - no user-visible regression found | No changed guard/order suggesting regression; Compose and host coverage passed. | Prior VS02 review plus final host/Pixel tests. |
| Delete | Existing VS02 mutation path, Operations UI | Reviewed - no user-visible regression found | No changed guard/order suggesting regression; prior tests remain green. | Prior review and final host/Pixel evidence. |
| Copy action visibility/gating | `FilesScreen.kt` | Intentional I1 | Copy is visible/enabled only for supported regular files. | Pixel selection showed Copy; host/Compose tests passed. |
| Move action visibility/gating | `FilesScreen.kt` | Intentional I1 | Move is visible/enabled only for supported regular files with delete capability. | Pixel selection showed Move; host/Compose tests passed. |
| Destination picker launch/cancel/back | `FilesViewModel.kt`, `FilesScreen.kt`, `MainActivity.kt` | Intentional I2 / Reviewed - no user-visible regression found | Pixel showed `COPY destination`; actual Back returned to selected source without enqueueing. | Pixel UI dump and actual Back. |
| Destination picker nested navigation | `FilesViewModel.kt` | Not covered | Static path is coherent; nested runtime navigation was not exercised in the final Pixel run. | Need one nested-directory disposable runtime check. |
| Local Copy completion | `OperationManager.kt`, `TransferEngine.kt`, `LocalStorageProvider.kt`, `OperationsPanel.kt` | Not covered | Host engine path is covered; no successful Android Copy completion and terminal Operations update was observed. | T1 from final code review; manual same-folder attempt did not yield a durable result. |
| Local Move completion/source deletion | `OperationManager.kt`, `TransferEngine.kt`, `LocalStorageProvider.kt` | Not covered | Host fault/order coverage exists; no successful Android Move completion/source disappearance was observed. | T1 from final code review; no personal files used. |
| Conflict UI | `OperationsPanel.kt`, error classification | Not covered | Host conflict classification is covered; no Pixel conflict operation terminal UI was observed. | Run a disposable same-name conflict after a stable destination fixture exists. |
| Cancellation UI | `OperationsPanel.kt`, `OperationManager.kt` | Not covered | Host cancellation semantics are covered; no Pixel cancellation flow was observed. | Requires a sufficiently large disposable app-owned file and stable operation UI. |
| Operations status/history | `OperationsPanel.kt`, `OperationsViewModel.kt` | Intentional I4 / Not covered | Static rendering and durable queries are reviewed; successful Copy/Move terminal refresh was not observed. | T1; runtime completion check needed. |
| Empty/loading/error states | Files/Operations UI | Reviewed - no user-visible regression found | Existing states remain represented; no final runtime evidence for every new operation error state. | Static trace and host/Compose tests. |
| Provider switching / SAF navigation | `FilesViewModel.kt`, `SafStorageProvider.kt` | Intentional I3 / Reviewed - no user-visible regression found | Controlled SAF instrumentation passed; transfer capabilities remain disabled. | Pixel direct 20/20 includes controlled SAF tests; no real external SAF transfer claimed. |
| Mutation refresh/navigation after operation | `FilesViewModel.kt`, `OperationManager.kt` | Not covered | Static refresh path is present; no successful Android operation was observed through refresh. | T1; disposable successful Copy/Move run required. |
| APK/startup surface | `MainActivity.kt`, APK | Reviewed - no user-visible regression found | Pixel app launched and remained alive; no fatal/ANR in bounded logcat. | Pixel startup ~633 ms; APK SHA recorded. |

## Evidence Appendix

### Behavior Graph Deltas

| ID | Surface | Baseline path | After-change path | Delta | Ledger / finding link |
| --- | --- | --- | --- | --- | --- |
| `B1` | Selection actions | `Files -> selected entry -> VS02 mutation actions` | `Files -> selected regular file -> Copy/Move gating -> picker or existing mutation actions` | `Two intentional actions and capability gates added; existing Rename/Delete paths retained.` | `I1; reviewed` |
| `B2` | Copy/Move picker | `No VS02 path` | `Selection -> begin picker -> browse eligible directory -> Back/cancel or confirm -> durable enqueue` | `New user-visible path; Pixel launch and Back/cancel verified, terminal operation not verified.` | `I2; not covered terminal continuation` |
| `B3` | Operation status | `No durable Copy/Move status surface` | `durable row -> OperationsPanel recent snapshot -> stage/state/error/cancel UI` | `New user-visible surface; terminal refresh after successful Android operation remains unverified.` | `I4; T1` |

### Diff Inventory

| File or area | Classification | User-visible path considered |
| --- | --- | --- |
| `FilesScreen.kt`, `FilesViewModel.kt`, `MainActivity.kt` | `surface` | `selection, Back, picker, Copy/Move actions` |
| `OperationsPanel.kt`, `OperationsViewModel.kt` | `surface` | `operation status/history/error/cancel` |
| `OperationManager.kt`, `TransferEngine.kt`, Room store | `dependency` | `durable operation side effects` |
| `LocalStorageProvider.kt` | `dependency` | `file contents, conflict, source deletion` |
| `SafStorageProvider.kt` and capability model | `dependency` | `provider gating and navigation` |
| Tests, schema, Gradle lock/verification files | `test-only/config/generated` | `verification and packaged schema; no direct UI output` |
| Production/review docs | `docs-only` | `evidence and release decision` |

### Candidate Sweep Log

| Candidate | Decision | Reason |
| --- | --- | --- |
| `SAF transfer accidentally enabled` | `intentional I3` | Capability gating and docs keep all SAF transfer routes unsupported. |
| `directories expose Copy/Move` | `intentional scope gate` | Files UI requires regular-file selection for actions. |
| `Back navigates directory while selection is active` | `dismissed` | Actual Pixel Back left selection active directory state and did not navigate. |
| `Local finalization overwrites an existing destination` | `dismissed after repair` | Focused Local conflict test and final 57-test host suite pass; provider now checks destination existence. |
| `Android app startup regression` | `dismissed` | Pixel process/display/logcat sanity passed. |
| `Operations terminal state stale after successful transfer` | `not covered` | No successful Android Copy/Move completion was observed; retained as the explicit blind spot, not a confirmed regression. |

### Verification Commands

- `:app:testDebugUnitTest --dependency-verification=strict --no-daemon --max-workers=1` -> `57 passed`
- `:app:lintDebug --dependency-verification=strict --no-daemon --max-workers=1` -> `PASS; warnings only`
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest` under strict verification -> `PASS`
- Direct Pixel `am instrument -w -r ... AndroidJUnitRunner` -> `20/20 passed`
- Pixel `am start com.omnifile/.MainActivity` plus bounded logcat -> `startup PASS; no fatal/ANR`
- Pixel UI long-press, actual Back, picker launch, picker Back -> `observed as described above`

### Blind Spots

| Area | Risk introduced by the blind spot | What would resolve it |
| --- | --- | --- |
| `Successful Local Copy/Move completion` | `Could hide an integration defect between picker enqueue, provider execution, refresh, and Operations terminal rendering.` | `One disposable Pixel/emulator Copy and Move completion run with source/destination verification and terminal UI observation.` |
| `Conflict/cancellation Operations UI` | `Backend facts may not surface correctly to users.` | `One disposable conflict and one bounded cancellation runtime run.` |
| `Gradle connected task result semantics` | `The Gradle task produced a zero-test report on one emulator even though direct Pixel instrumentation was valid.` | `Re-run connected Gradle on a stable single target after resolving target discovery; do not treat exit 0 alone as evidence.` |

### Report Self-Check

- `yes` Every touched user-visible or unknown-impact surface appears in Coverage Ledger.
- `yes` No confirmed regression is omitted from Complete Findings Index.
- `yes` Intentional changes are separated from unknown runtime coverage.
- `yes` Every Not covered row has a reason and concrete next step.
- `yes` Behavior graphs cover the changed selection/picker/status paths; operation-terminal graph is explicitly partial.
- `yes` Recommendation is Discuss because user-visible coverage is incomplete.
