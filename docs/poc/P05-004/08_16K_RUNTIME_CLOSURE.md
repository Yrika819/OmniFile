# P05-004 16 KiB Runtime Closure — 2026-09-19

## Root cause of `Broken pipe (32)`

The failure is **guest Package Manager / system_server instability under
resource pressure**, not an APK or zstd defect:

- `service check package` alternated between `found` and
  `Can't find service: package` / `not found`.
- `dumpsys package` timed out (10 s) while the service was degraded.
- logcat shows repeated `system_server` death cascades:
  `DeadSystemException: The system died`, Zygote
  `failed to write to system_server FD`, and mass app deaths at
  02:15:05, 02:24:35, and 02:59:18.
- Guest load average reached 15–65 on a 4-CPU / 8 GiB host where the
  emulator RSS alone was ~2.5 GiB and host free pages were ~52 MiB.
- `PackageManager` monitor contention up to 21.5 s was observed
  (`InstallPackageHelper.commitPackageSettings`).
- TheBinder reply times out on the client (`Broken pipe (32)`), while the
  server side can still commit: at 02:23:47 logcat records
  `installation completed for package:dev.p05.archive.poc` with final code
  path `/data/app/~~dPDoH-8su6BStlHwJq6UQg==/dev.p05.archive.poc-vlJ2LHpRlpP6BUHo0xa2MA==`
  even though the client reported `Broken pipe (32)`.
- A local `pm install /data/local/tmp/*.apk` (after `adb push`)
  reproduces the identical client error, proving the fault is not ADB
  streaming/incremental install.

## Prior APK packaging defect (found and fixed in-campaign)

The first x86_64 APK (`051b344a…`, v1) packaged only
`lib/x86_64/libzstd-jni-1.5.7-17.so` without the zstd-jni Java binding
classes. On PAGE_SIZE=16384 the probe started and reported identity, but
all decode tests failed with:

```text
java.lang.NoClassDefFoundError: Failed resolution of: Lcom/github/luben/zstd/Zstd
```

The probe was rebuilt (`/tmp/p05-16k-fix`, disposable, outside git) with
the exact `zstd-jni:1.5.7-17` JAR classes merged into `classes.dex` via
`d8 --min-api 21`, keeping the same x86_64 `.so`.

## Fixed APK (v2, disposable, not committed)

| Field | Value |
|---|---|
| APK SHA-256 | `ecc8f2711688efd4350632ec075faad46f69d79912fa7e22b098ded2e459f4c6` |
| Package | `dev.p05.archive.poc` (versionCode 2) |
| `zipalign -c -P 16 -v 4` | `Verification successful` |
| `apksigner verify` | exit 0 (debug cert; prior v1 used a different campaign cert, so v1 was uninstalled first) |
| Native lib | `lib/x86_64/libzstd-jni-1.5.7-17.so`, stored uncompressed |
| Java bindings | `com.github.luben.zstd.*` from exact `1.5.7-17` JAR inside `classes.dex` (verified via `dexdump`) |
| Fixtures in APK | `res/raw/{valid_zstd,tar_zst,truncated_zstd,malformed_zstd}.bin` (same bytes as v1 stage) |

Reproduction recipe (disposable, host tools only):

```text
javac -source 8 -target 8 -bootclasspath $SDK/platforms/android-36/android.jar \
  -cp zstd-jni-1.5.7-17.jar -d classes src/dev/p05/archive/poc/MainActivity.java
d8 --lib $SDK/platforms/android-36/android.jar --min-api 21 --output dexout \
  classes/dev/p05/archive/poc/MainActivity.class 'classes/dev/p05/archive/poc/MainActivity$1.class' \
  zstd-jni-1.5.7-17.jar
aapt2 compile --dir stage/res -o compiled/res.zip
aapt2 link -o out-unsigned.apk --manifest AndroidManifest.xml -I android.jar compiled/res.zip \
  --min-sdk-version 21 --target-sdk-version 36
zip -0 out-unsigned.apk classes.dex lib/x86_64/libzstd-jni-1.5.7-17.so  # stored
zipalign -P 16 -f 4 out-unsigned.apk out-aligned.apk
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --out v2.apk out-aligned.apk
```

