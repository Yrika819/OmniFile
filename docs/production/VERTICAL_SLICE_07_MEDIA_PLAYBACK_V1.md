# OmniFile CORE_V1 — Vertical Slice 07
## Provider-Neutral Media Playback V1

- Published VS06 base: `67a8e0e08d3b6ad03165d0d962ad8868b00c661c`
- Branch: `development/core-v1-media-playback-v1`
- Final implementation/evidence target: `607759c`
- Worktree: `/Users/yuta/Desktop/File Manager-worktrees/omnifile-media-playback-v1`
- Status: **COMPLETE — provider-neutral media playback V1 closed**

## Mission and boundary

VS07 adds provider-neutral, truthful Local/SAF audio playback through Media3/ExoPlayer, one current item, play/pause, truthful progress/duration, seek only when proven, MediaSession/MediaSessionService ownership, background/session lifecycle, system controls, Files Play, Search Play, and a functional Music Now Playing destination.

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

`OmniFilePlaybackService` owns exactly one ExoPlayer and one MediaSession. `PlaybackCoordinator` is process-scoped in `AppContainer`, owns one MediaController, survives Activity recreation, and never constructs a player. UI collects one `StateFlow<NowPlayingState>`.

`PlaybackItem.mediaId` is provider ID plus an opaque SHA-256 digest of the provider-scoped identity key. Raw local paths and SAF URIs are transport-only adapter details.

Readable is not treated as seekable:

- Local regular readable file: `SEEKABLE`.
- SAF regular-file descriptor: `SEEKABLE`.
- SAF pipe/socket descriptor: `NOT_SEEKABLE`.
- Inconclusive probe: `UNKNOWN`; playback may continue but seek remains disabled.

SAF playback requires a current persisted readable grant and provider/tree/document containment validation. No grant fabrication or broad storage permission is used.

## Service, controller, and security contract

- Exported `MediaSessionService` is intentional for system media controls.
- Manifest declares `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, and `foregroundServiceType="mediaPlayback"`.
- No storage, microphone, internet, `READ_EXTERNAL_STORAGE`, or `MANAGE_EXTERNAL_STORAGE` permission was added.
- Same-app controller requires matching UID/package identity; approved external controllers require `MEDIA_CONTENT_CONTROL`.
- External controllers receive read/transport commands only; app-only commands include source selection and seek.
- Session activity PendingIntent is explicit and immutable.
- No nested untrusted Intent, custom session command, arbitrary media-item injection, or SAF grant forwarding is exposed.

## UI and edge-to-edge

- Files: explicit Play only for eligible single audio entries; hidden in selection mode; ordinary row taps retain existing semantics.
- Search: same Play action for eligible results; result-open and CurrentFolder/ThisDevice scope behavior remain separate.
- Music: truthful empty/active/paused/ended/error states, real title/source, mapped errors, known duration/progress, and seek only when proven.
- `MainActivity.enableEdgeToEdge()` and `adjustResize` are present; Music `Scaffold` consumes inner padding; Material navigation components own navigation-bar/rail insets without double padding.

## Host/build verification

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
- Lint: pass.
- Debug APK assembly: pass.
- AndroidTest APK assembly: pass.
- Strict dependency verification: pass.
- Zed diagnostics: no relevant errors or warnings in changed files.

## Final APK hashes

```text
app/build/outputs/apk/debug/app-debug.apk
SHA-256: a67477b101758a1305e547fa4e015c51958bafd9b4c57b1c8c0f8a012eaf3f96

