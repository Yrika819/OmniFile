# User-Visible Regression Audit

## Scope

- Review date: `2026-09-21`
- Requested outcome: `post-fix review`
- Continuation: `report only`
- Scope reviewed: `branch diff`
- Baseline: `published VS04 e6f1148b1b6dbe946228316861b6f6e382ca2af1`
- Completion: `Complete within reviewed scope`
- Assumptions: `Working-tree VS05 is compared with published VS04; current VS04 source-selection surface is the existing Home-like entry surface; permanent Home/Search/Music/Settings shell is intentionally deferred by settled product decision`

## Gate Snapshot

- Recommendation: `Pass`
- Completion: `Complete within reviewed scope`
- Why now: `The final VS05 diff adds the scoped Search journey without changing existing Files, SAF, selection, mutation, transfer, Operations, or Back contracts.`
- Must-review now: `None`
- Findings count: `Block 0 | Discuss 0 | Watch 0 | Intentional 2`
- Coverage confidence: `high`
- Behavior graph coverage: `built for 9 surfaces`
- Biggest blind spot: `Future permanent bottom-navigation shell and executable ThisDevice scope are intentionally outside VS05`

## Complete Findings Index

No user-visible regression findings identified in the reviewed scope.

## Block

None.

## Discuss

None.

## Watch

None.

## Intentional Changes

