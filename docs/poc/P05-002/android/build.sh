#!/bin/sh
set -eu

# POC-ONLY — NOT PRODUCTION AUTHORITY.
# Raw SDK build; this script intentionally has no installer or device command.

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SDK=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/Users/yuta/Library/Android/sdk}}
PLATFORM=${ANDROID_PLATFORM:-android-36}
TOOLS=${ANDROID_BUILD_TOOLS:-35.0.0}
OUT="$ROOT/build"
JAR="$SDK/platforms/$PLATFORM/android.jar"
AAPT2="$SDK/build-tools/$TOOLS/aapt2"
D8="$SDK/build-tools/$TOOLS/d8"
APKSIGNER="$SDK/build-tools/$TOOLS/apksigner"

for required in "$JAR" "$AAPT2" "$D8" "$APKSIGNER"; do
    if [ ! -f "$required" ] && [ ! -x "$required" ]; then
        echo "missing Android SDK tool: $required" >&2
        exit 2
    fi
done

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -classpath "$JAR" \
    -d "$OUT/classes" \
    "$ROOT/src/dev/poc/safoperations/MainActivity.java"

"$D8" --lib "$JAR" --min-api 31 --output "$OUT/dex" \
    "$OUT/classes"/dev/poc/safoperations/*.class

"$AAPT2" link \
    -I "$JAR" \
    --manifest "$ROOT/AndroidManifest.xml" \
    --min-sdk-version 31 \
    --target-sdk-version 36 \
    --version-code 1 \
    --version-name 0.1-poc \
    -o "$OUT/p05-saf-operations-unsigned.apk"

cp "$OUT/p05-saf-operations-unsigned.apk" "$OUT/p05-saf-operations-unsigned-with-dex.apk"
zip -q -j "$OUT/p05-saf-operations-unsigned-with-dex.apk" "$OUT/dex/classes.dex"

KEYSTORE="$OUT/poc-debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -storepass android \
        -keypass android \
        -alias poc-debug \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=P05 SAF PoC, O=Disposable" >/dev/null 2>&1
fi

"$APKSIGNER" -J-enable-native-access=ALL-UNNAMED sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias poc-debug \
    --out "$OUT/p05-saf-operations.apk" \
    "$OUT/p05-saf-operations-unsigned-with-dex.apk"
"$APKSIGNER" -J-enable-native-access=ALL-UNNAMED verify --verbose "$OUT/p05-saf-operations.apk"
echo "APK: $OUT/p05-saf-operations.apk"
