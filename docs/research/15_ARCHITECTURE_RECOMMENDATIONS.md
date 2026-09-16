# Architecture Recommendations — Pre-Implementation Synthesis

Status: COMPLETE — recommendation only; **not frozen implementation authority**  
Last verified: 2026-09-17  
Research basis: `01_ANDROID_PLATFORM_STORAGE.md` through `14_LICENSES_DISTRIBUTION.md`

## Classification legend

- **FACT** — supported by cited platform/protocol/library documentation in the underlying research documents.
- **INFERENCE** — architectural consequence derived from those facts.
- **RECOMMENDATION** — proposed direction for user review; not an implementation decision.
- **UNRESOLVED** — documentation alone is insufficient; prototype/device validation is required.

## Executive synthesis

The future File Manager should not be designed as “a UI around `java.io.File`.” The research shows that local/direct storage, SAF, root, archives, SMB, SFTP, WebDAV and first-class cloud APIs expose materially different identities, metadata, access modes, atomicity, seekability, resumability and security boundaries.

The lowest-risk conceptual architecture therefore has three central properties:

1. **capability-based storage providers with opaque provider-scoped identities**;
2. **a durable Operation Manager whose state is independent from Android background execution primitives**;
3. **streaming/bounded I/O and explicit partial/finalization semantics across every large operation**.

These are architecture recommendations, not permission to create modules, package names, interfaces, Gradle files or source code in this phase.

---

# Strong recommendations

## 1. Use a capability-based storage abstraction

**RECOMMENDATION — HIGH confidence.**

A common storage model is useful, but only for genuinely common concepts such as:
- provider/source identity;
- opaque entry identity;
- display metadata;
- list/stat-like discovery;
- sequential read;
- capability discovery.

Provider-specific guarantees must remain explicit:
- seek/random access;
- file descriptor availability;
- append/random write;
- atomic rename;
- provider-native move/copy;
- server-side copy;
- resumable transfer;
- Unix metadata/chmod/chown/symlink;
- watch/change feed;
- stable version/etag/revision.

Why:
- SAF operations are provider-advertised and URI-based, not path-equivalent.
- cloud APIs use durable object IDs and provider-specific operations.
- SMB/SFTP/WebDAV do not have identical rename/locking/range semantics.
- archive entries may be virtual/sequential.

Primary references:
- https://developer.android.com/guide/topics/providers/document-provider
- https://developer.android.com/reference/android/provider/DocumentsContract.Document
- https://developers.google.com/workspace/drive/api/reference/rest/v3/files
- https://learn.microsoft.com/en-us/graph/api/resources/driveitem
- https://developers.dropbox.com/dbx-file-access-guide

See: `01_ANDROID_PLATFORM_STORAGE.md`, `02_STORAGE_PROVIDER_ARCHITECTURE.md`.

## 2. Do not use a path string as universal identity

**RECOMMENDATION — HIGH confidence.**

Use provider-scoped opaque identity. A breadcrumb/path can be presentation metadata, but:
- SAF document URIs/IDs can change or be provider-defined;
- cloud objects have IDs independent of display path;
- rename can change a path without changing conceptual object identity;
- remote providers may have multiple parents/shared locations;
- archives have virtual paths whose outer archive identity also matters.

This reduces rename bugs, cache aliasing and cross-provider confusion.

## 3. Keep sequential, seekable and random access separate

**RECOMMENDATION — HIGH confidence.**

A `ReadHandle`-like minimum abstraction should not promise `seek()`. Seekable/random access should be a separate capability because:
- local regular files are normally seekable;
- SAF may provide a regular FD, pipe or transformed stream;
- HTTP/cloud range behavior varies;
- solid archives can make apparently random entry reads expensive.

This decision is especially important for:
- FLAC/media seeking;
- archive browsing;
- resumable transfer;
- metadata parsers.

## 4. Make the Operation Manager durable and executor-independent

**RECOMMENDATION — VERY HIGH confidence.**

Do not make WorkManager, a Service, UIDT or a coroutine itself the durable operation model.

Persist operation intent/state independently, then execute it using the Android mechanism appropriate to the workload/version:
- UIDT candidate for explicit user-initiated network transfer on API 34+;
- WorkManager for appropriate deferrable/retryable work;
- foreground-service types only where platform/policy use cases match;
- `mediaProcessing` candidate for conversion with Android 15+ time limits respected.

Why:
- Android 12 restricts background FGS starts;
- Android 15 time-limits important FGS types;
- Android 16 changes job-quota accounting;
- system/user can stop work/process without a convenient final callback.

Primary references:
- https://developer.android.com/develop/background-work/background-tasks/uidt
- https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
- https://developer.android.com/about/versions/15/changes/foreground-service-types
- https://developer.android.com/about/versions/16/behavior-changes-all

