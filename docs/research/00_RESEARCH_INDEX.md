# Pre-Implementation Technical Research Index

Status: COMPLETE  
Campaign date: 2026-09-17  
Branch: `research/preimplementation-v1`  
Starting authority: `README.md`, `UI_DESIGN_V1.md`  
Scope: **research and documentation only; no Android application implementation**

## How to read this pack

Each research document distinguishes:

- **FACT** — directly supported by cited platform/library/protocol documentation;
- **INFERENCE** — conclusion derived from documented behavior;
- **RECOMMENDATION** — proposed direction for later user review;
- **UNRESOLVED** — documentation is insufficient and a prototype/device test is required.

Recommendations in this pack are deliberately **not** implementation authority. In particular, this campaign does not freeze application ID, namespace, module architecture, SDK levels, dependency versions, final libraries, permissions, manifest structure, signing or release architecture.

## Master research index

| # | Status | Document | Major finding | Recommended direction | Confidence | Main unresolved questions | Future prototype required? |
|---|---|---|---|---|---|---|---|
| 01 | Complete | [Android platform storage](01_ANDROID_PLATFORM_STORAGE.md) | Android 12–16 storage is a mix of scoped storage, SAF, MediaStore, direct paths and special all-files access; URI providers do not equal real filesystems | preserve direct/SAF semantics and expose capabilities explicitly | High | seekable SAF FDs, removable-media/OEM behavior, Android/data behavior | **Yes** |
| 02 | Complete | [Storage provider architecture](02_STORAGE_PROVIDER_ARCHITECTURE.md) | local, SAF, root, network, cloud and archive backends cannot honestly share all `File` semantics | provider-scoped opaque identity + capability-based handles/operations | Very high | exact interface granularity, cache/transfer adapter behavior | **Yes** |
| 03 | Complete | [Root access](03_ROOT_ACCESS.md) | root is optional, manager-specific and still subject to SELinux/mount behavior | non-root baseline; structured root-service/NIO candidate before shell strings | High | Magisk/KernelSU/APatch behavior, FD bridge, namespaces | **Yes** |
| 04 | Complete | [Archive formats](04_ARCHIVE_FORMATS.md) | no single low-risk engine perfectly covers encrypted ZIP, RAR, 7z and broad compressors; archives are hostile inputs | Java-first candidate stack first; retain libarchive alternative; virtual browsing | Med-High | RAR/7z edge cases, solid/random access, native benefit | **Yes** |
| 05 | Complete | [FLACtify playback](05_MEDIA_PLAYBACK_FLACTIFY.md) | MediaSession/ExoPlayer structure is useful but current `PlayerViewModel` and scanner/persistence are tightly coupled | extract/refactor playback behavior around provider-neutral media sources; do not transplant monolith | High | remote seek/cache, SAF seek, tag editing on remote sources | **Yes** |
| 06 | Complete | [File conversion](06_FILE_CONVERSION.md) | Media3/platform cover many mainstream cases; old FFmpegKit is retired; high-fidelity Office->PDF lacks credible local Android basis | platform/Media3 first; optional source-built FFmpeg only for proven gaps; postpone Office conversion | Med-High | codec/device matrix, FFmpeg gap, HDR/metadata fidelity | **Yes** |
| 07 | Complete | [NAS/network storage](07_NAS_NETWORK_STORAGE.md) | SMB/SFTP/WebDAV differ in security, seek, rename, locking and reconnect semantics | prototype SMBJ/SSHJ/dav4jvm separately; strict credentials/TLS/SSH trust | Med-High | NAS interoperability, Range/ETag, reconnect, media seek | **Yes** |
| 08 | Complete | [Cloud storage](08_CLOUD_STORAGE.md) | SAF hides many first-class cloud capabilities such as resumable upload, native search, change feeds and conflict/version data | first-class Drive/OneDrive/Dropbox providers where needed + SAF fallback | High concept | narrow OAuth scopes, SDK-vs-REST, range/resume behavior | **Yes** |
| 09 | Complete | [Background operations](09_BACKGROUND_OPERATIONS.md) | Android 12–16 background/FGS/job rules prevent one universal long-operation mechanism | durable Operation Manager independent from UIDT/WorkManager/FGS executor | Very high | very long local copy mechanism, checkpoint cadence, Android 15/16 stop behavior | **Yes** |
| 10 | Complete | [M3 Expressive/adaptive UI](10_UI_M3_EXPRESSIVE_ADAPTIVE.md) | stable Material3 + Adaptive already cover the baseline; alpha Expressive APIs are not required for core phone/tablet/two-pane design | stable-first UI; isolate optional newer expressive motion | High | signature motion API value, foldable/landscape ergonomics | **Yes** |
| 11 | Complete | [Security](11_SECURITY.md) | root, archives, arbitrary filenames, network servers, intents and credentials create several independent trust boundaries | security controls and hostile fixtures must be designed with each feature | Very high | implementation-specific TOCTOU/native-parser risks | **Yes** |
| 12 | Complete | [Performance and limits](12_PERFORMANCE_AND_LIMITS.md) | 100k-entry folders, million-entry indexes, 100 GB files and tiny-file workloads stress different layers | lazy/incremental UI, bounded backpressure, 64-bit sizes, reconstructible index | High | provider enumeration speed, DB/search choice, concurrency tuning | **Yes** |
| 13 | Complete | [Test strategy](13_TEST_STRATEGY.md) | correctness depends on provider contracts, hostile fixtures, process-death and physical-device behavior, not only UI tests | layered unit/component/integration/device strategy across API 31–36 | High | exact frameworks/device farm/coverage thresholds | Later implementation choice |
| 14 | Complete | [Licenses/distribution](14_LICENSES_DISTRIBUTION.md) | permissive stack is feasible but FFmpeg, UnRAR-derived code, native builds, all-files access and OAuth scopes require explicit gates | preserve Play/public-distribution option and audit exact artifacts/build flags before selection | High | final dependency artifacts, release channel, OAuth scope, FFmpeg config | **Yes** for selected artifacts |
| 15 | Complete | [Architecture recommendations](15_ARCHITECTURE_RECOMMENDATIONS.md) | the cross-cutting low-regret core is capability-based providers + durable operation state + bounded streaming | review and explicitly freeze principles before any application implementation | Very high for principles | implementation names/modules/dependencies and prototype-dependent semantics | **Yes** |