- `I1` Dedicated Search is now reachable from normal Files Content/Empty states through a transitional Search action; this is the settled VS05 product decision and is covered by Search/Files source and device evidence. [FilesScreen](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-search-v1/app/src/main/java/com/omnifile/ui/files/FilesScreen.kt#L142)
- `I2` The future Home/Search/Music/Settings bottom-navigation shell and executable ThisDevice aggregation remain absent; this is explicitly deferred and avoids fake tabs or misleading global search. [SearchModels](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-search-v1/app/src/main/java/com/omnifile/search/SearchModels.kt#L9)

## Coverage Ledger

| Surface / path | Touched files or entry points | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| `Home/source-selection entry` | `MainActivity.kt; FilesScreen.kt` | `Reviewed - no user-visible regression found` | `No change to source-selection behavior or existing Files entry surface.` | `Full host/device suites; diff trace shows Search action only for Content/Empty.` |
| `Files browsing` | `FilesViewModel.kt; FilesScreen.kt; MainActivity.kt` | `Reviewed - no user-visible regression found` | `Existing Local/SAF navigation remains the owner of browsing.` | `Full 37-test Pixel runner; existing Files tests; result navigation reuses Files.` |
| `Local browsing` | `LocalStorageProvider.kt; FilesRepository.kt` | `Reviewed - no user-visible regression found` | `No provider implementation changes; Search delegates through existing provider boundary.` | `80 host tests; Pixel Local Search test; source trace.` |
| `SAF browsing/grants` | `SafStorageProvider.kt; SafTreeGrantStore.kt; MainActivity.kt` | `Reviewed - no user-visible regression found` | `No grant/picker changes; Search consumes restored provider registrations.` | `Controlled SAF/full instrumentation; no new Intent or grant forwarding.` |
| `Search` | `SearchEngine.kt; SearchViewModel.kt; SearchScreen.kt` | `Intentional I1` | `New CurrentFolder recursive filename Search journey.` | `11 focused host tests; 3 Compose tests; Pixel Local/SAF and manual scope/IME/Back evidence.` |
| `Selection` | `FilesViewModel.kt; FilesScreen.kt` | `Reviewed - no user-visible regression found` | `Search ingress is not exposed while selection actions are active; selection code unchanged except additive scope/result methods.` | `Existing Files Compose/device tests pass.` |
| `Rename/Delete` | `FilesViewModel.kt; FilesScreen.kt; operations paths` | `Reviewed - no user-visible regression found` | `Mutation controls remain in Files and are not duplicated in Search.` | `Full Pixel instrumentation and existing mutation tests pass.` |
| `Local Copy/Move` | `FilesViewModel.kt; OperationManager; Local provider` | `Reviewed - no user-visible regression found` | `No transfer route or operation code changed.` | `Full host/device suite, including Local transfer runtime, passes.` |
| `SAF -> Local Copy` | `OperationManager; SafStorageProvider; FilesViewModel` | `Reviewed - no user-visible regression found` | `VS04 supported route remains unchanged.` | `Full SAF transfer instrumentation passes.` |
| `Destination picker` | `FilesScreen.kt; FilesViewModel.kt` | `Reviewed - no user-visible regression found` | `Search action is gated out of DestinationPicker.` | `Static branch trace and full Compose/device tests.` |
| `Operations UI` | `MainActivity.kt; OperationsPanel` | `Reviewed - no user-visible regression found` | `Operations panel and reconciliation remain mounted as before.` | `Full instrumentation and diff trace.` |
| `Back behavior` | `MainActivity.kt; FilesViewModel.kt; SearchScreen.kt` | `Reviewed - no user-visible regression found` | `Back closes Search/stops traversal, then existing Files Back behavior remains.` | `Compose Search Back test, manual Pixel Back smoke, full runner.` |
| `Permanent bottom navigation` | `MainActivity.kt; SearchScope` | `Intentional I2` | `No fake Home/Music/Settings tabs were added.` | `Settled Search authority resolution and whole-diff review.` |

## Evidence Appendix

### Behavior Graph Deltas

| ID | Surface | Baseline path | After-change path | Delta | Ledger / finding link |
| --- | --- | --- | --- | --- | --- |
| `B1` | `Files ingress` | `Files -> FilesScreen -> FilesViewModel` | `Files -> FilesScreen Search action -> MainActivity scope callback -> Search surface` | `Additive Search ingress only for normal browsing state.` | `Intentional I1` |
| `B2` | `Search request` | `None` | `SearchScreen -> SearchViewModel generation/debounce -> SearchEngine -> provider callbacks -> batched state` | `New bounded Search journey.` | `Intentional I1` |
| `B3` | `Search result` | `None` | `Search result -> MainActivity callback -> FilesViewModel provider/ancestor validation -> existing Files listing` | `New navigation path returns to existing Files.` | `Intentional I1` |
| `B4` | `Local browse` | `Files -> FilesRepository -> LocalStorageProvider` | `Same path plus independent Search callback to LocalStorageProvider.listChildren` | `No mutation or containment guard removed.` | `Reviewed` |
| `B5` | `SAF browse` | `Files -> restored SAF provider -> SafStorageProvider` | `Same restored provider map plus Search listChildren callback` | `No picker/grant path changed.` | `Reviewed` |
| `B6` | `Selection/mutation` | `Files selection -> mutation action -> provider/operation manager` | `Same path; Search action unavailable while selection is active` | `Search does not duplicate or bypass mutation guards.` | `Reviewed` |
| `B7` | `Transfers/destination` | `Files selection -> destination picker -> OperationManager` | `Same path; Search action unavailable in DestinationPicker` | `No route broadening.` | `Reviewed` |
| `B8` | `Operations` | `MainActivity -> OperationsPanel -> durable manager` | `Same path alongside conditional Files/Search surface content` | `No operations ownership change.` | `Reviewed` |
| `B9` | `Back` | `Files Back -> FilesViewModel.handleBack` | `Search Back -> stop Search -> Files; Files Back unchanged` | `Search adds an outer surface branch only.` | `Reviewed` |

### Diff Inventory

| File or area | Classification | User-visible path considered |
| --- | --- | --- |
| `app/src/main/java/com/omnifile/search/*` | `surface` | `Search journey and result state` |
| `app/src/main/java/com/omnifile/ui/search/SearchScreen.kt` | `surface` | `Search UI/IME/empty/error/results` |
| `app/src/main/java/com/omnifile/MainActivity.kt` | `surface` | `Files/Search routing and Back` |
| `app/src/main/java/com/omnifile/files/FilesViewModel.kt` | `surface` | `Files scope/result navigation` |
| `app/src/main/java/com/omnifile/ui/files/FilesScreen.kt` | `surface` | `Search ingress/action gating` |
| `app/src/androidTest/java/com/omnifile/*Search*` | `test-only` | `Runtime evidence for changed paths` |
| `app/src/test/java/com/omnifile/search/*` | `test-only` | `Host evidence` |
| `docs/production/VERTICAL_SLICE_05_SEARCH_V1.md` | `docs-only` | `Release documentation` |
| `tmp/reviews/*` | `docs-only` | `Review lineage` |

### Candidate Sweep Log

| Candidate | Decision | Reason |
| --- | --- | --- |
| `Search action visible during selection/destination/error/loading` | `dismissed` | `FilesScreen` gates Search to Content/Empty only; source and full device suite checked.` |
| `Search changes SAF grants or picker behavior` | `dismissed` | `MainActivity` uses existing grant flow; no new external Intent or manifest component was added.` |
| `Search result duplicates or mutates Files selection` | `dismissed` | `Search returns provider-scoped hit identity and Files navigation clears selection through existing path.` |
| `Existing Local/SAF transfer routes regress` | `dismissed` | `Full host/device coverage passes and transfer implementation files are unchanged.` |
| `Future shell absence is a regression` | `intentional I2` | `Product authority explicitly defers the permanent shell and forbids placeholder tabs in VS05.` |

### Verification Commands

- `git diff --check` -> `no whitespace errors`
- `:app:testDebugUnitTest` -> `80 passed, 0 failed, 0 errors, 0 skipped`
- `:app:lintDebug` -> `BUILD SUCCESSFUL`
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest` -> `BUILD SUCCESSFUL`
- Pixel 7a/API 36 `am instrument -w -r -e package com.omnifile` -> `OK (37 tests)`
- Pixel targeted Local Search -> `OK (1 test)`
- Manual Pixel smoke -> `scope chip, IME/no-match layout, and Search Back to Files observed`

### Supporting Code Links

| ID | Role | Link | Why it matters |
| --- | --- | --- | --- |
| `I1` | `ingress` | `/Users/yuta/Desktop/File Manager-worktrees/omnifile-search-v1/app/src/main/java/com/omnifile/ui/files/FilesScreen.kt#L142` | `Additive Search action is visible only in normal Files browsing.` |
| `I1` | `surface` | `/Users/yuta/Desktop/File Manager-worktrees/omnifile-search-v1/app/src/main/java/com/omnifile/MainActivity.kt#L112` | `Independent Search surface routing.` |
| `B3` | `navigation` | `/Users/yuta/Desktop/File Manager-worktrees/omnifile-search-v1/app/src/main/java/com/omnifile/files/FilesViewModel.kt#L185` | `Result returns through existing Files stack.` |

### Blind Spots

| Area | Risk introduced by the blind spot | What would resolve it |
| --- | --- | --- |
| `Future permanent app shell/ThisDevice` | `Not part of VS05; future global Search behavior is not covered by this slice.` | `Dedicated app-shell/root aggregation slice.` |

### Report Self-Check

- `yes` Every touched user-visible or unknown-impact surface appears in Coverage Ledger.
- `yes` Every finding in an action section appears in Complete Findings Index.
- `yes` Every Finding F# ledger row has a matching card.
- `yes` Every Not covered row has a reason and next verification step.
- `yes` Every user-visible or unknown-impact surface has graph or direct path evidence, or is explicitly marked as not covered.
- `yes` Recommendation follows the mapping rules from the skill.