See: `09_BACKGROUND_OPERATIONS.md`.

## 5. Define copy/move as recoverable plans, not single methods

**RECOMMENDATION — VERY HIGH confidence.**

A robust cross-provider move is conceptually:

`create partial destination -> stream/copy -> verify -> finalize destination -> delete source`

Source deletion must remain the final irreversible step.

Same-provider native/atomic move can be used when the provider advertises and proves that capability. Never infer atomicity from a method named “move.”

This recommendation directly protects against:
- process death;
- network disconnect;
- storage full;
- cloud conflict;
- provider crash;
- user cancellation.

## 6. Stream large data with bounded memory

**RECOMMENDATION — VERY HIGH confidence.**

All large-file paths — local, root, archive, NAS, cloud, conversion — should have memory usage approximately independent of total object size.

Do not:
- read entire files into byte arrays;
- base64 large root streams through textual commands;
- stage full archives in memory;
- materialize a 100 GB copy plan as all file bytes/data.

Use:
- bounded buffers;
- backpressure;
- 64-bit size/offset/progress counters;
- checkpointing where resumable.

## 7. Keep root strictly additive

**RECOMMENDATION — VERY HIGH confidence.**

Normal file-manager functionality must remain usable when:
- no root manager is installed;
- root authorization is denied;
- authorization is revoked;
- privileged service crashes;
- SELinux/mount policy blocks an individual operation.

Root should be an optional provider/capability boundary, not a global app mode.

Prototype libsu first because it provides structured Android root APIs/root services/NIO-style filesystem functionality, but do not freeze it before device validation.

Reference:
- https://github.com/topjohnwu/libsu

See: `03_ROOT_ACCESS.md`.

## 8. Prefer structured privileged file APIs over shell command construction

**RECOMMENDATION — VERY HIGH security confidence.**

Arbitrary filenames can contain shell metacharacters, quotes, newlines and option-like prefixes. Avoid using shell strings as the normal privileged file transport.

Where shell commands are unavoidable, use a narrow separately reviewed transport/escaping layer and never mix binary content with a delimiter-framed textual control channel.

## 9. Treat archives as virtual storage, not only “extract commands”

**RECOMMENDATION — HIGH confidence.**

Archive browsing should expose virtual entries and per-entry reads without whole extraction where format/engine permits.

Likely Java-first candidate family:
- Commons Compress for broad TAR/compressor/CPIO and general archive support;
- Zip4j for encrypted/split ZIP;
- Junrar for RAR/RAR5 extraction, subject to license/security validation.

libarchive remains a strong alternative where broad native coverage/performance outweighs JNI/16 KB/security cost.

References:
- https://commons.apache.org/proper/commons-compress/
- https://github.com/srikanth-lingala/zip4j
- https://github.com/junrar/junrar
- https://www.libarchive.org/

See: `04_ARCHIVE_FORMATS.md`.

## 10. Make safe archive extraction a baseline architecture property

**RECOMMENDATION — VERY HIGH security confidence.**

Enforce:
- path containment;
- explicit symlink policy;
- file-count limits;
- expanded-byte limits;
- nesting limits;
- storage-space handling;
- cancellation;
- malformed input handling;
- explicit overwrite/conflict policy.

Reference:
- https://developer.android.com/privacy-and-security/risks/zip-path-traversal

## 11. Retain Media3/MediaSession concepts, not FLACtify's monolith

**RECOMMENDATION — HIGH confidence.**

From FLACtify, preserve the useful behavioral architecture:
- `MediaSessionService` owns player/session;
- a controller/gateway reconnects UI to playback;
- metadata/artwork/player behavior remains first-class.

Do **not** transplant the current `PlayerViewModel` wholesale. It currently combines playback, scan/cache, metadata, playlists/favorites, tag editing, recommendations, statistics and UI-facing state.

Future playback should resolve provider-scoped media identity into a Media3-readable source rather than requiring local paths.

References:
- https://developer.android.com/media/media3/session/background-playback
- https://developer.android.com/media/media3/exoplayer

See: `05_MEDIA_PLAYBACK_FLACTIFY.md`.

## 12. Separate stream cache from offline files

**RECOMMENDATION — HIGH confidence.**

- stream/block cache: evictable performance optimization;
- offline pin/download: user-requested durable content;
- metadata cache: reconstructible metadata;
- thumbnail/artwork cache: bounded image data;
- operation partial: recovery state/output.

A generic “clear cache” must not silently destroy offline content or recovery state.

## 13. Use first-class cloud APIs where advanced features matter, with SAF fallback

**RECOMMENDATION — HIGH confidence.**

