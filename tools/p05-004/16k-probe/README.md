# P05-004 16 KiB disposable probe — reproducible build

Disposable PoC-only Android probe. Not production code, not an OmniFile
scaffold. Builds the exact `dev.p05.archive.poc` probe APK that produced
the PAGE_SIZE=16384 runtime evidence (`docs/poc/P05-004/08_16K_RUNTIME_CLOSURE.md`).

## Inputs (exact, pinned by hash in `docs/poc/P05-004/01_ENVIRONMENT.md`)

- `com.github.luben:zstd-jni:1.5.7-17` JAR (Java bindings) and the x86_64
  `libzstd-jni-1.5.7-17.so` from the `1.5.7-17` AAR.
- `res/raw/{valid_zstd,tar_zst,truncated_zstd,malformed_zstd}.bin` fixture
  bytes (same set as the prior campaign stage; see `08_*` for recipe
  provenance). Place them under `res/raw/` before building.

## Build (host tools only)

```sh
B=/tmp/p05-16k-repro; SDK=$HOME/Library/Android/sdk
BT=$SDK/build-tools/36.1.0; ANDROID_JAR=$SDK/platforms/android-36/android.jar
ZJAR=<path-to>/zstd-jni-1.5.7-17.jar
ZSO=<x86_64 libzstd-jni-1.5.7-17.so from 1.5.7-17 AAR>
mkdir -p $B/classes $B/dexout $B/compiled
javac -source 8 -target 8 -bootclasspath $ANDROID_JAR -cp "$ZJAR" \
  -d $B/classes tools/p05-004/16k-probe/MainActivity.java
$BT/d8 --lib $ANDROID_JAR --min-api 21 --output $B/dexout \
  $B/classes/dev/p05/archive/poc/MainActivity.class \
  "$B/classes/dev/p05/archive/poc/MainActivity\$1.class" "$ZJAR"
$BT/aapt2 compile --dir <res-dir-with-raw> -o $B/compiled/res.zip
$BT/aapt2 link -o $B/out-unsigned.apk --manifest tools/p05-004/16k-probe/AndroidManifest.xml \
  -I $ANDROID_JAR $B/compiled/res.zip --min-sdk-version 21 --target-sdk-version 36
cp $B/dexout/classes.dex $B/stage-classes.dex
(cd <stage> && zip -0 $B/out-unsigned.apk classes.dex lib/x86_64/libzstd-jni-1.5.7-17.so)
$BT/zipalign -P 16 -f 4 $B/out-unsigned.apk $B/out-aligned.apk
$BT/apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --out $B/p05-004-zstd-16k-x86_64-vN.apk $B/out-aligned.apk
$BT/zipalign -c -P 16 -v 4 $B/p05-004-zstd-16k-x86_64-vN.apk
$BT/apksigner verify $B/p05-004-zstd-16k-x86_64-vN.apk
```

Critical packaging rule (learned in-campaign): the APK must contain BOTH
the `com.github.luben.zstd` Java classes (merged into `classes.dex` via
d8) AND the ABI `.so` stored uncompressed. Shipping the `.so` alone
yields `NoClassDefFoundError: Lcom/github/luben/zstd/Zstd` for every
decode test. Verify with `dexdump -d classes.dex | grep Zstd`.

## Run

```sh
adb -s <16k-avd> shell getconf PAGE_SIZE   # must print 16384
adb -s <16k-avd> push <apk> /data/local/tmp/p05.apk
adb -s <16k-avd> shell pm install /data/local/tmp/p05.apk
# NOTE: on a loaded host the client may report
# "cmd: Failure calling service package: Broken pipe (32)" while the
# server side still commits. Verify with:
adb -s <16k-avd> shell pm list packages | grep p05
adb -s <16k-avd> shell am start -n dev.p05.archive.poc/.MainActivity
adb -s <16k-avd> logcat -d | grep P05_RESULT
```

Expected (exact):

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

## Manifest reference

See `AndroidManifest.xml` in this directory (package
`dev.p05.archive.poc`, minSdk 21, targetSdk 36, single exported
`.MainActivity`, no permissions, no network).
