# Archive Formats and Engine Research

Status: COMPLETE — candidate evaluation only; no dependency selected  
Last verified: 2026-09-17

## Classification legend

**FACT** documented behavior; **INFERENCE** derived consequence; **RECOMMENDATION** proposed direction; **UNRESOLVED** requires prototype/fixture evidence.

## Product goal

Archives should eventually be browsable as virtual folders without mandatory whole-archive extraction. Extraction and creation must stream large data and treat archive contents as hostile input.

## Format requirements

| Format | Browse/read target | Extract | Create target | Encryption | Split/multipart | Important notes |
|---|---:|---:|---:|---|---|---|
| ZIP / ZIP64 | Yes | Yes | Yes | AES + legacy compatibility desirable | Yes | strongest Java-native ecosystem |
| 7z | Yes | Yes | Desirable | encrypted 7z required | library-dependent | solid archives make random entry access expensive |
| RAR / RAR5 | Yes | Yes | No requirement | encrypted read desirable | Yes | creation should not be promised; proprietary ecosystem |
| TAR | Yes | Yes | Yes | container itself no | no standard split | sequential by design |
| TAR.GZ | Yes | Yes | Yes | no standard archive encryption | no | compressed stream + tar |
| TAR.BZ2 | Yes | Yes | Yes | no | no | sequential |
| TAR.XZ | Yes | Yes | Yes | no | no | sequential |
| TAR.ZST | Yes | Yes | Yes | no | no | sequential |
| GZIP | stream/member | Yes | Yes | no | no | not a directory archive in ordinary use |
| BZIP2 | stream | Yes | Yes | no | no | compressor stream |
| XZ | stream | Yes | Yes | no | no | compressor stream |
| Zstandard | stream | Yes | Yes | no | no | compressor stream |
| LZ4 | stream/frame | Yes | Yes | no | no | framing variants matter |
| CPIO | Yes | Yes | Desirable | no | no | useful Unix/firmware case |

## Candidate: Apache Commons Compress

**FACT:** Apache Commons Compress 1.28.0 was published in July 2025 and provides pure-Java APIs for many archive/compressor families: ZIP/ZIP64, TAR, CPIO, 7z, AR and others plus gzip, bzip2, XZ, Zstandard and LZ4 support depending on optional components. ZIP64 is supported. 7z reading supports substantially more than writing; encrypted ZIP is not its core strength.

Strengths:
- Apache-2.0 license.
- Java implementation; no JNI/page-size burden for core library.
- broad TAR/compressor/CPIO support.
- streaming APIs fit large-file extraction/creation.
- mature project and security fixes/releases.

Weaknesses:
- not one-stop support for encrypted/split ZIP and all desired RAR behavior.
- some formats inherently need seek/random access or special handling.
- virtual-folder indexing must be built above it.

Sources:
- https://commons.apache.org/proper/commons-compress/
- https://commons.apache.org/proper/commons-compress/examples.html
- https://commons.apache.org/proper/commons-compress/zip.html
- https://commons.apache.org/proper/commons-compress/security.html

**RECOMMENDATION:** Strong baseline candidate for TAR/compressor/CPIO and general ZIP/7z support, but not sufficient alone for the complete target matrix.

## Candidate: Zip4j

**FACT:** Zip4j is an Apache-2.0 Java ZIP library. Current research observed release 2.11.6 (2026-02-12). It supports password-protected ZIP, AES encryption, standard encryption, ZIP64, split ZIP creation/extraction, streams and normal ZIP create/extract operations.

Strengths:
- focused ZIP feature set.
- AES/encrypted ZIP support.
- split archives.
- pure Java, reducing native packaging risk.

Weaknesses:
- only ZIP family; needs companion engines.
- virtual browsing and provider-backed random access need integration work.

Sources:
- https://github.com/srikanth-lingala/zip4j
- https://github.com/srikanth-lingala/zip4j/releases

**RECOMMENDATION:** Preferred candidate to prototype for encrypted and split ZIP alongside Commons Compress rather than forcing one engine to cover every format.