SAF remains valuable for generic provider access, but first-class Drive/OneDrive/Dropbox providers preserve capabilities SAF cannot express consistently:
- resumable upload sessions;
- provider-native search;
- change/delta feeds;
- version/conflict tokens;
- shared/team-drive semantics;
- server-side copy/move;
- controlled range download.

References:
- https://developers.google.com/workspace/drive/api/reference/rest/v3
- https://learn.microsoft.com/en-us/graph/api/resources/driveitem
- https://www.dropbox.com/developers/documentation/http/documentation

See: `08_CLOUD_STORAGE.md`.

## 14. Protect provider credentials as security assets

**RECOMMENDATION — VERY HIGH confidence.**

- use standard OAuth/public-client flows;
- never treat APK-embedded values as confidential client secrets;
- protect refresh tokens/passwords/private keys through a Keystore-backed design;
- strict TLS validation;
- strict SSH host-key verification;
- redact tokens, signed URLs and sensitive paths from logs.

References:
- https://developer.android.com/privacy-and-security/keystore
- https://developer.android.com/privacy-and-security/security-ssl

## 15. Prefer stable Material3/Adaptive as the UI baseline

**RECOMMENDATION — HIGH confidence.**

As of 2026-09-09:
- Material3 stable 1.4.0;
- Material3 Adaptive stable 1.3.0;
- newer expressive/adaptive lines still include alpha APIs.

The requested phone/tablet/foldable/two-pane baseline does not require adopting alpha solely for visual branding. Stable APIs can implement dynamic color, adaptive navigation/panes, edge-to-edge and modern Compose UI; newer expressive motion can be isolated and reconsidered when stable.

References:
- https://developer.android.com/jetpack/androidx/releases/compose-material3
- https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive

See: `10_UI_M3_EXPRESSIVE_ADAPTIVE.md`.

## 16. Design Android 16 behavior in from the start without dropping Android 12

**RECOMMENDATION — HIGH confidence.**

Android 16 is the primary environment, but API 31 remains a compatibility requirement.

Important Android 16 implications include:
- target-36 edge-to-edge behavior;
- predictive back;
- background/job quota changes;
- native 16 KB page-size compatibility;
- evolving local-network permission behavior.

References:
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/about/versions/16/behavior-changes-all
- https://developer.android.com/guide/practices/page-sizes

## 17. Keep basic browsing independent from a global index

**RECOMMENDATION — HIGH confidence.**

The app should still browse sources when its search index is:
- absent;
- stale;
- rebuilding;
- corrupt and being reconstructed.

A durable database is likely needed for operation/provider/index state, but Room, raw/AndroidX SQLite, SQLDelight and AppSearch remain unfrozen candidates.

References:
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/develop/ui/views/search/appsearch
- https://www.sqlite.org/fts5.html

See: `12_PERFORMANCE_AND_LIMITS.md`.

## 18. Build security tests and hostile fixtures alongside features

**RECOMMENDATION — VERY HIGH confidence.**

Do not defer security to a final pass. Each provider/parser/operation feature should gain:
- arbitrary filename tests;
- permission-denied tests;
- malformed input;
- process-death points;
- provider disconnect/conflict;
- log-redaction tests;
- symlink/path-containment tests where applicable.

See: `11_SECURITY.md`, `13_TEST_STRATEGY.md`.

---

# Plausible options

The following have more than one reasonable path and should remain open.

## Archive engine strategy

### Option A — Java-first layered stack

Possible candidates:
- Commons Compress;
- Zip4j;
- Junrar.

Advantages:
- no JNI for core archive support;
- lower 16 KB/native crash integration risk;
- licenses are mostly straightforward except Junrar/UnRAR terms.

Costs:
- multiple libraries;
- capability/behavior differences;
- RAR/7z edge cases still need fixtures.

### Option B — native libarchive-heavy stack

Advantages:
- broad unified format support;
- mature streaming model.

Costs:
- native memory-safety/patching surface;
- JNI/ABI build ownership;
- 16 KB page-size gate;
- still does not eliminate every encryption/creation limitation.

**Current direction:** prototype Java-first first; retain libarchive as a serious alternative, not reject it.

## Conversion engine strategy

### Option A — platform/Media3 first

- Media3 Transformer/MediaCodec for supported audio/video paths;
- platform codecs for images.

Advantages: official integration, hardware acceleration, lower binary/license burden.

### Option B — optional FFmpeg for gaps

Use only where format breadth/remux/codec requirements are not adequately covered by platform APIs.

Costs: native build, binary size, security updates, LGPL/GPL configuration and codec-license review.

**Current direction:** Media3/platform first; FFmpeg remains optional and must be source-built from a maintained integration if chosen.

## Persistence/indexing

