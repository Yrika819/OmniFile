# Code Review Report

## Report Contract

- Report type: `code-review`
- Report ID: `cr-20260922-5bd01cbc`
- Review chain ID: `rc-20260922-76c66ac4`
- Review generation: `0`
- Review trigger: `initial`
- Parent review report ID: `None`
- Parent review report path: `None`
- Parent resolution ID: `None`
- Parent resolution path: `None`
- Generated at: `2026-09-22T20:12:00Z`
- Report path: `tmp/reviews/2026-09-22-code-review-vs07-final-5bd01cbc.md`
- Source skill: `code-review`
- Status: `Review incomplete`
- Git mutation during review: `None`
- Scope fingerprint: `sha256:b4096173cd68105e233078539923e5128fb67e18eecfec1b04ed46d85e4878d1`

This report is frozen. Later implementation changes must be recorded in a separate receiving/resolution report, not by rewriting this file.

## Scope

- Review date: `2026-09-22`
- Scope kind: `commit range`
- Scope description: `Final whole-diff review of VS07 provider-neutral playback from the published VS06 base through the focused hardening commit.`
- Scope mode: `full frozen scope`
- Baseline: `67a8e0e08d3b6ad03165d0d962ad8868b00c661c`
- Target: `51b83a02e4e805dd2899e402c14a3e5d372b43d2`
- Changed paths: `35`
- Diff size: `3017 insertions, 64 deletions`
- Completion: `Incomplete - source and host/static scope was reviewed; target-device UI/lifecycle acceptance was invalidated by Dozing/NotificationShade interference and real persisted-SAF playback had no persisted readable grant.`
- Requirements consulted: `VS07 handoff invariants; docs/architecture/04_MEDIA_INTEGRATION.md; docs/architecture/ADR/ADR-002-provider-scoped-identity.md; docs/architecture/02_STORAGE_MODEL.md; tmp/reviews/2026-09-21-code-review-vs07-review-a.md; current tests and production source.`
- Prior resolution consulted: `None`
- Assumptions: `The VS07 hard invariants and provider capability contract are authoritative. The connected Pixel is not valid physical UI evidence while it reports Dozing, NotificationShade focus, and an installed package different from this checkout.`
- Excluded as unrelated: `Older VS03-VS06 temporary review artifacts and generated build outputs.`

## Review Orchestration

- Assessment subagent: `Coordinator assessment - broad cross-layer playback change has independent correctness, security, runtime-evidence, and framework-lifecycle risks.`
- Orchestration decision: `Parallel specialists`
- Decision confidence: `high`
- Decision rationale: `The change spans provider adapters, an exported MediaSession service, asynchronous player ownership, Compose surfaces, and device-only acceptance. Independent correctness, security, and evidence passes materially reduce blind spots; the coordinator re-read all candidate paths.`
- Coordinator override: `None`
- Context or tool limits: `The target Pixel repeatedly entered Dozing with NotificationShade focused; real DocumentsUI grant setup was unavailable.`

### Risk Dimensions

- `Asynchronous service/player ownership and replacement callbacks can create stale state or duplicate lifecycle ownership.`
- `SAF grants, provider availability, transport identity, and per-item seekability cross a trust boundary.`
- `The exported MediaSessionService must support legitimate system controls without allowing arbitrary media-item injection.`
- `Physical UI, Activity recreation, background, and real-provider playback require a stable device state not available in this run.`

### Reviewer Assignments

| Reviewer | Angle | Owned surfaces | Mandatory cross-checks | Status |
| --- | --- | --- | --- | --- |
| `R1` | Correctness, concurrency, and contracts | `PlaybackCoordinator.kt; PlaybackModels.kt; Local/SAF adapters; changed playback tests` | `Trace Files/Search/Music -> coordinator -> provider -> controller -> service; verify replacement/error/request guards.` | `Complete` |
| `R2` | Security and framework lifecycle | `OmniFilePlaybackService.kt; AndroidManifest.xml; MainActivity grant/PendingIntent paths` | `Verify UID/package binding, external command allowlist, FGS permissions/type, standard media-action surface, SAF grant exposure.` | `Complete` |
| `R3` | Tests, regressions, and evidence | `all changed tests; host/device artifacts; VS06 surfaces; production/review docs` | `Reconcile counts, hashes, runtime failures, and changed user journeys to the final target.` | `Complete` |

