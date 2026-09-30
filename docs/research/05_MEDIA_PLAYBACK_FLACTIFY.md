# FLACtify Playback Architecture Research

Status: COMPLETE — `~/Desktop/FLACtify` inspected READ-ONLY  
Last inspected: 2026-09-17  
Role of FLACtify: reference implementation for playback behavior, **not** architecture authority

## Read-only integrity note

Before inspection, FLACtify was already dirty on `main` with pre-existing IDE/build/release metadata changes and APK files. This research made **no changes** to FLACtify, did not build it, and did not clean/revert its worktree.

## Current observed project baseline

The inspected `app/build.gradle.kts` currently declares:

- compileSdk / targetSdk 36 and minSdk 26 for FLACtify itself;
- Media3 ExoPlayer/session/common and OkHttp data source at 1.3.1;
- AndroidX DocumentFile;
- Coil 2.6.0;
- `net.jthink:jaudiotagger:3.0.1`;
- OkHttp 4.12.0.

**RECOMMENDATION:** None of those versions or SDK choices transfer automatically to File Manager. Several are old relative to September 2026; versions must be re-researched when dependency selection begins.

## Current architecture

### `PlaybackService`

Observed responsibilities:

- extends Media3 `MediaSessionService`;
- creates/owns an `ExoPlayer`;
- owns the `MediaSession`;
- configures a `CacheDataSource.Factory` and `DefaultMediaSourceFactory`;
- uses `SimpleCache` with an LRU evictor currently sized to 2 GiB;
- uses notification/session metadata including artwork;
- exposes the active session to controllers.

**Assessment:** The service/session ownership pattern is reusable conceptually. The static/global cache factory and fixed cache policy should be refactored rather than transplanted.

Official Media3 references:
- https://developer.android.com/media/media3/session/background-playback
- https://developer.android.com/media/media3/exoplayer
- https://developer.android.com/media/media3/exoplayer/caching-media

### `PlaybackController`

Observed responsibilities:

- builds a Media3 `MediaController` asynchronously from the playback service session token;
- registers player listeners;
- translates metadata/state callbacks into app state;
- creates/replaces MediaItems;
- sets playlists and starts selected collections;
- exposes play/pause, seek, next/previous, shuffle and repeat;
- cleans up controller resources.

**Assessment:** This is the strongest extraction candidate. Its role can become a playback-domain/session gateway, but it should not depend on FLACtify-specific track/cache assumptions.

### `PlayerViewModel`

Observed responsibilities are broad and currently coupled in one class:

- MediaController/playback state;
- current track metadata;
- position/duration;
- library scan state;
- directory loading;
- scan/cache orchestration;
- artwork/theme/lyrics/audio information;
- playlist setup;
- playback controls;
- tag editing;
- favorites/playlists;
- sleep timer;
- recommendation networking/cache;
- playback statistics;
- cache size/clear actions.

**INFERENCE:** This is the largest reuse risk. Moving it wholesale into File Manager would couple file browsing, music-library persistence, metadata, recommendations, playback and UI state, making future NAS/cloud/SAF support much harder.

**RECOMMENDATION:** Do **not** transplant `PlayerViewModel`. Split future responsibilities into playback session/state, media source resolution, music library/catalog, metadata/tagging, user library, recommendation/statistics (if retained), and feature UI state.

### `LibraryScanner`

Observed behavior:

- starts from `DocumentFile.fromTreeUri()`;
- recursively collects audio files;
- current extension filter is `.mp3`, `.flac`, `.m4a`, `.wav`;
- compares cached `lastModified` data;
- writes a JSON track cache through a temporary file then rename;
- uses local temporary files for some metadata work.

**Assessment:** The incremental-cache idea is useful, but direct dependence on `DocumentFile`, recursive eager collection and a single folder-oriented cache do not generalize to 100k-file folders, NAS/cloud or provider capabilities.

