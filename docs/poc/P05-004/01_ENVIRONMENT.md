# P05-004 Archive Candidate Closure Scope

Status: `REAL EVIDENCE CONTINUATION COMPLETE / NOT_READY / PRODUCTION CLOSURE NOT CLOSED`

## Objective

Audit the P0 archive evidence and define a truthful closure package for five candidates:

- Apache Commons Compress 1.28.0;
- Zip4j 2.11.6;
- Junrar 8.1.1 (exact Maven artifact and v8.1.1 source/test resources resolved in disposable space);
- zstd-jni 1.5.7-17 (exact JAR and AAR resolved; prior P0 AAR was not assumed equivalent);
- libarchive 3.8.9 (current official release; no project-supplied Android/JNI artifact).

The machine-readable manifest retains the historical labels `Junrar 8.1.x`
and `zstd-jni Android AAR`, while this continuation records exact `8.1.1` and
`1.5.7-17` host/native evidence separately from the earlier P0 records. The
source review was performed on 2026-09-17 against
[Junrar releases](https://github.com/junrar/junrar/releases),
[zstd-jni Central metadata](https://central.sonatype.com/artifact/com.github.luben/zstd-jni),
and the [libarchive release page](https://github.com/libarchive/libarchive/releases/latest).
The exact artifact checksums and source/POM metadata acquired on 2026-09-18
are recorded below. The worktree-local native packaging probe still finds no
checked-in AAR/ELF, which is distinct from the disposable resolved AAR
inspection. Artifact identity, notice inventory, and legal review therefore
remain closure gates.

The branch-local P0 reconciliation states that host and physical Pixel 7a archive records contained 54 records and `RUN_DONE=PASS`, while exact engine selection, libarchive/native alternatives, SAF/remote origins, fuzz coverage, cancellation, and production scale remain open ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:51-62`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md)). Those facts are carried forward without reinterpretation. This continuation adds independent host/JVM evidence against the exact resolved candidate artifacts below.

## In scope

- candidate-by-candidate disposition;
- format and operation coverage;
- direct, SAF, seekable, non-seekable, and remote access gates;
- extraction-security responsibilities outside parser libraries;
- license, provenance, native ABI, 16 KiB, size, crash, and update gates;
- architecture impact and explicit `NOT_TESTED` / `UNKNOWN` findings;
- a disposable host/JVM manifest validator;
- bounded host-only policy, scale, optional-capability, Junrar-local, and native-packaging probes.

## Out of scope

- selecting or adding production dependencies;
- Android application or module creation;
- ADB, emulator, physical-device, or Android runtime execution in the initial
  host-only continuation; the later physical-device continuation is documented
  in the dedicated section below;
- building libarchive, zstd-jni, or any archive library;
- claiming RAR creation;
- claiming that parser APIs provide extraction security;
- treating synthetic metadata scale as parser, UI, Android, or device evidence;
- Technology Freeze.

## Authority and decision boundary

P0 permits review of archive principles and boundaries, but not freezing exact engines, versions, native alternatives, Junrar licensing, full-entry/multipart behavior, fuzz coverage, cancellation, or production scale ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:110-125`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md)). The architecture likewise keeps the engine stack `POC_REQUIRED` and all five candidates as research candidates only ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:43-60`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)).

## Truth policy

`PASS` means the cited P0 or repository evidence covers the exact claim.
`CONDITIONAL` means a material gate remains open despite useful evidence.
`NOT_TESTED` means the campaign did not perform the measurement. `UNKNOWN`
means the available documents do not establish the fact. Open states are not
inferred closed by a passing neighboring test. Current-source version facts are
maintenance inputs, not runtime compatibility or legal approval.

## Continuation audit boundary

The initial 2026-09-18 continuation downloaded exact candidate artifacts into
disposable temporary directories and ran actual host/JVM parser, provider,
scale, hostile-input, and native-inspection probes. The later device-priority
pass additionally created one clearly disposable Android test APK outside the
worktree and ran it on the connected Pixel 7a. It did not create a production
dependency declaration, Android app/module, or OmniFile scaffold. The
worktree-local Junrar/native absence probes remain facts about this checkout
only; they do not contradict the separately resolved temporary artifacts.

## Real evidence continuation environment — 2026-09-18

Initial host-only environment: macOS x86_64, Java `26.0.2.1`, no Maven/Gradle
executable on `PATH`, no connected ADB device (`adb devices -l` returned only
the header), and 52 GiB free on the data volume. Artifacts were resolved into
disposable `/tmp` directories from Maven Central; no dependency declaration or
production build was created. The later device-priority environment is recorded
separately below.

| Candidate | Coordinate and tested artifact SHA-256 | POM SHA-256 | Sources SHA-256 |
|---|---|---|---|
| Commons Compress | `org.apache.commons:commons-compress:1.28.0` — `e1522945218456f3649a39bc4afd70ce4bd466221519dba7d378f2141a4642ca` | `033f4c78d632da88d0eb8ead974fc14a264392cebf12ab6c68d6cea7adf0c64a` | `6de9de4559f12bba6d41789c72f6a2a424514f2d2a3f7f49e2a3c52414db9632` |
| Zip4j | `net.lingala.zip4j:zip4j:2.11.6` — `5f1eb1e9d67cfee200a695e4079751b49e73a226b154193e13f7c602efbe72fb` | `9fe419f4b4bf26e8980c0cfcabdb24ff913a3a48b4f9d702ee9bf1fee9d6410b` | `c99829979f7d4a9e54ec954e95db63c6b7a601913adfdfdb636e80df49518910` |
| Junrar | `com.github.junrar:junrar:8.1.1` — `2de9ef91a179b40138c1480d16cecc63bce86de5076f5136c4598ad78ff9200e` | `5c5434471c2b7f6aa4efdb69e5c6cfdfbe3125155cabf815fe495086952f961f` | `5ca67d9aca7e1c9ba8a14ddbd7213bb67d82baa5c8665b284bf2c75e039fbf60` |
| zstd-jni | `com.github.luben:zstd-jni:1.5.7-17` JAR — `c9043e2da8a16c13b6cd7661a5cb56685a573c3546ef4bd0ead67b0b67c2e107`; AAR — `9528895e38141f4b547dd07a5a0a1f4b8de83fe96799782f45dc64d85ab94dd6` | `69386a1527d763b6e578afae22cf0ceb8aa71c4bab88c60746170004edf151ec` | `8928cc4bcedc57edafdb6eb78364a062ea417ddce41336d612d6573854579882` |

The source tags resolved to Commons Compress `852d9c23b94127feafc1649d9c7f13d4df338845`, Zip4j `c60f552986eb90e6862572b4209444c5435e423f`, and Junrar `1de660e148790448591e1d4076b2c961a1bfbca5`. No `v1.5.7-17` zstd-jni source tag was found; `v1.5.7-16` at `b74bc508f89c677310a7d863ccb77e0b98284662` is not treated as equivalent.

Required runtime transitives observed from the tested POMs were Commons Codec
`1.19.0`, Commons IO `2.20.0`, and Commons Lang3 `3.18.0` for the Commons
path; SLF4J API `2.0.17` for Junrar; and provided JetBrains annotations `24.1.0`
for zstd-jni. Commons declares optional format dependencies XZ `1.10`, Brotli
`0.1.2`, ASM `9.8`, and zstd-jni `1.5.7-4`; the optional zstd version does not
equal the separately tested `1.5.7-17`. Zip4j has no runtime dependencies in
its tested POM. Test-only dependencies are not treated as runtime transitives.

Commons Compress embeds `META-INF/LICENSE.txt` and `META-INF/NOTICE.txt`.
Commons IO, Commons Codec, Commons Lang3, and SLF4J API embed license material;
Zip4j, Junrar, and zstd-jni main artifacts do not embed a complete license or
NOTICE file. Junrar's POM names the `UnRar License`; its upstream license and
source headers are therefore required distribution inputs. The absence of an
embedded file is recorded as a notice/provenance gate, not as evidence of no
license obligation.

## Physical Pixel 7a and disposable Android probe — 2026-09-18

The later device-priority pass found exactly one intended physical device
(listed twice by USB and wireless transports for the same serial):

| Field | Observed value |
|---|---|
| ADB serial | `35241JEHN08768` |
| Manufacturer / model | Google / Pixel 7a |
| Product / device | `lynx` / `lynx` |
| Android / API | Android `16` / API `36` |
| Build fingerprint | `google/lynx/lynx:16/BP4A.260205.001/14624666:user/release-keys` |
| ABI | `arm64-v8a` (only ABI in `ro.product.cpu.abilist`) |
| Kernel | `6.1.134-android14-11-g66e758f7d0c0-ab13748739` |
| Authorization | `adb get-state=device` |
| Runtime page size | `adb shell getconf PAGE_SIZE=4096` |

The SDK has Android API 31, 36, and 37 platforms and Build Tools 35.0.0,
36.0.0, 36.1.0, and 37.0.0. The apparent
`system-images/android-37.0/google_apis_playstore_ps16k` directory is empty,
not an installed image. No NDK was installed. The host had 43 GiB free when
the device campaign began. The official Android guidance requires final APK
packaging and runtime testing in addition to ELF alignment; the Pixel is
therefore valuable 4 KiB Android evidence but is not a 16 KiB runtime.

The disposable probe was built outside Git with compile SDK/API 36, target
SDK 36, min SDK 21, Java 8 bytecode, Build Tools 36.1.0, and the exact
`zstd-jni:1.5.7-17` AAR. It is not OmniFile production scaffolding and did not
choose a production namespace, module, signing, or dependency policy. The
exact installed APK is recorded separately in `04_RESULTS.md`.

## Authorized 16 KiB image and isolated AVD — 2026-09-19

One authorized system image was installed and one isolated AVD was created;
no second image, NDK, production SDK, or physical-device reset was used:

| Field | Observed value |
|---|---|
| Package | `system-images;android-36;google_apis_ps16k;x86_64` |
| Package metadata SHA-256 | `6d8f1ae9e4bd19b485c6410fe056009bef6308d81ec461f84306fa9219070bbb` |
| AVD | `P05_Pixel_API36_16K_x86_64` / `pixel_7a` |
| Guest | API 36, `sdk_gphone16k_x86_64`, x86_64 |
| AVD path | `/Users/yuta/.android/avd/P05_Pixel_API36_16K_x86_64.avd` |
| Runtime page identity | `getconf PAGE_SIZE=16384`; `getconf PAGESIZE=16384` |
| Exact disposable APK | `/tmp/p05-004-16k-poc-build3/out/p05-004-zstd-16k-x86_64.apk` |
| APK SHA-256 | `051b344a39b82e5711af430eced14ade5dd0b47149796a338490bda49eeaead4` |

The APK was checked with `zipalign -c -P 16 -v 4` and passed signature
verification; it contains only the x86_64 zstd-jni native library. The first
installation attempt was not runtime evidence because the guest package
service was unstable. A single cold-boot retry reached `service check package:
found` and `PAGE_SIZE=16384`, but `adb install -r` later failed with `cmd:
Failure calling service package: Broken pipe (32)` while Package Manager was
being called. No 16 KiB app launch, decode, or crash result is claimed.
