# P05-002 Environment

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Scope and checkout

This branch is `poc/core-readiness-saf-operations-v1`, based exactly on
`0b75b19502217d59e2a8332c2af4c83c0497d3e1`. It contains the existing host
policy harness plus a new isolated raw-SDK APK under `android/`.

## Inspection performed

The inspection was read-only and stayed within the requested worktree plus the local toolchain paths. No ADB command, emulator command, physical device, or provider account was used.

Observed locally:

- Java executable: `/usr/bin/java`.
- Android SDK root: `/Users/yuta/Library/Android/sdk`.
- Installed platform directories include `android-31`, `android-36`, `android-36.1`, `android-37.0`, and `android-37.1`.
- Installed build-tool artifacts include `d8` and `aapt2` under build-tools 35.0.0, 36.0.0, 36.1.0, and 37.0.0.
- No `gradle`, `gradlew`, or `kotlinc` executable was found; the new harness therefore uses the SDK tools directly.
- The raw harness uses package `dev.poc.safoperations`, API 31 minimum/API 36 target, and framework APIs only.

## Gate result

The available artifacts are sufficient for a disposable raw APK build, but not for runtime execution in this task. No production-shaped app or dependency selection was introduced.

**Result: BUILDABLE HOST + ANDROID POC.** The Python model is host-only; the APK calls Android SAF APIs when later run by the parent, but this branch contains no runtime/device result.

## Physical-device blocker

The following POC-002 evidence remains blocked and is intentionally not attempted here:

- actual `DocumentsProvider`/`ContentResolver` behavior;
- persistable grant survival and revocation;
- descriptor type and seekability;
- API 31/API 36 provider behavior;
- process death, force-stop, reboot, storage-full, power-loss, SD/USB removal, or OEM behavior;
- user-visible recovery UX.

These require a separately authorized disposable Android test project plus controlled emulator/physical-device execution. This branch does not create that authority.

## Evidence boundary

The executable evidence in this branch is the standard-library host suite and
the raw SDK compile/package/sign/verify pipeline. No ADB command, emulator
command, physical device, provider account, production module, or user file
was created or used.