**RECOMMENDATION:** Reuse the *behavioral requirements* (incremental discovery, metadata caching, cancellation/progress) but redesign scanner inputs around the File Manager provider abstraction and paging/backpressure.

### `TrackMetadataExtractor`

Observed responsibilities:

- extracts track metadata, artwork, lyrics and audio information;
- caches artwork, lyrics, audio specs and detected extension in memory by URI;
- currently recognizes FLAC/WAV/M4A/MP3 paths explicitly;
- derives artwork/theme information.

**Assessment:** Metadata extraction should be separated from UI theming and from provider identity. In-memory Bitmap caches need bounded memory policy.

### Tag editing

`TagEditor` uses jaudiotagger and creates a temporary local file before committing changes back to the source.

**INFERENCE:** This copy-edit-replace workflow can work for SAF sources but is potentially expensive for multi-gigabyte remote files and creates conflict/atomicity concerns.

**RECOMMENDATION:** Keep tag editing as a separate capability. Require provider-aware temporary-space planning, conflict detection and atomic replacement where supported.

### Artwork

Artwork is currently extracted/cached and also used by playback notification/player theming.

**RECOMMENDATION:** Preserve artwork behavior, but separate raw artwork extraction/cache from UI color derivation. File Manager thumbnails and player artwork may share cache infrastructure only if keys, eviction and privacy policies are explicitly designed.

### Playlists and favorites

`LibraryManager` currently persists favorites and playlist JSON in SharedPreferences; `UserLibraryController` is a thin wrapper. Playback statistics also live in this persistence area.

**Assessment:** Fine for a small standalone app, but URI strings alone are fragile as a universal future identity across cloud/NAS/provider renames.

**RECOMMENDATION:** Future user-library entries should reference durable provider-scoped media identity plus recoverable metadata, not assume URI/path strings never change. Persistence technology remains unfrozen.

### Caching

There are multiple cache concepts:

1. Media3 byte cache (`SimpleCache`, currently 2 GiB LRU).
2. Library JSON cache.
3. Metadata/artwork in-memory caches.
4. Recommendation cache.
5. temporary edit/scan files.

**RECOMMENDATION:** Future architecture must distinguish media-block cache, metadata cache, thumbnail/artwork cache and temporary workspace. “Clear cache” should not blindly erase unrelated operation recovery or user data.

## Supported formats: current FLACtify vs future player

The current scanner explicitly admits:
- FLAC
- MP3
- M4A (metadata text treats this as ALAC/AAC family)
- WAV

Media3 itself supports a wider set of formats depending on extractors/platform decoders.

**RECOMMENDATION:** File Manager playback support should be capability-detected from Media3/platform rather than hard-coded only to FLACtify's current four extensions. Exact product format promise is postponed.

Official reference:
- https://developer.android.com/media/media3/exoplayer/supported-formats
- https://developer.android.com/media/platform/supported-formats

## Reuse/refactor/extract/rewrite matrix

| Area | Direction | Reason |
|---|---|---|
| MediaSessionService ownership pattern | Reuse concept | correct Android background playback model |
| `PlaybackService` class verbatim | Refactor | cache/source construction is FLACtify-specific |
| `PlaybackController` | Extract/refactor | useful session gateway, needs provider-neutral sources |
| `PlayerViewModel` | Rewrite/split | excessively broad responsibilities |
| LibraryScanner | Rewrite around provider abstraction | DocumentFile recursion/cache model will not scale/generalize |
| metadata extraction logic | Refactor/extract | useful behavior, currently URI/temp-file/UI coupled |
| TagEditor | Refactor | needs capability/conflict/remote handling |
| artwork extraction | Reuse logic selectively | separate from theming/cache policy |
| playlists/favorites behavior | Reuse product concept | persistence identity must change |
| recommendation feature | Keep standalone unless explicitly desired | not required for file-manager playback core |
| playback statistics | Optional extraction | product decision; not core file-manager requirement |
| sleep timer | Reusable optional player feature | independent from file storage architecture |
| existing UI concepts | Reference only | UI_DESIGN_V1 is File Manager authority |

