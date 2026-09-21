# User-Visible Regression Audit

## Scope

- Review date: `2026-09-22`
- Requested outcome: `review only`
- Continuation: `report only`
- Scope reviewed: `commit range`
- Baseline: `67a8e0e08d3b6ad03165d0d962ad8868b00c661c` (VS06)
- Target: `351cc27` (`feat(media): add provider-neutral playback v1`, VS07)
- Completion: `Incomplete - current physical UI/lifecycle acceptance was blocked by the connected device being in Dozing state; real persisted-SAF end-to-end playback also had no grant/fixture.`
- Assumptions: `The committed range is authoritative. The current checkout contained pre-existing untracked production/review artifacts; they were read as evidence only and were not included in the range or modified. Host validation used the checked-out toolchain/JDK 26. Prior VS07 artifacts were treated as historical evidence, not as a substitute for current-target execution.`

## Gate Snapshot

- Recommendation: `Discuss`
- Completion: `Incomplete - current UI/lifecycle and real persisted-SAF playback acceptance remain unverified in this environment.`
- Why now: `No confirmed VS06 user-visible regression was found, but the requested new UI/lifecycle and real-SAF playback paths could not all be re-proven on the current device.`
- Must-review now: `Full list is in Complete Findings Index; both items are acceptance/coverage gaps, not confirmed product regressions.`
  1. `F1` Current Compose UI and ActivityScenario evidence is invalidated by device Dozing state.
  2. `F2` Real persisted-SAF ExoPlayer playback did not execute because the device had no persisted readable SAF tree.
- Findings count: `Block 0 | Discuss 2 | Watch 0 | Intentional 7`
- Coverage confidence: `medium`
- Behavior graph coverage: `built for 8 surfaces; current physical UI evidence partial`
- Biggest blind spot: `The connected Pixel was in mWakefulness=Dozing, and the historical VS07 closure document names a different final HEAD than the requested 351cc27.`

## Complete Findings Index

| ID | Action | Surface | User-visible outcome | Confidence |
| --- | --- | --- | --- | --- |
| `F1` | `Discuss` | Files/Search/Music Compose UI and ActivityScenario lifecycle | Current device run cannot establish that the new Play affordances, Now Playing controls, or Activity recreation/background behavior render/work on an awake device. | High for coverage gap; low for product impact |
| `F2` | `Discuss` | SAF source resolution through real persisted grant and ExoPlayer | Real device SAF playback remains unverified; controlled DocumentsProvider probes pass, but no physical persisted readable tree existed. | High for coverage gap; low for product impact |

No confirmed VS06→VS07 user-visible behavioral regression was identified in the reviewed code range.

## Block

None.

## Discuss

### F1 Discuss - Current physical UI/lifecycle acceptance is blocked by device Dozing

User impact: The current run does not prove or disprove the new Files/Search Play buttons, Music Now Playing UI, or Activity recreation/background playback on the connected Pixel.

Review reason: These are explicitly in scope, and the current device result is not a valid UI result: the device reported `mWakefulness=Dozing`, `mFocusedApp=null`, and the test runner recorded Compose `No compose hierarchies found` plus `ActivityScenario` ending in `STOPPED`.

Surface: Files Play action, Search Play action, Music screen, playback Activity recreation/background lifecycle.

Confidence: High for the validation gap; low that the implementation itself is broken.

