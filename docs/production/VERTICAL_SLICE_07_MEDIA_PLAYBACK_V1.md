# OmniFile CORE_V1 — Vertical Slice 07
## Provider-Neutral Media Playback V1

- Published VS06 base: `67a8e0e08d3b6ad03165d0d962ad8868b00c661c`
- Branch: `development/core-v1-media-playback-v1`
- Final implementation target: `51b83a02e4e805dd2899e402c14a3e5d372b43d2`
- Worktree: `/Users/yuta/Desktop/File Manager-worktrees/omnifile-media-playback-v1`
- Closure status: **INCOMPLETE — target-device UI/lifecycle evidence and real persisted-SAF ExoPlayer evidence remain open**
- This note deliberately does not claim a green full-device acceptance result.

## Mission and boundary

VS07 adds provider-neutral, truthful playback behind Music: Local audio, SAF audio, Media3/ExoPlayer, one current item, play/pause, truthful progress/duration, seek only when proven, MediaSession/MediaSessionService ownership, background/session lifecycle, system controls, Files Play, Search Play, and a functional Music Now Playing destination.

Deferred: persistent library/indexing, playlists, album/artist databases, lyrics, equalizer, conversion/transcoding, video, cloud/network playback, and VS08.

## Versions and architecture

- Media3/ExoPlayer/MediaSession: `1.11.1`
- AGP: `9.4.0`
- Kotlin: `2.2.10`
- KSP: `2.2.10-2.0.2`
- Compose BOM: `2025.12.00`
- Gradle wrapper: `9.6.0`
- Android Studio JBR: `25.0.3`
- SDK: `/Users/yuta/Library/Android/sdk`

`OmniFilePlaybackService` owns exactly one ExoPlayer and one MediaSession for the process. `PlaybackCoordinator` is process-scoped in `AppContainer`, owns one MediaController, survives Activity recreation, and never constructs a player. UI calls coordinator actions and collects one `StateFlow<NowPlayingState>`.

`PlaybackItem.mediaId` is provider ID plus an opaque SHA-256 digest of the provider-scoped identity key. Raw local paths and raw SAF URIs are never generic identity. Local and SAF adapters resolve transient transports at play time.

`readable` is not treated as `seekable`:

- Local regular readable file: `SEEKABLE`.
- SAF regular-file descriptor: `SEEKABLE`.
- SAF pipe/socket descriptor: `NOT_SEEKABLE`.
- Inconclusive SAF probe: `UNKNOWN`; playback may be attempted but seek remains disabled.

SAF playback requires a current persisted readable grant and provider/tree/document containment validation. No grant fabrication or broad storage permission is used.

## Service, controller, and security contract

- Service is exported intentionally for the Media3 system media-control contract.
- Manifest declares `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, and `foregroundServiceType="mediaPlayback"`.
- No storage, microphone, internet, `READ_EXTERNAL_STORAGE`, or `MANAGE_EXTERNAL_STORAGE` permission was added.
- Same-app controller requires matching process UID, package name, and package-to-UID membership.
- External controllers require verified package-to-UID membership and `android.media.permission.MEDIA_CONTENT_CONTROL`.
- External controllers receive read/transport commands only; set-media-item, prepare, change-media-items, and seek are app-controller-only.
- Session activity PendingIntent is explicit and immutable.
- Media3's inherited standard media-action start path remains part of the service contract; it handles framework media-button/notification actions and does not expose arbitrary media-item injection or custom session commands. Adding a service binding permission would block the app's own controller and legitimate system binding, so no unsupported permission was added.

## UI behavior

- Files: explicit Play action only for eligible single audio entries; hidden in selection mode; ordinary row taps retain existing behavior.
- Search: same Play action for eligible results; result-open behavior remains separate; CurrentFolder and ThisDevice scopes remain unchanged.
- Music: truthful empty/active/paused/ended/error states; real title/source label; no fabricated artist/album/artwork; no fake queue controls; unknown duration does not render `0:00`; seek is enabled only for proven `SEEKABLE` with known positive duration.
- Edge-to-edge: `MainActivity.enableEdgeToEdge()`, manifest `adjustResize`, one navigation-bar contrast configuration, Music `Scaffold` inner padding, and Material navigation components' own inset handling. No additional double inset was introduced.

## Host verification

Command, run with Android Studio JBR 25.0.3:

```text
./gradlew --no-daemon --max-workers=1 \
  -Dkotlin.compiler.execution.strategy=in-process \
  --dependency-verification=strict \
  :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

Result: **BUILD SUCCESSFUL**.

