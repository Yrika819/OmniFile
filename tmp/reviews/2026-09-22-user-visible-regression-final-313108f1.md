# User-Visible Regression Audit

## Scope

- Review date: `2026-09-22`
- Requested outcome: `final VS06 -> VS07 regression audit`
- Scope reviewed: `commit range`
- Baseline: `67a8e0e08d3b6ad03165d0d962ad8868b00c661c` (VS06)
- Target: `607759c` (final VS07 implementation plus physical-acceptance tests)
- Completion: `Complete - all requested VS07 user-visible paths were statically reviewed and the final connected matrix passed.`
- Assumptions: `The Music Now Playing and Play affordances are intentional VS07 changes. No user-visible behavior was classified as a regression without evidence.`

## Gate Snapshot

- Recommendation: `Pass`
- Completion: `Complete within reviewed scope`
- Why now: `The final target passes the complete 77-test connected matrix, including Compose UI, lifecycle, real persisted-SAF playback, and binder controller coverage.`
- Must-review now: `None; full findings index and coverage ledger are retained below.`
- Findings count: `Block 0 | Discuss 0 | Watch 0 | Intentional 7`
- Coverage confidence: `high`
- Behavior graph coverage: `complete for requested VS06 -> VS07 surfaces`
- Biggest blind spot: `Provider diversity beyond the tested physical DocumentsProvider remains future work, not a VS06 regression.`

## Complete Findings Index

No user-visible VS06 -> VS07 regressions identified.

## Block

None.

## Discuss

None.

## Watch

None.

## Intentional Changes

- `I1` Provider-neutral playback identity, Local/SAF adapters, one current item, and truthful seekability were added per VS07 scope.
- `I2` Files adds explicit Play only for eligible audio and hides it during selection; ordinary row taps remain unchanged.
- `I3` Search adds explicit Play while preserving result-open behavior and CurrentFolder/ThisDevice scope semantics.
- `I4` Music changes from the VS06 empty/no-library surface to truthful Now Playing with mapped errors, real title/source, duration/progress, play/pause, and proven-only seek.
- `I5` Service-owned ExoPlayer/MediaSession and background/system control lifecycle were added per scope.
- `I6` Media playback FGS/notification permissions are additive and minimal; no broad storage/microphone/internet permission was added.
- `I7` Home, Settings, Operations, bottom navigation/rail, Back, selection, Rename/Delete, Local Copy/Move, SAF -> Local Copy, and search flows remain intact.

## Coverage Ledger

| Surface / path | Touched files or entry points | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| Home source overview and SAF-folder entry | `MainActivity.kt; HomeScreen.kt; HomeViewModel.kt` | `Reviewed - no user-visible regression found` | Existing Home and folder-picker entry remain. | Final Compose/full matrix pass; source trace. |
| Top-level Search / ThisDevice | `SearchViewModel.kt; SearchScreen.kt; SupportedRootRegistry.kt` | `Intentional I3` | Search scopes remain; eligible audio gains Play. | Search Compose `3/3`; Search Play `2/2`; host tests. |
| Files contextual Search / CurrentFolder | `FilesViewModel.kt; SearchViewModel.kt; SearchScreen.kt` | `Reviewed - no user-visible regression found` | Contextual scope and result-open remain separate from Play. | Search Compose and existing search tests pass. |
| Files browsing and ordinary row taps | `FilesScreen.kt; FilesViewModel.kt` | `Intentional I2` | Play is additive; row semantics remain. | Files Play `3/3`; Files Compose `4/4`. |
| Files selection mode | `FilesScreen.kt; MainActivity.kt` | `Reviewed - no user-visible regression found` | Selection guard remains; Play hidden during selection. | Files Play/Compose pass. |
| Rename | `FilesViewModel.kt; SafStorageProvider.kt` | `Reviewed - no user-visible regression found` | Rename flow remains intact. | Files Compose/full matrix pass. |
| Delete | `FilesViewModel.kt; LocalStorageProvider.kt; SafStorageProvider.kt` | `Reviewed - no user-visible regression found` | Delete confirmation and mutation path remain intact. | Files Compose/full matrix pass. |
| Local Copy/Move | `OperationManager; LocalStorageProvider.kt` | `Reviewed - no user-visible regression found` | Transfer semantics unchanged. | Local/S​​AF transfer groups pass. |
| SAF -> Local Copy/Move | `SafStorageProvider.kt; OperationManager` | `Reviewed - no user-visible regression found` | Durable source/delete ordering remains. | SAF transfer groups pass. |
| Operations panel/cancellation | `OperationsPanel.kt; OperationsViewModel.kt` | `Reviewed - no user-visible regression found` | Existing panel/reconciliation remains. | Operations/database groups pass. |
| Bottom navigation/adaptive shell | `AppShell.kt; AppNavigation.kt; MainActivity.kt` | `Reviewed - no user-visible regression found` | Destinations, selected state, bottom bar, and rail remain. | Production/UI matrix pass; navigation host tests pass. |
| Back and Files detail | `MainActivity.kt; FilesViewModel.kt; AppNavigation.kt` | `Reviewed - no user-visible regression found` | Back/detail/selection behavior remains. | Full UI matrix pass. |
| Settings | `SettingsScreen.kt; MainActivity.kt` | `Reviewed - no user-visible regression found` | Settings remains read-only with no playback settings. | Source trace and full matrix. |
| Music empty/active/error controls | `MusicScreen.kt; PlaybackCoordinator.kt; PlaybackModels.kt` | `Intentional I4` | Truthful empty, active, paused, ended, error, duration, and seek states. | Music `13/13`; media `7/7`. |
| Local playback | `PlaybackCoordinator.kt; LocalStorageProvider.kt; FilesScreen.kt; SearchScreen.kt` | `Intentional I1/I2/I3` | WAV/FLAC play through service with progress and controls. | Media playback `7/7`. |
| Controlled SAF semantics | `SafStorageProvider.kt; TestDocumentsProvider.java` | `Intentional I1` | Per-item regular/pipe/unknown/provenance matrix remains truthful. | Controlled SAF `7/7`. |
| Real persisted-SAF playback | `SafDevicePlaybackInstrumentedTest.kt` | `Intentional I1` | Normal DocumentsUI grant, real provider, ExoPlayer READY/PLAYING, progress, and seek gating pass. | Real SAF `1/1`; full matrix. |
| MediaSession/system controls/background lifecycle | `OmniFilePlaybackService.kt; PlaybackCoordinator.kt; AndroidManifest.xml` | `Intentional I5` | Service/session, background lifecycle, and system controls remain coherent. | MediaSession `5/5`; lifecycle `2/2`; full matrix. |

