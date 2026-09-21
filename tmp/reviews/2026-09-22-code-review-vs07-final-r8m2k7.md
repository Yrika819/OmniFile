# Code Review Report

## Report Contract

- Report type: `code-review`
- Report ID: `cr-20260922-r8m2k7`
- Review chain ID: `rc-20260922-n4p6s1`
- Review generation: `0`
- Review trigger: `initial`
- Parent review report ID: `None`
- Parent review report path: `None`
- Parent resolution ID: `None`
- Parent resolution path: `None`
- Generated at: `2026-09-21T18:58:19Z`
- Report path: `tmp/reviews/2026-09-22-code-review-vs07-final-r8m2k7.md`
- Source skill: `code-review`
- Status: `Review incomplete`
- Git mutation during review: `None`
- Scope fingerprint: `sha256:2fec0fe67fcc82a6f9c2755aebcbfcdccf5a01a94cd6b2388d654cf4bfca463c`

Treat this completed report as the fixed review input for downstream work. Do not rewrite it during receiving or implementation; record dispositions, challenges, code changes, and verification in a separate `receiving-code-review` resolution report that references this Report ID.

## Scope

- Review date: `2026-09-22`
- Scope kind: `commit range`
- Scope description: `Read-only final review of 67a8e0e08d3b6ad03165d0d962ad8868b00c661c..351cc27 for VS07 provider-neutral media playback, with current target files re-read after fixes.`
- Scope mode: `full frozen scope`
- Baseline: `67a8e0e08d3b6ad03165d0d962ad8868b00c661c`
- Target: `351cc273107e7447e195a15dae92de029ecb3abd`
- Changed paths: `35`
- Diff size: `2934 insertions, 64 deletions`
- Completion: `Incomplete - static review covered the changed scope, but the current device run did not verify Activity lifecycle behavior and skipped real persisted-tree SAF playback because the device had no readable persisted SAF grant.`
- Requirements consulted: `docs/architecture/04_MEDIA_INTEGRATION.md; docs/architecture/ADR/ADR-002-provider-scoped-identity.md; docs/architecture/02_STORAGE_MODEL.md; tmp/reviews/2026-09-21-code-review-vs07-review-a.md; current changed tests and source comments.`
- Prior resolution consulted: `None`
- Assumptions: `The approved VS07 design and the existing provider capability contract are authoritative for expected behavior; no product owner clarification was available beyond those artifacts.`
- Excluded as unrelated: `Pre-existing untracked documentation/review files and generated build reports; they were read only where they supplied requirements or verification evidence.`

## Review Orchestration

- Assessment subagent: `Subagent unavailable - coordinator fallback; no subagent primitive is exposed in this environment.`
- Orchestration decision: `Single reviewer`
- Decision confidence: `high`
- Decision rationale: `The range is broad but cohesive around one playback/session contract. A single coordinator can preserve the cross-layer ownership and identity context while explicitly partitioning security, adapter, race, lifecycle, and test surfaces in the coverage ledger.`
- Coordinator override: `None`
- Context or tool limits: `No delegated reviewers; device was available, but its current lifecycle run was disrupted and it had no persisted readable SAF tree.`

### Risk Dimensions

- `Player/session ownership and coordinator state races can make actual audio, MediaSession state, and the single UI StateFlow disagree.`
- `Provider and SAF trust boundaries include blocking ContentResolver I/O, grant revocation, descriptor seekability, and stale provider-scoped references.`
- `Exported MediaSession command policy must constrain both the in-app controller and system/external controllers.`
- `Runtime coverage is split between host tests, controlled DocumentsProvider probes, and a physical persisted SAF grant that was unavailable on the device.`

### Reviewer Assignments

| Reviewer | Angle | Owned surfaces | Mandatory cross-checks | Status |
| --- | --- | --- | --- | --- |
| `R1` | Coordinator deep review: correctness, concurrency, adapters, security, lifecycle, and tests | `PlaybackCoordinator.kt; OmniFilePlaybackService.kt; PlaybackModels.kt; PlaybackSource.kt; LocalStorageProvider.kt; SafStorageProvider.kt; AppContainer/MainActivity; manifest; all changed media tests` | `Trace UI -> coordinator -> provider -> MediaController -> service; compare seek/grant/security behavior with approved design; re-read current target files after verification.` | `Fallback` |

### Synthesis Statement

`The coordinator independently re-read every accepted candidate against the current target files and the approved design sources, merged duplicate race observations into the replacement/state finding, and kept product-approved exported-service behavior dismissed. Remaining blind spots are the failed ActivityScenario lifecycle run and the unexecuted real persisted-tree SAF ExoPlayer path.`

## Review Snapshot

- Recommendation: `Changes requested`
- Completion: `Incomplete - exact runtime blind spots are listed below.`
- Why now: `The implementation builds and local/controlled playback paths pass, but four approval-relevant state, threading, command-policy, and error-lifetime defects remain statically visible.`
- Must-review now:
  1. `F1` Failed replacement can leave the previous item playing while the new item is shown as an error.
  2. `F3` System controllers retain seek permission even when the current source is not proven seekable.
  3. `F4` Player errors can be overwritten by the next ordinary player-state callback.