- Host suite: **23 classes, 115 tests, 0 failures, 0 errors, 0 skipped**.
- Lint: successful; only existing non-blocking `delay(Long)` modernization warning is reported by diagnostics.
- Debug APK assembly: successful.
- AndroidTest APK assembly: successful.
- Strict dependency verification: enabled and successful.

## Rebuilt APK hashes

These hashes were computed after the final implementation commit `51b83a0` and the strict rebuild:

```text
app/build/outputs/apk/debug/app-debug.apk
SHA-256: a67477b101758a1305e547fa4e015c51958bafd9b4c57b1c8c0f8a012eaf3f96

app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
SHA-256: e555e8cd31516c879ef97895a1f9b188e5bf01c25cf6108d101da350fb2e37de
```

## Pixel 7a API 36 evidence

Focused final-target runs on the rebuilt APKs:

- `MediaPlaybackInstrumentedTest`: **7/7 pass**, including Local WAV/FLAC, proven seek, non-seekable seek suppression, missing source, ended state, and failed replacement stability.
- `MediaSessionServiceInstrumentedTest`: **3/3 pass**, including exported/mediaPlayback manifest contract, system-visible truthful title/MediaSession, foreground state, and stopped-service state.
- `SafPlaybackSourceInstrumentedTest`: **7/7 pass**, covering regular-file SEEKABLE, pipe NOT_SEEKABLE, UNKNOWN, permission, missing document, provider unavailable, and stale foreign reference.

The final full connected run recorded:

```text
75 tests recorded
47 passed
28 failures
0 errors
0 skipped
```

Breakdown:

- Media playback: 7/7 pass.
- MediaSession/service: 3/3 pass.
- Controlled SAF probe: 7/7 pass.
- Existing non-UI operations/search/storage/database groups: 30/30 pass.
- Activity lifecycle: 0/2; both ended `STOPPED` instead of `RESUMED`.
- Real persisted-SAF playback: 0/1; `AssumptionViolatedException` because persisted readable SAF permissions were `[]`.
- Files/Search/Music Compose groups: 0/25; all reported `No compose hierarchies found in the app`.

At the end of the full run, device evidence showed `mWakefulness=Dozing`, `mCurrentFocus=NotificationShade`, and `mFocusedApp=null`. A foreground recovery attempt also found the installed package was `org.omnifile.poc.media3`, not this checkout's `com.omnifile`, so the UI/lifecycle failures are not valid evidence against this target. They remain open acceptance gaps rather than being relabeled as passes.

## SAF real-device stop condition R3

R3 remains open by design:

- The real SAF test requires a physical DocumentsUI selection that persists a readable tree.
- The device had no persisted readable tree and returned `[]`.
- No grant or fixture was fabricated.
- To close R3, install/run this target on a stable device, physically select a readable SAF tree containing the expected fixture, and rerun `SafDevicePlaybackInstrumentedTest`.
- Controlled on-device SAF seekability is already proven; only the real persisted-grant-to-ExoPlayer path is missing.

## Reviews

- Historical VS07 architecture review: `tmp/reviews/2026-09-21-code-review-vs07-review-a.md` (architecture-only; not final implementation acceptance).
- Final code review: `tmp/reviews/2026-09-22-code-review-vs07-final-5bd01cbc.md` — validator-clean; no source findings; recommendation `Changes requested` because T1/T2 acceptance gaps remain and T3 is a binder test gap.
- VS06 -> VS07 regression review: `tmp/reviews/2026-09-22-user-visible-regression-report-76c66ac4.md` — no confirmed VS06 regression; recommendation `Discuss` because physical UI/lifecycle and real-SAF coverage are incomplete.
- Intent/security audit: `tmp/reviews/2026-09-22-android-intent-security-vs07-fc3a4468.md` — Pass with evidence caveat; exported service, command policy, PendingIntent, FGS permissions, and SAF grant boundaries audited.

## Actual commits

- `67a8e0e08d3b6ad03165d0d962ad8868b00c661c` — published VS06 base.
- `351cc273107e7447e195a15dae92de029ecb3abd` — `feat(media): add provider-neutral playback v1`.
- `51b83a02e4e805dd2899e402c14a3e5d372b43d2` — `fix(media): harden replacement, dispatch, and session errors`.
- Closure docs/review artifacts are intentionally separate from the implementation commits.

## Deferred work and next frontier

Deferred media work remains the VS08 frontier: persistent library/indexing, playlists, album/artist metadata, lyrics, equalizer, conversion, video, network/cloud playback, and broader queue semantics.

Before VS08, close the two current acceptance gaps: run the final APK on a stable awake/unlocked device with the correct package, and physically persist a readable SAF tree for end-to-end ExoPlayer playback. Add binder-level controller authorization/command tests when that device pass is available.