Reasonable candidates:
- Room over SQLite;
- AndroidX/raw SQLite;
- SQLDelight;
- SQLite + FTS5 for search;
- AppSearch for a dedicated search index.

A mixed design may be appropriate: transactional relational store for operations/accounts plus specialized search index. No choice should be frozen before the durable schema/query workload is specified and benchmarked.

## SMB/SFTP/WebDAV libraries

Leading prototype candidates:
- SMBJ for SMB2/3;
- SSHJ for SFTP;
- dav4jvm for WebDAV.

Alternatives:
- jcifs-ng for SMB compatibility comparison;
- Apache MINA SSHD for SSH/SFTP.

Selection should follow interoperability and fault tests rather than API aesthetics.

## Root transport

libsu root service/NIO is the strongest current candidate, but a final root architecture remains dependent on:
- Magisk;
- KernelSU;
- APatch;
- SELinux;
- mount namespaces;
- descriptor/stream behavior.

## Cloud SDK vs direct REST

For each provider, either official SDK/client or direct HTTP may be reasonable. Criteria:
- Android/JVM compatibility;
- binary/dependency weight;
- auth integration;
- range/resumable control;
- update cadence;
- ability to access latest API features.

Dropbox is a concrete example: current Java SDK compatibility changed in 2026, so “always latest SDK” is not automatically equivalent to “best Android choice.”

## Metadata library

FLACtify's jaudiotagger behavior is useful, but dependency reuse is not automatic. The 3.0.1 line, its LGPL terms, Android behavior and maintained alternatives require a dedicated prototype/security/license review.

---

# Decisions to postpone

The following should **not** be frozen by this research campaign:

- applicationId;
- Kotlin package / namespace;
- module structure;
- Gradle architecture;
- minSdk;
- targetSdk;
- compileSdk;
- AndroidManifest contents;
- exact permissions;
- dependency versions;
- final archive engine combination;
- final root library/version;
- final persistence/database technology;
- cloud SDK vs direct REST choices;
- exact OAuth scopes/client registrations;
- final background executor mapping for every operation;
- FFmpeg inclusion/configuration;
- exact Media3 version;
- exact Compose/Material versions;
- metadata/tagging library;
- image loader/cache library;
- signing/release architecture;
- CI/CD;
- public/private distribution channel.

### Distribution fact, not frozen decision

As of 2026-08-31, Google Play requires new mobile apps/app updates to target Android 16 / API 36 or higher. This matters if public Play distribution is pursued, but does not authorize setting `targetSdk` in this phase.

Source:
- https://support.google.com/googleplay/android-developer/answer/11926878

---

# Prototype-required questions

Documentation alone is insufficient for these questions.

## Storage / SAF

1. Which representative SAF providers yield regular seekable FDs vs pipe-like streams?
2. How does rename change URI identity across local, removable and cloud document providers?
3. Which write/append modes actually work across providers?
4. Can lower-level cursor/query APIs enumerate 100k SAF entries acceptably?
5. What happens during SD/USB disconnect mid-I/O on Android 12 and Android 16?

## All-files access / protected storage

6. Exact Android 16 OEM/device UX and behavior for `MANAGE_EXTERNAL_STORAGE`.
7. Protected `Android/data` / `Android/obb` behavior across standard mode and supported root setups.
8. FAT32/exFAT/removable-volume limits and rename/finalization behavior.

## Root

9. Magisk/KernelSU/APatch authorization and persistent service behavior.
10. SELinux and mount-namespace differences between app/root processes.
11. Safe large-file root streaming.
12. Whether useful FD passing can support player/archive operations safely.

## Archives

13. Commons Compress + Zip4j + Junrar coverage against full fixture matrix.
14. Encrypted/split ZIP and encrypted/multipart/solid RAR correctness.
15. 7z solid/random-entry performance.
16. 100k-entry archive memory/first-list latency.
17. libarchive JNI/16 KB/performance/security benefit if Java stack has gaps.

## Playback

18. Media3 seek over custom SMB/SFTP/WebDAV/cloud random-read adapters.
19. Remote FLAC start/seek latency under realistic Wi-Fi and interruption.
20. SAF non-seekable content playback behavior.
21. Block-cache size/key/version strategy.
22. Tag editing on SAF/remote files without unsafe whole-file assumptions.

## Conversion

23. Media3 Transformer actual device format/codec matrix across Android 12–16.
24. HDR/HEVC/AV1 and thermal behavior.
25. Exact audio conversions that truly require FFmpeg.
26. If FFmpeg is needed: reproducible LGPL-only source build + 16 KB compatibility.
27. image EXIF/ICC/HDR/alpha preservation behavior.

## Network