- Findings count: `Blocker 0 | Major 4 | Minor 0 | Question 0`
- Standalone test gaps: `Blocker 0 | Major 2 | Minor 1`
- Coverage confidence: `medium`
- Biggest blind spot: `Real SAF content URI playback through ExoPlayer with a physically persisted readable tree, plus Activity recreation/backgrounding on the current device.`

## Complete Findings Index

| ID | Severity | Surface | Review risk | Confidence | Origin | Verification | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `F1` | `Major` | `PlaybackCoordinator.play` | Failed replacement leaves audio/session and UI state on different items. | `high` | `Coordinator` | `Static trace of failure branches and player ownership; re-read current target.` | `behavior; entry=playback replacement failure; contract=failed play request must not leave an earlier item playing while the state reports the replacement error; effect=audio/session and NowPlayingState diverge` | `ifp-sha256:fec3cd265d92fcae4e5a3c0bd07695ae7541a1ad4f1bb2690edda706e46b0b75` | `kind:approved-design; strength:authoritative; evidence:tmp/reviews/2026-09-21-code-review-vs07-review-a.md#L70-L72 and current PlaybackCoordinator contract` |
| `F2` | `Major` | `PlaybackCoordinator` provider resolution | Local/SAF provider I/O runs on the main/UI dispatcher and can freeze the Activity. | `high` | `Coordinator` | `Static dispatcher trace plus current Local/SAF calls; compared with existing IO-dispatched storage flows.` | `behavior; entry=provider source resolution; contract=provider I/O must not block the main/UI thread; effect=slow Local or SAF resolution freezes the Activity and can cause ANR` | `ifp-sha256:cc8f45ed7556c975d10ad83e4d38c11fe7640148337c0dde8ce9a5f69cfa0aaf` | `kind:hard-invariant; strength:authoritative; evidence:Android main-thread responsiveness contract for blocking ContentResolver/filesystem I/O` |
| `F3` | `Major` | `MediaSession.Callback.onConnect` | External/system controllers can send seek commands for an unseekable current source. | `high` | `Coordinator` | `Static command-set trace; coordinator-only seek guard does not cover remote MediaController commands.` | `behavior; entry=MediaSession external transport control; contract=seek commands must be unavailable unless the current source is proven seekable; effect=system controller can seek an unseekable source` | `ifp-sha256:4c2448474e5e6be50b8c61ca08e2da4017c2215f3fdd6a7215588d43831c73f0` | `kind:approved-design; strength:authoritative; evidence:tmp/reviews/2026-09-21-code-review-vs07-review-a.md#L54-L57 and #L50-L52` |
| `F4` | `Major` | `PlaybackCoordinator.playerListener` | Mapped Media3 failures are not durable in the single state source. | `medium` | `Coordinator` | `Static callback trace; framework event-order confirmation is still the requested focused runtime proof.` | `behavior; entry=player error callback; contract=Media3 playback errors must remain represented as ERROR until a new request or explicit recovery; effect=onPlaybackStateChanged overwrites the mapped error with BUFFERING/READY` | `ifp-sha256:5ec39d0c05c1447fc1f424836c3af1d47fe7dc949b953ff2838e5ee96147070a` | `kind:approved-design; strength:authoritative; evidence:tmp/reviews/2026-09-21-code-review-vs07-review-a.md#L70-L72 and current PlaybackError contract` |

## Blocker

None.

## Major

### F1 Major - Failed replacement leaves the previous item playing

Impact: `The user can hear item A while Music/notification state reports item B as ERROR; system controls continue to operate on A. This violates the one-current-item/session-state invariant.`

Review reason: `Every failed play attempt from Files or Search can create this mismatch when playback is already active, and no explicit stop/clear or preserved-old-state policy resolves it.`

Surface: `PlaybackCoordinator.play replacement and error branches.`

Issue key: `behavior; entry=playback replacement failure; contract=failed play request must not leave an earlier item playing while the state reports the replacement error; effect=audio/session and NowPlayingState diverge`

Issue fingerprint: `ifp-sha256:fec3cd265d92fcae4e5a3c0bd07695ae7541a1ad4f1bb2690edda706e46b0b75`

Expected basis: `kind:approved-design; strength:authoritative; evidence:tmp/reviews/2026-09-21-code-review-vs07-review-a.md#L70-L72 and current PlaybackCoordinator contract`

Confidence: `high`

Origin: `Coordinator`

Coordinator verification: `The current code cancels only playJob, publishes a pending B state, and on resolver-null/failure calls publishError(B); those branches never call stop, clearMediaItems, or otherwise change the service player. The player therefore remains on A while the StateFlow contains B.`

