# zstd-jni 1.5.7-17 Provenance and Source Build — 2026-09-19

## Provenance trace (bounded forensic)

| Property | Finding |
|---|---|
| Coordinates | `com.github.luben:zstd-jni:1.5.7-17` (BSD-2-Clause per Central POM) |
| Central publication | `2026-09-16 20:18 UTC` all files; `maven-metadata.xml` `<latest>/<release>1.5.7-17</latest>` `<lastUpdated>20260916202315</lastUpdated>` |
| Registry | `https://central.sonatype.com/artifact/com.github.luben/zstd-jni`; files at `https://repo1.maven.org/maven2/com/github/luben/zstd-jni/1.5.7-17/` (jar 6,323,239 B = local `c9043e2d…`; AAR 947,256 B = local `9528895e…`) |
| GitHub Releases | **Unused by project**: `https://github.com/luben/zstd-jni/releases` shows "There aren't any releases here" |
| Tag `v1.5.7-17` | **Absent** (`git ls-remote` lists `v1.5.7-1..9,11..16`; `v1.5.7-10` also absent despite being published — precedent for publish-before-tag) |
| Newest tag | `v1.5.7-16` = `b74bc508f89c677310a7d863ccb77e0b98284662` (2026-08-29) |
| Best source inference | `master` = `b00e4d3a1a5f42fa3ac549754494b7a237080ac5` (2026-09-15, "Guard for negative offsets in `Zstd.decompressedSize`"), containing the `1.5.7-16 → 1.5.7-17` version bump (`14f9b9f`) — chronologically consistent (commit 09-15, artifact 09-16), GPG-verified, but **not cryptographically bound** to the artifact |
| JAR manifest | `Specification/Implementation-Version: 1.5.7-17`; `Bnd-LastModified: 1789589669241` = `2026-09-16T20:14:29Z` (~4 min pre-publish); **no** git SHA / build-host fields; all zip entries normalized to `2010-01-01` |
| Attestation | **None**: no SLSA/Sigstore; only legacy Maven `.asc` per-file signatures |
| Build scripts | Public: `build.sbt`, `build.gradle.kts`, `CMakeLists.txt`, `make_so*.sh`, `.github/workflows/ci.yml`, `version` file |
| Upstream 16 KiB intent | `CMakeLists.txt` sets `-Wl,-z,max-page-size=16384,-z,common-page-size=16384` explicitly |
| Android support | AAR published on Central; README: usable, supports Android 5.0+ |

Per the provenance rule, zstd-jni is **not** held to a stronger standard
than normal Maven dependencies. The gap (no tag/attestation) is the
project's normal operating mode, not an anomaly of `1.5.7-17`.

## Source build (bounded, isolated, outside git)

- Source: shallow clone `https://github.com/luben/zstd-jni.git` at
  `b00e4d3` (`version` file reads `1.5.7-17`), in disposable temp space.
- Toolchain: Apple clang 17.0.0, JDK 26.0.2.1, CMake 4.4.3, macOS x86_64.
- `cmake -DCMAKE_BUILD_TYPE=Release` configures cleanly
  (`Compiling version 1.5.7-17`, JNI found).
- Full compile of all C + ASM + JNI sources succeeds (100%).
- Link required removing the Linux-only `-fuse-ld=lld -Wl,-z,…` flags
  (macOS `ld` rejects them); with that host-only adjustment the shared
  library links: `libzstd-jni-1.5.7-17.dylib` (510,152 bytes).
  Consequence: upstream 16 KiB link intent was NOT compiled on host; the
  16 KiB decision evidence is the published artifact's ELF
  (`p_align=0x4000` throughout) plus the real `PAGE_SIZE=16384` run.
- Smoke test (published `1.5.7-17` JAR classes + source-built native lib):
  `Zstd.compress` → 22 bytes → `Zstd.decompress` roundtrip
  `hello-p05-004` = **PASS**. Lib identity verified via JVM library log:
  `Loaded library …/zstd-hostbuild/libzstd-jni-1.5.7-17.dylib` — the
  source-built lib, not the JAR-bundled one, was exercised. (Host smoke
  covers API linkage only; TAR.ZST/malformed shapes are covered by the
  device runs.)
- Native zstd in source: `ZSTD_VERSION 1.5.7`
  (`src/main/native/zstd.h`).
- No SHA equality with the published artifact is claimed or required
  (upstream never promises deterministic builds; different toolchain/host).

## Published x86_64 `.so` independent re-verification

Parsed ELF directly (no `readelf` on macOS): 9 program headers, all
`PT_LOAD` with `p_align=0x4000` and congruent `p_offset/p_vaddr`
(`0x0/0x0`, `0x5f0/0x5f0`, `0x11f0/0x11f0`).
SHA-256 `3cba491607edd9392911131000622454a42d534da40e022a0b4826943c223f27`,
size 547,360 — exactly the AAR x86_64 values in `04_RESULTS.md`.
Embedded strings: `libzstd-jni-1.5.7-17.so`, `1.5.7`.

## Version decision (Phase 6 escape hatch — NOT taken)

`1.5.7-17` **is** the latest stable as of 2026-09-18 (Central
`<latest>1.5.7-17</latest>`; no newer tag/release). No upgrade candidate
exists, so the tested artifact is retained. No revalidation delta.

## NDK decision (Phase 4)

No NDK was installed. Rationale: a full SBT+Scala+NDK rebuild of all four
Android ABIs is disproportionate on the current loaded host; the exact
published artifact already has static (`p_align=0x4000` all ABIs) and
real runtime (`PAGE_SIZE=16384`, 7/7 PASS) acceptance; the host source
build above demonstrates lineage cleanly. A production-toolchain rebuild
becomes a RELEASE_GATE (production packaging revalidation), not a
Technology Freeze blocker.

## Classification

`SOURCE_PROVENANCE_SUFFICIENT_WITH_NONREPRODUCIBLE_BUILD`:
artifact identity STRONG, source availability STRONG, source-to-version
mapping circumstantial-but-coherent (`b00e4d3`), build scripts public,
16 KiB intent explicit upstream, independent source build + roundtrip
PASS, no attestation (upstream never provides one).
Not a Technology Freeze blocker; final NOTICE/packaging revalidation is a
RELEASE_GATE.
