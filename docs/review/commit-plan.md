# VS07 Actual Commit Record and Closure Plan

This file records the commits that actually exist. The earlier prospective nine-commit decomposition is superseded; VS07 implementation history is intentionally preserved and not squashed.

## Actual commits

1. `351cc273107e7447e195a15dae92de029ecb3abd` — `feat(media): add provider-neutral playback v1`
   - Media3 dependencies and verification metadata.
   - Provider-neutral playback identity, eligibility, state/error models.
   - Local and SAF playback-source adapters and controlled provider fixture.
   - MediaSession service, coordinator, manifest contract, Files/Search/Music integration.
   - Host and instrumentation coverage.

2. `51b83a02e4e805dd2899e402c14a3e5d372b43d2` — `fix(media): harden replacement, dispatch, and session errors`
   - Clear the service player before replacement failures can publish a new error.
   - Ignore stale asynchronous callbacks from a previous expected media identity.
   - Dispatch provider resolution/probing on `Dispatchers.IO`.
   - Retry failed/disconnected controller futures and invalidate teardown callbacks.
   - Latch player errors until a new request or explicit stop.
   - Restrict external controller seek capability.
   - Add failed-replacement instrumentation coverage.

## Closure artifacts

The following artifacts describe the final implementation target and are separate from the implementation commits:

- `docs/production/VERTICAL_SLICE_07_MEDIA_PLAYBACK_V1.md`
- `docs/review/VS07_CODE_REVIEW_V2_CHANGE_SUMMARY.md`
- `tmp/reviews/2026-09-22-code-review-vs07-final-5bd01cbc.md`
- `tmp/reviews/2026-09-22-user-visible-regression-report-76c66ac4.md`
- `tmp/reviews/2026-09-22-android-intent-security-vs07-fc3a4468.md`

APK outputs are not committed. Final rebuilt hashes are recorded in the production note.

## Closure state

The source/build/media-focused checks are green. Closure is not a Pass because the connected Pixel remained in Dozing/NotificationShade state for UI/lifecycle tests and had no persisted readable SAF tree for real-device SAF playback.

Next verification boundary:

1. Install this final target on the correct package/device.
2. Keep the device awake and unlocked.
3. Physically persist a readable SAF tree containing the fixture.
4. Rerun full connected instrumentation and reconcile the reports without rewriting frozen historical reports.