### Synthesis Statement

`The coordinator independently re-read every candidate against the final source and final-target verification. The four findings in the prior frozen report are addressed by 51b83a0: replacement clears the service player, provider resolution is on IO, external seek is not granted, and player errors are latched. No new source Blocker/Major was confirmed. Three standalone acceptance/test gaps remain because the target device cannot supply the required physical evidence.`

## Review Snapshot

- Recommendation: `Changes requested`
- Completion: `Incomplete - exact uncovered device surfaces are listed in A11-A13.`
- Why now: `The final source target is statically coherent and the host/media/SAF controlled tests pass, but target-device UI/lifecycle and real-SAF acceptance remain unproven.`
- Must-review now:
  1. `T1` Real persisted-SAF ExoPlayer playback is not executed without a physical readable grant.
  2. `T2` Files/Search/Music Compose and Activity recreation evidence is invalid while the Pixel is Dozing and externally focused.
  3. `T3` Binder-level external-controller command-policy coverage is absent.
- Findings count: `Blocker 0 | Major 0 | Minor 0 | Question 0`
- Standalone test gaps: `Blocker 0 | Major 2 | Minor 1`
- Coverage confidence: `medium`
- Biggest blind spot: `A11-A13 target-device UI/lifecycle, physical SAF playback, and binder-level controller-policy coverage.`

## Complete Findings Index

No code-review findings identified in the reviewed source scope. Standalone verification gaps are listed in Test Gaps.

## Blocker

None.

## Major

None.

## Minor

None.

## Questions

None.

## Test Gaps

| ID | Severity | Surface | Missing coverage | Risk | Origin | Evidence | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `T1` | `Major` | Real SAF ExoPlayer playback | Physical persisted-grant playback through the real service was not executed. | Provider-specific reopen, duration, and mid-stream grant behavior remain unknown. | `R3` | `SafDevicePlaybackInstrumentedTest` assumption failure; persisted readable permissions were `[]`; controlled SAF probe passed separately. | `test-gap; entry=real SAF ExoPlayer playback; contract=the declared provider-neutral SAF playback path must be verified through the real service; gap=the physical persisted-grant path is unavailable on the target device` | `ifp-sha256:a73fa43398035dd1a617b1a05f57a4a40ea13376fc80b4c4abfd2d50077f488e` | `kind:approved-design; strength:authoritative; evidence:VS07 SAF acceptance condition and SafDevicePlaybackInstrumentedTest` |
| `T2` | `Major` | Playback lifecycle and UI acceptance | Files/Search/Music Compose tests and Activity recreation/background tests could not observe the target Activity. | New Play affordances, Music truthfulness, and Activity recreation remain physically unverified in this run. | `R3` | Final connected XML: 75 tests, 28 failures; 25 Compose failures report no hierarchy and 2 lifecycle failures end STOPPED; device reports Dozing and NotificationShade focus. | `test-gap; entry=playback lifecycle and UI acceptance; contract=the changed playback UI and Activity recreation flows must be verified on the target device; gap=the connected device remained in Dozing with NotificationShade focused` | `ifp-sha256:178fe13acd2c67656bbd8b5f00f78a7a06f57ab9ca8c87b21ed85ee5f065d93b` | `kind:approved-design; strength:authoritative; evidence:VS07 Pixel acceptance requirements and final connected result XML` |
| `T3` | `Minor` | MediaSession controller authorization | No binder-level test connects an unauthorized external controller or inspects external available commands. | Static policy is constrained, but the binder command surface is not directly asserted. | `R2` | `MediaSessionServiceInstrumentedTest` verifies manifest, session visibility, FGS state, and stop state; it does not instantiate unauthorized/external controller cases. | `test-gap; entry=MediaSession controller authorization; contract=only verified controllers receive the intended command policy; gap=no binder-level unauthorized-controller and external-command test exists` | `ifp-sha256:dde913a34a31be02a85c29022df40f899592465def6aafcbda099e340c0afe8e` | `kind:approved-design; strength:authoritative; evidence:VS07 service security invariant and MediaSessionServiceInstrumentedTest` |

