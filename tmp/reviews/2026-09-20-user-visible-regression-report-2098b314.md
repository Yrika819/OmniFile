# User-Visible Regression Audit

## Scope

- Review date: `2026-09-20`
- Requested outcome: `post-fix review`
- Continuation: `report only`
- Scope reviewed: `published VS02 base 14732fd8f120b93380e624143e534cceff6d09c4 -> current VS03 working tree on development/core-v1-durable-copy-move-v1`
- Baseline: `14732fd8f120b93380e624143e534cceff6d09c4`
- Completion: `Complete within reviewed scope`
- Assumptions: `Physical runtime evidence is Pixel 7a, Android 16/API 36; fixture data was disposable app-private data; cancellation UI E2E and exhaustive process-death runtime remain deferred by scope.`

## Gate Snapshot

- Recommendation: `Pass`
- Completion: `Complete within reviewed scope`
- Why now: `The last user-visible approval gap—picker-confirmed successful Copy/Move through terminal Operations UI—was physically verified after the picker parent-navigation repair.`
- Must-review now: `None`
- Findings count: `Block 0 | Discuss 0 | Watch 0 | Intentional 5`
- Coverage confidence: `high`
- Behavior graph coverage: `built for 2 affected transfer surfaces; direct path evidence for preserved VS02 surfaces`
- Biggest blind spot: `Cancellation UI E2E and exhaustive physical process-death runtime are intentionally deferred.`

## Complete Findings Index

No user-visible regression findings identified in the reviewed scope.

## Block

None.

## Discuss

None.

## Watch

None.

## Intentional Changes

- `I1` Copy and Move actions are available for supported selected regular files; this is the approved VS03 product addition. Evidence: `FilesScreen.kt` capability gating and Pixel selection UI.
- `I2` Destination picker uses explicit `Use this folder` confirmation; the visible picker `‹` action is cancel, while actual Android Back navigates the pending picker stack. Evidence: `FilesScreen.kt`, `FilesViewModel.kt`, Pixel UI XML.
- `I3` Operations panel remains visible with durable terminal Copy/Move rows after execution. Evidence: Pixel terminal rows `COMPLETE · 131072 B / 131072 B` and `COMPLETE · 196608 B / 196608 B`.
- `I4` SAF transfer and directory transfer remain disabled/unsupported rather than exposing a working-looking action. Evidence: capability gating and route matrix.
- `I5` Rename remains intentionally unsupported for Local until safe atomic/no-replace semantics are proven; VS02 Rename/Delete behavior is otherwise preserved.

## Coverage Ledger

| Surface / path | Touched files or entry points | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| Files normal browsing and provider switching | `MainActivity.kt`, `FilesViewModel.kt`, `FilesScreen.kt` | `Reviewed - no user-visible regression found` | `Local browse remains available; SAF browse behavior remains separate from transfer support.` | Existing 21/21 Pixel instrumentation and prior VS02 evidence; current Pixel Local browse. |
| Long-press, single/multi-selection, close selection, Android Back | `FilesScreen.kt`, `FilesViewModel.kt` | `Reviewed - no user-visible regression found` | `Selection actions remain visible; actual Android Back exits selection without navigating on first Back.` | Existing Pixel Back evidence and unchanged selection path. |
| Rename/Delete | `FilesViewModel.kt`, `FilesScreen.kt`, Local provider | `Intentional I5` | `Delete preserved; Local Rename remains Unsupported by design.` | VS02 authority and existing host/Pixel coverage. |
| Copy action and destination picker | `FilesViewModel.kt`, `FilesScreen.kt` | `Intentional I1/I2` | `Picker-confirmed Copy reaches sibling destination and terminal durable state.` | Pixel Copy E2E and helper digest verification. |
| Move action and destination picker | `FilesViewModel.kt`, `FilesScreen.kt` | `Intentional I1/I2` | `Picker-confirmed Move reaches sibling destination and terminal durable state; source remains safe until completion.` | Pixel Move E2E and helper source/destination verification. |
| Operations status and refresh | `MainActivity.kt`, `OperationsViewModel.kt`, `OperationsPanel.kt` | `Intentional I3` | `Terminal rows are visible and reflect durable COMPLETE truth, not a stale enqueue snapshot.` | Pixel UI XML after Copy and Move. |
| Conflict/error/cancellation surfaces | state machine, `OperationsPanel.kt`, provider mapping | `Reviewed - no user-visible regression found` | `Conflict remains terminal and non-overwriting; error/cancellation states remain classified. Full cancellation UI runtime is deferred.` | Prior Pixel conflict proof, host tests, prior instrumentation; explicit cancellation blind spot. |
| Unsupported SAF and directory routes | `FilesScreen.kt`, capability model, route matrix | `Intentional I4` | `Unsupported routes remain gated and are not represented as successful behavior.` | Static capability trace and prior instrumentation. |
| Navigation after operations and source reconciliation | `FilesViewModel.kt`, provider refresh | `Reviewed - no user-visible regression found` | `Copy source remains; Move source disappears after terminal completion; source view refreshes.` | Pixel Copy/Move UI and helper filesystem truth. |