## Candidate: Junrar

**FACT:** Junrar is a Java RAR reader/extractor. Releases in 2026 added RAR5 extraction/decryption/multipart support; research observed 8.0.0 in July 2026 and 8.1.0 in August 2026, with continuing fixes. It is aimed at reading/extraction, not RAR creation.

Strengths:
- avoids native JNI for RAR extraction.
- modern RAR5 work is active.
- multipart/encrypted cases now materially stronger than older Junrar generations.

Risks:
- RAR has complicated/hostile parser surface.
- format edge cases, solid archives, encryption and multipart require a large fixture corpus.
- do not promise creation.

Sources:
- https://github.com/junrar/junrar
- https://github.com/junrar/junrar/releases
- https://github.com/junrar/junrar/blob/master/CHANGELOG.md

**RECOMMENDATION:** Prototype as Java-native RAR/RAR5 reader before accepting native libarchive solely for RAR.

## Candidate: libarchive

**FACT:** libarchive 3.8.9 was released 2026-07-28. It is a native C multi-format streaming archive library under the New BSD license. It can read many formats including tar, cpio, zip, 7zip and rar-family formats, and write a broad but smaller set. It has a streaming-oriented architecture.

**SECURITY FACT:** 2025–2026 releases include multiple parser/security fixes affecting 7z, RAR5, CAB, CPIO and other formats. This is evidence both of active maintenance and of the attack surface inherent in broad native parsers.

Strengths:
- very broad format coverage.
- mature streaming model and automatic compression/filter detection.
- potentially reduces number of independent format engines.

Costs/risks:
- JNI/NDK integration is required on Android.
- native crash/memory-safety surface.
- every ABI and packaged `.so` must meet Android's 16 KB page-size requirements.
- native supply-chain and patch cadence must be owned by the project.

Sources:
- https://www.libarchive.org/
- https://github.com/libarchive/libarchive
- https://github.com/libarchive/libarchive/releases
- https://developer.android.com/guide/practices/page-sizes

**RECOMMENDATION:** Keep as a high-value alternative/second-stage candidate. Prefer a Java-first combination initially if it meets required formats and performance; prototype libarchive if format gaps or performance justify the native cost.

## Candidate comparison

| Candidate | Main formats | Read/extract | Create | Encryption | Split | Streaming | Native | License | Sep 2026 status |
|---|---|---|---|---|---|---|---|---|---|
| Commons Compress 1.28.x | ZIP, 7z, TAR, CPIO, many compressors | strong | broad | partial/format-specific | partial | strong | No | Apache-2.0 | maintained |
| Zip4j 2.11.6 | ZIP/ZIP64 | strong | strong | AES + ZipCrypto | strong | yes | No | Apache-2.0 | maintained |
| Junrar 8.1.x | RAR/RAR5 | extraction-focused | No | read support incl newer releases | yes | format-dependent | No | verify exact release license before freeze | actively maintained |
| libarchive 3.8.9 | very broad | very strong | broad subset | format-specific | format-specific | strong | **Yes** | New BSD | actively maintained |

**UNRESOLVED:** Exact final combination is not frozen. Re-check versions, transitive dependencies and licenses immediately before selection.

## Virtual-folder browsing

Archive browsing should not equal extraction.

Conceptual flow:
1. open archive using provider's sequential or seekable/random handle;
2. enumerate entries into bounded metadata structures/index;
3. expose entries as virtual `StorageEntry` objects;
4. open a single entry stream on demand;
5. cache indexes/checkpoints only where safe and useful.

### Seek implications

- ZIP central directory supports efficient metadata listing when the source is seekable; remote access can potentially use ranges.
- TAR is sequential and may need a scanned index for repeated navigation.
- solid 7z/RAR may require decoding earlier blocks to reach a later file, so “random open” can be expensive even if the outer source is seekable.

**RECOMMENDATION:** Archive provider capabilities should report entry access cost/seekability rather than promising O(1) random access.

