# User-Visible Regression Audit

## Scope

- Review date: `2026-09-22`
- Requested outcome: `VS06 -> final VS07 regression audit`
- Scope reviewed: `commit range`
- Baseline: `67a8e0e08d3b6ad03165d0d962ad8868b00c661c` (published VS06)
- Target: `51b83a02e4e805dd2899e402c14a3e5d372b43d2` (final VS07 source target)
- Completion: `Incomplete - target-device UI/lifecycle acceptance was blocked by Dozing/NotificationShade interference; real persisted-SAF playback had no physical grant.`
- Assumptions: `The VS07 provider-neutral playback changes are intentional per the handoff and architecture requirements. Runtime evidence is attributed only to the final target when rebuilt after 51b83a0.`

## Gate Snapshot

- Recommendation: `Discuss`
- Completion: `Incomplete - two acceptance surfaces remain unverified on the connected device.`
- Why now: `No confirmed VS06 regression was found in static tracing, host tests, or passing non-UI device groups, but the new UI/lifecycle and real-SAF paths cannot be closed from the current device state.`
- Must-review now: `F1 and F2 are coverage findings, not confirmed product regressions.`
  1. `F1` Files/Search/Music Compose and Activity lifecycle coverage is invalid while the device is Dozing and externally focused.
  2. `F2` Real persisted-SAF ExoPlayer playback did not execute because no readable persisted tree exists.
- Findings count: `Block 0 | Discuss 2 | Watch 0 | Intentional 7`
- Coverage confidence: `medium`
- Behavior graph coverage: `built for all requested user journeys; physical UI evidence partial`
- Biggest blind spot: `The connected device reports mWakefulness=Dozing, NotificationShade focus, and no focused app; the installed package is a stale POC package rather than this checkout's com.omnifile package.`

## Complete Findings Index

| ID | Action | Surface | User-visible outcome | Confidence |
| --- | --- | --- | --- | --- |
| `F1` | `Discuss` | Files/Search/Music UI and Activity lifecycle | The final target run cannot establish that new Play affordances, Music controls, recreation, or background behavior render correctly on a valid awake target. | High for coverage gap; low for product impact |
| `F2` | `Discuss` | Real persisted-SAF playback | A real DocumentsUI grant plus provider and ExoPlayer path remains unverified. | High for coverage gap; low for product impact |

No confirmed VS06 -> VS07 user-visible behavioral regression was identified.

## Block

None.

## Discuss

### F1 Discuss - Target-device UI/lifecycle acceptance is blocked by device state

User impact: The current run cannot prove or disprove the new Files/Search Play buttons, Music Now Playing controls, or Activity recreation/background behavior on the target device.

Review reason: These flows are in VS07 scope, but the final connected run reports 25 `No compose hierarchies found` failures and 2 ActivityScenario `STOPPED` failures while `dumpsys power` reports `mWakefulness=Dozing` and `dumpsys activity` reports `NotificationShade` focus with no focused app.

Surface: Files Play, Search Play, Music, Activity recreation, background playback, adaptive shell observation.

Confidence: High for the evidence gap; low that the implementation itself is broken.

