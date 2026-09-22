# User-Visible Regression Audit

## Scope

- Review date: `2026-09-23`
- Requested outcome: `review and fixes`
- Continuation: `continue already-authorized fixes`
- Scope reviewed: `branch diff / working tree`
- Baseline: `38d2f936c2a65c96c61f1754994de19c3c76f89a` (VS07 final)
- Completion: `Complete within reviewed scope`
- Assumptions: `VS08 untracked implementation and tests are in scope; root-checkout docs/ui-authority remains unrelated. Full device failures observed in pre-existing Media/UI tests are treated as baseline harness/device-state failures because their traces are Activity STOPPED / no Compose hierarchy and do not enter archive paths.`

## Gate Snapshot

- Recommendation: `Pass`
- Completion: `Complete within reviewed scope`
- Why now: `The VS08 delta adds a bounded Archive detail surface without changing existing top-level destinations, provider mutation semantics, media paths, or external intent surfaces; affected host and focused device evidence is green.`
- Must-review now: `None`
- Findings count: `Block 0 | Discuss 0 | Watch 0 | Intentional 2`
- Coverage confidence: `high`
- Behavior graph coverage: `built for 7 affected surfaces`
- Biggest blind spot: `Full pre-existing device UI/media baseline remains unstable outside the focused archive test; no archive failure was observed in those traces.`

## Complete Findings Index

No user-visible regression findings identified in the reviewed scope.

## Block

None.

## Discuss

None.

## Watch

None.

## Intentional Changes