## Secure extraction

### Path traversal / Zip Slip

**FACT:** Android's security guidance explicitly warns that malicious archive entry names can contain absolute/parent traversal paths and overwrite files outside the destination. This applies beyond ZIP to TAR/RAR/7z-style extraction.

Source:
- https://developer.android.com/privacy-and-security/risks/zip-path-traversal

**RECOMMENDATION:** Resolve each output under a trusted destination and verify canonical/normalized containment before creating it. Reject absolute paths, drive-like paths where relevant, NUL/confusing separators, and any normalized escape.

### Symlink attacks

Containment of the string path is insufficient if an attacker can cause an intermediate path component to become a symlink.

**RECOMMENDATION:**
- never follow archive-created symlinks when subsequently writing descendant entries;
- define an explicit “extract symlinks” policy per destination provider;
- where safe no-follow primitives are unavailable, reject or defer symlink creation;
- revalidate destination state close to mutation time.

### Decompression bombs

No single compression ratio threshold is universally correct. Defenses should compose:

- maximum entry count;
- maximum total expanded bytes;
- maximum single-entry size;
- maximum nesting depth for nested archive browsing/extraction;
- optional ratio/CPU/time guard;
- available-space reserve;
- cancellation support;
- byte accounting based on bytes actually written, not only declared headers.

### Malformed archives

**RECOMMENDATION:** Every archive is untrusted parser input. Fail one archive/entry without crashing the app process where practical; keep libraries current; fuzz/corpus-test malformed headers, truncation, integer boundaries and encrypted/multipart cases.

### Overwrite policy

Default extraction should not silently overwrite unrelated existing data. The operation plan should specify:

- skip
- rename/conflict suffix
- replace after confirmation/policy

Write into temporary/partial output and finalize when possible. A failed extraction must not leave a successful-looking destination.

## Creation

**RECOMMENDATION:** Initial creation targets should emphasize open/common formats with reliable libraries: ZIP/ZIP64 and TAR + compressors. 7z creation can be evaluated separately. RAR creation should remain unsupported unless a legitimate, maintainable and license-compatible encoder is identified later.

## Android 16 / native implications

Any native engine (libarchive or native compression codec) requires:

- arm64-v8a at minimum if that is later chosen, plus any additional intended ABIs;
- 16 KB ELF segment alignment/page-size compatibility;
- reproducible source build and security update plan;
- ABI crash testing on Android 12–16.

Source:
- https://developer.android.com/guide/practices/page-sizes

## Performance implications

- Stream extraction/creation; never stage whole archives in RAM.
- Avoid copying a SAF/cloud archive to local storage merely to list it unless the engine truly needs local seek and no range adapter is viable.
- For remote seek-heavy archive formats, use a block/range cache.
- Limit concurrent decompression to avoid RAM/thermal contention.
- Index huge archives incrementally.

## Prototype validation required

1. ZIP/ZIP64 + AES + split ZIP fixtures with Zip4j.
2. Commons Compress with TAR.GZ/BZ2/XZ/ZST, CPIO, LZ4 and 7z.
3. Junrar RAR4/RAR5, encrypted, solid and multipart fixtures.
4. Remote/SAF seekable and non-seekable archive sources.
5. 10k/100k archive entries without UI/memory collapse.
6. malformed corpus, path traversal, symlink, truncation and decompression limits.
7. If libarchive is retained: JNI integration, 16 KB page-size and fuzz/crash behavior on real devices.

## Recommendation summary

**Strong:** Java-first layered candidates — Commons Compress + Zip4j + Junrar — are lower integration risk than immediately adopting a broad native parser.  
**Plausible:** libarchive is a strong broad-format alternative if prototypes demonstrate a clear benefit.  
**Strong:** archives are virtual providers with honest random-access cost; extraction is a long-running Operation Manager workload.  
**Strong:** traversal/symlink/decompression limits are architecture requirements, not post-release hardening.  
**Postpone:** exact library set, versions and 7z creation policy until fixture prototypes.
