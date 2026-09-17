# P05-004 Candidate Closure Matrix

## Disposition summary

| Candidate | Intended role | Existing P0 evidence | Disposition | Blocking gates |
|---|---|---|---|---|
| Commons Compress 1.28.0 | TAR/compressors/CPIO plus general ZIP/7z | Broad ZIP/TAR/7z host + Pixel evidence; pure Java | `CONDITIONAL` | provider behavior, malformed corpus, 10k/100k scale, exact artifact/transitives |
| Zip4j 2.11.6 | encrypted/split ZIP | Encrypted/split ZIP host + Pixel evidence; pure Java | `CONDITIONAL` | provider behavior, malformed/security corpus, scale, exact artifact/transitives |
| Junrar 8.1.1 | RAR4/RAR5 read/extract | Technically survived RAR read/extract | `CONDITIONAL` | exact UnRAR terms, encrypted/solid/multipart corpus, cancellation, security |
| zstd-jni Android AAR | Zstandard codec path for TAR.ZST | TAR.ZST worked on tested arm64 Pixel 7a | `CONDITIONAL` | exact AAR/source provenance, ABI, 16 KiB, native crash/update/size evidence |
| libarchive 3.8.9 | broad native alternative | Documentation-only candidate description | `UNRESOLVED` | Android runtime, JNI, ABI/16 KiB, crash isolation, size, performance, update ownership |

The first four dispositions reflect the branch-local P0 reconciliation and archive boundary record ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:51-62`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md), [`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:58-66`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)). The libarchive status remains unresolved because exact native alternatives, SAF/remote origins, fuzz coverage, cancellation, and production scale remain open ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:55-62`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md)).

## Candidate-specific closure gates

### Commons Compress

The research describes broad pure-Java ZIP/ZIP64, TAR, CPIO, 7z, and compressor support, while noting that encrypted ZIP is not its core strength and 7z reading is broader than writing ([`docs/research/04_ARCHIVE_FORMATS.md:33-55`](../../research/04_ARCHIVE_FORMATS.md)). Closure requires the claimed format matrix, seekability behavior, malformed corpus, bounded memory at 10k/100k entries, exact release/transitive inventory, and a documented creation promise.

### Zip4j

Zip4j is the focused encrypted/split ZIP candidate, including AES, legacy encryption, ZIP64, and split create/extract behavior ([`docs/research/04_ARCHIVE_FORMATS.md:57-75`](../../research/04_ARCHIVE_FORMATS.md)). Closure requires positive and negative password cases, missing/corrupt split parts, provider-backed sources, malformed input, scale, and exact release/transitive notices.

### Junrar

Junrar is extraction-focused and must not be treated as a RAR creation path ([`docs/research/04_ARCHIVE_FORMATS.md:77-96`](../../research/04_ARCHIVE_FORMATS.md)). Closure requires exact artifact/legal review and RAR4/RAR5 encrypted, solid, multipart, malformed, and cancellation evidence. RAR creation remains unsupported.

### zstd-jni

The repository-level P0 reconciliation states that zstd-jni worked for TAR.ZST on the tested arm64 Pixel 7a, that the device report used 4 KiB pages, and that 16 KiB compatibility and libarchive viability remain untested ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:160-166`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)). No exact AAR version, native source revision, license manifest, ABI inventory, or reproducible-build record is present in the branch-local research/P0 record. Its disposition cannot be stronger than conditional.

### libarchive

The research presents libarchive as a broad native C streaming alternative under New BSD, with JNI, native memory-safety, ABI, 16 KiB, and patch-ownership costs ([`docs/research/04_ARCHIVE_FORMATS.md:98-121`](../../research/04_ARCHIVE_FORMATS.md)). It remains a second-stage alternative until a measured benefit over the Java stack justifies those costs.

## Machine-readable cross-check

`tools/p05-004/fixtures/valid.tsv` is the disposable evidence manifest consumed by `ArchiveEvidenceMatrixHarness.java`. The harness requires the five candidates, functional/provider/security/scale/license gates, and `native-16k` for native candidates. It reports `OVERALL=NOT_CLOSED` for this evidence set.
