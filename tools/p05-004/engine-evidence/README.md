# P05-004 actual-engine evidence harness

This directory is disposable host/JVM evidence tooling only. It is not a
production module, dependency declaration, Android app, or security
certification. Large archives and downloaded artifacts remain outside Git.

The harness expects the exact candidate artifacts recorded in
`docs/poc/P05-004/01_ENVIRONMENT.md` in one directory. With `curl`, `unzip`,
`javac`, and Java available:

```sh
ART=$(mktemp -d /tmp/p05-004-artifacts.XXXXXX)
BASE=https://repo.maven.apache.org/maven2
curl -fsSL "$BASE/org/apache/commons/commons-compress/1.28.0/commons-compress-1.28.0.jar" -o "$ART/commons-compress-1.28.0.jar"
curl -fsSL "$BASE/commons-io/commons-io/2.20.0/commons-io-2.20.0.jar" -o "$ART/commons-io-2.20.0.jar"
curl -fsSL "$BASE/commons-codec/commons-codec/1.19.0/commons-codec-1.19.0.jar" -o "$ART/commons-codec-1.19.0.jar"
curl -fsSL "$BASE/org/apache/commons/commons-lang3/3.18.0/commons-lang3-3.18.0.jar" -o "$ART/commons-lang3-3.18.0.jar"
curl -fsSL "$BASE/net/lingala/zip4j/zip4j/2.11.6/zip4j-2.11.6.jar" -o "$ART/zip4j-2.11.6.jar"
curl -fsSL "$BASE/com/github/junrar/junrar/8.1.1/junrar-8.1.1.jar" -o "$ART/junrar-8.1.1.jar"
curl -fsSL "$BASE/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar" -o "$ART/slf4j-api-2.0.17.jar"
curl -fsSL "$BASE/com/github/luben/zstd-jni/1.5.7-17/zstd-jni-1.5.7-17.jar" -o "$ART/zstd-jni-1.5.7-17.jar"
BUILD=$(mktemp -d /tmp/p05-004-engine-build.XXXXXX)
javac -cp "$ART/*" -d "$BUILD" tools/p05-004/engine-evidence/*.java
java -Xmx768m -cp "$BUILD:$ART/*" ArchiveRealEngineHarness "$BUILD/fixtures"
java -Xmx512m -cp "$BUILD:$ART/*" Zip64ScaleHarness 100000 "$BUILD/scale-100k.zip"
```

Junrar corpus resources are obtained from the tagged `v8.1.1` source archive;
they are not committed:

```sh
JUNRAR_SRC=$(mktemp -d /tmp/p05-004-junrar-source.XXXXXX)
curl -fsSL https://github.com/junrar/junrar/archive/refs/tags/v8.1.1.zip -o "$JUNRAR_SRC/junrar.zip"
unzip -q "$JUNRAR_SRC/junrar.zip" -d "$JUNRAR_SRC/unpacked"
java -Xmx512m -cp "$BUILD:$ART/*" JunrarCorpusHarness \
  "$JUNRAR_SRC/unpacked/junrar-8.1.1/src/test/resources/com/github/junrar/rar4.rar" \
  "$JUNRAR_SRC/unpacked/junrar-8.1.1/src/test/resources/com/github/junrar/rar5.rar" \
  "$JUNRAR_SRC/unpacked/junrar-8.1.1/src/test/resources/com/github/junrar/parent-dir.rar" \
  "$JUNRAR_SRC/unpacked/junrar-8.1.1/src/test/resources/com/github/junrar/mkdir-escape.rar" \
  "$JUNRAR_SRC/unpacked/junrar-8.1.1/src/test/resources/com/github/junrar/password/rar4-password-junrar.rar" \
  "$JUNRAR_SRC/unpacked/junrar-8.1.1/src/test/resources/com/github/junrar/password/rar5-password-junrar.rar"
```

The direct-file/stream and corrupt-header probe uses the same tagged fixtures;
the corrupt-header input is the tagged abnormal fixture:

```sh
RAR_REAL=$(mktemp -d /tmp/p05-004-junrar-real.XXXXXX)
cp "$JUNRAR_SRC/unpacked/junrar-8.1.1/src/test/resources/com/github/junrar/rar4.rar" "$RAR_REAL/rar4.rar"
cp "$JUNRAR_SRC/unpacked/junrar-8.1.1/src/test/resources/com/github/junrar/rar5.rar" "$RAR_REAL/rar5.rar"
cp "$JUNRAR_SRC/unpacked/junrar-8.1.1/src/test/resources/com/github/junrar/abnormal/corrupt-header.rar" "$RAR_REAL/corrupt-header.rar"
java -Xmx512m -cp "$BUILD:$ART/*" JunrarRealHarness "$RAR_REAL"
```

```sh
java -Xmx512m -cp "$BUILD:$ART/*" SpoolContainmentHarness
java -Xmx512m -cp "$BUILD:$ART/*" RealEngineSpoolHarness
```

`SpoolContainmentHarness` is stdlib-only (policy layer: spool, containment,
cleanup, cancellation, error-map; 18/18 PASS). `RealEngineSpoolHarness`
needs the exact candidate JARs on the classpath plus `xz-1.10.jar` for the
XZ route (Commons declares XZ `1.10` optional; resolved from Maven Central
into disposable space, SHA-256
`95c63c1a55b22dd6453890a419cc1a640f790bbf7d8ae82db1e30aefefb08888`):

```sh
curl -fsSL "$BASE/org/tukaani/xz/1.10/xz-1.10.jar" -o "$ART/xz-1.10.jar"
javac -cp "$ART/*" -d "$BUILD" tools/p05-004/engine-evidence/SpoolContainmentHarness.java tools/p05-004/engine-evidence/RealEngineSpoolHarness.java
java -Xmx512m -cp "$BUILD:$ART/*" SpoolContainmentHarness
java -Xmx512m -cp "$BUILD:$ART/*" RealEngineSpoolHarness
```

The expected hashes, POM/transitive inventory, and interpretation of partial
or untested cases are in the P05-004 evidence documents. The harness does not
claim Android SAF behavior, final APK packaging, 16 KiB runtime behavior, or
universal parser security.