Probe source: `/tmp/p05-16k-fix/src/dev/p05/archive/poc/MainActivity.java`
(disposable build input) is **byte-identical** to the committed
`tools/p05-004/16k-probe/MainActivity.java` (SHA-256
`3d72ad7b507f958f87eb0eab17d126aa0953791bec586a9d632b19dadb0f2a13`
both). Reproducibility claim is behavior-scoped: rebuilding from the
committed source reproduces the 8 `P05_RESULT` lines, not a
byte-identical APK (debug cert, timestamps differ per build).

## Verified 16 KiB environment

| Field | Observed value |
|---|---|
| AVD | `P05_Pixel_API36_16K_x86_64` (`pixel_7a` profile) |
| System image | `system-images;android-36;google_apis_ps16k;x86_64` (official) |
| Guest | API 36, `sdk_gphone16k_x86_64`, x86_64 |
| Fingerprint | `google/sdk_gphone16k_x86_64/emu64xa16k:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys` |
| Kernel | `Linux localhost 6.6.66-android15-8-gd0c43a640eab-ab13812146` |
| Runtime page size | `adb -s emulator-5554 shell getconf PAGE_SIZE` → `16384` |
| Emulator | `37.1.11.0 (build_id 15917651)`, `-no-snapshot -no-boot-anim -gpu swiftshader_indirect` |
| Install path | `package:/data/app/~~gP8KJgr9lT--lKEcDIDTfQ==/dev.p05.archive.poc-UNnEHb4D-TBGr3_pX23XBg==/base.apk` |

## Verified 16 KiB runtime results (PID 8961, 2026-09-19 02:57 UTC)

7/7 expected outcomes (5 PASS + 2 controlled errors; single process):

```text
P05_RESULT test=identity;page_size=16384;abi=x86_64
P05_RESULT test=valid_zstd;PASS;out=21
P05_RESULT test=tar_zst;PASS;out=1536
P05_RESULT test=repeated_load_use;PASS;count=20
P05_RESULT test=larger_decode;PASS;out=1536
P05_RESULT test=truncated_zstd;CONTROLLED_ERROR;error=com.github.luben.zstd.ZstdException:Src size is incorrect
P05_RESULT test=malformed_zstd;CONTROLLED_ERROR;error=com.github.luben.zstd.ZstdException:Unknown frame descriptor
P05_RESULT test=suite_done;DONE
```

Payload sizes (21-byte valid payload, 1536-byte TAR) match the Pixel 4 KiB
run exactly. Note: `larger_decode` re-decodes the same `tar_zst` bytes
(`out=1536` identical) — it exercises a second full decode path, not an
independent larger input. No `FATAL EXCEPTION` involving `dev.p05.archive.poc` or zstd
in the full logcat; the 73 `FATAL EXCEPTION` lines are the 02:59
system_server death cascade (`DeadSystemException` across all PIDs).
Pre-existing `/data/tombstones/tombstone_0{0,1}` are dated 01:10/01:18,
before this emulator boot (02:07), and are unrelated.

## Remaining 16 KiB limitation

- `am force-stop` succeeded, but the post-force-stop relaunch produced no
  second result set: the `activity` service itself returned
  `Broken pipe (32)` during the 02:59 system_server death window.
- The Pixel 4 KiB campaign already demonstrated force-stop/relaunch PASS
  for the same probe logic; the 16 KiB relaunch gap is guest-infrastructure
  instability, not an observed app failure.
- Classification: `16K_RUNTIME_VERIFIED` for the exact tested
  environment/ABI (API 36 ps16k x86_64, `zstd-jni 1.5.7-17`), with
  multi-process relaunch and broader ABI/device coverage as explicit
  RELEASE_GATEs. Architecture reviewer must confirm this downgrade.

## Gate effect

- Blocker group A item `16 KiB application runtime` moves from
  `NOT_COMPLETED / ENVIRONMENT_BLOCKER` to
  `16K_RUNTIME_VERIFIED (single-process full pass; relaunch = RELEASE_GATE)`.
- `Broken pipe (32)` root cause is closed as infrastructure, with evidence
  above. No zstd native defect was observed on PAGE_SIZE=16384.
