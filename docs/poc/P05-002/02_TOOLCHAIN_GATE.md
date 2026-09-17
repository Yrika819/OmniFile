# P05-002 Toolchain Gate

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Inspection performed

The inspection was read-only and stayed within the requested worktree plus the local toolchain paths. No ADB command, emulator command, physical device, or provider account was used.

Observed locally:

- Java executable: `/usr/bin/java`.
- Android SDK root: `/Users/yuta/Library/Android/sdk`.
- Installed platform directories include `android-31`, `android-36`, `android-36.1`, `android-37.0`, and `android-37.1`.
- Installed build-tool artifacts include `d8` and `aapt2` under build-tools 35.0.0, 36.0.0, 36.1.0, and 37.0.0.
- No `gradle`, `gradlew`, or `kotlinc` executable was found in the inspected SDK/worktree paths.
- The repository has no existing Android module or build files for a SAF harness.

## Gate result

The available artifacts are not a complete, reproducible Android application/instrumentation toolchain for this repository. An Android SAF harness would require an app/module build setup and Android runtime execution; creating a production-shaped app or selecting versions would violate the request, and device/ADB execution is explicitly prohibited.

**Result: HOST-SIDE FALLBACK.** The committed harness is an in-memory fault-injection model. It does not call Android APIs and must not be described as Android SAF evidence.

## Physical-device blocker

The following POC-002 evidence remains blocked and is intentionally not attempted here:

- actual `DocumentsProvider`/`ContentResolver` behavior;
- persistable grant survival and revocation;
- descriptor type and seekability;
- API 31/API 36 provider behavior;
- process death, force-stop, reboot, storage-full, power-loss, SD/USB removal, or OEM behavior;
- user-visible recovery UX.

These require a separately authorized disposable Android test project plus controlled emulator/physical-device execution. This branch does not create that authority.
