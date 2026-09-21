# VS07 Review A — Provider-Neutral Media Playback Architecture

Date: 2026-09-21
Branch: `development/core-v1-media-playback-v1`
Base: `67a8e0e08d3b6ad03165d0d962ad8868b00c661c` (published VS06, local == origin, clean)
Mode: READ-ONLY architecture review, frozen before substantial writer work.

## Inputs audited

- `docs/architecture/04_MEDIA_INTEGRATION.md` (provider-neutral resolution ACCEPTED; Media3 direction ACCEPTED; version/service class explicitly unfrozen; sequential != seekable invariant).
- `docs/architecture/ADR/ADR-002-provider-scoped-identity.md` (durable identity is provider-scoped).
- `TECHNOLOGY_FREEZE_V1.md` Media3 row: evidence base P05-003; PoC-version blind freeze rejected; current stable checked at scaffold.
- P05-003 PoC (`poc/core-readiness-media3-v1`, `tools/p05_003_harness`): Media3 `1.3.1` on Pixel 7a API 36; local WAV/FLAC direct playback PASS; SAF direct playback PASS; sequential source played to EOF and rejected seek probe (`ERROR_CODE_IO_UNSPECIFIED`) — the exact non-seekable boundary VS07 must represent truthfully.
- Production baseline: `StorageModel.kt` (`EntryRef.identityKey`, `StorageCapability.READ_SEQUENTIAL`, `READ_SEEKABLE` unused by SAF today), `SafStorageProvider`, `LocalStorageProvider`, `TestDocumentsProvider` (controlled DocumentsProvider), `AppContainer`, `AppShell` (Home/Search/Music/Settings), truthful empty `MusicScreen`.
- UI authority: `UI_DESIGN_V1.md` §7 mini-player/§8 full player are FLACtify-library era surfaces (out of VS07 scope); `docs/ui-authority/music/*` screenshots are folder/file browsing (Music Library slice), not a now-playing requirement. VS07 Music = truthful Now Playing surface only.
- Google Maven metadata (2026-09-11): Media3 current stable `1.11.1`.

## Reviewed design

### D1. Media3 dependency set

- New production dependencies: `androidx.media3:media3-exoplayer:1.11.1`, `androidx.media3:media3-session:1.11.1`. Nothing else: no media3-ui (Compose UI is custom), no cast/okhttp/decoder extensions.
- Rationale: production had no Media3 pin; the freeze requires checking current stable at scaffold rather than blind-freezing the PoC's 1.3.1; 1.11.1 keeps the exact APIs P05-003 exercised (ExoPlayer, DefaultDataSource over file:// and content://, MediaSession). Runtime acceptance re-runs on the same Pixel 7a / API 36; if any 1.11.1 regression appears, the documented fallback is the PoC-proven 1.3.1.
- Both pins enter `gradle/libs.versions.toml`, `app/gradle.lockfile` (regenerated), and `gradle/verification-metadata.xml` (sha256); final builds run with `--dependency-verification=strict`.

### D2. Player/session ownership

- `OmniFilePlaybackService : MediaSessionService` owns the single `ExoPlayer` and single `MediaSession`. UI never owns player lifetime.
- ExoPlayer: `AudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC)` with `handleAudioFocus=true`; `setHandleAudioBecomingNoisy(true)`. No custom audio-focus manager.
- Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`; service `exported=true` (required for system media controls), `androidx.media3.session.MediaSessionService` intent filter, `foregroundServiceType="mediaPlayback"`. No storage/mic/internet permissions added.
- POST_NOTIFICATIONS is requested once at first user-initiated Play (API 33+); playback never gates on it. Media3's own notification mechanism is used; no custom notification state machine.

### D3. Provider-neutral playback identity

- `PlaybackItem`: `mediaId = providerId.value + NUL + EntryRef.identityKey`, plus displayName, mimeType, sizeBytes, sourceLabel. Absolute paths and raw SAF URIs never enter the media identity model or the session; they exist only as transient transport inside provider adapters.
- Same display name from two providers yields distinct mediaIds; rename does not change identity; directories are never playable.

### D4. Provider-specific data access

- New optional storage interface `PlaybackSourceProvider : StorageProvider` (mirrors the `StorageTransferProvider` optional-interface pattern; base `StorageProvider` contract unchanged, no codec claims added):
  `suspend fun resolvePlaybackSource(entry): StorageResult<PlaybackSource>`.
- `PlaybackSource(transportUri: String, seekSupport: SeekSupport, sourceLabel: String)`. String (not android.net.Uri) keeps `LocalStorageProvider` host-testable (pure JVM suite must keep passing under JBR).
- Local adapter: `file://` transport from the checked `Path`; SEEKABLE only when regular readable file; other types fail per existing secure-traversal rules.
- SAF adapter: `content://` document transport from tree+documentId; per-item probe: `openFileDescriptor("r")` + `Os.fstat` → `S_ISREG` ⇒ SEEKABLE; FIFO/socket ⇒ NOT_SEEKABLE; probe failure ⇒ UNKNOWN (playback may still be attempted; seek UI stays disabled). No universal SAF seek claim.

