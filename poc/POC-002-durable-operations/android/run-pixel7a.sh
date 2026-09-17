#!/bin/zsh
set -euo pipefail
# POC-ONLY — NOT PRODUCTION AUTHORITY
ROOT="${0:A:h}"
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/36.0.0"
ANDROID_JAR="$SDK/platforms/android-36/android.jar"
ADB="$SDK/platform-tools/adb"
OUT="$ROOT/out"
RESULTS="$ROOT/../results/android-pixel7a"
DEVICE_BASE="/data/local/tmp/filemanager-poc002"
mkdir -p "$OUT/classes" "$OUT/dex" "$RESULTS"
rm -rf "$OUT/classes" "$OUT/dex"
mkdir -p "$OUT/classes" "$OUT/dex"
javac --release 17 -cp "$ANDROID_JAR" -d "$OUT/classes" "$ROOT/DurableAndroidPoc.java"
jar --create --file "$OUT/classes.jar" -C "$OUT/classes" .
"$BT/d8" --min-api 31 --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/classes.jar"
"$ADB" push "$OUT/dex/classes.dex" "$DEVICE_BASE/classes.dex" >/dev/null
"$ADB" shell getprop > "$RESULTS/device-getprop.txt"
"$ADB" shell 'uname -a; getconf PAGE_SIZE 2>/dev/null || true; df -h /data /storage/emulated/0 2>/dev/null || true' > "$RESULTS/device-env.txt"

app() {
  local scenario="$1"; shift
  "$ADB" shell "CLASSPATH=$DEVICE_BASE/classes.dex app_process / DurableAndroidPoc $DEVICE_BASE/$scenario $*"
}
capture() {
  local s="$1"
  "$ADB" shell "cat $DEVICE_BASE/$s/events.jsonl" > "$RESULTS/$s-events.jsonl" || true
  "$ADB" shell "cat $DEVICE_BASE/$s/state.properties" > "$RESULTS/$s-state.properties" || true
}
cleanup() { "$ADB" shell "rm -rf $DEVICE_BASE/$1" >/dev/null || true; }
complete_case() {
  local s="$1" bytes="$2" fault="$3" type="${4:-COPY}"
  cleanup "$s"
  app "$s" setup "$bytes" "$type" > "$RESULTS/$s-setup.txt"
  app "$s" run "$fault" > "$RESULTS/$s-first-run.txt" 2>&1 || true
  if [[ "$fault" != "none" ]]; then app "$s" run none > "$RESULTS/$s-resume.txt" 2>&1 || true; fi
  capture "$s"
  grep -q '^phase=COMPLETE' "$RESULTS/$s-state.properties"
  cleanup "$s"
}

complete_case small 8388608 none COPY
complete_case kill_early 268435456 kill_early COPY
complete_case kill_mid 268435456 kill_mid COPY
complete_case kill_near 268435456 kill_near COPY
complete_case kill_after_transfer 134217728 kill_after_transfer COPY
complete_case kill_after_verify 134217728 kill_after_verify COPY
complete_case cancel_mid 134217728 cancel_mid COPY
complete_case enospc_mid 134217728 enospc_mid COPY
complete_case move 67108864 none MOVE

# Destination conflict: expected BLOCKED.
s=conflict; cleanup "$s"; app "$s" setup 67108864 COPY > "$RESULTS/$s-setup.txt"; app "$s" run conflict_final > "$RESULTS/$s-run.txt" 2>&1 || true; capture "$s"; grep -q '^phase=BLOCKED' "$RESULTS/$s-state.properties"; grep -q '^lastError=DESTINATION_CONFLICT' "$RESULTS/$s-state.properties"; cleanup "$s"

# Source mutation after mid-transfer process death: expected BLOCKED on resume.
s=source_mutation; cleanup "$s"; app "$s" setup 67108864 COPY > "$RESULTS/$s-setup.txt"; app "$s" run kill_mid > "$RESULTS/$s-first-run.txt" 2>&1 || true; "$ADB" shell "printf Z | dd of=$DEVICE_BASE/$s/source.bin bs=1 seek=0 conv=notrunc >/dev/null 2>&1"; app "$s" run none > "$RESULTS/$s-resume.txt" 2>&1 || true; capture "$s"; grep -q '^phase=BLOCKED' "$RESULTS/$s-state.properties"; grep -q '^lastError=SOURCE_MUTATED' "$RESULTS/$s-state.properties"; cleanup "$s"

# Multi-GB real transfer on physical Android 16. Source begins sparse but destination is streamed byte-for-byte.
complete_case large_2g 2147483648 none COPY

# Verify MOVE ordering mechanically from retained events before cleanup above.
grep -q '"event":"COMPLETE".*"sourceExistsAtComplete":true' "$RESULTS/move-events.jsonl"
grep -q '"event":"MOVE_SOURCE_DELETE".*"afterPhase":"COMPLETE".*"deleted":true' "$RESULTS/move-events.jsonl"

python3 - "$RESULTS" <<'PY'
import json, pathlib, sys
r=pathlib.Path(sys.argv[1])
summary={}
for p in sorted(r.glob('*-events.jsonl')):
    events=[json.loads(x) for x in p.read_text().splitlines() if x.strip()]
    summary[p.stem.replace('-events','')]={
        'events':[e['event'] for e in events],
        'reconcile':[e for e in events if e['event']=='RECOVER_RECONCILE'],
        'complete':[e for e in events if e['event']=='COMPLETE'],
        'transfer':[e for e in events if e['event']=='TRANSFER_DONE'],
        'verify':[e for e in events if e['event']=='VERIFY_DONE'],
        'blocked':[e for e in events if e['event']=='BLOCKED'],
    }
(r/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
print('scenarios', len(summary))
PY

echo "POC002_ANDROID_PIXEL7A_PASS"
