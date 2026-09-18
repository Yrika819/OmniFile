# P05-004 Provider and Access Evidence

## Why access is a separate gate

P0 measured direct files, local SAF documents, and pipe-backed descriptors as materially different capabilities. A valid descriptor did not imply seekability, and the tested SAF provider’s behavior was explicitly provider-specific ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:23-35`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md)). Archive engines must therefore be evaluated against access capabilities rather than a universal `File` assumption.

## Proposed matrix

| Source kind | Required access cases | Candidate-sensitive questions | Current state |
|---|---|---|---|
| Direct regular file | sequential, seekable, random-offset | central-directory/index access; repeated entry opens | P0 storage evidence exists; candidate-specific mapping `UNKNOWN` |
| SAF seekable document | sequential + seekable | does listing require local staging; are offsets stable | P0 tested one local provider; not universal |
| SAF pipe/non-seekable document | sequential only | can listing/extraction stream; does an engine wrongly assume seek | P0 synthetic FIFO evidence; candidate matrix `NOT_TESTED` |
| Remote/range source | range/offset where advertised | block cache, repeated central-directory reads, retry behavior | research requirement; `NOT_TESTED` in this package |
| Solid 7z/RAR | expensive sequential revisit | first-entry versus later-entry cost; cancellation latency | explicitly unresolved ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:55-62`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md)) |

## Required measurements

For every candidate/format/source combination, record:

1. whether open/list/read works with the source’s actual access capability;
2. time to first listing row and time to open an early, middle, and late entry;
3. whether the engine requires seek, local staging, or a range cache;
4. peak managed/native memory;
5. cancellation latency and failure classification;
6. whether a retry reopens safely without duplicating output.

The P0 backlog specifically requires local direct and SAF seekable/non-seekable cases, single-entry cost, and native checks where applicable ([`docs/architecture/12_POC_BACKLOG.md:99-111`](../../architecture/12_POC_BACKLOG.md)).

## Architecture consequence