app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
SHA-256: d9c7145630d5bb7ff6161c5588d766f979ba2efefb2f01f7952b84453fdbbdc3
```

## Pixel 7a API 36 final evidence

The exact `com.omnifile` APKs were installed on an awake Pixel 7a. The earlier stale `org.omnifile.poc.media3` package was not used.

Focused evidence:

- `MediaPlaybackInstrumentedTest`: **7/7 pass** — WAV, FLAC, play/pause, proven seek, non-seekable suppression, missing source, ended state, and failed replacement.
- `MediaSessionServiceInstrumentedTest`: **5/5 pass** — service/FGS manifest, session visibility, stopped service, authorized same-app controller command surface, and rejected untrusted controller.
- `SafPlaybackSourceInstrumentedTest`: **7/7 pass** — regular-file, pipe, UNKNOWN, permission, missing, provider-unavailable, and stale-reference cases.
- `SafDevicePlaybackInstrumentedTest`: **1/1 pass** through the normal persisted DocumentsUI grant path.
- `PlaybackLifecycleInstrumentedTest`: **2/2 pass** — Activity recreation/background continuity and singular service-owned playback.
- Compose/UI: **25/25 pass** — Files Play/Compose, Music, Search Play/Compose.

Final complete connected matrix:

```text
77 tests recorded
77 passed
0 failures
0 errors
0 skipped
```

All instrumentation classes passed:

- Production scaffold: `2/2`
- Media playback: `7/7`
- MediaSession/service/binder: `5/5`
- Lifecycle: `2/2`
- Real persisted SAF: `1/1`
- Controlled SAF: `7/7`
- Operations/database: `8/8`
- Search/storage: `20/20`
- Files Compose/Play: `7/7`
- Music: `13/13`
- Search Compose/Play: `5/5`

## Real SAF acceptance details

- Grant method: normal DocumentsUI `ACTION_OPEN_DOCUMENT_TREE` flow from OmniFile Home.
- Selected disposable tree: `OmniFile-SAF-Test`.
- Provider authority: `com.android.externalstorage.documents` (Pixel external-storage DocumentsProvider; provider registry mechanically verified).
- Fixture: `vs07-saf-tone.wav`, deterministic synthetic PCM16 mono WAV, 2-second fixture generated by `MediaTestFixtures`.
- DocumentsUI metadata: WAV audio; approximately 64 KiB.
- Playback path: persisted read grant -> `SafStorageProvider` -> `content://` transport -> MediaSessionService -> ExoPlayer -> READY/PLAYING -> position advancement -> Music state.
- Seekability: per-item descriptor result; the regular external-storage fixture is treated only as the tested item result, not a global SAF rule. Pipe and UNKNOWN behavior remain covered by the controlled SAF matrix.
- Functional result: READY/PLAYING, duration/progress reported, position advanced, and seek was capability-gated according to the per-item probe.
- No personal media, root grant, grant database edit, or `MANAGE_EXTERNAL_STORAGE` was used.

## Reviews

- Historical architecture review remains unchanged: `tmp/reviews/2026-09-21-code-review-vs07-review-a.md`.
- Frozen generation-0 review remains unchanged: `tmp/reviews/2026-09-22-code-review-vs07-final-5bd01cbc.md`.
- Receiving resolution: `tmp/reviews/2026-09-22-receiving-code-review-vs07-1fe725c0.md`.
- Final generation-1 code review: `tmp/reviews/2026-09-22-code-review-vs07-final-gen1-9d018b94.md` — validator-clean, **Pass**, zero findings/gaps.
- Frozen prior regression review remains unchanged: `tmp/reviews/2026-09-22-user-visible-regression-report-76c66ac4.md`.
- Final regression review: `tmp/reviews/2026-09-22-user-visible-regression-final-313108f1.md` — **Pass**, no confirmed VS06 regression.
- Final intent/security audit: `tmp/reviews/2026-09-22-android-intent-security-final-7d717090.md` — **Pass**.

## Actual commits

- `67a8e0e08d3b6ad03165d0d962ad8868b00c661c` — published VS06 base.
- `351cc273107e7447e195a15dae92de029ecb3abd` — provider-neutral playback implementation.
- `51b83a02e4e805dd2899e402c14a3e5d372b43d2` — playback hardening.
- `172681e3e6c4cb94538bf5640bb60438f095004c` — initial closure evidence.
- `607759c` — physical SAF and binder controller evidence tests.

## Deferred work / VS08 frontier

Persistent music library/indexing, playlists, album/artist metadata, lyrics, equalizer, conversion/transcoding, video, cloud/network playback, and broader queue semantics remain deferred to VS08.