Look here first:
- [final connected result XML](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/build/outputs/androidTest-results/connected/debug/TEST-Pixel%207a%20-%2016.xml)
- [final lifecycle test](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/androidTest/java/com/omnifile/media/PlaybackLifecycleInstrumentedTest.kt#L42)

Behavior delta:
- Before: VS06 shell and Files/Search behavior had baseline evidence.
- After: VS07 adds explicit Play actions and a state-driven Music surface, but the current physical rerun cannot observe those surfaces because the device is not presenting the test Activity.

Evidence:
- Final target matrix: `75 recorded, 47 passed, 28 failures, 0 errors, 0 skipped`.
- All 25 Compose failures report no hierarchy; both lifecycle failures end with `STOPPED` rather than `RESUMED`.
- Focused media engine/service/controlled-SAF tests pass independently, reducing evidence that the failure is in the playback engine itself.

Reviewer action:
`Run the final APK on an awake/unlocked device with the correct com.omnifile package installed, then rerun Files/Search/Music and PlaybackLifecycle tests before treating UI acceptance as closed.`

### F2 Discuss - Real persisted-SAF playback remains unverified

User impact: A user selecting a real SAF tree may still encounter provider-specific playback, duration, or seek behavior that the controlled provider does not expose.

Review reason: VS07 adds real `content://` playback, while the final real-device test is intentionally gated on a physical persisted readable grant and did not execute.

Surface: SAF audio selected from Files/Search and played through Music and the MediaSession service.

Confidence: High for the coverage gap; low that a regression exists.

Look here first:
- [real SAF acceptance test](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/androidTest/java/com/omnifile/media/SafDevicePlaybackInstrumentedTest.kt#L42)
- [SAF playback source adapter](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-media-playback-v1/app/src/main/java/com/omnifile/storage/SafStorageProvider.kt#L147)

Behavior delta:
- Before: VS06 had real SAF browse/transfer paths but no playback path.
- After: VS07 adds per-item SAF transport and seekability probing; the physical persisted-grant-to-ExoPlayer path remains unexecuted.

Evidence:
- `SafDevicePlaybackInstrumentedTest` records `AssumptionViolatedException: no persisted readable SAF tree on this device: []`.
- Controlled `SafPlaybackSourceInstrumentedTest` passes all 7 cases: regular-file SEEKABLE, pipe NOT_SEEKABLE, UNKNOWN, permission, missing, provider-unavailable, and stale-reference behavior.

Reviewer action:
`Physically persist a readable SAF tree containing the expected fixture, then rerun the real SAF playback test on the final target without fabricating a grant.`

## Watch

None.

## Intentional Changes

- `I1` Provider-neutral `PlaybackItem`, shared coordinator, opaque provider-scoped identity, and truthful per-item seekability were added as the VS07 playback contract.
- `I2` Files adds an explicit Play action only for eligible audio and hides it during selection; ordinary row navigation/selection remains separate.
- `I3` Search adds the same explicit Play action while retaining result-open behavior and CurrentFolder/ThisDevice search semantics.
- `I4` Music changes from the VS06 empty/no-library surface to truthful single-item Now Playing with mapped errors, known duration/progress, and seek only when proven.
- `I5` Playback ownership moves to one service-owned ExoPlayer/MediaSession with a process-scoped controller and background/system-control support.
- `I6` Media playback foreground-service and notification permissions are additive; no storage, microphone, internet, or broad storage permission was introduced.
- `I7` Home, Settings, Operations, bottom navigation/rail, Back, selection, Rename/Delete, Local Copy/Move, SAF -> Local Copy, and search provider behavior remain unchanged by static trace and passing host/non-UI tests.

## Coverage Ledger

| Surface / path | Touched files or entry points | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| Home source overview and SAF-folder entry | `MainActivity.kt; HomeScreen.kt; HomeViewModel.kt` | `Reviewed - no user-visible regression found` | Existing source overview and folder-picker entry remain; playback is additive. | Static trace; host/device storage groups. |
| Top-level Search / ThisDevice scope | `SearchViewModel.kt; SearchScreen.kt; SupportedRootRegistry.kt` | `Intentional I3` | Search scopes and provider resolution remain; eligible results gain Play. | Host search tests; passing non-UI device groups. |
| Files contextual Search / CurrentFolder scope | `FilesViewModel.kt; SearchViewModel.kt; SearchScreen.kt` | `Reviewed - no user-visible regression found` | Current-folder search and result-open action remain distinct from Play. | Source trace and host search tests. |
| Files browsing and ordinary row taps | `FilesScreen.kt; FilesViewModel.kt` | `Intentional I2` | Explicit Play is additive; ordinary row taps retain prior semantics. | Source guard; physical assertion blocked by F1. |
| Files selection mode | `FilesScreen.kt; MainActivity.kt` | `Reviewed - no user-visible regression found` | Play is hidden during selection and mutation callbacks remain separate. | Source guard; physical assertion blocked by F1. |
| Rename | `FilesViewModel.kt; SafStorageProvider.kt` | `Reviewed - no user-visible regression found` | Rename behavior is outside playback adapter method and existing storage tests pass. | Passing storage device group; source trace. |
| Delete | `FilesViewModel.kt; LocalStorageProvider.kt; SafStorageProvider.kt` | `Reviewed - no user-visible regression found` | Delete/refresh/capability path remains separate from playback resolution. | Passing storage device group; source trace. |
| Local Copy/Move | `OperationManager; LocalStorageProvider.kt` | `Reviewed - no user-visible regression found` | Transfer implementation is unchanged by optional playback capability. | Host operations; passing local/SAF transfer tests. |
| SAF -> Local Copy/Move | `SafStorageProvider.kt; OperationManager` | `Reviewed - no user-visible regression found` | Durable source-delete ordering remains separate. | Passing SAF transfer tests. |
| Operations panel and cancellation | `OperationsPanel.kt; OperationsViewModel.kt; MainActivity.kt` | `Reviewed - no user-visible regression found` | Existing panel and reconciliation flow remain. | Host/database and device operation tests. |
| Bottom navigation / adaptive shell | `AppShell.kt; AppNavigation.kt; MainActivity.kt` | `Reviewed - no user-visible regression found` | Destinations and selected-state logic are unchanged; expanded rail physical observation is included in F1 limitation. | Host navigation tests; static trace. |
| Back and Files detail | `MainActivity.kt; FilesViewModel.kt; AppNavigation.kt` | `Reviewed - no user-visible regression found` | Existing Back/selection/detail behavior remains. | Host navigation tests; source trace. |
| Settings | `SettingsScreen.kt; MainActivity.kt` | `Reviewed - no user-visible regression found` | Settings remains read-only and playback adds no settings. | No settings behavior delta. |
| Music no-media and active controls | `MusicScreen.kt; PlaybackCoordinator.kt; PlaybackModels.kt` | `Intentional I4` | Truthful empty, active, paused, ended, error, duration, and seek behavior is implemented. | Host state tests; physical Compose assertions blocked by F1. |
| Local playback from Files/Search | `PlaybackCoordinator.kt; LocalStorageProvider.kt; FilesScreen.kt; SearchScreen.kt` | `Intentional I1/I2/I3` | Local WAV/FLAC playback and progress pass through the real service. | MediaPlayback 7/7. |
| Controlled SAF playback source semantics | `SafStorageProvider.kt; TestDocumentsProvider.java` | `Intentional I1` | Per-item descriptor capability matrix passes. | SafPlaybackSource 7/7. |
| Real persisted-SAF playback | `SafDevicePlaybackInstrumentedTest.kt; SafStorageProvider.kt` | `Finding F2` | Not executed because persisted readable permissions are empty. | Final XML assumption result. |
| MediaSession/system controls/background lifecycle | `OmniFilePlaybackService.kt; PlaybackCoordinator.kt; AndroidManifest.xml` | `Reviewed - no confirmed regression; F1 coverage gap` | Service/session/FGS checks pass; Activity lifecycle physical observation is blocked. | MediaSession 3/3; lifecycle F1. |

## Evidence Appendix

### Behavior Graph Deltas

| ID | Surface | Baseline path | After-change path | Delta | Result |
| --- | --- | --- | --- | --- | --- |
| `B1` | Files Play | Entry -> ordinary row tap | Entry -> eligibility -> Play -> coordinator -> provider -> service | Additive explicit action; ordinary row path retained. | No confirmed regression. |
| `B2` | Search Play | Result -> result-open action | Result -> either result-open or Play -> shared coordinator | Separate action; search scope retained. | No confirmed regression. |
| `B3` | Music | Destination -> truthful empty/no library | Destination -> coordinator state -> empty or current-item controls | Deliberate VS07 surface change. | Intentional. |
| `B4` | Local playback | No playback path | Entry -> checked file transport -> service player | New requested path. | Media 7/7. |
| `B5` | SAF playback | No playback path | Entry -> grant/tree check -> content transport -> descriptor probe -> service player | New requested path; physical endpoint unverified. | F2. |
| `B6` | Shell/navigation | MainActivity -> shell/destinations/Back | Same path plus playback collection/callbacks | Playback wiring added without route removal. | No confirmed regression. |
| `B7` | Operations/mutations | Files selection -> mutation/transfer -> operations panel | Same path; playback resolver is separate. | No input/order guard change found. | No confirmed regression. |
| `B8` | Background/session | No playback service | Play -> MediaController -> MediaSessionService -> FGS/session | New requested lifecycle/system-control effect. | Service tests pass; physical lifecycle F1. |

### Verification Commands

- `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process --dependency-verification=strict :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` -> `BUILD SUCCESSFUL`.
- Host suite -> `23 classes, 115 tests, 0 failures, 0 errors, 0 skipped`.
- Final focused media -> `MediaPlayback 7/7; MediaSession 3/3; controlled SAF 7/7`.
- Final full connected run -> `75 recorded, 47 passed, 28 failed, 0 errors, 0 skipped`.
- Device state -> `mWakefulness=Dozing; mCurrentFocus=NotificationShade; mFocusedApp=null`.
- No broad permission delta -> manifest contains FGS/media notification permissions only; no storage/microphone/internet permission.

### Evidence Limitations

- The final full run is not a green acceptance result and is not represented as one.
- The three focused media groups are green on the rebuilt final target.
- The real SAF test did not fabricate a grant and remains open.
- No confirmed VS06 user-visible regression was found; Discuss is required by incomplete coverage.

## Report Self-Check

- Every requested VS06 surface appears in the Coverage Ledger.
- Every finding appears in Complete Findings Index and its matching action section.
- Intentional product changes are separated from regression findings.
- Device limitations and exact next steps are explicit.
- Recommendation follows the mapping rules: unresolved Discuss coverage findings require Discuss.
- This report does not claim full physical acceptance or Pass.