## Cross-cutting decision matrix

This matrix records the current research position. “Strong” means strong evidence for the **principle**, not authorization to implement it.

| Decision area | Current research position | Evidence strength | Freeze now? | Why / dependency | Prototype before freeze? |
|---|---|---:|---:|---|---:|
| universal `java.io.File`-style model | Reject as architecture assumption | Very high | **Yes: reject assumption only** | SAF/cloud/archive semantics contradict it | No for principle |
| capability-based storage providers | Strong recommendation | Very high | User review first | lowest-regret way to preserve backend semantics | interface details: Yes |
| provider-scoped opaque IDs | Strong recommendation | High | User review first | paths/URIs are not universal stable identity | provider mapping: Yes |
| sequential vs seekable/random handles | Strong recommendation | High | User review first | pipes/ranges/solid archives differ | **Yes** |
| durable Operation Manager | Strong recommendation | Very high | User review first | Android lifecycle + cross-provider recovery | executor mapping: **Yes** |
| partial destination + finalization | Strong recommendation | Very high | User review first | prevents corruption/data loss on interruption | backend finalization: Yes |
| root required for app | Reject | Very high | **Yes: non-root baseline** | product requirement + Android security reality | root features later |
| libsu | leading root candidate | Medium-high | No | structured root-service/NIO model is attractive | **Yes** |
| `MANAGE_EXTERNAL_STORAGE` | plausible later permission for core file manager | High policy evidence | No | Play-eligible category but restricted/reviewed | device/policy check |
| SAF | required interoperability mechanism | High | principle only | document providers/removable/cloud access | **Yes** |
| first-class cloud APIs | strong for advanced cloud features | High | provider scope later | SAF loses resume/search/delta/version controls | **Yes** |
| archive virtual folders | strong recommendation | High | principle only | aligns browsing goal and streaming engines | **Yes** |
| Java-first archive stack | preferred first prototype | Medium-high | No | lower native/16 KB integration risk | **Yes** |
| libarchive | serious alternative | Medium | No | broad formats but native attack/integration cost | **Yes** |
| Media3 playback/session | strong platform direction | High | concept only | official Android media stack | remote source: Yes |
| transplant FLACtify `PlayerViewModel` | Reject | High | **Yes: do not transplant monolith** | coupling conflicts with multi-provider architecture | No |
| Media3 Transformer first | preferred conversion prototype | Medium-high | No | official/hardware path | **Yes** |
| FFmpeg | optional gap-filler only | Medium | No | native + LGPL/GPL + build burden | **Yes** |
| local Office->PDF | postpone/unsupported without new evidence | High | No | no proven high-fidelity Android-local engine | only if revisited |
| SMBJ | preferred SMB prototype | Medium-high | No | maintained pure-Java SMB2/3 candidate | **Yes** |
| SSHJ | preferred SFTP prototype | Medium-high | No | maintained, modern security fixes | **Yes** |
| dav4jvm | preferred WebDAV prototype | Medium | No | active library, MPL-2.0 | **Yes** |
| database: Room | candidate only | Medium | **No** | data model/scale not frozen | **Yes** |
| AppSearch | search-index candidate | Medium | **No** | search strengths, API maturity to recheck | **Yes** |
| stable Material3/Adaptive baseline | strong recommendation | High | versions: No | stable APIs cover baseline | UI prototype |
| alpha Expressive dependency | optional only | Medium | **No** | should not be adopted for branding alone | Yes if desired |
| Android 12 support | retain | High/product requirement | principle only | compatibility requirement | device tests |
| Android 16 primary environment | retain | High/product requirement | principle only | current primary target environment | device tests |
| exact targetSdk/API 36 | distribution fact, not research decision | High | **No** | Play currently requires API 36 for new/update submission | review at implementation |
| persistence/index required for browsing | Reject coupling | High | principle only | index can be stale/rebuilding | Yes for performance |
| secure flash overwrite guarantee | Do not promise | High | wording principle | modern flash/storage cannot guarantee app-level forensic erase | backend-specific only |