Look here first:
- [`PlaybackCoordinator.play` replacement/error path](app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt#L64-L110)
- [`PlaybackCoordinator.publishError`](app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt#L260-L271)

Failure mode:
- Expected: `A failed B request must either leave A as the represented current item or atomically stop/clear A before publishing B's error; it must not report B as the sole state while A continues playing.`
- Current: `play(B)` cancels only the resolver job, immediately writes BUFFERING/B, and then publishes B's ERROR on resolver failure. The service player remains A because no player command is issued in those failure branches.`

Evidence:
- `The successful branch replaces the player only at lines 175-178; the failure branches at lines 95-110 return before that replacement. The existing instrumented missing-source test starts with no prior player, so it does not cover this state divergence.`

Assumptions and limits:
- `The exact user-facing policy may choose to stop or preserve A, but the single StateFlow cannot truthfully represent B's error and A's ongoing audio simultaneously.`

Reviewer action:
`request fix and add a regression test for failure while another item is playing`

### F2 Major - Provider source resolution blocks the main/UI dispatcher

Impact: `A slow or temporarily unavailable SAF DocumentsProvider can block the Activity's main thread during tree containment and descriptor probing; local filesystem metadata also runs there. This can freeze interaction and cause an ANR.`

Review reason: `The playback path introduces blocking provider work directly into a Main.immediate coroutine, unlike the existing browse/mutation paths that dispatch storage work to IO.`

Surface: `PlaybackCoordinator.play -> resolvePlaybackSource for LocalStorageProvider and SafStorageProvider.`

Issue key: `behavior; entry=provider source resolution; contract=provider I/O must not block the main/UI thread; effect=slow Local or SAF resolution freezes the Activity and can cause ANR`

Issue fingerprint: `ifp-sha256:cc8f45ed7556c975d10ad83e4d38c11fe7640148337c0dde8ce9a5f69cfa0aaf`

Expected basis: `kind:hard-invariant; strength:authoritative; evidence:Android main-thread responsiveness contract for blocking ContentResolver/filesystem I/O`

Confidence: `high`

Origin: `Coordinator`

Coordinator verification: `mainScope defaults to SupervisorJob() + Dispatchers.Main.immediate. The launched play job calls resolver.resolvePlaybackSource without withContext(IO). Local resolution calls Files.readAttributes/isReadable; SAF resolution calls DocumentsContract.isChildDocument and ContentResolver.openFileDescriptor. All are synchronous calls on the Main dispatcher.`

Look here first:
- [`PlaybackCoordinator` Main scope and resolution call](app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt#L41-L110)
- [`SafStorageProvider.resolvePlaybackSource`](app/src/main/java/com/omnifile/storage/SafStorageProvider.kt#L147-L183)

Failure mode:
- Expected: `Provider resolution/probing runs on an IO-capable dispatcher; only state updates and MediaController commands are marshalled to Main.`
- Current: `The new playback entry path performs synchronous Local and SAF provider I/O from a Main.immediate coroutine. Cancellation and playRequestId checks cannot interrupt a blocking call once it has entered the provider.`

Evidence:
- `Existing FilesViewModel storage operations use Dispatchers.IO (for example app/src/main/java/com/omnifile/files/FilesViewModel.kt#L509-L530), while this new path does not. Controlled provider tests are fast and do not exercise a delayed provider.`

Assumptions and limits:
- `The Android provider contract permits arbitrary provider latency; no slow-provider runtime test was available.`

Reviewer action:
`request fix and add a delayed SAF/provider responsiveness regression test`

### F3 Major - Seek command is exposed to system controllers for every source

Impact: `SystemUI/Bluetooth/headset controllers accepted by the service receive COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM even when the provider reported NOT_SEEKABLE or UNKNOWN. Their seek action bypasses the coordinator's seekSupportById guard and can cause an invalid seek or a player read error.`

Review reason: `The implementation makes seekability truthful in the app UI but not in the MediaSession command surface, so the same source has different guarantees depending on the controller.`

Surface: `OmniFilePlaybackService.SessionCallback.onConnect and MediaSession command policy.`

Issue key: `behavior; entry=MediaSession external transport control; contract=seek commands must be unavailable unless the current source is proven seekable; effect=system controller can seek an unseekable source`

Issue fingerprint: `ifp-sha256:4c2448474e5e6be50b8c61ca08e2da4017c2215f3fdd6a7215588d43831c73f0`

Expected basis: `kind:approved-design; strength:authoritative; evidence:tmp/reviews/2026-09-21-code-review-vs07-review-a.md#L54-L57 and #L50-L52`

Confidence: `high`

Origin: `Coordinator`

Coordinator verification: `The service adds COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM unconditionally before the ownController conditional. The only seekSupport gate is PlaybackCoordinator.seekTo, which is used by the in-app Music screen and cannot constrain an external MediaController.`

Look here first:
- [`OmniFilePlaybackService.SessionCallback command set`](app/src/main/java/com/omnifile/media/OmniFilePlaybackService.kt#L69-L108)
- [`PlaybackCoordinator` app-only seek guard](app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt#L136-L144)

Failure mode:
- Expected: `The session must dynamically expose seek only when the current item's source is proven seekable, or route every seek request through an equivalent capability gate.`
- Current: `Any accepted external controller receives the seek command at connection time regardless of the current item's SeekSupport value. The controlled NOT_SEEKABLE test covers only direct coordinator.seekTo.`

Evidence:
- `The service has no reference to seekSupportById and never updates per-controller commands after source resolution. Media3's accepted-controller policy therefore advertises the command for pipe-backed SAF sources.`

Assumptions and limits:
- `This finding is about command availability and the resulting allowed request; exact OEM handling of a remote seek against an unseekable ExoPlayer source needs device confirmation.`

Reviewer action:
`request a dynamic command-policy fix or focused Media3 controller proof before approval`

### F4 Major - Player errors can be overwritten by ordinary state callbacks

Impact: `Unsupported, decode, and mid-stream read failures can disappear from the StateFlow, leaving the UI in BUFFERING/READY instead of the required mapped ERROR state and potentially leaving the user without an actionable failure.`

Review reason: `The error callback and normal player-state callbacks write the same state without a request/error generation or latched error condition.`

Surface: `PlaybackCoordinator.playerListener, publishError, and publishFromPlayer.`

Issue key: `behavior; entry=player error callback; contract=Media3 playback errors must remain represented as ERROR until a new request or explicit recovery; effect=onPlaybackStateChanged overwrites the mapped error with BUFFERING/READY`

Issue fingerprint: `ifp-sha256:5ec39d0c05c1447fc1f424836c3af1d47fe7dc949b953ff2838e5ee96147070a`

Expected basis: `kind:approved-design; strength:authoritative; evidence:tmp/reviews/2026-09-21-code-review-vs07-review-a.md#L70-L72 and current PlaybackError contract`

Confidence: `medium`

Origin: `Coordinator`

Coordinator verification: `onPlayerError calls publishError. Separately, onPlaybackStateChanged, onIsPlayingChanged, onMediaItemTransition, and onPositionDiscontinuity all call publishFromPlayer. publishFromPlayer always passes hasError=false and writes error=null. After an error, the player commonly emits a state transition (often STATE_IDLE), which the reducer turns into BUFFERING when an item remains.`

Look here first:
- [`PlaybackCoordinator.playerListener and publishFromPlayer`](app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt#L219-L257)
- [`PlaybackCoordinator error mapping`](app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt#L260-L305)

Failure mode:
- Expected: `Once a current request produces a PlaybackError, subsequent callbacks for that same request must not clear it until a new request, stop, or explicit recovery establishes a new state.`
- Current: `publishError writes ERROR, but any later ordinary callback unconditionally reconstructs a non-error state and null error. The code has no error latch or request/error generation check.`

Evidence:
- `The current tests cover resolver failure before a player item exists, not Media3 decode/read errors after setMediaItem/prepare. The connected local tests therefore do not disprove this callback race.`

Assumptions and limits:
- `Confidence is medium because the exact callback ordering is Media3 implementation behavior; a focused malformed/decode or mid-stream read device test should confirm the visible sequence.`

Reviewer action:
`request fix or focused runtime proof; do not rely on the resolver-failure test as coverage`

## Minor

None.

## Questions

None.

## Test Gaps

| ID | Severity | Surface | Missing coverage | Risk | Origin | Evidence | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `T1` | `Major` | Real SAF ExoPlayer playback | The committed SAF content URI is never verified end-to-end through the real service on the current device. | SAF adapter probing passes, but ExoPlayer reopen/stream behavior, actual duration, and mid-stream grant behavior remain unverified. | `Coordinator` | `SafDevicePlaybackInstrumentedTest.kt#L42-L60` assumption-skips with no persisted readable tree; controlled tests stop at `resolvePlaybackSource` in `SafPlaybackSourceInstrumentedTest.kt#L64-L153`. | `test-gap; entry=SAF ExoPlayer playback; contract=the committed SAF content URI must play end-to-end through the service; gap=the real-device path is assumption-skipped and controlled tests stop before ExoPlayer` | `ifp-sha256:44ab97338fcbde40a5803d422783e37052d4ef3b411df14be3545b9601b0a1f2` | `kind:approved-design; strength:authoritative; evidence:tmp/reviews/2026-09-21-code-review-vs07-review-a.md#L74-L78` |
| `T2` | `Major` | Coordinator concurrent play requests | No regression test exercises overlapping source resolution, stale callbacks, or a failed replacement while another item is playing. | A future change can reintroduce stale state/player commands without host or instrumentation detection. | `Coordinator` | `MediaPlaybackInstrumentedTest.kt#L52-L176` covers one request at a time; no fake/delayed provider or overlapping `play` sequence exists. | `test-gap; entry=coordinator concurrent play requests; contract=last user request must exclusively determine player and state; gap=no regression test exercises overlapping resolution, stale callbacks, or failed replacement while another item plays` | `ifp-sha256:b6a63c07497c169372c2a89e672d665bcfb92efd49a3dc769ecaec7866c6d14e` | `kind:approved-design; strength:authoritative; evidence:tmp/reviews/2026-09-21-code-review-vs07-review-a.md#L54-L61` |
| `T3` | `Minor` | MediaSession controller authorization and command policy | No instrumentation connects as an unauthorized external controller or inspects the accepted controller's available command set. | The static gate is security-sensitive, and the seek-policy defect is not exercised through the actual MediaSession binder path. | `Coordinator` | `MediaSessionServiceInstrumentedTest.kt#L51-L76` checks manifest shape only; no SessionCallback connection-policy test is present. | `test-gap; entry=MediaSession controller authorization; contract=only own verified controller and approved system controller are accepted with policy-limited commands; gap=no instrumentation attempts unauthorized/external controllers or inspects available commands` | `ifp-sha256:548a01a7d496c4727b9c7e638dbd62427e47c43565dcd9013a8df691ba40224a` | `kind:approved-design; strength:authoritative; evidence:tmp/reviews/2026-09-21-code-review-vs07-review-a.md#L50-L52` |

## Review Coverage Ledger

| Area ID | Area / path | Touched files or entry points | Owner | Depth | Status | Result | Evidence / next step |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `A1` | Media3 dependencies and lock/verification metadata | `app/build.gradle.kts; gradle/libs.versions.toml; app/gradle.lockfile; gradle/verification-metadata.xml` | `R1` | `diff-only + assemble` | `Reviewed - no issue found` | `Pinned Media3 dependencies compile and assemble under strict verification.` | `assembleDebug passed; dependency verification was enabled.` |
| `A2` | Manifest, foreground service, notification permission, extraction rules | `app/src/main/AndroidManifest.xml; app/src/main/res/xml/data_extraction_rules.xml; MainActivity.kt#L52-L55` | `R1` | `contract trace + device manifest` | `Reviewed - no issue found` | `Exported MediaSessionService and mediaPlayback FGS declaration match the approved design; no storage/mic/internet permissions added.` | `Manifest instrumentation passed; data extraction is permissive internal-data backup as an explicit existing-policy replacement.` |
| `A3` | MediaSession controller identity and command policy | `app/src/main/java/com/omnifile/media/OmniFilePlaybackService.kt#L69-L108` | `R1` | `contract trace` | `Finding F3` | `Authorization gate is coherent, but seek command availability is not per-item capability aware.` | `Re-check with dynamic available-player-command updates and an actual external-controller test.` |
| `A4` | Coordinator replacement ownership and request cancellation | `app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt#L64-L179` | `R1` | `dependency trace` | `Finding F1` | `Failed replacement publishes the new error without stopping or preserving the previous player state.` | `Add explicit replacement failure policy and overlapping/failure regression tests.` |
| `A5` | Coordinator error/state reduction | `app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt#L219-L305; PlaybackModels.kt#L122-L149` | `R1` | `dependency trace` | `Finding F4` | `Error writes race with normal callbacks that always clear error.` | `Add a malformed or mid-stream read device test and latch/generation handling.` |
| `A6` | Main-thread scheduling around provider resolution | `PlaybackCoordinator.kt#L41-L110; LocalStorageProvider.kt#L75-L95; SafStorageProvider.kt#L147-L183` | `R1` | `contract trace` | `Finding F2` | `Synchronous filesystem/ContentResolver work executes from Main.immediate.` | `Move provider resolution/probes to IO and add delayed-provider responsiveness coverage.` |
| `A7` | Provider-neutral identity and eligibility | `PlaybackModels.kt#L8-L45; PlaybackEligibility.kt#L19-L61` | `R1` | `contract trace + host tests` | `Reviewed - no issue found` | `Raw path/URI text is not session-visible; provider IDs distinguish sources and eligibility is conservative.` | `PlaybackIdentityTest and PlaybackEligibilityTest passed in the rerun host suite.` |
| `A8` | Local playback adapter | `LocalStorageProvider.kt#L75-L95; LocalPlaybackSourceTest.kt` | `R1` | `runtime verified` | `Reviewed - no issue found` | `Regular readable local files resolve to file:// and SEEKABLE; stale/unsupported cases map through storage errors.` | `Local unit tests and real-device WAV/FLAC playback passed.` |
| `A9` | SAF playback adapter, grant boundary, and seekability probe | `SafStorageProvider.kt#L147-L183; #L692-L743; AppContainer.kt#L79-L145; SafTreeGrantStore.kt` | `R1` | `contract trace + controlled runtime` | `Reviewed - no issue found` | `Exact provider/tree references and current grants are checked; pipe/regular/inconclusive probe mappings passed. | `Real persisted-tree ExoPlayer playback remains open in T1/A12.` |
| `A10` | Files/Search/Music integration and notification prompt | `MainActivity.kt#L158-L193; #L214-L293; FilesScreen.kt#L392-L399; SearchScreen.kt#L279-L284; MusicScreen.kt` | `R1` | `runtime verified` | `Reviewed - no issue found` | `Shared coordinator wiring and truthful UI gates are present; focused UI/media tests passed where executed.` | `Run full UI package after resolving device lifecycle interference.` |
| `A11` | Activity recreation/background playback runtime path | `PlaybackLifecycleInstrumentedTest.kt#L42-L88` | `R1` | `device attempted` | `Not covered` | `Both lifecycle tests ended with ActivityScenario last transition STOPPED before requested recreation/resume.` | `Reason: no app exception identified. Next step: repeat on a stable device/runner and inspect why the test Activity loses foreground.` |
| `A12` | Real persisted SAF content URI through ExoPlayer | `SafDevicePlaybackInstrumentedTest.kt#L42-L86` | `R1` | `device attempted` | `Not covered` | `The test assumption was unmet: current device had no persisted readable SAF tree.` | `Reason: no physical grant. Next step: select a readable tree and ensure a vs07-saf fixture exists, then rerun without fabricating a grant.` |
| `A13` | Full changed-test matrix on current device | `all changed androidTest classes` | `R1` | `partial runtime` | `Not covered` | `The filtered media run reached 19 tests: 16 passed, 2 lifecycle failures, and 1 assumption failure.` | `Reason: device/runner interference and absent SAF grant. Next step: isolate those conditions and run the complete matrix.` |

## Subagent Candidate Adjudication

| Candidate ID | Proposed by | Decision | Final ID | Coordinator evidence | Reason |
| --- | --- | --- | --- | --- | --- |
| `Coordinator-C1` | `Coordinator` | `accepted` | `F1` | `PlaybackCoordinator.kt#L64-L110 and #L260-L271` | `Failure branches update state but never stop/clear the existing player.` |
| `Coordinator-C2` | `Coordinator` | `accepted` | `F2` | `PlaybackCoordinator.kt#L41-L110; SafStorageProvider.kt#L147-L183` | `Blocking provider calls are reached from Main.immediate.` |
| `Coordinator-C3` | `Coordinator` | `accepted` | `F3` | `OmniFilePlaybackService.kt#L91-L108; PlaybackCoordinator.kt#L136-L143` | `Session advertises seek to accepted external controllers while app-only guard is capability-aware.` |
| `Coordinator-C4` | `Coordinator` | `accepted` | `F4` | `PlaybackCoordinator.kt#L219-L305` | `publishFromPlayer always clears error after onPlayerError; exact framework ordering remains a runtime check.` |
| `Coordinator-C5` | `Coordinator` | `accepted` | `T1` | `SafDevicePlaybackInstrumentedTest.kt#L42-L60; connected XML current run` | `Real SAF ExoPlayer path did not execute because no persisted readable tree existed.` |
| `Coordinator-C6` | `Coordinator` | `accepted` | `T2` | `MediaPlaybackInstrumentedTest.kt#L52-L176` | `No overlapping/delayed-provider coordinator regression coverage exists.` |
| `Coordinator-C7` | `Coordinator` | `accepted` | `T3` | `MediaSessionServiceInstrumentedTest.kt#L51-L76` | `Manifest is tested, but binder-level authorization and command set are not.` |
| `Coordinator-C8` | `Coordinator` | `dismissed` | `None` | `AndroidManifest.xml#L24-L34; OmniFilePlaybackService.kt#L69-L108; approved design D6` | `The exported service is intentional and the callback performs package/UID/permission checks; adding a binding permission would break the approved controller model.` |
| `Coordinator-C9` | `Coordinator` | `dismissed` | `None` | `PlaybackModels.kt#L26-L45; PlaybackIdentityTest.kt#L38-L47` | `Raw local paths and SAF URIs are hashed out of mediaId; provider ID remains the intended opaque namespace.` |
| `Coordinator-C10` | `Coordinator` | `dismissed` | `None` | `SafStorageProvider.kt#L141-L183; SafPlaybackSourceInstrumentedTest.kt#L64-L127` | `The per-item S_ISREG/pipe/unknown probe and controlled tests support the stated seekability model; no universal seek claim was found.` |
| `Coordinator-C11` | `Coordinator` | `dismissed` | `None` | `connected XML and lifecycle logcat for current run` | `The two lifecycle failures show ActivityScenario reaching STOPPED without an app exception; they are recorded as runtime blind spots, not attributed to the playback implementation without a stable runner reproduction.` |
| `Coordinator-C12` | `Coordinator` | `dismissed` | `None` | `git diff --check` output and current test EOFs | `Three extra blank lines at EOF are hygiene findings, not meaningful VS07 behavior risks.` |

## Evidence Appendix

### Diff Inventory

| File or area | Classification | Semantic review area considered |
| --- | --- | --- |
| `app/build.gradle.kts; gradle/libs.versions.toml; app/gradle.lockfile; gradle/verification-metadata.xml` | `config` | `Media3 dependency, strict verification, build compatibility` |
| `app/src/main/java/com/omnifile/media/PlaybackModels.kt; PlaybackEligibility.kt` | `surface` | `provider-neutral identity, eligibility, state/error model` |
| `app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt` | `surface` | `ownership, races, state transitions, error handling, seek policy` |
| `app/src/main/java/com/omnifile/media/OmniFilePlaybackService.kt` | `surface` | `player/session ownership, controller authorization, command policy, lifecycle` |
| `app/src/main/java/com/omnifile/storage/PlaybackSource.kt; LocalStorageProvider.kt; SafStorageProvider.kt` | `surface` | `Local/SAF adapter, grant boundary, transport and seekability` |
| `app/src/main/java/com/omnifile/AppContainer.kt; MainActivity.kt; FilesRepository.kt` | `surface` | `process ownership, provider registration, notification/grant wiring, UI entry points` |
| `app/src/main/AndroidManifest.xml; app/src/main/res/xml/data_extraction_rules.xml` | `config` | `exported service, FGS/media permissions, backup/extraction policy` |
| `app/src/main/java/com/omnifile/ui/files/FilesScreen.kt; ui/search/SearchScreen.kt; ui/music/MusicScreen.kt` | `surface` | `play affordances, truthful now-playing and seek UI` |
| `app/src/androidTest/java/com/omnifile/media/*; ui/*PlayActionInstrumentedTest.kt; storage/TestDocumentsProvider.java` | `test-only` | `runtime playback, lifecycle, SAF probes, UI integration, test coverage` |
| `app/src/test/java/com/omnifile/media/*; storage/LocalPlaybackSourceTest.kt` | `test-only` | `host identity, eligibility, state mapping, Local adapter` |
| `app/src/androidTest/assets/vs07-primary.flac` | `test-only` | `real local decode fixture` |

### Verification Commands

- `git --no-optional-locks status --short` -> `Tracked worktree files remained unchanged; pre-existing untracked docs/review artifacts were present.`
- `git --no-pager diff --stat 67a8e0e08d3b6ad03165d0d962ad8868b00c661c..351cc27` -> `35 paths, 2934 insertions, 64 deletions.`
- `git --no-pager diff --binary --no-ext-diff 67a8e0e08d3b6ad03165d0d962ad8868b00c661c..351cc27 | shasum -a 256` -> `2fec0fe67fcc82a6f9c2755aebcbfcdccf5a01a94cd6b2388d654cf4bfca463c.`
- `./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process --dependency-verification=strict --rerun-tasks :app:testDebugUnitTest` -> `BUILD SUCCESSFUL; all host unit tests passed.`
- `./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process --dependency-verification=strict :app:assembleDebug` -> `BUILD SUCCESSFUL.`
- `./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process --dependency-verification=strict :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.omnifile.media` -> `FAILED as a test task: 19 media tests reported, 16 passed, 2 ActivityScenario lifecycle failures, and 1 assumption failure for absent persisted SAF permission; local playback/service/controlled SAF probe tests passed.`
- `functions.diagnostics` on current `PlaybackCoordinator.kt`, `OmniFilePlaybackService.kt`, and `SafStorageProvider.kt` -> `No diagnostics on coordinator; only non-blocking warnings on service/provider.`

### Supporting Code Links

| ID | Role | Link | Why it matters |
| --- | --- | --- | --- |
| `F1` | `entry` | [`play`](app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt#L64-L91) | `The new request becomes the sole StateFlow item before resolution completes.` |
| `F1` | `risk` | [`failure branches`](app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt#L95-L110) | `Resolver failure publishes error without a player replacement/stop.` |
| `F2` | `entry` | [`Main.immediate scope`](app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt#L41-L45) | `All provider resolution launched from this scope runs on Main.` |
| `F2` | `risk` | [`SAF probe`](app/src/main/java/com/omnifile/storage/SafStorageProvider.kt#L147-L183) | `Synchronous containment and descriptor calls may block.` |
| `F3` | `entry` | [`onConnect`](app/src/main/java/com/omnifile/media/OmniFilePlaybackService.kt#L69-L108) | `Seek command is added before controller-type branching.` |
| `F3` | `risk` | [`coordinator-only gate`](app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt#L136-L143) | `This gate cannot constrain system controllers.` |
| `F4` | `entry` | [`onPlayerError`](app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt#L219-L233) | `Mapped error is published here.` |
| `F4` | `risk` | [`publishFromPlayer`](app/src/main/java/com/omnifile/media/PlaybackCoordinator.kt#L235-L257) | `Normal callbacks write error=null.` |
| `T1` | `coverage gap` | [`SAF device assumption`](app/src/androidTest/java/com/omnifile/media/SafDevicePlaybackInstrumentedTest.kt#L42-L60) | `The real end-to-end path did not execute.` |
| `T2` | `coverage gap` | [`sequential local tests`](app/src/androidTest/java/com/omnifile/media/MediaPlaybackInstrumentedTest.kt#L52-L176) | `No overlap/delayed resolver sequence is covered.` |
| `T3` | `coverage gap` | [`manifest-only service test`](app/src/androidTest/java/com/omnifile/media/MediaSessionServiceInstrumentedTest.kt#L51-L76) | `No binder-level controller-policy assertion exists.` |

### Dismissed Coordinator Candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| `Exported MediaSessionService is an authorization bypass` | `dismissed` | `Manifest export is required by the approved system-control design; onConnect verifies package/UID correspondence and MEDIA_CONTENT_CONTROL for external packages.` |
| `PlaybackItem leaks a raw local path or SAF URI` | `dismissed` | `PlaybackItem hashes EntryRef.identityKey before constructing mediaId; the identity test asserts no content URI or slash is present.` |
| `SAF descriptor existence universally proves seekability` | `dismissed` | `resolvePlaybackSource` uses fstat mode and reports UNKNOWN on inconclusive probe; controlled pipe and regular-file tests pass.` |
| `Current lifecycle test failures prove service/player lifecycle regression` | `dismissed as code finding; retained as blind spot` | `The device XML reports ActivityScenario STOPPED failures, and captured logs show no app FATAL EXCEPTION; a stable runner reproduction is required.` |
| `Extra blank lines at EOF are a release defect` | `dismissed` | `git diff --check` reports only three blank-line hygiene warnings in newly added tests.` |

### Blind Spots

| Area ID | Blind spot | Decision risk | What would resolve it |
| --- | --- | --- | --- |
| `A11` | `Activity recreation/backgrounding did not complete on the current device runner.` | `The central process-scoped coordinator/service lifetime claim lacks current runtime confirmation.` | `Repeat the two lifecycle tests on a stable Pixel/emulator runner and capture lifecycle plus player/session state.` |
| `A12` | `No physical readable persisted SAF tree was available.` | `The real content URI reopen, duration, and grant-loss behavior through ExoPlayer remain unverified.` | `Physically select a readable tree and a vs07-saf fixture, then rerun T1 without fabricated grants.` |
| `A13` | `Full changed instrumentation matrix was not run to green after the filtered media run failed.` | `UI action regressions and cross-suite state leakage are not fully rechecked on this target/device state.` | `Run complete connectedDebugAndroidTest after isolating the ActivityScenario/device interference.` |
| `F4` | `Media3 callback ordering after decode/read error was not directly exercised.` | `The static error overwrite race is highly plausible but its exact user-visible sequence is runtime-dependent.` | `Run a malformed-container and mid-stream read-failure test and assert ERROR persists until the next request.` |

## Prior Resolution Reconciliation

None - initial review generation.

## Receiving Handoff

- Handoff status: `Ready for receiving-code-review`
- Automatic receiving permitted: `No`
- Source report ID: `cr-20260922-r8m2k7`
- Scope fingerprint to recheck: `sha256:2fec0fe67fcc82a6f9c2755aebcbfcdccf5a01a94cd6b2388d654cf4bfca463c`
- Actionable finding IDs: `F1, F2, F3, F4`
- Deferred finding IDs: `None`
- Actionable test-gap IDs: `T1, T2`
- Deferred test-gap IDs: `T3`
- Open question IDs: `None`
- Open coverage area IDs: `A11, A12, A13`
- Highest-risk verification to repeat: `Exercise failed replacement while A is playing, external/system seek against a pipe-backed SAF source, and Media3 decode/read error persistence; then repeat lifecycle and real SAF tests on a stable device.`
- Suggested implementation boundaries: `Keep fixes inside PlaybackCoordinator state/player transaction and dispatcher boundaries, OmniFilePlaybackService dynamic command policy, and the changed media instrumentation harness; do not widen into library/catalog features.`
- Re-review note: `Treat every finding as a claim to verify. Challenges require a counterclaim, argument, evidence, limits, and settlement criterion.`
- Chain rule: `Generation 1 is terminal. Do not automatically invoke receiving-code-review; return remaining findings to the user or product owner.`

## Report Self-Check

- `yes` Actual assessment mode and rationale are recorded: coordinator, delegated assessor, or unavailable fallback.
- `yes` Every changed review-relevant or unknown-impact area appears once in `Review Coverage Ledger`.
- `yes` Every final finding appears once in the index and once as a matching card.
- `yes` Every `Finding F#` area references an existing finding.
- `yes` Every standalone test gap has a stable ID and severity.
- `yes` Every `F#` and `T#` has a unique semantic issue fingerprint and an authoritative expected basis, or the item is an explicit `Question` for unconfirmed intent.
- `yes` Generation, trigger, parent resolution, scope mode, and receiving handoff satisfy the bounded chain contract.
- `yes` Generation `1` reconciliation is not applicable to this generation `0` report.
- `yes` Every non-Question finding and standalone test gap appears exactly once in actionable or deferred handoff IDs; every Question and Not-covered area appears in its matching open list.
- `yes` Every meaningful coordinator candidate has an adjudication.
- `yes` Every `Not covered` area has a reason and next step.
- `yes` Recommendation follows the skill mapping.
- `yes` The report validator passed: `python3 ~/.agents/skills/code-review/scripts/validate_review_report.py tmp/reviews/2026-09-22-code-review-vs07-final-r8m2k7.md`.
- `yes` Git state was not mutated; this report is the only review artifact created by this pass.