Archive entries remain virtual storage with provider-scoped identity. Random entry access must advertise cost rather than promise O(1), particularly for TAR and solid archives ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:7-20`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)). No candidate in this package closes the provider/access gate.

## Continuation results - 2026-09-17

Command:

```text
./tools/p05-004/test_archive_evidence_matrix.sh
```

The focused validator produced these bounded results:

```text
SECURITY_SCOPE=APPLICATION_POLICY_ONLY
SECURITY_CASES=14
SECURITY_FIXTURES=PASS
SCALE_SCOPE=SYNTHETIC_ENTRY_INDEX_ONLY
SCALE_10000=PASS
SCALE_100000=PASS
OPTIONAL_CAPABILITY_ROWS=9
OPTIONAL_CAPABILITIES=PASS
JUNRAR_LOCAL_REPOSITORY=ABSENT
JUNRAR_LOCAL_LICENSE=ABSENT
JUNRAR_LOCAL_SOURCE=ABSENT
JUNRAR_RAR_CREATION=UNSUPPORTED
JUNRAR_CLASSIFICATION=LICENSE_REVIEW_REQUIRED
NATIVE_LOCAL_AAR_COUNT=0
NATIVE_LOCAL_ELF_COUNT=0
NATIVE_PACKAGING=NO_LOCAL_ARTIFACT
NATIVE_16K=UNRESOLVED_4K_ONLY
```

The 10k/100k lines are synthetic host metadata-index measurements. They add
bounded scale evidence to the package but do not close Commons Compress, Zip4j,
Junrar, or libarchive parser-scale gates. The security pass similarly validates
the wrapper policy fixture set, not the libraries. The local Junrar and native
results are absence/classification evidence, not artifact acceptance. The same
validator also rejected a deliberately incorrect security expectation and a
100,001-entry request, confirming the new fixture and scale bounds fail closed.

## Exact artifact and parser results — 2026-09-18

The artifact table, POM/source hashes, transitives, and embedded license/NOTICE
inventory are recorded in `01_ENVIRONMENT.md`. The following observations are
from the exact JARs/AAR listed there.

| Case | Observed result |
|---|---|
| Commons ZIP sequential | Four entries listed from an `InputStream`; `safe.txt`, `../escape.txt`, `/absolute.txt`, and `dir/../normalized.txt` were parser-visible. |
| Commons ZIP seekable | Four entries listed through `ZipFile.Builder().setPath(...)`; a seekable ZIP API was exercised. |
| Commons truncated ZIP | Rejected with `java.io.EOFException`. |
| Commons TAR/TAR.GZ | Three entries listed and read sequentially for both TAR and TAR.GZ. |
| Zip4j AES | AES-256 fixture read with the correct password; wrong password produced `net.lingala.zip4j.exception.ZipException: Wrong Password`. |
| Zip4j truncated ZIP | Rejected with `ZipException: Zip headers not found. Probably not a zip file`. |
| Junrar RAR4/RAR5 | Exact `8.1.1` listed two entries and extracted file entries for both formats from a local file. |
| Junrar RAR4/RAR5 stream | Exact `8.1.1` listed two entries from sequential streams for both fixtures. Separate provider harness evidence found other stream-shaped fixtures that require file spooling; provider neutrality is not claimed. |
| Junrar hostile/password | `parent-dir.rar` was accepted and extracted; `mkdir-escape.rar` rejected with `CorruptHeaderException`; missing/wrong passwords rejected with `InitDeciphererFailedException`, `WrongPasswordException`, or `CrcErrorException`; password `junrar` extracted the RAR4/RAR5 password fixtures. |
| Junrar corrupted header | `corrupt-header.rar` was accepted with zero file entries, `hasBrokenHeaders=true`, and two header failures. This is controlled parser behavior requiring wrapper policy, not a security pass. |
| zstd-jni valid/truncated | Matching `1.5.7-17` host JAR decoded valid data; empty, truncated, bad-magic, mutated-header, zero-filled, and random inputs ended in `ZstdIOException` under a 20-second watchdog. Truncated frames could emit up to 36 bytes before the exception. |

## Provider-access results

The provider harness used counting streams and pipes over the exact JARs.
Commons Compress ZIP/TAR stream APIs worked sequentially; seekable `ZipFile`
and `TarFile` paths observed 15 and 10 seeks respectively. Zip4j's
`ZipInputStream` worked sequentially, while its full-feature `ZipFile` path was
local-file based; an explicit spool is required for a non-path source in that
mode. Junrar's `Archive(File)` succeeded for RAR4/RAR5, but the tested
`Archive(InputStream)` consumed complete streams and returned zero headers for
other RAR4/RAR5 fixtures; stream-to-temp-file spooling restored success.
zstd-jni plus a TAR parser was sequential and did not provide random-entry
access without replay/staging. Real Android `ContentResolver`,
`ParcelFileDescriptor`, SAF, cloud, revocation, and remote/range behavior remain
`NOT_TESTED`.

## Scale results

The real compressed ZIP run reached 10,000 entries: build `23,266 ms`, Commons
listing `2,494 ms`, Zip4j open `1,175 ms`, archive size `1,227,802` bytes. The
Zip64-forced stored ZIP run reached 100,000 entries: build `2,833 ms`, Commons
listing `2,022 ms`, Zip4j open `2,437 ms`, archive size `15,777,878` bytes; both
engines enumerated `100,000` entries. A separate compressed 100,000-entry
attempt exceeded its 120-second watchdog and is not counted as a pass. First
listing latency, single-entry latency, managed/native peak memory, cancellation
latency, and spool disk footprint were not measured in this run and remain
`NOT_TESTED`.

## zstd-jni native inventory

The exact AAR `1.5.7-17` contains four compressed native entries. All had
`p_align=0x4000` for every PT_LOAD segment and congruent `p_offset/p_vaddr`
pairs: arm64-v8a, 476,304 bytes, SHA-256
`e30520c5ca997c9462751f453f674500ec2c4b20dc422a776d0e3da509df965e`; armeabi-v7a,
363,308 bytes, `691823eaa1668b808a6371ffd3eb84fa4623e7e1b0469935d88ff71a2be44776`;
x86, 552,328 bytes, `43ff557f9e6d5fb697e99c30a7f2ee0d0262512e914eb7be457bfbce473d885a`;
x86_64, 547,360 bytes, `3cba491607edd9392911131000622454a42d534da40e022a0b4826943c223f27`.
The AAR native entries were DEFLATED. `zipalign -c -P 16 -v 4` succeeded for
the AAR's compressed entries. In the initial host-only pass no final APK
existed; the later disposable APK result is recorded below and is separate
from the AAR-only check.

The initial native inspection pass had no connected device, so its result was
`16K_STATIC_COMPATIBILITY_VERIFIED` with Android runtime
`16K_STATIC_PASS_RUNTIME_PENDING`. The later device-priority pass below adds
fresh Pixel evidence; it does not change the fact that the Pixel reports 4 KiB.

## Physical Pixel 7a evidence — disposable APK — 2026-09-18

The connected Pixel 7a was tested serially after explicit discovery and page
size measurement. The disposable APK was assembled from the exact
`zstd-jni:1.5.7-17` AAR, with only `lib/arm64-v8a/libzstd-jni-1.5.7-17.so`
packaged for the device. It was not added to this repository and is not a
production OmniFile artifact.

| Artifact/runtime field | Observed value |
|---|---|
| APK local SHA-256 | `dd567436ef461cf568c300fc57194232846cee7610a4d0e2addd0196be8336d9` |
| Installed APK SHA-256 | exactly equal to local SHA-256 (`dd567436ef461cf568c300fc57194232846cee7610a4d0e2addd0196be8336d9`) |
| Compile / target / min | API 36 / 36 / 21 |
| Build tools | 36.1.0 (`aapt2`, `d8`, `zipalign`, `apksigner`) |
| Native ABI | arm64-v8a; `libzstd-jni-1.5.7-17.so` |
| APK alignment | `zipalign -c -P 16 -v 4`: `Verification successful` |
| Runtime page size | `4096` |
| Process restart | force-stop followed by a new PID and a second complete result set |
| Crash status | no `FATAL EXCEPTION`, native crash, or hang observed |

The exact installed process emitted these results:

| Test | Observed result |
|---|---|
| native load / identity | `PASS`; arm64-v8a, page size 4096 |
| valid Zstandard | `PASS`; 30-byte frame to 21-byte payload |
| TAR.ZST representative | `PASS`; 92-byte frame to 1536-byte TAR payload containing `P05-004-TAR` |
| repeated load/use | `PASS`; 20/20 decodes |
| truncated Zstandard | `CONTROLLED_ERROR`; `ZstdException:Src size is incorrect` |
| malformed Zstandard | `CONTROLLED_ERROR`; `ZstdException:Unknown frame descriptor` |
| force-stop/relaunch | `PASS`; same six results under a new process PID |

This is real Android 4 KiB runtime evidence. It does not close runtime
16 KiB verification. It also does not prove extraction containment: the probe
decoded to memory and did not create destination files.

## Real SAF / ParcelFileDescriptor evidence — 2026-09-18

The same disposable probe launched `ACTION_OPEN_DOCUMENT`. A controlled
`p05-004.tar.zst` fixture was placed only under the disposable device area
`/sdcard/Download/p05-004-disposable/`, selected through Android DocumentsUI,
and returned as the real URI
`content://com.android.providers.downloads.documents/document/msf%3A13498`.
The app opened it with `ContentResolver.openFileDescriptor(uri, "r")` and
decoded 92 compressed bytes to 1536 bytes successfully. This closes one real
Downloads DocumentsProvider/PFD sequential-read observation on the tested
Pixel. Seekability, pipe-backed PFD behavior, revocation, cloud providers,
spooling, and extraction cleanup remain untested.

