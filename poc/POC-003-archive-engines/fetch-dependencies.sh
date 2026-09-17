#!/bin/zsh
set -euo pipefail
# POC-ONLY — NOT PRODUCTION AUTHORITY
ROOT="${0:A:h}"
J="$ROOT/vendor/jars"
A="$ROOT/vendor/aar"
mkdir -p "$J" "$A"
fetch() { local url="$1" out="$2"; curl -fL --retry 3 "$url" -o "$out"; }
M=https://repo1.maven.org/maven2
fetch "$M/org/apache/commons/commons-compress/1.28.0/commons-compress-1.28.0.jar" "$J/commons-compress-1.28.0.jar"
fetch "$M/org/apache/commons/commons-io/2.20.0/commons-io-2.20.0.jar" "$J/commons-io-2.20.0.jar"
fetch "$M/commons-codec/commons-codec/1.19.0/commons-codec-1.19.0.jar" "$J/commons-codec-1.19.0.jar"
fetch "$M/org/apache/commons/commons-lang3/3.18.0/commons-lang3-3.18.0.jar" "$J/commons-lang3-3.18.0.jar"
fetch "$M/org/tukaani/xz/1.10/xz-1.10.jar" "$J/xz-1.10.jar"
fetch "$M/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar" "$J/slf4j-api-2.0.17.jar"
fetch "$M/net/lingala/zip4j/zip4j/2.11.6/zip4j-2.11.6.jar" "$J/zip4j-2.11.6.jar"
fetch "$M/com/github/junrar/junrar/8.1.1/junrar-8.1.1.jar" "$J/junrar-8.1.1.jar"
fetch "$M/com/github/luben/zstd-jni/1.5.7-16/zstd-jni-1.5.7-16.jar" "$J/zstd-jni-1.5.7-16.jar"
fetch "$M/com/github/luben/zstd-jni/1.5.7-16/zstd-jni-1.5.7-16.aar" "$A/zstd-jni-1.5.7-16.aar"
rm -rf "$A/unpacked"
mkdir -p "$A/unpacked"
(cd "$A/unpacked" && unzip -q ../zstd-jni-1.5.7-16.aar)
printf 'Fetched PoC dependencies only. These versions are NOT PRODUCTION AUTHORITY.\n'