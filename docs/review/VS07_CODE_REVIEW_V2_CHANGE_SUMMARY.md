# VS07 Final Review and Evidence Summary

## Scope

Provider-neutral media playback V1: Local/SAF playback through Media3/ExoPlayer, one current item, MediaSession/MediaSessionService, Files/Search Play, truthful Music Now Playing, play/pause, duration/progress, proven-only seek, background/session lifecycle, system controls, and Pixel evidence.

Deferred: persistent library/indexing, playlists, album/artist database, lyrics, equalizer, conversion/transcoding, video, cloud/network playback, and VS08.

## Final implementation history

- VS06 base: `67a8e0e08d3b6ad03165d0d962ad8868b00c661c`
- `351cc273107e7447e195a15dae92de029ecb3abd` — provider-neutral playback implementation.
- `51b83a02e4e805dd2899e402c14a3e5d372b43d2` — replacement/request callback hardening, IO dispatch, error latching, external seek restriction, and failed-replacement test.

The previous four implementation-review findings were repaired in `51b83a0`:

1. failed replacement now stops/clears the prior service player before publishing the new request;
2. Local/SAF source resolution runs on `Dispatchers.IO`;
3. external controllers no longer receive seek capability;
4. Media3 player errors remain latched until a new request or explicit stop.

## Architecture/security conclusions

- ExoPlayer and MediaSession are owned only by `OmniFilePlaybackService`.
- `PlaybackCoordinator` is process-scoped and owns one MediaController; UI never owns player lifetime.
- Media identity is provider ID plus opaque SHA-256 identity digest; raw path/URI is transport-only.
- SAF grant validity, provider/tree/document containment, and per-item descriptor seekability are checked at the adapter boundary.
- Service controller authorization verifies package-to-UID binding, same-app UID/package, and `MEDIA_CONTENT_CONTROL` for approved external controllers.
- External commands are read/transport only; app-only commands include source selection and seek.
- PendingIntent is explicit and immutable.
- FGS/media notification permissions are minimal; no broad storage/microphone/internet permission exists.
- The inherited Media3 standard media-action start path is retained for legitimate system controls; it does not expose arbitrary media-item injection or custom commands.

## Verification

### Host

- JBR `25.0.3`, Gradle `9.6.0`, strict dependency verification.
- `23 classes, 115 tests, 0 failures, 0 errors, 0 skipped`.
- Lint, debug assembly, and androidTest assembly passed.

### Pixel 7a API 36 focused media evidence

- Media playback: `7/7` pass.
- MediaSession/service: `3/3` pass.
- Controlled SAF source probe: `7/7` pass.

### Pixel full matrix

- `75 recorded, 47 passed, 28 failures, 0 errors, 0 skipped`.
- 25 Compose tests failed with no hierarchy while the device was Dozing/NotificationShade-focused.
- 2 lifecycle tests ended STOPPED rather than RESUMED under the same device state.
- 1 real-SAF test stopped at its deliberate assumption because persisted readable SAF permissions were empty.
- This is not reported as a green full-device pass.

### APK hashes

- Debug: `a67477b101758a1305e547fa4e015c51958bafd9b4c57b1c8c0f8a012eaf3f96`
- AndroidTest: `e555e8cd31516c879ef97895a1f9b188e5bf01c25cf6108d101da350fb2e37de`

## Frozen reports

- Final code review: `tmp/reviews/2026-09-22-code-review-vs07-final-5bd01cbc.md` — validator-clean; no source findings; `Changes requested` for two Major acceptance gaps and one Minor test gap.
- Regression review: `tmp/reviews/2026-09-22-user-visible-regression-report-76c66ac4.md` — no confirmed VS06 regression; `Discuss` for incomplete physical coverage.
- Intent/security audit: `tmp/reviews/2026-09-22-android-intent-security-vs07-fc3a4468.md` — Pass with evidence caveat.

## Final status

`OMNIFILE_CORE_V1_VS07_INCOMPLETE`.

Exact blockers: the current device cannot provide valid UI/Activity lifecycle evidence, and no physical readable persisted SAF tree exists for real SAF-to-ExoPlayer playback. Neither gap is worked around or fabricated.