### D5. Eligibility

Pure function over `StorageEntry`: kind == FILE AND `READ_SEQUENTIAL` capability AND (`audio/*` provider MIME OR conservative extension fallback {flac, mp3, m4a, aac, wav, ogg, oga, opus}). Fallback exists because some providers omit MIME; it is documented and does not promise codecs. Unreadable/unsupported entries get truthful ineligibility reasons.

### D6. Session/controller security

- `MediaSession.Callback.onConnect`: accept own package/UID; accept external packages holding `android.media.permission.MEDIA_CONTENT_CONTROL` (SystemUI, Bluetooth/headset via system); reject everything else (Media3 1.11 `ConnectionResult.reject()`, verified at compile time). The service stays exported for legitimate system controls; arbitrary third-party control injection is denied. No custom session commands are defined; no PendingIntent/content-URI grants are exposed.

### D7. Now-playing state model

Single `StateFlow<NowPlayingState>` from `PlaybackCoordinator` (AppContainer-scoped):
status ∈ {NO_MEDIA, BUFFERING, READY, ENDED, ERROR}, item metadata, isPlaying, positionMs, durationMs (null = unknown, never rendered as 0:00), seekSupport, mapped `PlaybackError` ∈ {PermissionUnavailable, SourceNotFound, ProviderUnavailable, UnsupportedMedia, DecodeFailure, ReadFailure, SourceChanged, Unknown}. Raw `PlaybackException` never reaches UI.

### D8. Controller lifecycle

One `MediaController` built lazily via `MediaController.Builder(context, SessionToken(service)).buildAsync()` inside the coordinator; listener attached once; position polling (500 ms) only while playing. Activity recreation reconnects to the same coordinator/session — no duplicate player, one listener, no per-recomposition controllers.

### D9. UI integration

- Files: explicit trailing Play action on eligible single audio rows only; hidden in selection mode; row tap semantics unchanged (Preview remains free).
- Search: same Play action on eligible result rows; same coordinator; stale results fail through provider identity errors.
- Music: Now Playing surface — title, source label, play/pause, position/duration (duration only when known), Slider enabled only when `seekSupport == SEEKABLE` and duration known; truthful empty state retained when NO_MEDIA. No fake library/metadata; unknown metadata fields are omitted, not fabricated.
- Edge-to-edge: reuse existing Scaffold/insets patterns; controls padded above the nav bar via the existing shell layout; no double padding.

### D10. Failure semantics

Provider unavailable / grant lost / source missing before or during playback → mapped error state, no crash, no silent item switch, no false playing state, no storage mutation from the media layer. Source deletion during playback surfaces as read failure/ended state per Media3 callbacks.

### D11. Test plan (unchanged stack: JUnit4 + Compose instrumentation + controlled DocumentsProvider)

Host: identity distinctness/rename/directory, eligibility matrix, state mapping, error mapping.
Instrumentation: service creation + controller connect, generated PCM16 WAV (runtime-generated) and committed 16 KB P05-003 FLAC (Lavf-generated synthetic tone; provenance recorded) via Local provider; controlled SAF seekable/pipe/failure cases (TestDocumentsProvider extended in place); error state; Activity recreation; Music/Files/Search Compose actions.
Pixel 7a API 36: Local WAV+FLAC end-to-end, SAF via the existing disposable `OmniFile-SAF-Test` persisted grant, background/navigation persistence, MediaSession visibility via read-only `dumpsys media_session`.

## Findings

None at Blocker/Major/Minor. Risks carried with mitigation: (R1) Media3 1.11.1 not yet runtime-proven by this project → Pixel acceptance gate with documented 1.3.1 fallback; (R2) SAF seek probe costs one extra fd open per resolve → bounded, documented; (R3) the existing device SAF grant may have been revoked since VS04 → verified mechanically before SAF acceptance; a revoked grant requires physical DocumentsUI selection (declared stop condition, never worked around).

## Verdict: Pass

Architecture is frozen for implementation. Any deviation from D1–D11 reopens this review.

