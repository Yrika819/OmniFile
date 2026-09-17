#!/bin/zsh
set -euo pipefail
# POC-ONLY — NOT PRODUCTION AUTHORITY
ROOT="${0:A:h}"
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/36.0.0"
ANDROID_JAR="$SDK/platforms/android-36/android.jar"
ADB="$SDK/platform-tools/adb"
SERIAL="${ANDROID_SERIAL:?Set ANDROID_SERIAL to the test device serial}"
OUT="$ROOT/out/android"
AAR="$ROOT/vendor/aar/unpacked"
RESULTS="$ROOT/results"
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$RESULTS"
CP=$(printf '%s:' "$ROOT"/vendor/jars/*.jar)
CP=${CP%:}
javac --release 17 -cp "$CP:$AAR/classes.jar" -d "$OUT/classes" "$ROOT/src/ArchivePoc.java"
jar --create --file "$OUT/harness.jar" -C "$OUT/classes" .
D8_INPUTS=("$OUT/harness.jar" "$AAR/classes.jar")
for j in "$ROOT"/vendor/jars/*.jar; do
  [[ "${j:t}" == zstd-jni-1.5.7-16.jar ]] && continue
  D8_INPUTS+=("$j")
done
"$BT/d8" --min-api 31 --lib "$ANDROID_JAR" --output "$OUT/dex" "${D8_INPUTS[@]}"
BASE=/data/local/tmp/filemanager-poc003
"$ADB" -s "$SERIAL" shell "rm -rf $BASE; mkdir -p $BASE/fixtures $BASE/work"
"$ADB" -s "$SERIAL" push "$OUT/dex/classes.dex" "$BASE/classes.dex" >/dev/null
"$ADB" -s "$SERIAL" push "$ROOT/fixtures/generated/." "$BASE/fixtures/" >/dev/null
"$ADB" -s "$SERIAL" push "$AAR/jni/arm64-v8a/libzstd-jni-1.5.7-16.so" "$BASE/libzstd-jni-1.5.7-16.so" >/dev/null
"$ADB" -s "$SERIAL" shell "CLASSPATH=$BASE/classes.dex LD_LIBRARY_PATH=$BASE app_process /system/bin ArchivePoc $BASE/fixtures $BASE/work $BASE/results.jsonl" > "$RESULTS/android-api36-stdout.txt" 2> "$RESULTS/android-api36-stderr.txt"
"$ADB" -s "$SERIAL" pull "$BASE/results.jsonl" "$RESULTS/android-api36-results.jsonl" >/dev/null
printf 'Android API36 archive PoC complete for %s.\n' "$SERIAL"