## Cross-cutting invariants proposed for user review

These are the strongest low-regret findings in the campaign:

1. Standard/non-root mode is independently functional.
2. A provider advertises capabilities; the app does not invent missing filesystem semantics.
3. Provider-scoped object identity is separate from display path/name.
4. Seek/random access, POSIX metadata, atomic move and server-side operations are optional capabilities.
5. Large data flows are bounded streams with 64-bit progress.
6. Long operations persist recoverable state independently from Android worker/service lifetime.
7. Cross-provider move deletes the source only after destination success/finalization.
8. Root, network and cloud credentials are isolated security boundaries.
9. Archives are untrusted and extraction is containment/limit checked.
10. Search indexes/caches are reconstructible acceleration, not the authority for destructive actions.
11. Playback resolves provider sources rather than requiring local file paths.
12. Stable Android UI APIs form the baseline; experimental APIs must justify their risk.
13. Android 12 compatibility and Android 16 real-device behavior are both tested.
14. Dependency/version/license choices are made when their feature phase begins, not copied from FLACtify.

## Major unresolved questions retained intentionally

- Which SAF providers reliably support seek/random reads and descriptor-backed access?
- What exact non-root/all-files experience is acceptable under Android 16 and desired Play distribution?
- Which archive-engine combination survives encrypted/solid/multipart/malformed corpus testing?
- Does libarchive offer enough real benefit to justify native complexity?
- How should huge local-only copies run across Android 12–16 under modern background limits?
- Can SMB/SFTP/WebDAV/cloud provider adapters deliver low-latency Media3 seeking?
- What is the narrowest viable Google Drive OAuth scope for intended UX?
- Which persistence/search stack performs acceptably at 1M+ entries without coupling browsing to indexing?
- Can tag editing safely preserve metadata across SAF/remote providers without excessive full-file staging?
- Which conversions genuinely require FFmpeg after Media3/platform prototypes?
- Which root architecture is consistent enough across Magisk, KernelSU and APatch?
- Which newest Material Expressive features add enough value beyond stable APIs to justify experimental dependencies?

## Prototype campaign priorities suggested after user review