28. SMBJ compatibility across Windows, Samba and consumer NAS.
29. signing/encryption negotiation and reconnect/durable-handle behavior.
30. SSHJ offset read/write performance and host-key workflows.
31. WebDAV Range/ETag/MOVE/COPY compatibility across common servers.
32. Android local-network permission/discovery behavior as Android 16/17 policy evolves.

## Cloud

33. Narrowest Google Drive scope that can support intended first-class file-manager UX.
34. 100 GB resumable upload/download recovery for Drive/OneDrive/Dropbox.
35. provider throttling and pagination under large indexing.
36. media seeking using range/download URL APIs.
37. conflict behavior when remote object changes during operation.

## Background operations

38. Best execution mechanism for a very long local-only copy on API 31–36.
39. UIDT stop/process-death reconciliation.
40. Android 15/16 FGS timeout/job-quota behavior under real 100 GB transfer/transcode.
41. checkpoint frequency vs write amplification/lost-progress window.

## Performance/persistence

42. 100k directory memory/latency across providers.
43. 1M+ index performance and storage footprint across candidate persistence/search designs.
44. thumbnail queue/cache memory under rapid scrolling.
45. provider-specific concurrency limits for tiny-file workload.

## UI

46. stable-only implementation vs optional newer expressive motion for signature transitions.
47. two-pane file manager behavior on foldables/small landscape windows.
48. large font/keyboard/mouse selection behavior with dense file lists.

---

# High-risk areas

## Risk 1 — pretending storage backends share filesystem semantics

Severity: **Critical**  
Failure mode: data loss, broken resume, incorrect UI capability, impossible remote playback/archive behavior.  
Mitigation: capability model + provider contract tests.

## Risk 2 — long-running operation lifecycle

Severity: **Critical**  
Failure mode: 100 GB transfer disappears/corrupts after process death, Android quota/FGS changes break operation.  
Mitigation: durable Operation Manager independent from executor; partial/finalize/reconcile semantics.

## Risk 3 — root privilege boundary

Severity: **Critical**  
Failure mode: shell injection or destructive action damages protected system/user data.  
Mitigation: optional provider, structured privileged APIs, adversarial filenames, stronger confirmation, isolated tests.

## Risk 4 — hostile archive/native parsers

Severity: **High/Critical**  
Failure mode: traversal overwrite, decompression disk exhaustion, parser crash/native vulnerability.  
Mitigation: containment/limits, maintained libraries, malformed corpus, Java-first evaluation, rapid security updates.

## Risk 5 — cloud OAuth/policy scope

Severity: **High**  
Failure mode: desired first-class browsing requires restricted scopes/verification not anticipated.  
Mitigation: prototype narrow scopes before product promise; SAF fallback.

## Risk 6 — remote playback random access

Severity: **High**  
Failure mode: media starts but seek is unusable; full download required unexpectedly.  
Mitigation: provider random/range-read contract + Media3 prototype + block cache.

## Risk 7 — FFmpeg/native licensing and packaging

Severity: **High**  
Failure mode: accidental GPL build, abandoned binary, non-16 KB native `.so`, large attack surface.  
Mitigation: platform/Media3 first; source-reproducible audited build only if needed.

## Risk 8 — massive directories/indexes

Severity: **High**  
Failure mode: OOM/UI freezes, huge recomposition/thumbnail storms, indexing blocks browsing.  
Mitigation: lazy/incremental rendering, backpressure, optional reconstructible index, 100k/1M benchmarks.

## Risk 9 — credentials/certificates/host keys

Severity: **Critical security**  
Failure mode: NAS/cloud compromise or token leakage.  
Mitigation: Keystore-backed secrets, strict TLS/SSH trust, log redaction, least privilege.

## Risk 10 — dependency inheritance from FLACtify

Severity: **Medium/High**  
Failure mode: old versions/current coupling become accidental architecture.  
Mitigation: reuse behavior and selected concepts only; dependency/module decisions require fresh review.

---

# Suggested implementation order

This is a staged future sequence only. **No implementation is started or authorized by this document.**

## Stage 0 — user architecture review and explicit freezes

Review this research pack and decide only the invariants the user wants to freeze. Leave SDK/dependency versions until their implementation stage.

## Stage 1 — storage semantics contract + test fakes

Future work:
- provider-scoped identity model;
- capability vocabulary;
- sequential/seekable/random handle semantics;
- structured error taxonomy;
- fake providers and provider contract tests.

Why first: every file operation, archive, player, NAS/cloud feature depends on these semantics.

## Stage 2 — standard local/SAF browsing core

Future work:
- direct/shared storage within chosen Android access strategy;
- SAF provider;
- removable-source lifecycle;
- basic list/stat/read/write/rename/delete based on capabilities;
- no root dependency.

Validate API 31 and 36 early.

## Stage 3 — durable Operation Manager

