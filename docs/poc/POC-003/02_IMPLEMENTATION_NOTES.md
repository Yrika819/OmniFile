# POC-003 — Implementation Notes

Status: COMPLETE

## Harness

`poc/POC-003-archive-engines/src/ArchivePoc.java` is a disposable Java harness. `generate_fixtures.py` builds deterministic fixtures. `fetch-dependencies.sh`, `prepare-fixture-tools.sh`, `build-and-run-host.sh` and `build-and-run-android-api36.sh` document reproduction steps.

Generated dependencies, archives, compiled classes, DEX files, native binaries and temporary work directories are ignored by Git. Raw result JSONL and environment captures are retained as evidence.

## Separation of engine behavior and File Manager policy

The harness does not treat successful archive parsing as safe extraction. Path containment is checked with normalized destination resolution. Link entries are detected separately. Expansion is read through a byte limiter. This mirrors Architecture V1's rule that archive engines are parsers/codecs, while extraction policy remains an explicit boundary.

## Android execution

The same Java harness was compiled with `--release 17`, converted with D8 using `--min-api 31`, pushed to `/data/local/tmp/filemanager-poc003`, and executed on the Pixel 7a with `app_process`. This avoids creating production Android application structure.

For TAR.ZST, the Maven Android AAR for zstd-jni 1.5.7-16 was inspected and contained native libraries for arm64-v8a, armeabi-v7a, x86 and x86_64. The arm64-v8a `.so` was supplied only to the PoC runtime through `LD_LIBRARY_PATH`. The full Android run then completed with TAR.ZST passing.

## Fixture provenance

ZIP/TAR/7z fixtures were generated locally from synthetic content. RAR fixtures were copied from the Junrar v8.1.1 test-resource corpus. No production/user archives or unrelated user data were modified.