## Review Coverage Ledger

| Area ID | Area / path | Touched files or entry points | Owner | Depth | Status | Result | Evidence / next step |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `A1` | Media3 dependencies and verification metadata | `app/build.gradle.kts; gradle/libs.versions.toml; app/gradle.lockfile; gradle/verification-metadata.xml` | `R1` | `runtime verified` | `Reviewed - no issue found` | Media3 1.11.1 is pinned; strict verification, test, lint, and both APK assemblies pass. | Final strict Gradle command and hashes. |
| `A2` | Manifest and foreground service contract | `app/src/main/AndroidManifest.xml; OmniFilePlaybackService.kt` | `R2` | `contract trace + runtime verified` | `Reviewed - no issue found` | Exported MediaSessionService and mediaPlayback FGS declaration are intentional; no broad storage/mic/internet permission was added. | Physical binder test remains T3. |
| `A3` | Controller identity and command policy | `OmniFilePlaybackService.kt#onConnect` | `R2` | `contract trace` | `Reviewed - no issue found` | Same-app UID/package binding and external MEDIA_CONTENT_CONTROL gate are strict; external set/prepare/change/seek commands are withheld. | Add binder-level test for T3. |
| `A4` | Coordinator replacement and request cancellation | `PlaybackCoordinator.kt#play; clearPlayerForReplacement; publishFromPlayer` | `R1` | `runtime verified` | `Reviewed - no issue found` | Expected media identity filters asynchronous callbacks; failed replacement clears the service player; new replacement regression passes 7/7 media suite. | No open source finding. |
| `A5` | Error/state reduction | `PlaybackCoordinator.kt#onPlayerError; publishFromPlayer` | `R1` | `contract trace + runtime verified` | `Reviewed - no issue found` | Errors are latched until a new request/stop and polling stops on error. | Malformed mid-stream error test remains a future strengthening case. |
| `A6` | Provider dispatch and cancellation | `PlaybackCoordinator.kt; LocalStorageProvider.kt; SafStorageProvider.kt` | `R1` | `contract trace + host/build verified` | `Reviewed - no issue found` | Resolution/probing runs under Dispatchers.IO and cancellation is preserved by the SAF adapter. | No open source finding. |
| `A7` | Provider-neutral identity and eligibility | `PlaybackModels.kt; PlaybackEligibility.kt` | `R1` | `host verified` | `Reviewed - no issue found` | Opaque provider-scoped IDs exclude raw paths/URIs; eligibility remains conservative. | Host identity/eligibility tests pass. |
| `A8` | Local playback adapter | `LocalStorageProvider.kt; LocalPlaybackSourceTest.kt` | `R1` | `host + device verified` | `Reviewed - no issue found` | Regular readable files resolve to file transport and SEEKABLE; WAV/FLAC playback passes. | MediaPlayback 7/7. |
| `A9` | SAF adapter, grant, and seekability | `SafStorageProvider.kt; SafTreeGrantStore.kt; TestDocumentsProvider.java` | `R1/R2` | `controlled device verified` | `Reviewed - no issue found` | Regular/pipe/unknown, permission, missing, provider, and stale cases pass; grants are revalidated. | SafPlaybackSource 7/7; real device path is T1. |
| `A10` | Files/Search/Music integration | `MainActivity.kt; FilesScreen.kt; SearchScreen.kt; MusicScreen.kt` | `R3` | `static + source/test trace` | `Reviewed - no issue found` | Explicit Play actions, selection guard, shared coordinator, and truthful Music state are present. | Physical Compose proof is T2. |
| `A11` | Activity recreation/background playback | `PlaybackLifecycleInstrumentedTest.kt` | `R3` | `device attempted` | `Not covered` | Both tests ended STOPPED; device was Dozing with NotificationShade focused. | Rerun on an awake/unlocked device running this APK. |
| `A12` | Real persisted SAF playback | `SafDevicePlaybackInstrumentedTest.kt` | `R3` | `device attempted` | `Not covered` | Assumption required a persisted readable tree; device returned none. | Physically persist readable tree and rerun. |
| `A13` | Full target-device matrix | `all changed androidTest classes` | `R3` | `partial runtime` | `Not covered` | 75 recorded / 47 passed / 28 failed; media/service/controlled-SAF and non-UI groups pass, UI/lifecycle cascade is environmental. | Repeat on stable device; do not relabel this run green. |