Before broad file mutations:
- persistent operation state;
- copy/move/delete plans;
- partial/finalization;
- pause/resume/cancel capability;
- process-death reconciliation;
- storage-full behavior;
- Android executor strategy prototypes.

## Stage 4 — performance/search foundation

- 10k/100k directory measurements;
- choose compact listing/paging behavior;
- evaluate persistence/index candidates with 1M fixture;
- thumbnail/metadata backpressure.

Choose database/search stack only after this evidence.

## Stage 5 — archives

- virtual archive provider;
- secure extraction limits;
- ZIP/TAR/compressor baseline;
- encrypted/split ZIP;
- RAR/RAR5 fixture validation;
- archive operation integration.

## Stage 6 — playback extraction/refactor

- MediaSession service boundary;
- provider-neutral media source resolver;
- local/SAF player first;
- metadata/artwork/user-library state separated from giant ViewModel;
- cache/offline distinction.

## Stage 7 — NAS providers

In order of practical value, likely SMB first, then SFTP/WebDAV subject to user priorities:
- credentials/trust;
- provider adapters;
- fault/reconnect tests;
- large transfer;
- media seek prototype.

No protocol order is frozen here.

## Stage 8 — first-class cloud

For each provider independently:
- OAuth least-privilege prototype;
- list/search/range;
- resumable upload/download;
- conflict/version handling;
- operation integration;
- optional media streaming.

## Stage 9 — optional root

Only after non-root core is mature:
- root authorization/service prototype;
- isolated root provider;
- POSIX metadata/chmod/chown/symlink;
- protected-path warnings;
- Magisk/KernelSU/APatch matrix.

## Stage 10 — conversion

- platform image conversion;
- Media3/MediaCodec audio/video paths;
- archive conversion;
- decide whether remaining requirements justify FFmpeg.

High-fidelity local Office-to-PDF stays unsupported unless a later independent proof changes the evidence.

## Stage 11 — hardening/adaptive polish/release policy

- full security corpus;
- Android 12–16 device matrix;
- tablets/foldables/keyboard/mouse/accessibility;
- performance budgets;
- dependency/license audit;
- Play/OAuth policy review;
- only then release architecture decisions.

---

# Dependency candidate matrix

No candidate below is selected by this table. “Preferred” means **preferred for future prototype/review**, not approved dependency authority.

| Capability | Preferred candidate | Alternative | Why | License | Confidence | Prototype needed |
|---|---|---|---|---|---|---|
| Android storage/direct | Android platform `ContentResolver`, `DocumentsContract`, `android.system.Os` as appropriate | AndroidX `DocumentFile` convenience adapter | platform semantics/capability detail | platform / AndroidX Apache-2.0 | High | Yes, SAF/device semantics |
| SAF convenience | lower-level DocumentsContract + optional DocumentFile adapter | DocumentFile-centric implementation | lower level exposes flags/control | AndroidX Apache-2.0 | High | Yes |
| Root | libsu | custom privileged service/shell transport | Android-focused root service/NIO APIs | Apache-2.0 | Med-High | **Yes** |
| ZIP encrypted/split | Zip4j | Commons Compress where supported | focused AES/split/ZIP64 support | Apache-2.0 | High | Yes fixtures |
| TAR/compressors/CPIO | Commons Compress | libarchive | broad pure-Java coverage | Apache-2.0 | High | Yes |
| 7z | Commons Compress initially | libarchive | avoid native first; validate solid/encrypted needs | Apache-2.0 / BSD | Medium | **Yes** |
| RAR/RAR5 extraction | Junrar | libarchive | Java candidate now has active RAR5 work | UnRAR-derived non-standard / BSD | Medium | **Yes** |
| broad native archive | libarchive | Java layered stack | broad mature native parser | New BSD | Medium | **Yes**, 16 KB/security |
| Playback | Media3 ExoPlayer + MediaSession | none preferred | official Android media stack | Apache-2.0 | High | remote adapters yes |
| Mainstream video conversion | Media3 Transformer/MediaCodec | FFmpeg | official/hardware path | Apache-2.0 | Med-High | **Yes**, device matrix |
| Broad media conversion | optional FFmpeg/FFmpegKitNext source build | platform/Media3 only | only if format gaps justify native stack | LGPL/GPL depending build | Medium | **Yes** |
| Image conversion | Android platform codecs | later audited external codec | lower dependency/native burden | platform | Med-High | HDR/metadata yes |
| Image loading/thumbnails | Coil candidate | custom/platform loader | modern bounded image loading/cache ecosystem | Apache-2.0 | Medium | Yes at scale |
| Audio tags | unresolved; evaluate jaudiotagger behavior | other maintained parser/editor | FLACtify reference exists but Android/maintenance/license need review | LGPL for JThink line | Low-Med | **Yes** |
| SMB2/3 | SMBJ | jcifs-ng | modern pure-Java SMB2/3 candidate, permissive | Apache-2.0 / LGPL-2.1 | Med-High | **Yes** interoperability |
| SFTP | SSHJ | Apache MINA SSHD | focused maintained client, Apache license | Apache-2.0 | Med-High | **Yes** |
| WebDAV | dav4jvm | direct OkHttp/WebDAV implementation or older alternatives | actively maintained DAV library | MPL-2.0 | Medium | **Yes** server matrix |
| Google Drive | Drive REST API + current official auth/client approach | SAF Drive provider | advanced capabilities/IDs/resume | client Apache-2.0; API terms separate | High concept | **Yes** scopes |
| OneDrive | Microsoft Graph + MSAL | SAF document provider | advanced item/delta/resume semantics | MSAL MIT; API terms separate | High concept | **Yes** account variants |
| Dropbox | Dropbox API; SDK vs REST unresolved | SAF provider | resumable/cursor/version APIs | official Java SDK MIT | High concept | **Yes** SDK/JVM/range |
| Durable relational persistence | Room/SQLite family to benchmark | SQLDelight/direct SQLite | mature transactional storage | Apache-2.0 | Medium | **Yes**, schema/load |
| Search index | SQLite FTS or AppSearch candidate | provider-native search + relational metadata | different strengths; AppSearch maturity must be rechecked | AndroidX/SQLite terms | Medium | **Yes**, 1M entries |
| Adaptive UI | stable Material3 + Material3 Adaptive | isolated future experimental expressive APIs | stable phone/tablet/two-pane baseline exists | Apache-2.0 | High | UI prototype yes |
| Persistent deferrable work | WorkManager where workload fits | direct JobScheduler | official persistent scheduler abstraction | Apache-2.0/platform | High concept | workload-specific |
| User-initiated network transfer | UIDT on API 34+ | compatible API31-33 strategy / appropriate FGS | intended platform mechanism for user transfer | platform | High concept | **Yes** lifecycle |

