# Architecture V1 — Media Integration

Status: PRINCIPLES FROZEN / DEPENDENCIES AND REMOTE SEEK UNFROZEN

Source authority: `docs/research/05_MEDIA_PLAYBACK_FLACTIFY.md` and research synthesis at SHA `b03a2ea99f24206f847f513fa4106e90268f3fc4`.

## Media3 / MediaSession concepts — `ACCEPTED`

The future File Manager may use Android's Media3 / MediaSession architecture as the conceptual playback foundation. The accepted architectural responsibilities are:

- a playback/session owner whose lifetime is not tied directly to one screen;
- a controller/gateway that reconnects UI state to the active session;
- provider-neutral media-source resolution;
- metadata/artwork handling as first-class playback concerns;
- capability-aware source handling rather than local-path assumptions.

This decision does **not** freeze the Media3 version, exact service class, exact controller API, package/module structure, cache implementation, or dependency declaration.

## Reusing FLACtify `PlayerViewModel` wholesale — `REJECTED`

The existing FLACtify `PlayerViewModel` is too broad for the new architecture because it combines playback, library scanning/cache, metadata, user-library state, recommendations/statistics, tag editing, and UI-facing state.

Replacement direction: preserve useful behavior and selected session/controller concepts, but separate future responsibilities around playback session/state, media-source resolution, metadata/artwork, library/catalog, user-library features, optional tag editing, and UI state.

FLACtify must not be modified during this phase.

## Provider-neutral media-source resolution — `ACCEPTED`

Playback receives provider-scoped media identity and resolves it through the storage capability model into the weakest source access sufficient for playback.

Possible source forms may later include:

- direct file or suitable native descriptor;
- SAF `content://` access;
- provider random/range reader;
- SMB/SFTP/WebDAV-backed reader;
- cloud ranged download source;
- bounded block cache layered over a high-latency origin.

The player must not require a real filesystem path.

## Local and SAF playback

Local provider playback through available direct/descriptor semantics — `ACCEPTED` in principle.

SAF playback through provider-supported URI/descriptor/stream semantics — `ACCEPTED` in principle.

Representative SAF seek/descriptor behavior — `POC_REQUIRED` because file-descriptor existence does not prove regular-file seekability.

## Remote media random access / seeking — `POC_REQUIRED`

Architecture must allow remote ranged/random reads, but it may not promise first-class seek quality before empirical evidence.

The later PoC must measure at least:

- startup latency;
- seek latency across beginning/middle/end positions;
- random read behavior under realistic Wi-Fi latency;
- recovery after transient disconnect;
- source-version change handling;
- cache hit/miss behavior;
- behavior when the remote source cannot provide efficient range reads.

SMB FLAC playback is the highest-priority remote media case. SFTP, WebDAV, and at least one cloud/range source follow.

## Cache categories — `ACCEPTED`

The architecture distinguishes at least five data classes:

1. **stream/block cache** — evictable performance optimization for playback/random access;
2. **metadata cache** — reconstructible parsed metadata;
3. **artwork/thumbnail cache** — reconstructible, bounded visual data;
4. **offline/pinned file** — user-requested durable content, not generic cache;
5. **operation partial/recovery data** — incomplete output/checkpoint state required for safe recovery.

A generic “clear cache” operation must not destroy user-requested offline files or operation recovery data — `ACCEPTED`.

Exact cache sizes, eviction algorithms, persistence engine, and cache-sharing strategy — `DEFERRED`.

## Signed URLs and credentials — `ACCEPTED`

Bearer-tokenized, signed, or temporary remote URLs must not become durable media identity. Durable identity remains provider-scoped; tokenized URLs are transient transport details.

Credentials/tokens are security assets and must not enter media-library identity or logs.

## Metadata and tag editing

Metadata extraction as a provider-aware feature — `ACCEPTED` in principle.

Tag editing on SAF/remote sources — `POC_REQUIRED` before any broad product promise because copy-edit-replace can be expensive and unsafe for large remote objects.

Tag editing must use Operation Manager semantics when replacement/staging is required and must respect provider conflict/version/finalization guarantees.

Exact metadata/tagging library — `DEFERRED`.

## Offline playback

User-requested offline/pinned media is durable user content, not an evictable stream cache — `ACCEPTED`.

Exact offline feature scope and storage policy — `DEFERRED` to the playback/product implementation phase.

## Format promise

Hard-coding FLACtify's historical extension set as File Manager's permanent media capability — `REJECTED`.

Playback support should be derived from the selected Media3/platform stack and validated provider capabilities.

Exact product format list — `DEFERRED` until playback implementation and codec validation.

## Process death and session reconnection

Playback/session state must tolerate UI process/screen recreation according to Android media architecture — `ACCEPTED` as a design requirement.

Exact service/process lifecycle behavior and persistence policy — `POC_REQUIRED` where real-device validation is needed.

## Architecture consequences

- File browsing and playback remain decoupled enough that playback can consume any provider that exposes sufficient read capability.
- Media source resolution must not bypass provider credentials/trust/version logic.
- Playback may request stronger read capabilities than ordinary file viewing; a provider may support listing/downloading yet not first-class seeking.
- Media cache invalidation must consider provider version tokens where available.
- Media integration must not force network/cloud providers to masquerade as local files.
