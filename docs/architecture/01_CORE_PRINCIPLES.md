# Architecture V1 — Core Principles

Status: PRINCIPLE REVIEW COMPLETE
Source authority: `docs/research/` at `b03a2ea99f24206f847f513fa4106e90268f3fc4`
Scope: architecture principles and boundaries only; no implementation technology is selected here.

## Decision-state legend

Every material decision in this architecture set uses exactly one state:

- `ACCEPTED` — low-regret Architecture V1 principle.
- `REJECTED` — prohibited Architecture V1 assumption.
- `POC_REQUIRED` — evidence from a later prototype/device/provider campaign is required.
- `DEFERRED` — intentionally postponed until a named later milestone.

## Reviewed candidate principles

### A. Normal/non-root operation is independently functional — `ACCEPTED`

The application must remain useful when no root manager exists, authorization is denied/revoked, a privileged service fails, or SELinux/mount policy blocks an individual privileged operation. Root is additive and isolated; it is never a hidden prerequisite for ordinary browsing, SAF, removable sources, archives, network/cloud access, playback, or conversion where normal Android APIs permit them.

Evidence: `01_ANDROID_PLATFORM_STORAGE.md`, `03_ROOT_ACCESS.md`, `11_SECURITY.md`, `15_ARCHITECTURE_RECOMMENDATIONS.md`.

### B. Universal filesystem-path / `java.io.File` modeling — `REJECTED`

The application must not pretend that local/direct, SAF, root, archive, NAS, or cloud entries share one real filesystem path model. A path may exist as provider-specific metadata, but it is not universal identity and it is not required for every source.

Replacement: provider-scoped identity plus explicit capabilities.

Evidence: `01_ANDROID_PLATFORM_STORAGE.md`, `02_STORAGE_PROVIDER_ARCHITECTURE.md`.

### C. Storage behavior is capability-based — `ACCEPTED`

The architecture advertises what the current provider/entry can actually do and with what guarantee. It must not invent unsupported seek, random access, append/random write, POSIX metadata, atomic rename, server-side move/copy, resumability, watch/change feed, or stable version semantics.

Capabilities may be parameterized rather than reduced to booleans when guarantees matter.

Evidence: `01_ANDROID_PLATFORM_STORAGE.md`, `02_STORAGE_PROVIDER_ARCHITECTURE.md`, `07_NAS_NETWORK_STORAGE.md`, `08_CLOUD_STORAGE.md`.

### D. Provider-scoped opaque identity is separate from display path/name — `ACCEPTED`

An entry identity is scoped to its provider/source and remains conceptually separate from user-facing display name and breadcrumb/path metadata. Display text must not be used as destructive-operation authority.

Evidence: SAF document IDs/URIs, cloud object IDs, remote/shared semantics, and archive virtual-entry identity in `01`, `02`, `08`, `12`, and `15` research documents.

### E. Sequential, seekable, and random-access reads are distinct semantic capabilities — `ACCEPTED`

The minimum read semantic is bounded sequential streaming. Seekability and random-offset access are separate capabilities because SAF descriptors may be pipe-like, HTTP/range behavior varies, and compressed/solid archives may make random entry access expensive or impossible.

Evidence: `01_ANDROID_PLATFORM_STORAGE.md`, `02_STORAGE_PROVIDER_ARCHITECTURE.md`, `04_ARCHIVE_FORMATS.md`, `05_MEDIA_PLAYBACK_FLACTIFY.md`.

### F. Long-running operations have durable state independent from Android executor lifetime — `ACCEPTED`

Operation intent, checkpoints, reconciliation state, partial destination identity, and irreversible boundaries must outlive any particular coroutine, WorkManager worker, foreground service, UIDT job, or process lifetime.

`WorkManager`, UIDT, foreground-service mechanisms, and foreground coroutines are execution mechanisms, not operation truth.

Evidence: `09_BACKGROUND_OPERATIONS.md`, `13_TEST_STRATEGY.md`, `15_ARCHITECTURE_RECOMMENDATIONS.md`.

### G. Large data flows use bounded streaming and 64-bit size/progress semantics — `ACCEPTED`

Large-file memory use must remain approximately independent of total file size. Whole-file byte-array assumptions are prohibited for large inputs. Lengths, offsets, and progress use 64-bit-capable semantics.

Evidence: `01_ANDROID_PLATFORM_STORAGE.md`, `03_ROOT_ACCESS.md`, `06_FILE_CONVERSION.md`, `09_BACKGROUND_OPERATIONS.md`, `12_PERFORMANCE_AND_LIMITS.md`.