## Evidence Appendix

### Behavior Graph Deltas

| ID | Surface | Baseline path | After-change path | Result |
| --- | --- | --- | --- | --- |
| `B1` | Files Play | Entry -> ordinary row tap | Entry -> eligibility -> Play -> coordinator -> provider -> service | Additive; no regression. |
| `B2` | Search Play | Result -> result-open | Result -> result-open or Play -> coordinator | Additive; no regression. |
| `B3` | Music | Destination -> truthful empty/no library | Destination -> state-driven Now Playing | Intentional VS07 change; 13/13 pass. |
| `B4` | Local playback | No playback path | Entry -> checked local transport -> service player | 7/7 pass. |
| `B5` | SAF playback | No playback path | Entry -> persisted grant -> descriptor probe -> service player | Real SAF 1/1 and full matrix pass. |
| `B6` | Shell/navigation | MainActivity -> shell/routes/Back | Same path plus playback collection/actions | Full UI/lifecycle matrix pass. |
| `B7` | Operations/mutations | Files -> mutation/transfer -> operations | Same path; playback adapter is separate | Existing operation groups pass. |
| `B8` | Background/session | No playback service | Play -> controller -> MediaSessionService -> FGS/session | Service/lifecycle tests pass. |

### Verification Commands and Results

- Strict host/lint/build command -> `BUILD SUCCESSFUL`; host `23 classes, 115 tests, 0 failures, 0 errors, 0 skipped`.
- Focused Compose/UI -> Files Play `3/3`, Files Compose `4/4`, Music `13/13`, Search Play `2/2`, Search Compose `3/3`.
- Lifecycle -> `2/2` pass on awake Pixel with correct `com.omnifile` package.
- Media playback -> `7/7` pass.
- MediaSession/binder -> `5/5` pass.
- Controlled SAF -> `7/7` pass.
- Real persisted SAF -> `1/1` pass after normal DocumentsUI grant.
- Final full connected matrix -> **`77/77 pass, 0 failures, 0 errors, 0 skipped`**.
- Zed diagnostics -> no relevant errors or warnings in changed test files.
- Debug APK SHA-256 -> `a67477b101758a1305e547fa4e015c51958bafd9b4c57b1c8c0f8a012eaf3f96`.
- AndroidTest APK SHA-256 -> `d9c7145630d5bb7ff6161c5588d766f979ba2efefb2f01f7952b84453fdbbdc3`.

### Dismissed Candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| `Play action changed ordinary Files row behavior` | `dismissed` | Explicit Play and ordinary row tests pass. |
| `Search Play changed result-open or scope behavior` | `dismissed` | Search Compose/Play and existing search tests pass. |
| `Music renders fabricated duration/metadata or enables false seek` | `dismissed` | Music `13/13`, host state tests, and real media/SAF tests pass. |
| `Activity recreation creates duplicate player/session` | `dismissed` | Lifecycle `2/2` and service ownership tests pass. |
| `SAF playback depended on fabricated grant` | `dismissed` | DocumentsUI grant was physically approved; real SAF `1/1` passed. |

## Report Self-Check

- Every requested VS06 user-visible surface appears in the Coverage Ledger.
- No confirmed regression was omitted from the findings index.
- Intentional VS07 changes are separated from regression findings.
- Full final runtime evidence, including real SAF and binder security, is recorded.
- Recommendation follows the mapping rules: no unresolved findings or incomplete user-visible coverage remains.