## Libarchive comparator disposition

A bounded host attempt downloaded the official libarchive `3.8.9` source,
started a CMake Release configuration, and detected the host C compiler plus
zlib, bzip2, lzma, and zstd. Configuration was stopped after several minutes
of macOS feature probes before a library or comparator executable was built.
No Android/JNI artifact was produced. Since the actual Java-first probes have
not exposed a required CORE_V1 capability that needs native libarchive, the
final disposition is `NOT_JUSTIFIED_FOR_CORE_V1`, with native comparison
retained as a later trigger if the focused Java/provider matrix fails. This is
not a claim that libarchive is unfit globally.

The unchanged disposable manifest/harness still prints
`libarchive 3.8.9=UNRESOLVED` and `OVERALL=NOT_CLOSED`; that is the machine
guard for an uninvoked candidate and is intentionally not reclassified as a
successful libarchive result. The final architecture disposition for this
campaign is the separate, explicit `NOT_JUSTIFIED_FOR_CORE_V1` decision above.

## Error normalization proof boundary

The observed results support a future wrapper taxonomy without freezing library
exception class names:

| Observed result | Architecture category supported |
|---|---|
| malformed/truncated ZIP `EOFException` / `ZipException`; Junrar broken headers | `INVALID_ARCHIVE` or `CORRUPT_OR_TRUNCATED` |
| unsupported or untested format route | `UNSUPPORTED_FORMAT` / `UNSUPPORTED_FEATURE` |
| missing Junrar password; wrong ZIP/RAR password exceptions | `PASSWORD_REQUIRED` / `BAD_PASSWORD` |
| zstd truncated/bad frame `ZstdException` / `ZstdIOException` | `CORRUPT_OR_TRUNCATED` |
| application cancellation/resource threshold | `CANCELLED` / `RESOURCE_LIMIT_EXCEEDED` (contract only; not yet runtime extraction evidence) |
| provider open/read failure | `IO_FAILURE` (contract only; real PFD read passed for one URI) |

