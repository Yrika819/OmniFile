#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
HARNESS="$ROOT/tools/p05-004/ArchiveEvidenceMatrixHarness.java"
BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/p05-004-harness.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM

javac -d "$BUILD_DIR" "$HARNESS"

for source in \
  ArchiveSecurityFixtureHarness.java \
  ArchiveScaleHarness.java \
  ArchiveOptionalCapabilityHarness.java \
  JunrarLocalInspectionHarness.java \
  NativePackagingEvidenceHarness.java; do
  javac -cp "$BUILD_DIR" -d "$BUILD_DIR" "$ROOT/tools/p05-004/$source"
done

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

security_output=$(java -cp "$BUILD_DIR" ArchiveSecurityFixtureHarness \
  "$ROOT/tools/p05-004/fixtures/security_cases.tsv")
printf '%s\n' "$security_output"
printf '%s\n' "$security_output" | grep -F 'SECURITY_FIXTURES=PASS'

awk -F '\t' 'BEGIN { OFS="\t" } NR == 2 { $4 = "REJECT" } { print }' \
  "$ROOT/tools/p05-004/fixtures/security_cases.tsv" > "$BUILD_DIR/security-bad.tsv"
if java -cp "$BUILD_DIR" ArchiveSecurityFixtureHarness \
  "$BUILD_DIR/security-bad.tsv" >"$BUILD_DIR/security-bad.out" 2>&1; then
  echo 'malformed security fixture unexpectedly accepted' >&2
  exit 1
fi
grep -F 'expected REJECT but observed ALLOW' "$BUILD_DIR/security-bad.out"

scale_output=$(java -cp "$BUILD_DIR" ArchiveScaleHarness 10000 100000)
printf '%s\n' "$scale_output"
printf '%s\n' "$scale_output" | grep -F 'SCALE_10000=PASS'
printf '%s\n' "$scale_output" | grep -F 'SCALE_100000=PASS'
if java -cp "$BUILD_DIR" ArchiveScaleHarness 100001 >"$BUILD_DIR/scale-bad.out" 2>&1; then
  echo 'over-bound scale unexpectedly accepted' >&2
  exit 1
fi
grep -F 'outside bounded range: 100001' "$BUILD_DIR/scale-bad.out"

capability_output=$(java -cp "$BUILD_DIR" ArchiveOptionalCapabilityHarness \
  "$ROOT/tools/p05-004/fixtures/optional_capabilities.tsv")
printf '%s\n' "$capability_output"
printf '%s\n' "$capability_output" | grep -F 'OPTIONAL_CAPABILITIES=PASS'

junrar_output=$(java -cp "$BUILD_DIR" JunrarLocalInspectionHarness "$ROOT")
printf '%s\n' "$junrar_output"
printf '%s\n' "$junrar_output" | grep -F 'JUNRAR_CLASSIFICATION=LICENSE_REVIEW_REQUIRED'

native_output=$(java -cp "$BUILD_DIR" NativePackagingEvidenceHarness \
  "$ROOT" "$ROOT/tools/p05-004/fixtures/native_packaging.tsv")
printf '%s\n' "$native_output"
printf '%s\n' "$native_output" | grep -F 'NATIVE_PACKAGING=NO_LOCAL_ARTIFACT'
printf '%s\n' "$native_output" | grep -F 'NATIVE_16K=UNRESOLVED_4K_ONLY'

echo 'PASS: archive evidence matrix harness'
