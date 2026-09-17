# P05-004 License and Native Acceptance Evidence

## License matrix

| Candidate | Repository-documented license position | Distribution gate | Status |
|---|---|---|---|
| Commons Compress 1.28.0 | Apache-2.0; NOTICE obligations called out | exact artifact/transitives, license and NOTICE inventory | `CONDITIONAL` |
| Zip4j 2.11.6 | Apache-2.0 | exact artifact/transitives and notices | `CONDITIONAL` |
| Junrar 8.1.1 | UnRAR-derived non-standard terms | local repository/license/source absent; legal/product review required and RAR creation excluded | `LICENSE_REVIEW_REQUIRED` |
| zstd-jni 1.5.7-17 | BSD-2 binding plus native Zstandard licensing; exact AAR not present locally | exact artifact/native sources, notices, ABI, 16 KiB runtime | `CONDITIONAL` |
| libarchive 3.8.9 | New BSD-style license | no Android/JNI integration evidence and native gates are open | `NOT_JUSTIFIED_FOR_CORE_V1` |

Commons Compress and Zip4j are listed as Apache-2.0 candidates; Junrar’s
UnRAR-derived terms prohibit using the sources to recreate RAR compression
([`docs/research/14_LICENSES_DISTRIBUTION.md:29-38`](../../research/14_LICENSES_DISTRIBUTION.md), [`docs/research/14_LICENSES_DISTRIBUTION.md:82-91`](../../research/14_LICENSES_DISTRIBUTION.md)).
The current-source zstd-jni version is 1.5.7-17, while the prior P0 AAR was
1.5.7-16; no artifact equivalence is claimed.
The bounded local probe found no Junrar checkout/license/source, zstd-jni
AAR/ELF, or libarchive source/build. Junrar is therefore
`LICENSE_REVIEW_REQUIRED`, not license-approved.

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

The continuation also requires `native-packaging` as a separate gate. The
probe result `NATIVE_LOCAL_AAR_COUNT=0`, `NATIVE_LOCAL_ELF_COUNT=0` is an
absence finding only. The inherited P0 `4 KiB` page report is recorded as
`UNRESOLVED_4K_ONLY`; it must not be relabeled as 16 KiB compatibility.

These requirements come directly from the architecture native boundary and license research ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:160-166`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md), [`docs/research/14_LICENSES_DISTRIBUTION.md:301-308`](../../research/14_LICENSES_DISTRIBUTION.md)).

## Current native findings

- TAR.ZST worked on the tested arm64 Pixel 7a through an Android zstd-jni AAR/native library.
- The device report used 4 KiB pages; 16 KiB compatibility is explicitly unmeasured.
- libarchive viability remains untested ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:160-166`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)).
- This worker did not build, package, inspect, or execute native code.
- The optional-capability harness correctly leaves zstd-jni and libarchive
  capabilities absent while native provenance/packaging/runtime gates are open.
- libarchive remains retained as a second-stage alternative, but is
  `NOT_JUSTIFIED_FOR_CORE_V1` on the current evidence; this is a disposition,
  not a claim that the library is globally rejected.

## Legal boundary

This is an engineering evidence register, not legal advice. RAR creation remains unsupported/uncommitted unless a separately licensed and technically credible encoder is proven ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:66-70`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)).

## Core V1 disposition

`READY_WITH_EXPLICIT_GATES`: a Java-first review shape is supportable for
Commons Compress, Zip4j, and Junrar read/extract paths only after exact artifact,
notice, security-corpus, provider-access, and scale gates are completed. zstd-jni
remains optional and conditional on a separately pinned native artifact and
16 KiB/ABI evidence. libarchive is `NOT_JUSTIFIED_FOR_CORE_V1`; adding JNI
complexity is not warranted by the current evidence. The conservative manifest
validator remains `OVERALL=NOT_CLOSED` until those gates are supplied.
