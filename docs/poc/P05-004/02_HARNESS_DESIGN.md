# P05-004 Candidate Closure Matrix

## Disposition summary

| Candidate | Intended role | Existing P0 evidence | Disposition | Blocking gates |
|---|---|---|---|---|
| Commons Compress 1.28.0 | TAR/compressors/CPIO plus general ZIP/7z | Broad ZIP/TAR/7z host + Pixel evidence; pure Java | `CONDITIONAL` | provider behavior, malformed corpus, 10k/100k scale, exact artifact/transitives |
| Zip4j 2.11.6 | encrypted/split ZIP | Encrypted/split ZIP host + Pixel evidence; pure Java | `CONDITIONAL` | provider behavior, malformed/security corpus, scale, exact artifact/transitives |
| Junrar 8.1.x | RAR4/RAR5 read/extract | Technically survived the documented 8.1.x line | `CONDITIONAL` | exact artifact/UnRAR terms, encrypted/solid/multipart corpus, cancellation, security |
| zstd-jni Android AAR | Zstandard codec path for TAR.ZST | TAR.ZST worked on tested arm64 Pixel 7a | `CONDITIONAL` | exact AAR/source provenance, ABI, 16 KiB, native crash/update/size evidence |
| libarchive 3.8.9 | broad native alternative | No build or JNI invocation; required Java-first format/provider matrix is not fully closed | `UNRESOLVED` | Native alternative disposition requires closure of the retained Java-first requirements |

The first four dispositions reflect the branch-local P0 reconciliation and the
actual-engine continuation ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:51-62`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md), [`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:58-66`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)). Libarchive remains `UNRESOLVED`: it was not implemented, and the retained Java-first format/provider matrix is not fully closed. No native alternative is added by this assessment.

## Candidate-specific closure gates

### Commons Compress

The research describes broad pure-Java ZIP/ZIP64, TAR, CPIO, 7z, and compressor support, while noting that encrypted ZIP is not its core strength and 7z reading is broader than writing ([`docs/research/04_ARCHIVE_FORMATS.md:33-55`](../../research/04_ARCHIVE_FORMATS.md)). Closure requires the claimed format matrix, seekability behavior, malformed corpus, bounded memory at 10k/100k entries, exact release/transitive inventory, and a documented creation promise.

### Zip4j

Zip4j is the focused encrypted/split ZIP candidate, including AES, legacy encryption, ZIP64, and split create/extract behavior ([`docs/research/04_ARCHIVE_FORMATS.md:57-75`](../../research/04_ARCHIVE_FORMATS.md)). Closure requires positive and negative password cases, missing/corrupt split parts, provider-backed sources, malformed input, scale, and exact release/transitive notices.

### Junrar

Junrar is extraction-focused and must not be treated as a RAR creation path ([`docs/research/04_ARCHIVE_FORMATS.md:77-96`](../../research/04_ARCHIVE_FORMATS.md)). Closure requires exact artifact/legal review and RAR4/RAR5 encrypted, solid, multipart, malformed, and cancellation evidence. RAR creation remains unsupported.

### zstd-jni

The repository-level P0 reconciliation states that zstd-jni worked for TAR.ZST on the tested arm64 Pixel 7a, that the device report used 4 KiB pages, and that 16 KiB compatibility and libarchive viability remain untested ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:160-166`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)). This continuation resolves exact zstd-jni `1.5.7-17` JAR/AAR bytes and ABI/ELF facts, but source-tag, final APK, Android runtime, and reproducible-build gates remain open, so its disposition remains conditional.

### libarchive

The research presents libarchive as a broad native C streaming alternative under New BSD, with JNI, native memory-safety, ABI, 16 KiB, and patch-ownership costs ([`docs/research/04_ARCHIVE_FORMATS.md:98-121`](../../research/04_ARCHIVE_FORMATS.md)). It remains a second-stage alternative until a measured benefit over the Java stack justifies those costs.

## Machine-readable cross-check

`tools/p05-004/fixtures/valid.tsv` is the disposable evidence manifest consumed
by `ArchiveEvidenceMatrixHarness.java`. The harness requires the five
candidates, functional/provider/scale/license gates, two host-policy gates, ten individual security
negative-case gates (`security-traversal`, `security-symlink`,
`security-expansion`, `security-duplicates`, `security-truncated`,
`security-malformed`, `security-cancellation`, `security-nesting`,
`security-password`, and `security-multipart`), and eleven native gates for native
candidates (`native-provenance`, `native-abi`, `native-reproducible-build`,
`native-packaging`, `native-android-compat`, `native-16k`, `native-crash`, `native-size`, `native-symbols`,
`native-update-ownership`, and `native-notices`). It reports
`OVERALL=NOT_CLOSED` for this evidence set.

## P05-004 continuation harnesses

The continuation adds five dependency-free probes under `tools/p05-004`:

| Probe | Evidence produced | Deliberate non-claim |
|---|---|---|
| `ArchiveSecurityFixtureHarness` | 14 controlled application-policy cases for path containment, symlink rejection, expansion, duplicates, truncation, cancellation, nesting, password, and multipart failures | Does not parse archives or certify Junrar/native parser behavior |
| `ArchiveScaleHarness` | Deterministic synthetic entry-index checks at 10,000 and 100,000 entries with elapsed/heap observations | Not archive-parser, UI, Android, or device performance evidence |
| `ArchiveOptionalCapabilityHarness` | Truth-table check that only evidenced Java capabilities are declared; unproven zstd/libarchive and RAR creation remain absent | Not a production wrapper or dependency selection |
| `JunrarLocalInspectionHarness` | Bounded inspection of local Junrar checkout/license/source presence and conservative `LICENSE_REVIEW_REQUIRED` classification | Does not infer legal approval from research prose or release pages |
| `NativePackagingEvidenceHarness` | Counts local AAR/known ELF artifacts and preserves the inherited 4 KiB observation as distinct from 16 KiB evidence | Does not build, execute, or validate native code/page alignment |

The manifest adds `security-fixture-policy` and
`optional-capability-wrapper` as explicit host-policy gates, `scale-10k` to
separate the bounded size from the larger workload, and `native-packaging` for
native candidates. Candidate parser, artifact, provider, and Android gates
remain independently represented and open where the exact evidence is absent.

## Actual-engine continuation harness — 2026-09-18

The disposable parent-controlled JVM harness resolved the exact artifacts in
`01_ENVIRONMENT.md` and invoked their public APIs. It did not mock parser
results and it did not modify the repository. The harness generated compact
ZIP/TAR/TAR.GZ/AES-ZIP/Zstandard fixtures, sourced Junrar RAR4/RAR5 and
hostile fixtures from the Junrar `v8.1.1` test-resource archive, and used a
Zip64-forced stored-entry generator for real 100,000-entry scale.

Evidence commands were run as bounded Java processes with `-Xmx768m` or
`-Xmx512m`; the 100,000-entry compressed ZIP attempt exceeded the 120-second
watchdog, so it is recorded as a bounded timeout rather than a pass. The
Zip64/stored-entry generator completed the same 100,000-entry target in a
separate run.

The parser layer and application policy layer remain separate. Names such as
`../escape.txt` and `/absolute.txt` were observed as parser-visible names;
containment rejection remains the application wrapper's responsibility.