Recommended evidence order, without implementing production architecture:

1. SAF/direct/removable capability probe on Android 12 and 16.
2. Operation Manager lifecycle experiment for very large copy/transfer and process death.
3. archive engine fixture matrix.
4. Media3 provider-backed random-read/seek experiment over SMB and one cloud/range source.
5. SMB/SFTP/WebDAV interoperability/fault matrix.
6. Google Drive least-privilege OAuth capability experiment using non-production test credentials only.
7. root service/stream behavior on isolated Magisk/KernelSU/APatch test devices.
8. 100k directory + 1M index benchmark comparing persistence/search candidates.
9. Media3 Transformer/device codec conversion matrix; only then assess FFmpeg gap.
10. adaptive/large-font/keyboard/two-pane UI interaction prototype using stable UI APIs.

## Primary authority anchors

The detailed documents contain complete source lists. Key official/current anchors include:

### Android platform/storage/background/security
- https://developer.android.com/training/data-storage
- https://developer.android.com/training/data-storage/manage-all-files
- https://developer.android.com/guide/topics/providers/document-provider
- https://developer.android.com/reference/android/provider/DocumentsContract
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/about/versions/16/behavior-changes-all
- https://developer.android.com/develop/background-work/background-tasks/uidt
- https://developer.android.com/privacy-and-security/keystore
- https://developer.android.com/privacy-and-security/security-config
- https://developer.android.com/guide/practices/page-sizes

### Android UI/media/testing
- https://developer.android.com/jetpack/androidx/releases/compose-material3
- https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
- https://developer.android.com/media/media3
- https://developer.android.com/media/media3/transformer
- https://developer.android.com/training/testing/fundamentals/strategies

### Google Play/OAuth policy
- https://support.google.com/googleplay/android-developer/answer/10467955
- https://support.google.com/googleplay/android-developer/answer/11926878
- https://support.google.com/googleplay/android-developer/answer/13392821
- https://developers.google.com/workspace/drive/api/guides/api-specific-auth

### Key library/protocol authorities
- https://commons.apache.org/proper/commons-compress/
- https://github.com/srikanth-lingala/zip4j
- https://github.com/junrar/junrar
- https://www.libarchive.org/
- https://github.com/topjohnwu/libsu
- https://github.com/hierynomus/smbj
- https://github.com/hierynomus/sshj
- https://github.com/bitfireAT/dav4jvm
- https://ffmpeg.org/legal.html
- https://github.com/arthenica/ffmpeg-kit-next

### Cloud authorities
- https://developers.google.com/workspace/drive/api/reference/rest/v3
- https://learn.microsoft.com/en-us/graph/api/resources/driveitem
- https://developers.dropbox.com/dbx-file-access-guide

## Research-pack completion scope

Documents in this directory:

- `00_RESEARCH_INDEX.md`
- `01_ANDROID_PLATFORM_STORAGE.md`
- `02_STORAGE_PROVIDER_ARCHITECTURE.md`
- `03_ROOT_ACCESS.md`
- `04_ARCHIVE_FORMATS.md`
- `05_MEDIA_PLAYBACK_FLACTIFY.md`
- `06_FILE_CONVERSION.md`
- `07_NAS_NETWORK_STORAGE.md`
- `08_CLOUD_STORAGE.md`
- `09_BACKGROUND_OPERATIONS.md`
- `10_UI_M3_EXPRESSIVE_ADAPTIVE.md`
- `11_SECURITY.md`
- `12_PERFORMANCE_AND_LIMITS.md`
- `13_TEST_STRATEGY.md`
- `14_LICENSES_DISTRIBUTION.md`
- `15_ARCHITECTURE_RECOMMENDATIONS.md`

## Scope confirmation

This campaign intentionally created **documentation only**. It did not create or select:

- Android modules/source/resources/manifest;
- Kotlin or Java application code;
- Activities or Services;
- Compose implementation;
- applicationId or namespace;
- Gradle Android configuration;
- minSdk/targetSdk/compileSdk;
- dependency declarations;
- OAuth production credentials/secrets;
- signing configuration;
- CI/CD;
- APK/AAB or proof-of-concept application code.

The architecture remains subject to user review and later prototype evidence.
