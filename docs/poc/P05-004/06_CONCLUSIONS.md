# P05-004 License and Native Acceptance Evidence

## License matrix

| Candidate | Repository-documented license position | Distribution gate | Status |
|---|---|---|---|
| Commons Compress 1.28.0 | Apache-2.0; exact JAR contains `META-INF/LICENSE.txt` and `META-INF/NOTICE.txt` | optional zstd convergence, full transitive notice register, broader parser/provider corpus | `READY_WITH_GATES` |
| Zip4j 2.11.6 | Apache-2.0; exact JAR has no embedded license/NOTICE file | external notice register, split/multipart/provider/security corpus | `READY_WITH_GATES` |
| Junrar 8.1.1 | UnRAR-derived non-standard terms; exact JAR/source contain no complete embedded license file | external license review, explicit RAR feature gate, broader solid/multipart/provider corpus | `READY_WITH_GATES` |
| zstd-jni 1.5.7-17 | exact JAR/AAR resolved; AAR has four 16 KiB-aligned ELF ABIs but no embedded license/NOTICE | source/reproducible-build provenance, final APK/runtime packaging, Android runtime, partial-output wrapper | `READY_WITH_GATES` |
| libarchive 3.8.9 | New BSD-style license | no Android/JNI integration evidence; Java-first retained matrix is not fully closed | `UNRESOLVED` |

Commons Compress and Zip4j are listed as Apache-2.0 candidates; Junrar’s
UnRAR-derived terms prohibit using the sources to recreate RAR compression
([`docs/research/14_LICENSES_DISTRIBUTION.md:29-38`](../../research/14_LICENSES_DISTRIBUTION.md), [`docs/research/14_LICENSES_DISTRIBUTION.md:82-91`](../../research/14_LICENSES_DISTRIBUTION.md)).
The current-source zstd-jni version is 1.5.7-17, while the prior P0 AAR was
1.5.7-16; no artifact equivalence is claimed.
The exact artifacts were resolved outside the worktree. Junrar remains
`EXTERNAL_LICENSE_REVIEW_REQUIRED`, not license-approved. zstd-jni has static
ELF evidence but no final APK or 16 KiB runtime evidence. No legal clearance is
claimed for any artifact.

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
- The exact zstd-jni AAR was inspected; all four bundled ABIs had 16 KiB ELF
  PT_LOAD alignment. Final APK packaging and Android runtime remain untested.
- The optional-capability harness correctly leaves zstd-jni and libarchive
  capabilities absent while native provenance/packaging/runtime gates are open.
- libarchive remains a second-stage alternative with `UNRESOLVED` disposition;
  this is not a claim that the library is globally rejected.

## Legal boundary

This is an engineering evidence register, not legal advice. RAR creation remains unsupported/uncommitted unless a separately licensed and technically credible encoder is proven ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:66-70`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)).

## Core V1 disposition

`NOT_READY`: the tested evidence supports a Java-first direction, but the
required RAR read/extract path still has an external license disposition and
the Zstandard/native path still lacks source/build, final APK, and Android
runtime closure. The retained Java-first format/provider matrix is also not
fully closed, so the evidence cannot prove that no unresolved issue would
change the selected technology family. libarchive remains `UNRESOLVED` until
that comparison is complete.

## Final candidate classifications

| Candidate | Technical classification | License/native classification |
|---|---|---|
| Commons Compress | `READY_WITH_GATES` | Apache notices and optional-zstd convergence remain explicit gates |
| Zip4j | `READY_WITH_GATES` | external notices plus split/provider/security gates remain |
| Junrar | `READY_WITH_GATES` technical | `EXTERNAL_LICENSE_REVIEW_REQUIRED`; `RAR_FEATURE_FREEZE_BLOCKING_ONLY` |
| zstd-jni | `READY_WITH_GATES` | `16K_STATIC_PASS_RUNTIME_PENDING`; source/build/APK/runtime gates remain |
| Java-first family | `NOT_READY` | required RAR/Zstandard and retained format/provider evidence could still change the family |

## Gate severity

No `ARCHITECTURE_BLOCKER` was found. Remaining `TECHNOLOGY_FREEZE_BLOCKER`
items are the unresolved Junrar/UnRAR license disposition for required RAR
read/extract, zstd-jni source/reproducible-build and Android acceptance if
TAR.ZST is retained, the unclosed retained-format/provider matrix, and the
unresolved libarchive comparison. Remaining `IMPLEMENTATION_GATE` items are
adapter spooling and reopen semantics, application containment/partial-output
cleanup, error normalization, cancellation, and broader per-format hostile
corpus/scale. Remaining `RELEASE_GATE` items are the complete transitive
notice register, final APK packaging, 16 KiB runtime/device evidence, Android
API coverage, and update ownership. These classifications do not execute
Technology Freeze.
