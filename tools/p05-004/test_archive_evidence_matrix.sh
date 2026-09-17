#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
HARNESS="$ROOT/tools/p05-004/ArchiveEvidenceMatrixHarness.java"
BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/p05-004-harness.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM

javac -d "$BUILD_DIR" "$HARNESS"

valid_output=$(java -cp "$BUILD_DIR" ArchiveEvidenceMatrixHarness \
  "$ROOT/tools/p05-004/fixtures/valid.tsv")
printf '%s\n' "$valid_output"
printf '%s\n' "$valid_output" | grep -F 'OVERALL=NOT_CLOSED'
printf '%s\n' "$valid_output" | grep -F 'zstd-jni Android AAR=CONDITIONAL'
printf '%s\n' "$valid_output" | grep -F 'libarchive 3.8.9=UNRESOLVED'

if java -cp "$BUILD_DIR" ArchiveEvidenceMatrixHarness \
  "$ROOT/tools/p05-004/fixtures/duplicate.tsv" >"$BUILD_DIR/duplicate.out" 2>&1; then
  echo 'duplicate manifest unexpectedly accepted' >&2
  exit 1
fi
grep -F 'duplicate candidate/gate' "$BUILD_DIR/duplicate.out"

awk -F '\t' '$1 != "zstd-jni Android AAR" || $2 != "native-abi"' \
  "$ROOT/tools/p05-004/fixtures/valid.tsv" > "$BUILD_DIR/missing-native.tsv"
if java -cp "$BUILD_DIR" ArchiveEvidenceMatrixHarness \
  "$BUILD_DIR/missing-native.tsv" >"$BUILD_DIR/missing-native.out" 2>&1; then
  echo 'manifest missing a native gate unexpectedly accepted' >&2
  exit 1
fi
grep -F 'native-abi' "$BUILD_DIR/missing-native.out"

awk -F '\t' '$1 != "Commons Compress 1.28.0" || $2 != "security-traversal"' \
  "$ROOT/tools/p05-004/fixtures/valid.tsv" > "$BUILD_DIR/missing-security.tsv"
if java -cp "$BUILD_DIR" ArchiveEvidenceMatrixHarness \
  "$BUILD_DIR/missing-security.tsv" >"$BUILD_DIR/missing-security.out" 2>&1; then
  echo 'manifest missing a security negative-case gate unexpectedly accepted' >&2
  exit 1
fi
grep -F 'security-traversal' "$BUILD_DIR/missing-security.out"

echo 'PASS: archive evidence matrix harness'
