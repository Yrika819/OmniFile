#!/bin/zsh
set -euo pipefail
# POC-ONLY — NOT PRODUCTION AUTHORITY; parent agent serializes ADB use.
ROOT="${0:A:h}"
ADB="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG=dev.poc.filemanager.p05executor
OUT="$ROOT/results"
mkdir -p "$OUT"
: "${ADB_SERIAL:?set ADB_SERIAL to the exact intended device serial}"
adb() { "$ADB" -s "$ADB_SERIAL" "$@"; }
if ! "$ADB" devices -l | awk -v serial="$ADB_SERIAL" '$1 == serial { found=1 } END { exit found ? 0 : 1 }'; then
  echo "selected ADB serial is not connected" >&2
  exit 1
fi
serial_hash="$(printf '%s' "$ADB_SERIAL" | shasum -a 256 | awk '{print $1}')"
apk_hash="$(shasum -a 256 "$ROOT/out/p05-executor.apk" | awk '{print $1}')"
printf '{"serialHash":"%s","apkSha256":"%s","startedAt":"%s"}\n' "$serial_hash" "$apk_hash" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$OUT/campaign-metadata.json"
adb shell getprop | awk -F']:[[:space:]]*' '/ro.build.version.sdk|ro.build.id|ro.product.model|ro.product.cpu.abi|ro.build.version.security_patch/ {print}' > "$OUT/device-identity.txt"
adb shell 'getconf PAGE_SIZE; dumpsys package dev.poc.filemanager.p05executor | sed -n "1,80p"' > "$OUT/device-package.txt" || true
adb install -r "$ROOT/out/p05-executor.apk" > "$OUT/install.txt"
adb shell pm clear "$PKG" > "$OUT/clear.txt"
run_mode() {
  mode="$1"
  adb shell am force-stop "$PKG" || true
  adb shell pm clear "$PKG" >/dev/null
  adb shell am start -n "$PKG/.MainActivity" --es mode "$mode" > "$OUT/start-$mode.txt"
  for i in {1..20}; do
    sleep 1
    state="$(adb shell cat "/sdcard/Android/data/$PKG/files/runtime-state.properties" 2>/dev/null || true)"
    if [[ "$state" == *"phase=COMPLETE"* ]]; then break; fi
  done
  adb shell cat "/sdcard/Android/data/$PKG/files/runtime-report.jsonl" > "$OUT/runtime-report-$mode.jsonl" || true
  adb shell cat "/sdcard/Android/data/$PKG/files/runtime-state.properties" > "$OUT/runtime-state-$mode.properties" || true
}
run_mode foreground
run_mode fgs
run_mode job
if [ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 34 ]; then run_mode uidt; else echo 'UIDT=NOT_APPLICABLE_API_LT_34' > "$OUT/runtime-report-uidt.jsonl"; fi
adb shell am force-stop "$PKG" || true
adb shell pm clear "$PKG" >/dev/null
adb shell am start -n "$PKG/.MainActivity" --es mode foreground > "$OUT/start-kill.txt"
for i in {1..20}; do
  sleep 0.2
  report="$(adb shell cat "/sdcard/Android/data/$PKG/files/runtime-report.jsonl" 2>/dev/null || true)"
  if [[ "$report" == *'"event":"CHECKPOINT"'* && "$report" != *'"event":"COMPLETE"'* ]]; then break; fi
done
pid="$(adb shell pidof "$PKG" | tr -d '\r')"
if [ -n "$pid" ]; then
  if ! adb shell kill -9 "$pid"; then
    echo 'DIRECT_SIGNAL=BLOCKED_BY_DEVICE_SELINUX; FALLBACK=AM_FORCE_STOP' > "$OUT/process-kill.txt"
    adb shell am force-stop "$PKG"
  else
    echo 'DIRECT_SIGNAL=PASS' > "$OUT/process-kill.txt"
  fi
fi
adb shell am start -n "$PKG/.MainActivity" --es mode resume > "$OUT/start-resume.txt"
for i in {1..20}; do
  sleep 1
  state="$(adb shell cat "/sdcard/Android/data/$PKG/files/runtime-state.properties" 2>/dev/null || true)"
  if [[ "$state" == *"phase=COMPLETE"* ]]; then break; fi
done
adb shell cat "/sdcard/Android/data/$PKG/files/runtime-report.jsonl" > "$OUT/runtime-report.jsonl"
adb shell cat "/sdcard/Android/data/$PKG/files/runtime-state.properties" > "$OUT/runtime-state.properties"
cat "$OUT"/runtime-report-foreground.jsonl "$OUT"/runtime-report-fgs.jsonl \
  "$OUT"/runtime-report-job.jsonl "$OUT"/runtime-report-uidt.jsonl \
  "$OUT"/runtime-report.jsonl > "$OUT/runtime-report-all.jsonl"
echo 'WORKMANAGER=NOT_APPLICABLE — no AndroidX WorkManager dependency is present in this disposable framework-only harness; JobScheduler evidence is not substituted.' > "$OUT/workmanager.txt"
