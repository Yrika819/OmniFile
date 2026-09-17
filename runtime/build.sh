#!/bin/zsh
set -euo pipefail
# POC-ONLY — NOT PRODUCTION AUTHORITY
ROOT="${0:A:h}"
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/36.0.0"
ANDROID_JAR="$SDK/platforms/android-36/android.jar"
OUT="$ROOT/out"
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"
javac --release 17 -cp "$ANDROID_JAR" -d "$OUT/classes" "$ROOT"/*.java
jar --create --file "$OUT/classes.jar" -C "$OUT/classes" .
"$BT/d8" --min-api 31 --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/classes.jar"
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$ANDROID_JAR" --manifest "$ROOT/AndroidManifest.xml" --min-sdk-version 31 --target-sdk-version 36
(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$OUT/p05-executor.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --verbose "$OUT/p05-executor.apk"
shasum -a 256 "$OUT/p05-executor.apk"