## Subagent Candidate Adjudication

| Candidate ID | Proposed by | Decision | Final ID | Coordinator evidence | Reason |
| --- | --- | --- | --- | --- | --- |
| `R1-C1` | `R1` | `dismissed` | `None` | Final coordinator trace of expectedMediaId and failed-replacement test. | Stale callbacks are filtered and the replacement is cleared before resolution. |
| `R1-C2` | `R1` | `dismissed` | `None` | Final coordinator trace of controllerGeneration, future cancellation, and disconnected-controller retry. | Teardown cannot repopulate coordinator state; failed futures are not cached. |
| `R2-C1` | `R2` | `dismissed` | `None` | Media3 1.11.1 bytecode and manifest/service trace. | The inherited surface is standard media/custom notification actions, not arbitrary media-item injection; app/service policy still withholds item-selection commands. |
| `R2-C2` | `R2` | `represented` | `T3` | Existing service tests and final service command allowlist. | Static security is sound, but binder-level command-policy coverage is absent. |
| `R3-C1` | `R3` | `represented` | `T1` | Final XML and persistedUriPermissions evidence. | Physical SAF grant is required and was not fabricated. |
| `R3-C2` | `R3` | `represented` | `T2` | Final XML plus dumpsys power/activity evidence. | Device state prevents valid UI/lifecycle observation. |

## Evidence Appendix

### Diff Inventory

| File or area | Classification | Semantic review area considered |
| --- | --- | --- |
| `app/build.gradle.kts; gradle/libs.versions.toml; app/gradle.lockfile; gradle/verification-metadata.xml` | `config` | Media3 dependency and strict verification |
| `app/src/main/java/com/omnifile/media/*` | `surface` | ownership, races, state, errors, session policy |
| `app/src/main/java/com/omnifile/storage/PlaybackSource.kt; LocalStorageProvider.kt; SafStorageProvider.kt` | `dependency` | provider transport, grant, seekability, mutation parity |
| `app/src/main/java/com/omnifile/AppContainer.kt; MainActivity.kt; FilesRepository.kt` | `dependency` | process ownership, providers, notification/grant wiring |
| `app/src/main/java/com/omnifile/ui/files/FilesScreen.kt; SearchScreen.kt; MusicScreen.kt` | `surface` | Play actions and truthful Now Playing output |
| `app/src/main/AndroidManifest.xml; data_extraction_rules.xml` | `config` | exported service, FGS, notification, extraction |
| `app/src/androidTest/java/com/omnifile/media/*; ui/*PlayActionInstrumentedTest.kt; TestDocumentsProvider.java` | `test-only` | playback, lifecycle, SAF, UI, service evidence |
| `app/src/test/java/com/omnifile/media/*; LocalPlaybackSourceTest.kt` | `test-only` | identity, eligibility, state, local adapter |
| `app/src/androidTest/assets/vs07-primary.flac` | `test-only` | committed local decode fixture |

### Verification Commands

- `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process --dependency-verification=strict :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` -> `BUILD SUCCESSFUL`.
- Host XML -> `23 classes, 115 tests, 0 failures, 0 errors, 0 skipped`.
- Focused Pixel media run -> `MediaPlaybackInstrumentedTest 7/7; MediaSessionServiceInstrumentedTest 3/3; SafPlaybackSourceInstrumentedTest 7/7`.
- Full Pixel run -> `75 tests, 47 passed, 28 failures, 0 errors, 0 skipped`; failures are 25 no-hierarchy Compose results, 2 STOPPED lifecycle results, and 1 no-grant SAF assumption result.
- `adb shell dumpsys power` -> `mWakefulness=Dozing`; `dumpsys activity activities` -> `mCurrentFocus=NotificationShade`, `mFocusedApp=null`.
- `shasum -a 256` -> debug `a67477b101758a1305e547fa4e015c51958bafd9b4c57b1c8c0f8a012eaf3f96`; androidTest `e555e8cd31516c879ef97895a1f9b188e5bf01c25cf6108d101da350fb2e37de`.
- `git diff --binary --no-ext-diff 67a8e0e..51b83a0 | shasum -a 256` -> `b4096173cd68105e233078539923e5128fb67e18eecfec1b04ed46d85e4878d1`.