## Future shared player architecture

Conceptual boundaries, not implementation names:

```text
Player UI / Mini Player
        |
Playback State / Commands
        |
MediaSession Gateway
        |
Media3 Player Service
        |
Media Source Resolver
  +-----+------+----------+----------+
  |            |          |          |
Local/SAF   NAS source  Cloud     Cache source
                           \
                         provider handles
```

### Media Source Resolver

**RECOMMENDATION:** The player should receive provider-scoped media identity and ask a resolver for a Media3-readable source. It must not require a filesystem path.

Possible source shapes:
- local file/descriptor;
- `content://` URI;
- HTTP(S) stream;
- custom Media3 `DataSource` backed by SMB/SFTP/cloud random reads;
- cached block source.

## Local files

Direct files and stable `content://` resources are the easiest paths. Media3 already handles Android URI/data source patterns well.

## SAF content

**UNRESOLVED:** Different document providers may expose seekable FDs or pipe-backed content. Player seek behavior must be validated against representative providers.

## NAS streams

SMB and SFTP do not map automatically to HTTP. Smooth FLAC seeking requires an efficient provider-backed random/range reader and a Media3 data-source bridge (or another carefully measured adapter).

**RECOMMENDATION:** Avoid downloading a full NAS FLAC before playback. Use block caching and random reads where protocol/library supports them.

## Cloud streams

Google Drive, OneDrive and Dropbox can provide download streams/ranges in provider-specific ways. Authentication and expiring download URLs must be hidden behind the provider/source resolver.

**RECOMMENDATION:** Media3 must never persist bearer-tokenized URLs as library identity.

## Seekable remote media

For large FLAC and long media:

- byte length should be known when provider knows it;
- range/random read should map to Media3 seek requests;
- block cache should avoid refetching headers/adjacent ranges;
- remote version/etag should invalidate stale cache;
- disconnect should become retryable playback/source error.

## Caching and offline playback

Separate concepts:

- **stream cache**: performance optimization, evictable;
- **offline pin/download**: user-requested durable copy, not silently evicted;
- **metadata cache**: small indexed metadata;
- **artwork/thumbnail cache**: bounded decoded/encoded image data.

**RECOMMENDATION:** Do not call offline media merely “cache”; durability semantics matter to users and Operation Manager.

## Media3 status

**FACT:** Media3 is Android's official Jetpack media stack and is Apache-2.0 licensed. The File Manager should evaluate the current stable Media3 release at implementation time, not FLACtify's 1.3.1 baseline.

References:
- https://developer.android.com/media/media3
- https://developer.android.com/jetpack/androidx/releases/media3
- https://github.com/androidx/media

## Security/privacy

- Treat media files as hostile parser input.
- Keep platform/Media3 codecs patched with OS/library updates.
- Avoid logging cloud URLs/tokens/full sensitive paths.
- Bound artwork decode dimensions/memory.
- Temp files for metadata editing should use app-private storage and be removed on success/failure/recovery.
- Verify source version before replacing edited remote media.

## Prototype-required validation

1. Local + SAF playback and seeking across Android 12/16.
2. SMB FLAC streaming with seek under latency/disconnect.
3. SFTP FLAC streaming with seek.
4. WebDAV/cloud ranged playback.
5. cache invalidation on remote version change.
6. offline-pin semantics separate from stream cache.
7. metadata/tag editing against SAF and remote sources without whole-file memory buffering.
8. process death with active MediaSession and subsequent UI reconnection.

## Recommendation summary

**Strong:** Keep Media3/MediaSession service architecture, but refactor source resolution around provider capabilities.  
**Strong:** split `PlayerViewModel`; do not transplant it.  
**Strong:** treat FLACtify scanner/persistence as behavioral reference, not new architecture.  
**Strong:** make remote seeking/cache a dedicated prototype before claiming first-class NAS/cloud playback.  
**Postpone:** exact Media3 version, cache size/policy, metadata library and final format list.