### H. Cross-provider move deletes source only after destination verification/finalization — `ACCEPTED`

The safe conceptual sequence is:

`create partial destination -> transfer/process -> verify -> finalize destination -> delete source only for move`

Provider-native atomic/same-provider move may replace this plan only when the provider explicitly exposes a suitable guarantee. Ending a write stream successfully is not sufficient evidence to delete the source.

Evidence: `02_STORAGE_PROVIDER_ARCHITECTURE.md`, `09_BACKGROUND_OPERATIONS.md`, `11_SECURITY.md`, `15_ARCHITECTURE_RECOMMENDATIONS.md`.

### I. Indexes, metadata caches, and thumbnails are reconstructible acceleration layers — `ACCEPTED`

Browsing and destructive operations must not depend on a stale or complete global index. Before mutation, the provider object is revalidated as appropriate. Search/index/thumbnail metadata can be discarded and rebuilt without becoming authoritative object identity.

Evidence: `12_PERFORMANCE_AND_LIMITS.md`, `15_ARCHITECTURE_RECOMMENDATIONS.md`.

### J. Playback resolves provider-neutral media sources — `ACCEPTED`

Playback receives provider-scoped media identity and resolves it into a Media3-readable source appropriate to the current capability set. It must not require a local filesystem path.

Evidence: `05_MEDIA_PLAYBACK_FLACTIFY.md`, `02_STORAGE_PROVIDER_ARCHITECTURE.md`.

### K. Stable Android UI APIs are the baseline — `ACCEPTED`

Stable Material 3 / adaptive capabilities form the baseline. The product-level expressive design direction remains valid, but an experimental/alpha API is not architecture authority merely because it offers a desired effect. Experimental UI APIs require an explicit later evidence/review gate.

Evidence: `10_UI_M3_EXPRESSIVE_ADAPTIVE.md` and `UI_DESIGN_V1.md`.

### L. Security and hostile-input tests are designed alongside each feature — `ACCEPTED`

Provider, root, archive, media, network/cloud, IPC, and credential boundaries require adversarial fixtures and failure-path testing during feature construction rather than at the end of development.

Evidence: `11_SECURITY.md`, `13_TEST_STRATEGY.md`.

## Additional core decisions

### Common abstraction may expose only genuinely common semantics — `ACCEPTED`

A cross-provider layer may standardize identity, metadata discovery, list/stat-like discovery, bounded sequential read, write/create primitives where supported, and capability discovery. Higher-order guarantees remain optional/provider-specific.

### Unknown metadata remains unknown — `ACCEPTED`

The architecture must not synthesize false timestamps, ownership, inode-like values, hashes, or POSIX semantics for providers that do not expose them.

### Structured provider errors are preserved — `ACCEPTED`

Provider failures should remain distinguishable at an architecture level: not-found, permission/grant revoked, read-only, unsupported capability, conflict/version change, storage/quota full, network unavailable/timeout, authentication expiry, trust failure, rate limit, disconnect, corruption, cancellation, and unknown provider failure. Exact enum/type names are implementation details.

### Direct/root POSIX semantics are optional facets, not the baseline — `ACCEPTED`

chmod/chown/symlink/inode-like metadata must never become requirements of SAF, cloud, archive, or unrelated remote providers.

## Explicitly rejected Architecture V1 assumptions

- Universal path string as entry identity — `REJECTED`.
- Universal `java.io.File` semantics across all providers — `REJECTED`.
- Universal seek/random access — `REJECTED`.
- Universal atomic rename/move — `REJECTED`.
- Universal POSIX metadata — `REJECTED`.
- Root as baseline or global application mode — `REJECTED`.
- Global index as authority for browsing or destructive operations — `REJECTED`.
- Android executor object/lifetime as durable operation truth — `REJECTED`.
- FLACtify `PlayerViewModel` wholesale reuse as the new architecture — `REJECTED`.
- Experimental UI dependency as a requirement for the V1 visual identity — `REJECTED`.

## Decisions intentionally not frozen here

Concrete Kotlin interfaces/classes, module boundaries, package names, database schema/technology, SDK versions, dependency versions, executor mapping, provider libraries, cache implementation, permissions, manifests, signing, release/distribution configuration, and PoC-dependent behavior remain outside this document's authority. See `13_DEFERRED_DECISIONS.md` and `12_POC_BACKLOG.md`.