- `I1` ZIP containers recognized from existing Files/Search results open a non-top-level Archive detail surface; Back returns to the originating Files/Search state by explicit origin state. This is the requested VS08 behavior. [`AppNavigation.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/ui/shell/AppNavigation.kt#L4)
- `I2` Extract is explicitly labeled Local-only because the current default SAF destination capability is not universally proven; no broken SAF destination route is advertised. [`ArchiveScreen.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/ui/archive/ArchiveScreen.kt#L74)

## Coverage Ledger

| Surface / path | Touched files or entry points | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| Files ZIP recognition/open | `FilesScreen.kt`, `MainActivity.kt` | Reviewed - no user-visible regression found | Existing file rows keep directory/play/selection behavior; ZIP row routes to Archive callback | Files callback instrumentation source and host compile |
| Search archive result/open | `SearchScreen.kt` existing callback, `MainActivity.openSearchResult` | Reviewed - no user-visible regression found | Search remains filename-only; archive result enters Archive without archive indexing | Search callback instrumentation source; static trace |
| Archive detail browse | `ArchiveScreen.kt`, `ArchiveViewModel.kt` | Intentional I1 | Root/nested/empty/duplicate/unsafe states are explicit; no new top-level destination | 128 host tests; focused SAF Pixel test |
| Archive extract action | `ArchiveExtractor.kt`, `LocalStorageProvider.kt` | Intentional I2 | Local partial/finalize, no overwrite, rollback/cancel/resource guards; SAF source→Local proven | ArchiveExtractorTest; Pixel 1/1 |
| Files/Search origin and Back | `AppNavigation.kt`, `MainActivity.kt` | Reviewed - no user-visible regression found | HOME, top Search, and contextual Search origin paths preserve return surface | 9 AppNavigationState host tests; static callback trace |
| Existing Files selection/rename/delete/copy/move | `FilesScreen.kt`, `FilesViewModel.kt` callers | Reviewed - no user-visible regression found | Added archive callback is only for non-directory ZIP row click; selection and mutation callbacks remain unchanged | Full host suite 128/128; no changed mutation implementation |
| Existing Home/Search/Music/Settings/Operations shell | `MainActivity.kt`, `AppShell` callers | Reviewed - no user-visible regression found | Archive is detail-only; primary nav hidden only while detail is shown; operation panel remains mounted | Host suite, lint/build, static shell trace |
| Existing Local/SAF playback and MediaSession | no media source changes | Not user-visible | No archive code enters playback coordinator/service paths | Diff inventory and full host/device traces |
| External intents/exports/security boundary | `AndroidManifest.xml` unchanged | Not user-visible | No MIME filter, ACTION_VIEW, provider, FileProvider, or exported activity change | Manifest diff; no intent review triggered |

## Evidence Appendix

### Behavior Graph Deltas

| ID | Surface | Baseline path | After-change path | Delta | Ledger / finding link |
| --- | --- | --- | --- | --- | --- |
| `B1` | Files ZIP open | `Files row -> directory-only open or play affordance` | `Files row -> directory open, ZIP -> Archive open, other file behavior unchanged` | Requested ZIP branch added after directory guard | Reviewed |
| `B2` | Search ZIP open | `Search result -> MainActivity.openSearchResult -> Files detail` | `ZIP result -> Archive detail; non-ZIP -> existing Files detail` | Requested archive branch preserves Search origin | Reviewed |
| `B3` | Archive browse | `Not present` | `Archive detail -> virtual path -> nested list -> Back` | Intentional new detail surface | I1 |
| `B4` | Extract | `Not present` | `Archive selection -> bounded plan -> Local partial -> stream -> verify -> finalize/rollback` | Intentional new operation path | I2 |
| `B5` | Files origin Back | `Files detail -> provider root -> owning Home/Search` | `Archive detail -> owning Files/Search; Files root behavior unchanged` | Explicit archive origin state added | Reviewed |
| `B6` | SAF archive source | `SAF file browse/read provider path` | `SAF sequential read -> ZIP parser -> Local destination only` | Capability-specific route added; no seek assumption | Reviewed |
| `B7` | Media and operations | `Existing coordinator/service/operation paths` | `Same paths; archive is not playback or regular COPY/MOVE` | No path delta | Reviewed |

### Diff Inventory

| File or area | Classification | User-visible path considered |
| --- | --- | --- |
| `app/src/main/java/com/omnifile/archive/*` | surface/dependency | Archive browse/extract output and errors |
| `app/src/main/java/com/omnifile/ui/archive/ArchiveScreen.kt` | surface | Archive detail UI |
| `app/src/main/java/com/omnifile/MainActivity.kt` | surface/dependency | Files/Search/shell routing |
| `app/src/main/java/com/omnifile/ui/files/FilesScreen.kt` | surface | Files row behavior |
| `app/src/main/java/com/omnifile/ui/shell/AppNavigation.kt` | surface | detail origin/back |
| `app/src/main/java/com/omnifile/files/FilesRepository.kt` | dependency | provider-neutral source read |
| `app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt` | dependency | Local extraction output |
| `app/src/test/java/com/omnifile/archive/*` | test-only | archive behavior coverage |
| `app/src/androidTest/java/com/omnifile/archive/*` | test-only | controlled SAF/Pixel evidence |
| `app/src/androidTest/java/com/omnifile/ui/files/FilesScreenComposeInstrumentedTest.kt` | test-only | Files ZIP callback |
| `app/src/androidTest/java/com/omnifile/ui/search/SearchScreenComposeInstrumentedTest.kt` | test-only | Search archive callback |
| `docs/production/*`, `docs/review/*` | docs-only | evidence/contract only |

### Candidate Sweep Log

| Candidate | Decision | Reason |
| --- | --- | --- |
| Archive becoming a top-level navigation destination | intentional I1, not regression | `DetailSurface.ARCHIVE` is rendered only as a detail surface and hides primary nav while open |
| Search indexing inside ZIP | dismissed | SearchEngine/listChildren path remains provider-file filename search; no archive index added |
| Archive extraction silently overwriting | dismissed | preflight and provider finalization reject existing targets; host conflict tests pass |
| Archive parse/extract blocking Main | dismissed | ViewModel/repository/extractor launches on `Dispatchers.IO`; host parser/extractor tests pass |
| Existing media playback changes | dismissed | no media files or coordinator/service changed; full host suite passes |
| SAF destination promise | intentional I2 | UI and docs explicitly expose Local-only destination capability |

### Verification Commands

- `:app:testDebugUnitTest` -> `128 tests, 0 failures, 0 errors, 0 skipped`.
- `:app:assembleDebugAndroidTest` -> success.
- `:app:lintDebug` -> success; baseline/tooling warnings only.
- `:app:assembleDebug` -> success.
- Full `:app:connectedDebugAndroidTest` -> archive test passed; unrelated baseline Activity/Compose failures remained outside archive paths.
- Focused Pixel 7a/API 36 `ArchiveStorageInstrumentedTest` -> `1/1 passed` after repair.
- Generation-1 code review -> `Pass`, validator-clean.

### Supporting Code Links

| ID | Role | Link | Why it matters |
| --- | --- | --- | --- |
| `B1` | entry | [`FilesScreen.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/ui/files/FilesScreen.kt#L378) | ZIP row branch is isolated from directory and selection behavior |
| `B2` | route | [`MainActivity.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/MainActivity.kt#L325) | Search archive result preserves owning Search route |
| `B3` | output | [`ArchiveScreen.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/ui/archive/ArchiveScreen.kt#L45) | Archive remains a detail surface with explicit states |
| `B4` | effect | [`ArchiveExtractor.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/archive/ArchiveExtractor.kt#L34) | safe extraction effect is bounded and rollback-aware |
| `B5` | guard | [`AppNavigation.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/ui/shell/AppNavigation.kt#L51) | explicit archive origins preserve Back behavior |

### Blind Spots

| Area | Risk introduced by the blind spot | What would resolve it |
| --- | --- | --- |
| Existing full-device Activity/Compose harness failures | Reduced confidence in unrelated pre-existing physical UI/media journeys, not in the focused archive storage path | Repair the pre-existing device harness/state and rerun the complete suite |

### Report Self-Check

- yes Every touched user-visible or unknown-impact surface appears in Coverage Ledger.
- yes No regression finding exists outside Intentional Changes.
- yes Every intentional change has a ledger row and behavior graph.
- yes Every baseline device blind spot has a reason and next verification step.
- yes Recommendation follows the mapping rules.