This proves category-level normalization is feasible for the tested outcomes;
it does not freeze Kotlin exception types or close cancellation, limits, or
destination-cleanup behavior.

## Fixture hashes and reproduction anchors

The checked-in sources are `tools/p05-004/engine-evidence/*.java`; the exact
compile/run commands are in its `README.md`. The generated fixtures from the
fresh checked-in-source run were: `hostile.zip`
`ae9e5bbc12647e531bb8ce56f69b024cf8d98b6321b6d5a4af06501b295f97eb`,
`hostile.tar` `bbdb2d91e4cb86f1637e77418198ce25057954b0eb8ff0a7a5fc4da535f5b703`,
`hostile.tar.gz`
`eda103da614546da0cd818d44b88d03f409e5a0fe51c00ec63949cd03dcea9d6`,
`encrypted.zip` `2cb8db90bd422be7b19339db8f2fefec9a6e9d5cc665692be20202f5c94cd20e`,
`payload.zst` `de75753abbb4471db7836c6ee02510302d30645180b6fa9f67ad12e8174ffd2a`,
and `scale-100k.zip`
`ea7786960e046206e8615ef88fe587a86fd152c64a70acdfec0b574912a31e9d`.
The Junrar `v8.1.1` resource hashes were RAR4
`91e21f4126181790429f5e5ecdefedae6d6883ed6b4b396fc94d4ed6b32c065a`, RAR5
`aaf1a7d974f302f181720280151053f15ef1119b13d2f7bf9fddf2163bbf1701`, corrupt
header `52cb2d337569bded1bef57d484adcdabf28b15bce916d1a4cf6babb3e21fe2b8`,
`parent-dir.rar` `9d3c14e766a9f08893c16a37a9c0ca837a1b3a19aca8ea84c7eb5209b7bbab72`,
and `mkdir-escape.rar`
`d1d09c89d32a555cae7fb4157ec4b16d8be6bac4b702727d14e4b68e7653a0cb`.
