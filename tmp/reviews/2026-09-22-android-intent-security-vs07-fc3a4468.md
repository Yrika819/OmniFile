# VS07 Android Intent / Media Service Security Audit

- Date: `2026-09-22`
- Scope: `final VS07 target 51b83a02e4e805dd2899e402c14a3e5d372b43d2`
- Baseline: `67a8e0e08d3b6ad03165d0d962ad8868b00c661c`
- Skill: `android-intent-security`
- Verdict: `Pass with evidence caveat`

## Component and service policy

- `OmniFilePlaybackService` is intentionally `android:exported="true"` because Media3 system media controls discover/bind through the `MediaSessionService` contract.
- The service declares the `androidx.media3.session.MediaSessionService` intent filter and `android:foregroundServiceType="mediaPlayback"`.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `POST_NOTIFICATIONS` are the only playback-specific permissions. No `READ_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, microphone, or internet permission was introduced.
- The service does not accept nested intents, redirect externally supplied components, or expose a ContentProvider.

## Controller authorization and command injection

`SessionCallback.onConnect` verifies:

1. the reported package belongs to the reported UID;
2. the app controller matches both `Process.myUid()` and the app package;
3. external controllers hold `android.media.permission.MEDIA_CONTENT_CONTROL`.

Other controllers are rejected with `MediaSession.ConnectionResult.reject()`.

The accepted external command set is limited to current-item/timeline/metadata reads, play/pause, and stop. External controllers do not receive `COMMAND_SET_MEDIA_ITEM`, `COMMAND_PREPARE`, `COMMAND_CHANGE_MEDIA_ITEMS`, or `COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM`. The app-owned controller receives source-selection and seek commands; the coordinator separately requires per-item proven `SEEKABLE` support before issuing seek.

Media3 `1.11.1` inherits a standard `onStartCommand` media-action path for framework media-button and notification actions. This is a deliberate part of the `MediaSessionService` contract, not an arbitrary URI/media-item command surface: the app's source-selection commands remain app-controller-only and no custom session commands are defined. Adding `android:permission="android.media.permission.MEDIA_CONTENT_CONTROL"` to the service was not appropriate because it would also block the app's own controller binding and can break legitimate system control binding.

## PendingIntent

The session activity PendingIntent is:

- explicit: `Intent(this, MainActivity::class.java)`;
- immutable: `PendingIntent.FLAG_IMMUTABLE`;
- updated only with `FLAG_UPDATE_CURRENT`.

No SAF URI or grant flags are placed in that PendingIntent.

## SAF grant boundary

- Tree selection requests read plus persistable read permission.
- The grant store masks permissions, requires a valid tree URI/authority, calls `takePersistableUriPermission`, and re-reads actual persisted permission before storing.
- `AppContainer` and `SafStorageProvider` require a current readable persisted grant and validate provider/tree/document containment.
- Playback identity is provider-neutral and opaque; raw local paths and raw SAF URIs are transport details used only inside adapters.
- No grant fabrication, broad storage access, or grant forwarding to external controllers was found.

## Evidence

- `MediaSessionServiceInstrumentedTest`: 3/3 pass on the rebuilt final target, including exported service, mediaPlayback foreground type, requested permission restrictions, system-visible title, foreground state, and stopped-service state.
- Full final device run: 75 recorded, 47 passed, 28 failures, 0 errors, 0 skipped. The failures are UI/lifecycle device-state cascades plus the absent persisted-SAF assumption; no security assertion failed.
- The remaining direct-controller binder authorization/command-set test is recorded as code-review gap `T3`, not as a confirmed security defect.

## Security conclusion

- Export policy: intentional and required by Media3 system-control integration.
- Foreground type/permissions: correct and minimal.
- Controller connection policy: strict UID/package binding plus `MEDIA_CONTENT_CONTROL` for approved external controllers.
- Arbitrary external media-item injection: not exposed through the accepted command set or custom commands.
- PendingIntent: explicit and immutable.
- SAF grants: revalidated and not leaked through the session activity intent.
- Broad storage permission: not introduced.
- Residual follow-up: add binder-level positive/negative controller tests and inspect external metadata exposure if product privacy requirements later become stricter.
