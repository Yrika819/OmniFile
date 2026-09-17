# P05-002 Environment

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Scope and checkout

This branch is `poc/core-readiness-saf-operations-v1`, based exactly on
`0b75b19502217d59e2a8332c2af4c83c0497d3e1`. It contains the existing host
policy harness plus a new isolated raw-SDK APK under `android/`.

## Inspection performed

The initial inspection was read-only and stayed within the requested worktree
plus local toolchain paths. A later explicitly authorized parent phase used
only a connected Pixel 7a and disposable DocumentsUI trees; no production app
or user file was used.

Observed locally:

- Java executable: `/usr/bin/java`.
- Android SDK root: `/Users/yuta/Library/Android/sdk`.
- Installed platform directories include `android-31`, `android-36`, `android-36.1`, `android-37.0`, and `android-37.1`.
- Installed build-tool artifacts include `d8` and `aapt2` under build-tools 35.0.0, 36.0.0, 36.1.0, and 37.0.0.
- No `gradle`, `gradlew`, or `kotlinc` executable was found; the new harness therefore uses the SDK tools directly.
- The raw harness uses package `dev.poc.safoperations`, API 31 minimum/API 36 target, and framework APIs only.

## Gate result

The available artifacts were sufficient for the disposable raw APK build and
the parent phase subsequently supplied one Pixel 7a API 36 runtime. No
production-shaped app or dependency selection was introduced.

**Result: HOST + ANDROID POC WITH ONE API 36 DEVICE CAPTURE.** The Python
model remains host-only; the APK result is limited to the exact parent run
recorded in `04_RESULTS.md` and does not generalize across providers or API
levels.

## Remaining physical-device blockers

The following POC-002 evidence remains blocked or partial and was not claimed:

- provider behavior beyond the one selected DocumentsUI tree and its
  `ContentResolver` copy operations;
- persistable grant revocation;
- descriptor type and seekability;
- API 31 behavior and provider-specific API 36 behavior beyond that tree;
- pre-finalization process death, reboot, storage-full, power-loss, SD/USB removal, or OEM behavior;
- user-visible recovery UX.

These require additional separately authorized disposable Android runs. This
branch does not create that authority.

## Evidence boundary

The executable evidence in this branch is the standard-library host suite, the
raw SDK compile/package/sign/verify pipeline, and the bounded Pixel 7a API 36
capture summarized in `04_RESULTS.md`. No production module or user file was
created or used.
