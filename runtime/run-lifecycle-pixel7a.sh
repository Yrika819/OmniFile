#!/bin/zsh
set -euo pipefail
# POC-ONLY — NOT PRODUCTION AUTHORITY; parent agent serializes ADB use.
ROOT="${0:A:h}"
ADB="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG=dev.poc.filemanager.p05executor
OUT="$ROOT/results"
: "${ADB_SERIAL:?set ADB_SERIAL to the exact intended device serial}"
adb() { "$ADB" -s "$ADB_SERIAL" "$@"; }
if ! "$ADB" devices -l | awk -v serial="$ADB_SERIAL" '$1 == serial { found=1 } END { exit found ? 0 : 1 }'; then
  echo "selected ADB serial is not connected" >&2
  exit 1
fi
model="$(adb shell getprop ro.product.model | tr -d '\r')"
sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "$model" != "Pixel 7a" || "$sdk" != "36" ]]; then
  echo "lifecycle runner requires Pixel 7a/API36; got model=$model sdk=$sdk" >&2
  exit 1
fi

adb shell am force-stop "$PKG" || true
adb shell pm clear "$PKG" >/dev/null
adb shell am start -n "$PKG/.MainActivity" --es mode foreground >/dev/null
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 1
adb shell dumpsys power | awk '/Display Power: state=|mWakefulness=|mInteractive=/' > "$OUT/lifecycle-after-home.txt"
adb shell input keyevent KEYCODE_SLEEP
sleep 1
adb shell dumpsys power | awk '/Display Power: state=|mWakefulness=|mInteractive=/' > "$OUT/lifecycle-screen-off.txt"
adb shell input keyevent KEYCODE_WAKEUP
sleep 1
adb shell dumpsys power | awk '/Display Power: state=|mWakefulness=|mInteractive=/' > "$OUT/lifecycle-screen-on.txt"
adb shell input keyevent KEYCODE_APP_SWITCH
sleep 1
adb shell input swipe 540 1200 540 180 600
sleep 1
adb shell am start -n "$PKG/.MainActivity" --es mode resume >/dev/null
for i in {1..20}; do
  sleep 1
  state="$(adb shell cat "/sdcard/Android/data/$PKG/files/runtime-state.properties" 2>/dev/null || true)"
  if [[ "$state" == *"phase=COMPLETE"* ]]; then break; fi
done
adb shell cat "/sdcard/Android/data/$PKG/files/runtime-report.jsonl" > "$OUT/lifecycle-report-pixel7a.jsonl"
adb shell cat "/sdcard/Android/data/$PKG/files/runtime-state.properties" > "$OUT/lifecycle-state-pixel7a.properties"
adb shell am force-stop "$PKG"