---

# Conceptual architecture diagram

This diagram shows likely **boundaries**, not Gradle modules, package names or fixed interfaces.

```text
┌──────────────────────────────────────────────────────────────────────┐
│                              UI                                      │
│ Home / Files / Search / Archive / Player / Operations / Settings    │
│ stable Material3 + adaptive layout; capability-aware actions         │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ commands + observable state
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    Application / Feature Coordination                 │
│ navigation state • selection • search orchestration • player state   │
└───────────────┬──────────────────────────┬───────────────────────────┘
                │                          │
                ▼                          ▼
┌─────────────────────────────┐  ┌─────────────────────────────────────┐
│ Durable Operation Manager   │  │ Playback / Media Source Resolver    │
│ copy/move/delete/upload/... │  │ MediaSession / Media3               │
│ progress/checkpoint/retry   │  │ local/SAF/NAS/cloud + block cache   │
│ partial/finalize/reconcile  │  └──────────────────┬──────────────────┘
└───────────────┬─────────────┘                     │ provider reads
                │ plans/handles                     │
                ▼                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│                 Capability-Based Storage Abstraction                 │
│ provider-scoped identity • metadata • list • handles • capabilities │
│ sequential / seekable / random • native/server operations           │
└──────┬────────┬────────┬─────────┬──────────┬──────────┬─────────────┘
       │        │        │         │          │          │
       ▼        ▼        ▼         ▼          ▼          ▼
   ┌──────┐ ┌──────┐ ┌───────┐ ┌──────┐ ┌────────┐ ┌─────────────┐
   │Local │ │ SAF  │ │ Root  │ │Archive│ │ NAS    │ │ Cloud       │
   │direct│ │ URI  │ │optional│ │virtual│ │SMB/... │ │Drive/...    │
   └──────┘ └──────┘ └───────┘ └──────┘ └────────┘ └─────────────┘
                │                    │          │          │
                └─────────┬──────────┴──────────┴──────────┘
                          ▼
┌──────────────────────────────────────────────────────────────────────┐
│              Cache / Metadata / Search / Persistence                 │
│ operation state • provider config refs • metadata index              │
│ thumbnails/artwork • stream blocks • user-offline state separated   │
└──────────────────────────────────────────────────────────────────────┘

Separate conversion pipeline:

Storage source -> Conversion Engine (Media3/platform/[optional FFmpeg])
               -> Operation Manager partial/finalize
               -> Storage destination

Android execution mechanisms are below/around Operation Manager,
not the durable model itself:
UIDT / WorkManager / appropriate foreground service / foreground app work.
```

## Diagram boundary notes

