# CORE_V1 Technology Freeze V1

Status: `CORE_V1_TECHNOLOGY_FREEZE_COMPLETE_WITH_EXPLICIT_GATES`

`ARCHITECTURE-ONLY — NO PRODUCTION CODE — PRODUCTION SCAFFOLD NOT STARTED`

## Authority inputs

- Research: `b03a2ea99f24206f847f513fa4106e90268f3fc4`
- Original Architecture V1: `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`
- Current Architecture: `6ec9e1037d0fd86afebdec6bd1a5be67b008ccbb` (this branch's base)
- P05-001 `41030aa`, P05-002 `1912ee8`, P05-003 `f649f1d` (unchanged)
- P05-004 final `dd7ed4af9639de8011e6f1646c0b41eaf5e9f45c`
- Synthesis `37068be4` → `YES_WITH_EXPLICIT_GATES`
- Product identity (frozen): display name `OmniFile`, applicationId `com.omnifile`
- Official Android requirements as of 2026-09 (targetSdk 36 era, 16 KiB alignment mandatory)

## Decision matrix

| Decision | Frozen choice | Evidence | Alternatives considered | Rejected/deferred | Explicit gate | Revalidation trigger |
|---|---|---|---|---|---|---|
| minSdk policy | API 31 (Android 12), do not raise without architecture authority | Product requirement Android 12+; PoC probes used minSdk 21–31 range without issue | Higher minSdk for smaller matrix | Rejected: would cut the required Android 12 base | RELEASE: API 31/33/34/35 runtime coverage | Google Play target requirements change |
| target/compileSdk policy | Current stable at scaffold (36 at freeze time) | API 36 ps16k + Pixel 7a runs in P05 | Pinning 36 forever | Deferred: re-check at scaffold | RELEASE: 16 KiB relaunch/device evidence | New targetSdk mandate |
| JDK | Current stable toolchain at scaffold; PoC built/run on JDK 26 host | zstd host build clean on JDK 26.0.2.1 | Pinning 26 | Deferred exact pin to scaffold | RELEASE: CI toolchain lock | — |
| Kotlin | Current stable 2.x at scaffold | — (no production Kotlin yet) | — | Version pin deferred | RELEASE: language version lock + `explicitApi`-style boundaries | — |
| AGP/Gradle | Current stable at scaffold | APK packaging via Build Tools 36.1.0 proven (`zipalign -P 16`, `apksigner`) | — | Exact pin deferred | RELEASE: version catalog + lockfile | — |
| Compose/Material3 adaptive | Material3 adaptive family for UI | `UI_DESIGN_V1.md`, arch UI principles | Views | Rejected for new code: adaptive requirement | IMPLEMENTATION: adaptive navigation patterns | — |
| Navigation | Adaptive navigation family (list-detail), exact library deferred | Arch UI principles | — | Exact artifact deferred | IMPLEMENTATION | — |
| Coroutines/concurrency | Kotlin coroutines + structured concurrency; executor policy/router (not one universal executor) | P05-001 | Single universal executor | Rejected by P05-001 | RELEASE: WorkManager + media-FGS gates | — |
| Persistence/database | Deferred with boundary principles (provider-scoped identity; durable truth ≠ executor lifetime) | P05-001/P05-002, ADR-004 | Freezing Room/SQLDelight now | Deferred: no codebase exists | IMPLEMENTATION: durable schema | — |
| Durable execution | Durable records + reconcile-with-storage-truth on restart; persisted checkpoint ≠ storage truth | P05-001/P05-002; archive contract in P05-004 `10_*` | In-memory-only operations | Rejected | IMPLEMENTATION: schema + kill-restart tests | — |
| Media3 family | Provider-neutral playback boundary; exact Media3 version at scaffold | P05-003 (local WAV/FLAC/SAF; sequential seek-failure boundary) | ExoPlayer direct / platform MediaPlayer | Rejected: capability-aware boundary required | RELEASE: API31/pipe/remote/lifecycle/audio acceptance | — |
| Archive retained stack | Java-first: Commons Compress (ZIP/Zip64/TAR/compressed TARs), Zip4j (encrypted/split ZIP), Junrar technical-only for RAR4/5 read (license-gated), zstd-jni (ZSTD/TAR.ZST assist) | P05-004 `dd7ed4a` (host exact-artifact, 4 KiB + 16 KiB single-process runtime — relaunch = release gate, spool 18/18 + 13/13, containment/cleanup/cancel, hostile corpus) | libarchive native; single-engine ZIP-only | libarchive `NOT_JUSTIFIED_FOR_CORE_V1`; single-engine rejected (no RAR/ZSTD path) | IMPLEMENTATION corpus (split/solid/multipart/symlink/nested/cancel-latency); RAR license feature gate | Any fixture forcing family change; new stable versions need revalidation |
| Archive reference versions | Commons Compress 1.28.0, Zip4j 2.11.6, Junrar 8.1.1, zstd-jni 1.5.7-17 as TESTED references, not automatic production pins | P05-004 `01_ENVIRONMENT.md` hashes | Blind-freezing PoC versions | Rejected: check current stable at scaffold | RELEASE: hash-pinned SBOM + NOTICE register | Newer stable chosen → revalidate decode/matrix subset |
| Image loading/thumbnails | Deferred with boundary: capability-aware loading, no mandatory global index | Research/arch scope | Freezing Coil/Glide now | Deferred | IMPLEMENTATION | — |
| PDF/text preview | Deferred approach (no conversion stack in CORE_V1) | Arch conversion boundaries | Broad conversion stack | Rejected for CORE_V1 | — | — |
| Testing stack | Host JVM harnesses + device probes + matrix validators; hard timeouts; no intentional exhaustion | P05-001–004 runs | — | — | RELEASE: API31/device coverage, human acceptance | — |
| Dependency locking | Hash-pinned SBOM at scaffold; `.asc`/hash verification; no tag-less ambiguity without record | zstd-jni provenance lesson (`09_*`) | Floating versions | Rejected | RELEASE: lockfile + SBOM | Upstream tag/attestation changes |
| Native dependency policy | Minimize native deps; any native addition needs ELF `p_align=0x4000` static proof + `PAGE_SIZE=16384` runtime proof + provenance record | zstd-jni 16 KiB closure (`08_*`) | Native-first archive stack | Rejected for CORE_V1 | RELEASE: NDK rebuild + symbols/size/update ownership | — |
| 16 KiB acceptance policy | Final APK `zipalign -P 16` + runtime on PAGE_SIZE=16384 required for any native-bearing release | 7/7 expected outcomes on ps16k x86_64 | Static-only acceptance | Rejected: Pixel 4 KiB ≠ 16 KiB proof | RELEASE: second 16 KiB run + relaunch + ABI/device coverage | — |
| OSS license/NOTICE policy | Complete transitive NOTICE register before release; Junrar RAR enablement requires external legal approval | P05-004 license matrix; `EXTERNAL_LICENSE_REVIEW_REQUIRED` | Post-hoc notice assembly | Rejected | RELEASE: NOTICE + legal sign-off | New dependency → re-audit |

## Module architecture

`DEFERRED_WITH_BOUNDARY_PRINCIPLES`: no Gradle module graph is frozen
(no codebase exists). Boundaries frozen: capability-based storage
providers, provider-scoped identity, durable operation truth separate from
executors, archive engines separate from extraction containment policy,
provider-neutral playback.

## Kotlin namespace / applicationId

- applicationId `com.omnifile`: FROZEN (unchanged).
- Kotlin namespace: `DEFERRED_TO_PRODUCTION_SCAFFOLD` (no rationale to freeze now).

## Later features (explicitly NOT frozen, NOT blocked)

Root, SMB/SFTP/WebDAV, Drive/OneDrive/Dropbox, full indexing, duplicate
detection, broad conversion stack — no interfaces frozen except
capability/provider boundary principles that already exist.

## Production scaffold

NOT STARTED. No `settings.gradle`, app module, manifest, Kotlin source,
resources, signing, or CI was created by this campaign. Next campaign only.