## Evidence Appendix

### Behavior Graph Deltas

| ID | Surface | Baseline path | After-change path | Delta | Ledger / finding link |
| --- | --- | --- | --- | --- | --- |
| `B1` | Picker-confirmed Copy | `Selection -> Copy -> picker current source -> cancel/back -> source` | `Selection -> Copy -> picker -> Android Back parent -> sibling destination -> Use this folder -> enqueue -> execute -> Operations COMPLETE` | Parent stack is now retained and traversable; terminal UI refresh is observed. | `Intentional I1/I2/I3` |
| `B2` | Picker-confirmed Move | `Selection -> Move -> picker -> no previously observed successful terminal UI path` | `Selection -> Move -> Android Back parent -> sibling destination -> confirm -> durable Move ordering -> Operations COMPLETE` | Successful user journey is now physically evidenced; source deletion remains after destination completion. | `Intentional I1/I2/I3` |

### Diff Inventory

| File or area | Classification | User-visible path considered |
| --- | --- | --- |
| `app/src/main/java/com/omnifile/files/FilesViewModel.kt` | `surface` | picker navigation, Back/cancel, Copy/Move enqueue |
| `app/src/test/java/com/omnifile/files/FilesViewModelTest.kt` | `test-only` | navigation regression guard |
| `docs/production/VERTICAL_SLICE_03_DURABLE_COPY_MOVE.md` | `docs-only` | evidence claims and closure status |
| Full VS03 implementation from VS02 base | `surface/dependency` | browsing, selection, mutations, durable operations, Operations UI, unsupported routes |

### Candidate Sweep Log

| Candidate | Decision | Reason |
| --- | --- | --- |
| `Visible picker arrow returning to source` | `intentional I2` | The arrow is explicitly cancel; Android Back is the navigation control and was verified separately. |
| `Operations row remains stale after enqueue` | `dismissed` | Pixel terminal XML shows post-execution COMPLETE rows with exact bytes. |
| `Move deletes source before destination completion` | `dismissed` | Current durable engine and Pixel source/destination truth prove the opposite; prior ordering tests remain green. |
| `SAF/directory action regression` | `intentional I4` | Those routes remain unsupported and gated by approved scope. |

### Verification Commands

- Focused unit test for sibling picker navigation -> `1 passed, 0 failures`.
- Strict JBR 25 serialized repaired APK/test build -> `BUILD SUCCESSFUL`.
- Pixel 7a/API 36 Copy E2E -> terminal Operations `COMPLETE`, source preserved, digest/size matched, partial absent.
- Pixel 7a/API 36 Move E2E -> terminal Operations `COMPLETE`, source absent after completion, digest/size matched, partial absent.
- Historical final host/Pixel/lint/build evidence -> `58 host tests`, `21 Pixel instrumentation tests`, lint/build PASS; no production changes outside the focused picker repair in this closure.

### Supporting Code Links

| ID | Role | Link | Why it matters |
| --- | --- | --- | --- |
| `B1` | `entry` | `/Users/yuta/Desktop/File Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/files/FilesViewModel.kt:243` | Starts Copy/Move picker with the complete navigation stack. |
| `B1` | `guard` | `/Users/yuta/Desktop/File Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/ui/files/FilesScreen.kt:91` | Keeps visible picker arrow as explicit cancel and confirmation explicit. |
| `B2` | `output` | `/Users/yuta/Desktop/File Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/ui/operations/OperationsPanel.kt:32` | Renders durable terminal operation state and byte totals. |

### Blind Spots

| Area | Risk introduced by the blind spot | What would resolve it |
| --- | --- | --- |
| Cancellation UI E2E | User-visible cancellation affordance has deterministic host coverage but not a full Pixel UI run in this closure. | Dedicated controlled large-file cancellation run. |
| Physical process-death matrix | Host reconciliation coverage is authoritative, but the full physical kill/restart matrix is not exhaustive. | Dedicated controlled process/restart campaign. |

### Report Self-Check

- `yes` Every touched user-visible or unknown-impact surface appears in Coverage Ledger.
- `yes` No regression finding appears outside the Complete Findings Index.
- `yes` Every intentional visible change is separated from regressions.
- `yes` Every blind spot has a reason and concrete next step.
- `yes` Behavior graphs cover both newly approved picker-success journeys.
- `yes` Recommendation follows the mapping rules.
