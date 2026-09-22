# VS07 Final Android Intent / Media Service Security Audit

- Date: `2026-09-22`
- Target: `607759c`
- Baseline: `67a8e0e08d3b6ad03165d0d962ad8868b00c661c`
- Skill: `android-intent-security`
- Verdict: **Pass**

## Service/export policy

- `OmniFilePlaybackService` remains intentionally exported for the Media3 system media-control contract.
- The service has the `MediaSessionService` intent filter and `foregroundServiceType="mediaPlayback"`.
- Permissions are minimal: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `POST_NOTIFICATIONS`.
- No storage, microphone, internet, `READ_EXTERNAL_STORAGE`, or `MANAGE_EXTERNAL_STORAGE` permission was introduced.

## Controller policy and binder evidence

`SessionCallback.onConnect` verifies package-to-UID membership, requires same-app UID/package for the app controller, and requires `MEDIA_CONTENT_CONTROL` for approved external controllers. All other controllers are rejected.

Final instrumentation evidence:

- Authorized same-app controller: connects and receives required current-item/timeline/metadata read commands, play/pause, stop, and app-only source-selection capability.
- Untrusted `com.omnifile.test` controller: connection is rejected.
- External controllers do not receive set-media-item, prepare, change-media-items, or seek commands.
- Seek remains per-item and coordinator-gated by proven `SEEKABLE` support; no universal SAF seek claim exists.

`MediaSessionServiceInstrumentedTest`: **5/5 pass**.

## Intent and PendingIntent handling

- No nested untrusted Intent is consumed or redirected.
- Session activity PendingIntent is explicit and immutable.
- No SAF URI/grant flags are placed in the PendingIntent.
- Media3's standard media-action start path is retained for legitimate system controls; no custom session commands or arbitrary media-item injection surface is exposed.

## SAF grant boundary

- The real grant was created only through normal DocumentsUI.
- `SafDevicePlaybackInstrumentedTest`: **1/1 pass** after a physical `OmniFile-SAF-Test` selection.
- Provider/tree/document containment and current persisted readable grant checks remain active.
- No grant fabrication, grant database editing, or broad storage access was used.

## Final conclusion

Export policy, foreground configuration, controller authorization, command allowlisting, PendingIntent behavior, and SAF grant handling are all supported by static inspection and final runtime evidence. No unresolved intent-security finding remains within VS07 scope.