- “Storage Abstraction” does **not** imply every provider implements every operation.
- “Persistence” is a role, not a Room decision.
- “Archive” can use storage handles and expose virtual entries; it is not necessarily a normal writable provider.
- “Root” is optional and isolated.
- playback reads through provider capabilities, not through a hard-coded path API.
- conversion writes through Operation Manager so partial/final states match copy/upload/extract semantics.
- Android scheduler/service components execute durable work but do not define operation truth.

---

# Cross-cutting security architecture

All future components should obey these invariants:

1. names/paths/URIs/server metadata are untrusted;
2. credentials/tokens are not UI state strings or log data;
3. no trust-all TLS or SSH behavior;
4. root elevation is explicit and optional;
5. recursive/destructive operations have explicit symlink policy;
6. archive extraction verifies destination containment and limits expansion;
7. operation completion occurs only after destination finalization;
8. source deletion occurs only after a move destination is finalized/verified;
9. caches/temp data are private and separately classified;
10. “delete” is not marketed as guaranteed secure flash erasure.

See: `11_SECURITY.md`.

---

# Cross-cutting performance architecture

1. lazy/incremental directory UI;
2. stable entry identity keys;
3. bounded metadata/thumbnail queues;
4. explicit I/O/CPU concurrency controls per workload/provider;
5. 64-bit length/offset/progress;
6. browsing does not wait for global indexing;
7. indexes are reconstructible accelerators, not mutation authority;
8. 100k directory and 1M-index fixtures are acceptance benchmarks;
9. remote/random access uses range/block caching where justified;
10. large-file memory stays bounded.

See: `12_PERFORMANCE_AND_LIMITS.md`.

---

# Current distribution constraints that architecture should preserve

These are facts to accommodate, not frozen release decisions.

## All-files access

A general-purpose file manager is an eligible core use case for Google Play `MANAGE_EXTERNAL_STORAGE`, but the permission is restricted and subject to declaration/review.

Sources:
- https://developer.android.com/training/data-storage/manage-all-files
- https://support.google.com/googleplay/android-developer/answer/10467955

## Target API

As of 2026-08-31, Play submissions for new mobile apps/updates require Android 16 / API 36+ under the current policy.

Source:
- https://support.google.com/googleplay/android-developer/answer/11926878

## Drive OAuth

Broad Drive scopes can be restricted and require verification/security obligations; use the narrowest viable scope.

Source:
- https://developers.google.com/workspace/drive/api/guides/api-specific-auth

## Foreground/background work

FGS declarations, UIDT and Android 15/16 lifecycle constraints affect the architecture of long operations.

Sources:
- https://support.google.com/googleplay/android-developer/answer/13392821
- https://developer.android.com/develop/background-work/background-tasks/uidt

---

# User-review gates before implementation

Before Android implementation starts, explicitly review at least:

1. whether the capability-provider + durable-operation principles are accepted;
2. desired non-root storage scope and Play-distribution intent;
3. which future providers are V1 vs later (NAS/cloud/root);
4. which archive format/creation promises belong to V1;
5. whether integrated conversion is V1 or later;
6. playback feature scope inherited from FLACtify vs intentionally omitted;
7. indexing/search product requirements;
8. first proof-of-concept campaign priorities;
9. only after that: SDK/package/module/dependency freezes.

No source code or Android project scaffold should be created merely because these recommendations exist.

---

# Final recommendation status

### Strongly supported, low architectural regret

- capability-based providers;
- opaque provider identity;
- separate sequential/seekable/random handles;
- durable Operation Manager independent from executor;
- streaming/bounded I/O;
- explicit partial/finalization/recovery;
- non-root baseline with additive root;
- security-first archive/root/network boundaries;
- Media3 session architecture with provider-neutral source resolution;
- first-class cloud APIs alongside SAF fallback;
- stable-first Material3/Adaptive;
- Android 12–16 compatibility testing;
- hostile/fault/process-death fixture strategy.

### Reasonable but not yet settled

- exact archive engine combination;
- persistence/search technology;
- SMB/SFTP/WebDAV libraries;
- root implementation library;
- cloud SDK vs REST;
- metadata/tag library;
- optional FFmpeg;
- cache architecture details.

### Must remain unresolved until prototype evidence

- remote media seek quality;
- SAF FD/seek behavior across providers;
- root environment consistency;
- 100 GB lifecycle/executor behavior;
- archive edge-case coverage/performance;
- 100k directory / 1M index performance;
- narrow OAuth scopes for full cloud UX;
- codec/HDR/transcode behavior across real devices.

**Research conclusion:** the project now has enough evidence to review architectural principles without prematurely freezing Android implementation details. The next step should be user review and a separately authorized prototype/architecture-freeze phase, not silent application implementation.