Look here first:
- [current connected-test result XML](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/build/outputs/androidTest-results/connected/debug/TEST-Pixel%207a%20-%2016.xml#L29)
- [Files/Music/Search wiring](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/MainActivity.kt#L157)

Behavior delta:
- Before: VS06 had existing shell/Files/Search Compose and ActivityScenario evidence.
- After: VS07 adds Play affordances, a state-driven Music surface, and service-owned lifecycle behavior, but the current physical rerun could not observe those surfaces because the device was Dozing.

Evidence:
- Current `connectedDebugAndroidTest`: `74 tests`, `28 recorded failures`, `0 errors`; the 25 Compose failures all report no Compose hierarchy, and the two lifecycle failures report that the Activity never became `RESUMED` and was last `STOPPED`.
- The current device state was independently observed as `mWakefulness=Dozing` with no focused app.
- Static path tracing shows the Play actions and coordinator wiring are present; the prior untracked VS07 artifact reports the corresponding UI/lifecycle tests passing, but its target metadata is not the requested `351cc27`.

Reviewer action:
`Re-run the focused Compose UI and PlaybackLifecycle tests on an awake/unlocked Pixel or equivalent device, then reconcile the result to 351cc27 before treating physical UI acceptance as closed.`

### F2 Discuss - Real persisted-SAF playback remains unverified

User impact: A user selecting a real SAF tree may still encounter a provider/device-specific playback or seekability issue that the controlled provider cannot expose.

Review reason: The requested scope includes SAF→Local operations and new SAF playback flows. The controlled `TestDocumentsProvider` proves source resolution and seekability classification, but it is not the real DocumentsUI grant plus real provider plus ExoPlayer path.

Surface: SAF audio opened from Files/Search and rendered in Music through the MediaSession service.

Confidence: High for the coverage gap; low that a regression exists.

Look here first:
- [declared real-SAF acceptance stop condition](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/androidTest/java/com/omnifile/media/SafDevicePlaybackInstrumentedTest.kt#L42)
- [SAF playback-source adapter](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/storage/SafStorageProvider.kt#L147)

Behavior delta:
- Before: VS06 had real SAF browse/transfer evidence but no playback path.
- After: VS07 adds SAF `content://` playback and per-item seek probing, but the real-device end-to-end test exits at its assumption because `persistedUriPermissions` is empty.

Evidence:
- Current connected result records `SafDevicePlaybackInstrumentedTest.persistedDeviceSafAudioPlaysThroughTheSession` as an assumption failure with `no persisted readable SAF tree on this device: []`.
- Current controlled SAF tests pass pipe-backed `NOT_SEEKABLE`, regular-file-backed `SEEKABLE`, `UNKNOWN` probe, permission, not-found, provider-unavailable, and stale-reference cases.
- The VS07 production note explicitly declares this physical-grant stop condition rather than fabricating a grant.

Reviewer action:
`Physically select and persist a readable SAF tree containing the documented fixture, then rerun the real SAF playback test on an awake device; keep the stop condition open until that evidence exists.`

## Watch

None.

## Intentional Changes

- `I1` Provider-neutral playback was added as a new optional storage capability: one `PlaybackItem`/`PlaybackCoordinator`, provider-specific transient transports, and truthful `SEEKABLE`/`NOT_SEEKABLE`/`UNKNOWN` behavior. This follows the requested VS07 mission and does not alter the base browse/mutation contract - [PlaybackSource.kt](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/storage/PlaybackSource.kt#L20).
- `I2` Files now exposes a trailing `Play` action only for eligible readable audio files, hides it in selection mode, and preserves ordinary row taps for directory navigation/selection - [FilesScreen.kt](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/ui/files/FilesScreen.kt#L355).
- `I3` Search now exposes the same explicit `Play` action for eligible results while retaining the existing result-open action as a separate control - [SearchScreen.kt](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/ui/search/SearchScreen.kt#L248).
- `I4` Music intentionally changes from the VS06 truthful “playback later” empty surface to a truthful single-item Now Playing surface with title/source, play/pause, duration/progress, mapped errors, and seek only when proven - [MusicScreen.kt](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/ui/music/MusicScreen.kt#L35).
- `I5` Playback lifetime is intentionally moved behind an exported Media3 `MediaSessionService` with one service-owned player/session, app-owned source selection, restricted external transport controls, and background/session support - [OmniFilePlaybackService.kt](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/media/OmniFilePlaybackService.kt#L17).
- `I6` Media playback manifest permissions, `mediaPlayback` foreground-service declaration, and one first-Play notification-permission request are additive; playback itself is not gated on notification visibility - [AndroidManifest.xml](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/AndroidManifest.xml#L3), [MainActivity.kt](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/MainActivity.kt#L283).
- `I7` Home, Settings, four-destination shell, Files detail navigation, Back/selection semantics, Rename/Delete, Local Copy/Move, SAF→Local Copy, and CurrentFolder/ThisDevice search remain existing VS06 paths; the VS07 delta only adds playback state/action wiring around them. The existing operation/search-provider device tests remained green.

## Coverage Ledger

| Surface / path | Touched files or entry points | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| Home source overview and SAF-folder entry | `MainActivity.kt`; `HomeScreen.kt`; `HomeViewModel.kt`; `SupportedRootRegistry.kt` | `Reviewed - no user-visible regression found` | Home remains the source overview; Local and Choose SAF folder entry points are unchanged. | MainActivity diff has no Home behavior deletion; prior VS06 shell evidence; host/device provider tests. |
| Top-level Search / ThisDevice scope | `MainActivity.kt`; `SearchViewModel.kt`; `SearchScreen.kt`; `SupportedRootRegistry.kt` | `Intentional I3` | This device continues to resolve live Local/SAF roots and partial failures; eligible audio results gain Play. | Host `ThisDeviceSearchTest`/SearchViewModel tests; source trace; current non-UI connected search-provider tests pass. |
| Files contextual Search / CurrentFolder scope | `MainActivity.kt`; `FilesViewModel.kt`; `SearchViewModel.kt`; `SearchScreen.kt` | `Reviewed - no user-visible regression found` | Current-folder scope and recursive result behavior remain; result open is separate from Play. | Existing source contract and `SearchScope.CurrentFolder` resolver; focused prior VS06 evidence; static callback trace. |
| Files browsing and ordinary row taps | `FilesScreen.kt`; `FilesViewModel.kt` | `Intentional I2` | Directory taps and selection semantics remain; audio files add an explicit Play affordance instead of changing row tap behavior. | [FilesScreen.kt](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/ui/files/FilesScreen.kt#L375); current Compose result is not usable because device Dozing. |
| Files selection mode / multi-selection | `FilesScreen.kt`; `FilesViewModel.kt`; `MainActivity.kt` | `Reviewed - no user-visible regression found` | Play is hidden when selection mode is active; existing selection callbacks and capability gating remain unchanged. | Source guard at `FilesScreen.kt` Play branch; prior VS07 artifact reports selection test pass; current UI rerun blocked by Dozing. |
| Rename | `FilesViewModel.kt`; `FilesScreen.kt`; `SafStorageProvider.kt` | `Reviewed - no user-visible regression found` | Rename dialog/capability behavior is unchanged; Saf adapter edits are additive playback plus formatting/explicit result branches. | No Rename production diff outside formatting; current `SafStorageProviderInstrumentedTest` rename cases pass. |
| Delete | `FilesViewModel.kt`; `FilesScreen.kt`; `LocalStorageProvider.kt`; `SafStorageProvider.kt` | `Reviewed - no user-visible regression found` | Delete confirmation, partial-delete handling, provider flags, and refresh semantics are unchanged. | No delete-path production delta; current SAF/storage operation tests pass. |
| Local Copy/Move | `FilesViewModel.kt`; `OperationManager`; `LocalStorageProvider.kt` | `Reviewed - no user-visible regression found` | Durable local copy/move behavior is unchanged by the optional playback interface. | Current `LocalTransferRuntimeInstrumentedTest` passes; host operation suite is green. |
| SAF→Local Copy/Move | `SafStorageProvider.kt`; `OperationManager`; `SafTransferRuntimeInstrumentedTest` | `Reviewed - no user-visible regression found` | Durable source-delete ordering remains intact; playback source resolution is a separate method. | Current `safToLocalCopyAndMoveUseDurableSourceDeleteOrdering` passes. |
| Local→SAF and SAF→SAF transfer paths | `SafStorageProvider.kt`; `LocalStorageProvider.kt`; `OperationManager` | `Reviewed - no user-visible regression found` | Finalization, conflict, and non-native move behavior remain unchanged. | Current three SAF transfer runtime cases pass; diff shows no transfer algorithm change. |
| Operations panel and cancellation | `MainActivity.kt`; `OperationsPanel.kt`; `OperationsViewModel.kt` | `Reviewed - no user-visible regression found` | Panel remains rendered below content, with existing conditional navigation-bar padding; operation reconciliation remains in the same launch effect. | MainActivity only adds playback state collection/action wiring; current operation/database tests pass. |
| Bottom shell / adaptive rail | `AppShell.kt`; `MainActivity.kt`; `AppNavigation.kt` | `Reviewed - no user-visible regression found` | Home/Search/Music/Settings destinations, selected state, compact bottom bar, and expanded rail are unchanged. | No AppShell/AppNavigation diff; host `AppNavigationStateTest` passes; expanded physical rail remains unverified. |
| Back navigation and Files detail | `MainActivity.kt`; `FilesViewModel.kt`; `AppNavigation.kt` | `Reviewed - no user-visible regression found` | Back still clears destination picker/selection, walks directories, then closes Files detail; contextual Search still returns to Files. | `consumeFilesBack`/`consumeBack` unchanged apart from spacing; prior VS06 direct shell evidence; host navigation tests. |
| Music with no media | `MusicScreen.kt`; `PlaybackCoordinator.kt` | `Intentional I4` | Honest empty state remains, now instructing users to play from Files/Search rather than claiming a library. | `MusicScreen` no-media branch; prior/current test source. Current physical Compose assertion blocked by Dozing. |
| Music active/paused/ended/error controls | `MusicScreen.kt`; `PlaybackCoordinator.kt`; `PlaybackModels.kt` | `Intentional I4` | Single current item, truthful duration/position, mapped errors, disabled unknown/non-seekable seek, and Play/Pause controls are added. | Host state/eligibility tests pass; prior artifact reports 13 Music tests; current UI run is F1. |
| Local playback from Files/Search | `PlaybackCoordinator.kt`; `LocalStorageProvider.kt`; `FilesScreen.kt`; `SearchScreen.kt` | `Intentional I1/I2/I3` | Local audio resolves to a checked `file://` source and plays through the service; unsupported/ineligible entries do not claim Play. | Current device MediaPlayback tests pass all 6; host LocalPlaybackSource tests pass. |
| Controlled SAF playback-source semantics | `SafStorageProvider.kt`; `TestDocumentsProvider.java` | `Intentional I1` | Per-item descriptor probing distinguishes pipe, regular-file, unknown, permission, not-found, provider, and stale cases. | Current device `SafPlaybackSourceInstrumentedTest`: 7/7 pass. |
| Real persisted-SAF playback | `SafDevicePlaybackInstrumentedTest.kt`; `SafStorageProvider.kt`; `PlaybackCoordinator.kt` | `Finding F2` | Real DocumentsUI grant plus ExoPlayer path not executed because no persisted readable tree/fixture was present. | Current XML assumption failure; declared VS07 stop condition. |
| MediaSession/system controls/background lifecycle | `OmniFilePlaybackService.kt`; `PlaybackCoordinator.kt`; manifest | `Finding F1` | Engine/session/service checks pass, but current ActivityScenario physical lifecycle checks were invalidated by Dozing. | Current media service tests pass 3/3; current lifecycle tests stopped; prior artifact reports lifecycle pass. |
| Settings | `SettingsScreen.kt`; `MainActivity.kt` | `Reviewed - no user-visible regression found` | Settings remains read-only current information; no playback-specific settings were added. | No Settings diff; source trace. |
| Manifest/data extraction/dependencies | `AndroidManifest.xml`; `data_extraction_rules.xml`; Gradle files | `Intentional I5/I6` | Media3/session and media foreground/notification contracts are additive; deprecated `allowBackup` is replaced with explicit extraction rules. | Lint passes; merged build compiles; no storage/microphone/internet permission added. |

## Evidence Appendix

### Behavior Graph Deltas

| ID | Surface | Baseline path | After-change path | Delta | Ledger / finding link |
| --- | --- | --- | --- | --- | --- |
| `B1` | Files Play | `Files entry -> ordinary row tap -> directory navigation or no-op for file` | `Files entry -> eligibility guard -> Play action -> MainActivity.playEntry -> PlaybackCoordinator -> provider source -> MediaSessionService` | Additive explicit action; ordinary row path remains. | `I2`; Reviewed |
| `B2` | Search Play | `Search result -> result button -> Files.openSearchResult` | `Search result -> either result button -> Files.openSearchResult, or Play button -> shared playback command` | Separate action added; result navigation callback retained. | `I3`; Reviewed |
| `B3` | Music | `Music destination -> static truthful empty/no-library copy` | `Music destination -> coordinator StateFlow -> NO_MEDIA or current item/status/controls` | Deliberate VS07 Now Playing surface. | `I4` |
| `B4` | Local playback | `No media source resolution` | `StorageEntry -> LocalStorageProvider.resolvePlaybackSource -> checked regular file/file:// -> coordinator -> service player` | New provider-neutral playback path; browse/mutation methods unchanged. | `I1`; Reviewed |
| `B5` | SAF playback | `No media source resolution` | `StorageEntry -> SAF checkedRef -> content:// + fd probe -> coordinator -> service player` | New per-item transport/seek probe; real grant path remains unverified. | `I1`; `F2` |
| `B6` | Shell/navigation | `MainActivity -> AppShell -> Home/Search/Music/Settings or Files/Search detail -> Back` | Same path plus playback state collection and Play callbacks | No route, bottom-bar, Back, or selection guard removed. | Reviewed |
| `B7` | Mutations/operations | `Files selection -> Rename/Delete or destination picker -> OperationManager -> OperationsPanel` | Same path; providers additionally implement optional playback-source interface | No mutation input, guard, ordering, or output changed. | Reviewed |
| `B8` | Background/session | `No playback service/session` | `Play -> MediaController -> exported MediaSessionService -> foreground media playback/session controls` | New requested lifecycle/system-control effect; current physical Activity lifecycle acceptance is F1. | `I5`; `F1` |

### Diff Inventory

| File or area | Classification | User-visible path considered |
| --- | --- | --- |
| `app/src/main/java/com/omnifile/media/*` | `surface/dependency` | Playback state, errors, service/session, source resolution |
| `app/src/main/java/com/omnifile/storage/PlaybackSource.kt` | `dependency` | Provider-neutral playback transport contract |
| `app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt` | `dependency` | Local playback; existing browse/mutation/transfer paths |
| `app/src/main/java/com/omnifile/storage/SafStorageProvider.kt` | `dependency` | SAF playback probe; existing rename/delete/transfer paths |
| `app/src/main/java/com/omnifile/AppContainer.kt` | `dependency` | Process-scoped coordinator; existing repository/provider registration |
| `app/src/main/java/com/omnifile/files/FilesRepository.kt` | `dependency` | Optional provider lookup; existing browse/mutation calls |
| `app/src/main/java/com/omnifile/MainActivity.kt` | `surface/dependency` | Shell, Home/Search/Files/Music/Settings, Back, notification prompt, playback callbacks |
| `app/src/main/java/com/omnifile/ui/files/FilesScreen.kt` | `surface` | Files row actions/selection |
| `app/src/main/java/com/omnifile/ui/search/SearchScreen.kt` | `surface` | Search result actions/navigation |
| `app/src/main/java/com/omnifile/ui/music/MusicScreen.kt` | `surface` | Music Now Playing |
| `app/src/main/AndroidManifest.xml`; `data_extraction_rules.xml` | `config` | Media service/notification/background contract; backup rule declaration |
| `app/build.gradle.kts`; `gradle/*` | `dependency/config` | Media3 build/runtime dependency |
| `TestDocumentsProvider.java` | `test-only` | Controlled SAF descriptor fixture; default pipe behavior retained |
| New host/instrumentation tests and FLAC asset | `test-only/generated fixture` | Playback and regression evidence |
| `docs/production/*`; `docs/review/*`; `tmp/reviews/*` | `docs-only/untracked evidence` | Review claims only; not part of requested commit range |

### Candidate Sweep Log

| Candidate | Decision | Reason |
| --- | --- | --- |
| `Home, Settings, bottom shell, or route selection regressed because MainActivity changed` | `dismissed` | No route/shell source changed; additions are playback state collection and callbacks; existing navigation host tests pass. |
| `CurrentFolder or ThisDevice Search semantics changed` | `dismissed` | Scope resolver/search engine path is unchanged in behavior; only result rows gain an explicit Play branch; host ThisDevice/Search tests pass. |
| `Search result opens Files incorrectly after row layout split` | `dismissed` | Existing `onOpenResult` button remains separate from `onPlayResult`; `MainActivity.openSearchResult` is unchanged. Current physical UI proof is covered by F1. |
| `Selection mode can accidentally start playback` | `dismissed` | Files Play branch is guarded by `!selectionMode`; prior artifact reports the focused test pass. |
| `Rename/Delete changed because SafStorageProvider diff is large` | `dismissed` | Functional diff is optional playback interface plus formatting/explicit Failure branches; current SAF rename/delete tests pass. |
| `Local Copy/Move or SAF→Local Copy changed` | `dismissed` | Transfer implementations are unchanged; current local and SAF runtime operation tests pass. |
| `TestDocumentsProvider broke existing pipe-backed tests` | `dismissed` | Regular-file backing is opt-in; default content still uses the existing pipe path; current SAF suite passes. |
| `Playback exposes raw paths/URIs or claims universal seekability` | `dismissed` | Playback identity hashes entry identity, source transports stay in adapters, and controlled SAF probe tests distinguish pipe/regular/unknown. |
| `Notification denial prevents playback` | `dismissed` | `playEntry` launches the permission request but immediately invokes the coordinator; service contract and local playback tests pass. |
| `Historical VS07 artifact proves 351cc27 completely` | `not relied upon` | The untracked production note names a different final HEAD (`e6e2f16...`) and range; current source/test commands were used where possible, with the mismatch retained as a blind spot. |

### Verification Commands

- `git --no-optional-locks status --short --branch` -> branch clean of tracked modifications; pre-existing untracked `docs/production`, `docs/review`, and `tmp/reviews` artifacts remained untouched.
- `git --no-pager diff --stat 67a8e0e08d3b6ad03165d0d962ad8868b00c661c 351cc27` -> 35 committed files, `2934 insertions(+), 64 deletions(-)`.
- `./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process --dependency-verification=strict :app:testDebugUnitTest` -> `115 tests, 0 failures, 0 errors, 0 skipped`.
- `./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process --dependency-verification=strict :app:lintDebug` -> `BUILD SUCCESSFUL`.
- `./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process --dependency-verification=strict :app:connectedDebugAndroidTest` -> `74 tests recorded; 28 failures, 0 errors, 0 skipped`; failures are 25 no-Compose-hierarchy results, 2 ActivityScenario STOPPED results, and 1 declared no-SAF-grant assumption result while the device was Dozing.
- `adb shell dumpsys power` -> `mWakefulness=Dozing`; `adb shell dumpsys activity activities` -> `mFocusedApp=null`.
- Current connected non-UI groups: media engine `6/6`, MediaSession/service `3/3`, controlled SAF probe `7/7`, local transfer `1/1`, SAF transfer `3/3`, operation DB `4/4`, search provider `3/3`, storage `17/17`, ProductionScaffold `2/2`.
- `git --no-pager diff --check 67a8e0e08d3b6ad03165d0d962ad8868b00c661c 351cc27` -> reports only new blank-at-EOF warnings in three added test files; no functional whitespace/content issue was inferred.

### Supporting Code Links

| ID | Role | Link | Why it matters |
| --- | --- | --- | --- |
| `F1` | `entry/wiring` | [MainActivity playback/UI wiring](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/MainActivity.kt#L157) | Connects coordinator state to Music and Play callbacks to Files/Search. |
| `F1` | `physical evidence` | [connected result XML](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/build/outputs/androidTest-results/connected/debug/TEST-Pixel%207a%20-%2016.xml#L130) | Shows current Compose failures and their common no-hierarchy cause. |
| `F2` | `source resolution` | [SafStorageProvider.resolvePlaybackSource](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/storage/SafStorageProvider.kt#L147) | New real-SAF probe/transport path. |
| `F2` | `acceptance gate` | [SafDevicePlaybackInstrumentedTest](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/androidTest/java/com/omnifile/media/SafDevicePlaybackInstrumentedTest.kt#L42) | Explicitly requires a physical persisted readable grant and fixture. |
| `I2` | `Files output` | [Files Play branch](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/ui/files/FilesScreen.kt#L392) | Shows eligibility and selection-mode guard. |
| `I3` | `Search output` | [Search result action split](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/ui/search/SearchScreen.kt#L265) | Shows navigation and Play as separate controls. |
| `I4` | `Music output` | [Music Now Playing surface](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/ui/music/MusicScreen.kt#L47) | Shows truthful empty/active/error/control behavior. |
| `I5` | `service effect` | [OmniFilePlaybackService](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/media/OmniFilePlaybackService.kt#L31) | Shows service-owned player/session and controller gate. |
| `I7` | `mutation path` | [FilesViewModel mutation entry points](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/files/FilesViewModel.kt#L296) | Existing Rename/Delete/transfer owner remains separate from playback. |

### Blind Spots

| Area | Risk introduced by the blind spot | What would resolve it |
| --- | --- | --- |
| `Current Pixel is Dozing` | Current physical Compose and ActivityScenario evidence is unusable; it cannot establish UI rendering or lifecycle behavior. | Use an awake/unlocked interactive Pixel or equivalent and rerun focused Files/Search/Music/lifecycle tests. |
| `No persisted readable SAF tree` | Real provider URI grant, provider-specific ExoPlayer behavior, and physical SAF source lifetime remain unknown. | Use DocumentsUI to persist a readable tree containing the fixture, then rerun `SafDevicePlaybackInstrumentedTest`. |
| `Historical VS07 production note target mismatch` | The untracked note claims final HEAD `e6e2f16...`, not requested target `351cc27`; its APK hashes and 72/73 result cannot be independently attributed to this commit from the note alone. | Re-record a target-351cc27-specific device artifact/hash and attach the exact test XML. |
| `Expanded-width rail runtime` | Adaptive rail geometry/insets were not physically exercised in this run; a narrow-screen result would not detect rail-specific layout regressions. | Run a focused expanded-width/large-screen UI check while awake. |
| `Rename/Delete while a track is actively playing` | Static contract says source deletion should surface an error/ended state, but the cross-feature live mutation-to-playback transition was not directly exercised. | Add/run a focused device scenario: start local/SAF playback, rename/delete, verify truthful state and no crash. |
| `System media-controller seek command availability` | Service statically exposes standard seek command to external controllers even before per-item seekability is known; UI/coordinator seek is guarded, but Bluetooth/SystemUI seek behavior was not directly exercised for non-seekable SAF. | Add a system-controller/controlled non-seekable test or restrict external command availability per current item capability. |

### Report Self-Check

- `yes` Every touched user-visible or unknown-impact surface appears in Coverage Ledger.
- `yes` Every finding in an action section appears in Complete Findings Index.
- `yes` Every `Finding F#` ledger row has a matching card.
- `yes` Every `Not covered` user-visible row has a reason and next verification step; unresolved acceptance gaps are represented as `F1`/`F2`.
- `yes` Every user-visible or unknown-impact surface has a behavior graph/direct path evidence or is explicitly marked with an acceptance gap.
- `yes` Recommendation follows the mapping rules: unresolved `Discuss` coverage gaps require `Discuss`.
