# POC-003 — Failures and Edge Cases

Status: COMPLETE

## Measured failures that are expected capability limits

- Commons Compress 1.28.0 cannot read the AES-256 or traditional encrypted ZIP fixtures through its ZIP reader. `canReadEntryData=false` and entry read throws `UnsupportedZipFeatureException`.
- Junrar exposes read/extract behavior only in this PoC. RAR creation is `NO` and must not be claimed.
- libarchive was not built or executed on Android; all libarchive Android conclusions remain `NOT_TESTED`.

## Build/runtime anomalies resolved during the PoC

- The first Android DEX attempt used Java 26 class-file output and aborted under `app_process`. Recompiling the harness with `javac --release 17` and rebuilding with D8 resolved the issue.
- The first pure-Java Android bundle did not include zstd-jni classes/native code, so TAR.ZST failed with `NoClassDefFoundError`. The official zstd-jni 1.5.7-16 Android AAR contains `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64` native libraries. Supplying the arm64-v8a binary to the Pixel 7a made TAR.ZST pass.
- The host 7-Zip 26.03 macOS tool did not implement direct `-tzstd` creation in the attempted invocation. The TAR.ZST fixture was therefore produced with the local Zstandard 1.5.7 CLI instead.

## Security edge cases

- Path traversal is not considered solved merely because a specific library rejects one fixture. A normalized destination-root containment check remains mandatory.
- Symlink and hard-link entries require an explicit policy before materialization. A successful parser/listing result is not extraction authorization.
- Expansion limits must be enforced while streaming, not only by trusting declared uncompressed size.
- Entry count and nested-archive depth limits were represented in fixtures but a final production threshold was intentionally not frozen.
- Malformed archives can report failure differently: exceptions, broken-header collections, or unsupported-feature exceptions. The production abstraction must normalize these without discarding engine-specific diagnostics.

## NOT TESTED

- 16 KiB-page-size Android runtime.
- native libarchive Android build and crash behavior.
- cancellation of long-running solid archives under Android process death.
- 100k-entry 7z and 100k-entry RAR.
- adversarial fuzz corpus beyond the small synthetic/malformed set.
- RAR archive creation.