### Supporting Code Links

| ID | Role | Link | Why it matters |
| --- | --- | --- | --- |
| `T1` | `acceptance` | [SafDevicePlaybackInstrumentedTest](app/src/androidTest/java/com/omnifile/media/SafDevicePlaybackInstrumentedTest.kt#L42) | Requires a real persisted readable grant and does not fabricate one. |
| `T2` | `runtime evidence` | [PlaybackLifecycleInstrumentedTest](app/src/androidTest/java/com/omnifile/media/PlaybackLifecycleInstrumentedTest.kt#L42) | The lifecycle contract is explicit but the target device cannot keep the Activity foreground. |
| `T3` | `security coverage` | [MediaSessionServiceInstrumentedTest](app/src/androidTest/java/com/omnifile/media/MediaSessionServiceInstrumentedTest.kt#L51) | Current test covers manifest/session/FGS, not binder authorization commands. |

### Dismissed Coordinator Candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| `Exported service is automatically an arbitrary URI injection surface` | `dismissed` | Media3 standard start actions do not grant set-media-item; app controller-only set/prepare/change commands remain enforced. |
| `Raw local path or SAF URI is exposed as generic identity` | `dismissed` | `PlaybackItem` uses provider ID plus SHA-256 identity digest; raw transport remains adapter-local. |
| `Readable implies seekable` | `dismissed` | Controlled regular/pipe/unknown probe matrix passes and UI seek remains capability-gated. |

### Blind Spots

| Area ID | Blind spot | Decision risk | What would resolve it |
| --- | --- | --- | --- |
| `A11` | Pixel Dozing/NotificationShade prevents ActivityScenario and Compose hierarchy observation. | Physical UI and lifecycle acceptance cannot be claimed. | Run final APK on an awake/unlocked device with the correct package installed. |
| `A12` | No persisted readable SAF tree exists. | Real DocumentsProvider plus ExoPlayer behavior remains unknown. | Physically select a readable tree containing the fixture and rerun. |
| `A13` | No binder-level external-controller test. | Static command policy is not directly exercised through a remote controller. | Add/run an authorized and unauthorized controller command test. |

## Prior Resolution Reconciliation

None - initial review generation.

## Receiving Handoff

- Handoff status: `Ready for receiving-code-review`
- Automatic receiving permitted: `No`
- Source report ID: `cr-20260922-5bd01cbc`
- Scope fingerprint to recheck: `sha256:b4096173cd68105e233078539923e5128fb67e18eecfec1b04ed46d85e4878d1`
- Actionable finding IDs: `None`
- Deferred finding IDs: `None`
- Actionable test-gap IDs: `T1, T2`
- Deferred test-gap IDs: `T3`
- Open question IDs: `None`
- Open coverage area IDs: `A11, A12, A13`
- Highest-risk verification to repeat: `Run the final APK on an awake/unlocked device with a persisted readable SAF tree, then rerun full connected instrumentation.`
- Suggested implementation boundaries: `None`
- Re-review note: `Treat every test gap as a bounded evidence limitation; do not convert environmental failure or missing grant into a product pass.`
- Chain rule: `Generation 1 is terminal. Do not automatically invoke receiving-code-review; return remaining gaps to the user or product owner.`

## Report Self-Check

- `yes` Actual assessment mode and rationale are recorded.
- `yes` Every changed review-relevant or unknown-impact area appears once in the Review Coverage Ledger.
- `yes` Every final finding appears once in the index and once as a matching card; no source findings were accepted.
- `yes` Every standalone test gap has a stable ID and severity.
- `yes` Every T# has a unique semantic fingerprint and authoritative expected basis.
- `yes` Generation, trigger, parent resolution, scope mode, and receiving handoff are consistent.
- `yes` Every meaningful subagent candidate has an adjudication.
- `yes` Every Not covered area has a reason and next step.
- `yes` Recommendation follows the skill mapping: unresolved Major test gaps require Changes requested.
- `yes` The validator was run after report creation.
- `yes` Git state was not mutated during review generation.
