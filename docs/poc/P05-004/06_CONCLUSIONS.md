# P05-004 License and Native Acceptance Evidence

## License matrix

| Candidate | Repository-documented license position | Distribution gate | Status |
|---|---|---|---|
| Commons Compress 1.28.0 | Apache-2.0; NOTICE obligations called out | exact artifact/transitives, license and NOTICE inventory | `CONDITIONAL` |
| Zip4j 2.11.6 | Apache-2.0 | exact artifact/transitives and notices | `CONDITIONAL` |
| Junrar 8.1.x | UnRAR freeware / non-standard terms | exact artifact license review; explicitly exclude RAR creation | `CONDITIONAL` |
| zstd-jni Android AAR | exact AAR/native license not recorded in P0/research docs | exact artifact, native sources, transitive notices | `UNKNOWN` |
| libarchive 3.8.9 | New BSD-style license | exact source/build/transitive inventory and notices | `CONDITIONAL` |

Commons Compress and Zip4j are listed as Apache-2.0 candidates; Junrar’s UnRAR-derived terms prohibit using the sources to recreate RAR compression ([`docs/research/14_LICENSES_DISTRIBUTION.md:29-38`](../../research/14_LICENSES_DISTRIBUTION.md), [`docs/research/14_LICENSES_DISTRIBUTION.md:82-91`](../../research/14_LICENSES_DISTRIBUTION.md)). The repository does not provide a dedicated zstd-jni license/provenance record; that absence is an explicit `UNKNOWN`, not a license conclusion.

## Native gate

For zstd-jni and libarchive, closure requires all of the following:

- exact artifact and source revision;
- reproducible source build and build flags;
- ABI inventory for every packaged `.so`;
- ELF/16 KiB page-size validation;
- Android 12–16 compatibility evidence;
- real-device parser/crash behavior;
- binary-size and stripping impact;
- symbol/debug strategy;
- security patch/update ownership;
- complete license and notice manifest.

These requirements come directly from the architecture native boundary and license research ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:160-166`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md), [`docs/research/14_LICENSES_DISTRIBUTION.md:301-308`](../../research/14_LICENSES_DISTRIBUTION.md)).

## Current native findings

- TAR.ZST worked on the tested arm64 Pixel 7a through an Android zstd-jni AAR/native library.
- The device report used 4 KiB pages; 16 KiB compatibility is explicitly unmeasured.
- libarchive viability remains untested ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:160-166`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)).
- This worker did not build, package, inspect, or execute native code.

## Legal boundary

This is an engineering evidence register, not legal advice. RAR creation remains unsupported/uncommitted unless a separately licensed and technically credible encoder is proven ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:66-70`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)).
