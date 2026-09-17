#!/bin/zsh
set -euo pipefail
# POC-ONLY — NOT PRODUCTION AUTHORITY
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
PKG="dev.poc.filemanager.storagecap"
APK="${0:A:h}/out/poc001.apk"
OUTDIR="${0:A:h}/results"
mkdir -p "$OUTDIR"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"

"$ADB" -s "$SERIAL" install -r "$APK"
"$ADB" -s "$SERIAL" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow || true
"$ADB" -s "$SERIAL" shell am force-stop "$PKG" || true
"$ADB" -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" --es mode clear >/dev/null
sleep 1
"$ADB" -s "$SERIAL" shell am force-stop "$PKG" || true
"$ADB" -s "$SERIAL" shell am start -W -n "$PKG/.MainActivity" --es mode all_no_picker >/dev/null
for i in {1..120}; do
  if "$ADB" -s "$SERIAL" shell run-as "$PKG" cat files/results.jsonl 2>/dev/null | grep -q '"operation":"RUN_DONE".*"mode":"all_no_picker"'; then break; fi
  sleep 1
done
API=$("$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')
REL=$("$ADB" -s "$SERIAL" shell getprop ro.build.version.release | tr -d '\r')
"$ADB" -s "$SERIAL" shell run-as "$PKG" cat files/results.jsonl > "$OUTDIR/api${API}-android${REL}-direct-pipe.jsonl"
"$ADB" -s "$SERIAL" shell getprop > "$OUTDIR/api${API}-getprop.txt"
"$ADB" -s "$SERIAL" shell dumpsys package "$PKG" > "$OUTDIR/api${API}-package.txt"
"$ADB" -s "$SERIAL" shell dumpsys meminfo "$PKG" > "$OUTDIR/api${API}-meminfo.txt" || true
printf 'Captured %s API %s -> %s\n' "$REL" "$API" "$OUTDIR"
