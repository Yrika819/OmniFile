#!/bin/zsh
set -euo pipefail
# POC-ONLY — NOT PRODUCTION AUTHORITY
ROOT="${0:A:h}"
OUT="$ROOT/out/host"
RESULTS="$ROOT/results"
rm -rf "$OUT" "$ROOT/work"
mkdir -p "$OUT/classes" "$RESULTS"
CP=$(printf '%s:' "$ROOT"/vendor/jars/*.jar)
CP=${CP%:}
javac --release 17 -Xlint:deprecation -cp "$CP" -d "$OUT/classes" "$ROOT/src/ArchivePoc.java"
java -cp "$OUT/classes:$CP" ArchivePoc "$ROOT/fixtures/generated" "$ROOT/work" "$RESULTS/host-results.jsonl" | tee "$RESULTS/host-stdout.txt"
printf 'Host archive PoC complete.\n'