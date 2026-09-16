# Performance, Scale, and Edge-Case Research

Status: COMPLETE — performance requirements and candidate comparison only  
Last verified: 2026-09-17

## Classification legend

- **FACT** — documented platform/library behavior.
- **INFERENCE** — consequence derived from facts and workload characteristics.
- **RECOMMENDATION** — proposed direction; not frozen implementation authority.
- **UNRESOLVED** — requires benchmark/prototype/device evidence.

## Executive finding

A high-capability file manager must distinguish at least four scale problems:

1. **directory enumeration** — potentially 10,000–100,000 children;
2. **global indexing/search** — potentially millions of entries;
3. **large-object I/O** — >4 GB and >100 GB files;
4. **tiny-file fan-out** — high per-file metadata/open/close latency where total bytes may be modest.

One data structure or one database does not solve all four. Provider APIs, UI paging, metadata caches, search indexes and long-running I/O require independent backpressure and failure limits.

## Folder with 10,000 files

### UI

**FACT:** Compose lazy lists render only the visible/windowed content, and Android's Paging library is designed to load/display chunks from larger local or network datasets.

Sources:
- https://developer.android.com/develop/ui/compose/lists
- https://developer.android.com/topic/libraries/architecture/paging/v3-paged-data

**RECOMMENDATION:**
- render directory content in a lazy list/grid;
- use stable provider-scoped IDs as keys rather than display name/index;
- load thumbnails and expensive metadata only for visible/near-visible items;
- move sorting and metadata extraction off the UI thread;
- avoid one observable state update per file if it causes thousands of recompositions.

### Enumeration

Not every provider supports true server-side paging. Direct local APIs may return an entire result set, SAF providers expose cursor-based queries at lower levels but provider behavior varies, and cloud/network providers often expose explicit page/cursor APIs.

**RECOMMENDATION:** The provider layer should be able to emit entries incrementally even when the underlying source had to enumerate eagerly. Do not require the UI to receive one complete immutable 10,000-entry list before showing the first items.

## Folder with 100,000 files

100,000 entries materially changes the design:

- retaining rich thumbnail/metadata objects for every row can exhaust heap;
- client-side full sorting can become expensive in both time and memory;
- recursive eager operations multiply the problem;
- `DocumentFile` convenience calls are not an adequate performance contract for this scale.

**RECOMMENDATION:**
- keep a compact entry model;
- defer secondary metadata;
- prefer provider/server-side sorting only where semantics are trustworthy;
- otherwise sort compact metadata off-thread and report an explicit loading/sorting state;
- use cancellation when the user leaves/sorts again;
- benchmark worst-case Unicode and long-name rows rather than synthetic short ASCII only.

**UNRESOLVED / PROTOTYPE REQUIRED:** Compare lower-level SAF cursor enumeration to `DocumentFile`, direct filesystem APIs, SMBJ, SFTP, WebDAV and cloud pagination at 100k entries.

## Millions of indexed entries

A global index is different from the currently viewed directory. The index should not be mandatory for basic browsing.

### Candidate: SQLite / Room

**FACT:** Android includes SQLite; AndroidX Room provides a higher-level abstraction over SQLite with compile-time database access/migration support. As of 2026-09-09, stable Room is 2.8.5. This version is research context only and is not selected.

Sources:
- https://developer.android.com/reference/android/database/sqlite/package-summary
- https://developer.android.com/jetpack/androidx/releases/room

**Strengths:**
- mature transactional relational model;
- precise schema/index control;
- good fit for provider identity, metadata, operation records and relationships;
- SQLite full-text search can be used where supported.

**Costs:**
- schema/migration ownership;
- write amplification if every transient metadata event is persisted;
- filename/path search behavior and Unicode/tokenization require deliberate design;
- Room is an abstraction choice, not a necessity.

### Candidate: direct AndroidX SQLite

**FACT:** AndroidX SQLite exposes lower-level interfaces; stable 2.7.1 was current on 2026-09-09. Android's own guidance notes that Room is an abstraction option over SQLite, not that every database use must employ it.

Source:
- https://developer.android.com/jetpack/androidx/releases/sqlite

**Assessment:** Greater SQL/control with more manual correctness/migration code. Keep as a comparison baseline.

### Candidate: SQLite FTS5

**FACT:** SQLite FTS5 is a full-text-search virtual-table module intended for efficient search over large collections.

Source:
- https://www.sqlite.org/fts5.html

**UNRESOLVED:** The exact SQLite features/version available through every Android 12–16 platform SQLite build and through any bundled SQLite option must be verified before relying on one FTS behavior. Filename search also has requirements unlike natural-language document search.

### Candidate: AppSearch

**FACT:** AndroidX AppSearch is an on-device structured-data search/index solution intended for efficient indexing/querying of large datasets. Current Android documentation still demonstrates AppSearch artifacts in a 1.2.0 alpha line, while `PlatformStorage` is available on Android 12+.

Source:
- https://developer.android.com/develop/ui/views/search/appsearch

**Strengths:**
- search-oriented model;
- relevance and indexing features;
- mobile-focused I/O behavior.

**Risks:**
- API/version maturity needs re-evaluation at implementation time;
- not an obvious replacement for relational operation/persistence state;
- provider-specific identity/update/delete consistency still has to be owned by the app.

**RECOMMENDATION:** Keep AppSearch as an index candidate, not a mandatory persistence layer.

### Candidate: SQLDelight

**FACT:** SQLDelight generates type-safe Kotlin APIs from SQL and is Apache-2.0 licensed.

Source:
- https://github.com/sqldelight/sqldelight

**Assessment:** Plausible alternative if direct SQL/type-safety is preferred. It should be compared with Room only after data model and module constraints are known.

### Database recommendation

**RECOMMENDATION:** Do not freeze Room, AppSearch, SQLDelight or raw SQLite now. The strongest current direction is:

- a durable transactional store will likely be needed for Operation Manager and provider/account/index metadata;
- full-text/file-name indexing may use the same SQLite database or a specialized search index;
- basic folder browsing must remain functional with the index absent/corrupt/rebuilding.

## Files larger than 4 GB

**FACT/INFERENCE:** Android and common modern filesystems/protocols can handle >4 GB files, but FAT32 itself has a per-file size ceiling near 4 GiB. Therefore size limits are source/destination capability questions, not a universal app limit.

**RECOMMENDATION:**
- use 64-bit sizes/offsets everywhere;
- never cast length/progress to 32-bit integer;
- destination preflight should expose filesystem/provider maximum-size failures where detectable;
- tests must include boundaries around 2 GiB and 4 GiB.

## Files larger than 100 GB

Requirements:
- bounded-buffer streaming;
- no whole-file hashes in memory;
- progress in 64-bit counters;
- resumable/checkpointed network/cloud transfer;
- source version validation when resuming;
- partial destination/finalization model;
- user-visible handling when storage space or cloud quota is exhausted.

**RECOMMENDATION:** A 100 GB local or remote operation must consume memory approximately independent of file size.

## Tiny-file workloads

Copying 100,000 tiny files can be slower than one 100 GB file because listing/stat/open/create/close/metadata/network round trips dominate.

**RECOMMENDATION:**
- bound I/O concurrency per provider;
- batch protocol operations only where semantics/API support them;
- avoid calculating expensive checksums by default;
- aggregate progress by both item count and bytes;
- measure operations/second in addition to MB/s.

**UNRESOLVED:** Optimal concurrency differs dramatically among flash storage, SAF/FUSE, SMB, SFTP, WebDAV and cloud APIs. Tune by benchmark, not a single hard-coded thread count.

## Unicode and filename correctness

Required fixtures:
- Japanese and non-Latin scripts;
- emoji and multi-code-point emoji;
- combining marks (for example visually equivalent composed/decomposed names);
- RTL/bidirectional text;
- leading/trailing spaces where backend permits;
- newline/control-like display cases;
- case-sensitive/case-insensitive collisions;
- normalization differences;
- names containing shell/path metacharacters.

**RECOMMENDATION:** Identity comes from provider ID/path bytes/semantics, not normalized display text. Normalization/case-folding may be used for *search/sort presentation* without changing actual object identity.

## Extremely long filenames and paths

Limits vary by filesystem, protocol and server. A remote/cloud display path can also exceed local filesystem limits even if each remote component is valid.

**RECOMMENDATION:**
- avoid fixed small buffers for path/name strings;
- preserve full name in model and accessible UI even if visually ellipsized;
- validate destination-specific filename restrictions during copy/export rather than globally rejecting names that are valid on the source;
- operation conflict errors should explain incompatible destination naming.

## Deeply nested directories

Risks:
- stack overflow from recursive traversal;
- path-length limits;
- cycle via symlink/provider graph anomaly;
- huge operation plans.

**RECOMMENDATION:** Use iterative traversal or explicitly bounded recursion; keep visited identities when following links is allowed; stream operation planning rather than constructing a million-node in-memory tree.

## Zero-byte files

Zero-byte files are valid. Never infer corruption solely from size zero. Copy/create must preserve them and progress handling must avoid divide-by-zero.

## Sparse files

**FACT/INFERENCE:** Direct Linux filesystems can represent sparse files, but generic stream copy commonly materializes holes. SAF/cloud/network protocols may not expose sparse extent semantics.

**RECOMMENDATION:** Baseline copy guarantees logical content, not sparse-layout preservation. Sparse preservation can become an optional direct/root/provider-specific capability later.

**PROTOTYPE REQUIRED:** local direct-copy strategy if sparse preservation becomes a product requirement.

## Symlinks and broken symlinks

Direct/root providers may expose symlinks; SAF/cloud generally do not share the same concept.

Performance and correctness rules:
- listing a broken symlink should not block/fail the containing directory;
- stat and lstat-like metadata must be distinct where supported;
- thumbnail/metadata workers should not repeatedly resolve a known broken link;
- recursive traversal needs explicit follow/no-follow behavior.

## Removable-storage disconnect

A source can disappear between list and open or mid-stream.

**RECOMMENDATION:** Volume/source lifetime is independent from entry objects. On eject:
- cancel/fail active handles cleanly;
- preserve durable operation checkpoint;
- mark source unavailable rather than deleting metadata blindly;
- resume/reconcile only when the same source identity is re-established.

## Network interruption

Covered in `07_NAS_NETWORK_STORAGE.md` and `09_BACKGROUND_OPERATIONS.md`.

**RECOMMENDATION:** Reconnect from a persisted safe offset/version where supported; never assume a reused hostname means the same file version.

## Storage-full conditions

Preflight estimates are advisory. Handle actual ENOSPC/quota responses at any point.

**RECOMMENDATION:** Keep the source intact, preserve or clean partial output according to recoverability, and never mark finalization complete until the destination confirms completion.

## Sorting

Potential sort keys:
- name;
- modified time;
- size;
- type;
- folders-first policy.

Performance/correctness concerns:
- locale-aware collation cost;
- unknown metadata values;
- provider pagination that does not support requested order;
- stable order while metadata arrives asynchronously.

**RECOMMENDATION:** Define deterministic tie-breakers using stable identity. Do not let asynchronous thumbnail/media metadata reorder rows unless the chosen sort explicitly depends on it.

## Search indexing

Index only useful searchable metadata unless user explicitly enables richer content indexing. Candidate fields:
- display name;
- provider/source identity;
- parent identity/breadcrumb metadata;
- type/MIME;
- size/time;
- selected media metadata.

Avoid full file-content indexing in baseline; it radically increases I/O, privacy, battery and parser exposure.

### Incremental index lifecycle

- first scan;
- provider change/delta feed when available;
- local watcher where reliable;
- periodic/reconciliation scan;
- removable/network source offline state;
- account/source removal cleanup.

**RECOMMENDATION:** A stale index is a cache-like accelerator, not authority for destructive operations. Revalidate the actual provider object before mutation.

## Thumbnail generation

Threats/performance costs:
- huge images;
- malformed media;
- video frame extraction;
- remote download for a thumbnail;
- 100k-item folder stampede.

**RECOMMENDATION:**
- request bounded dimensions;
- viewport-prioritized queue;
- small concurrency;
- cancel work for rows leaving viewport;
- encoded disk cache plus bounded decoded memory cache where later justified;
- remote thumbnail/provider-native preview preferred when trustworthy and cheaper.

## Metadata cache

Cache key should include enough source version information to detect stale metadata:
- provider ID;
- opaque entry ID;
- revision/etag/generation where available;
- otherwise size/mtime heuristics with known limitations.

Do not use filename alone as a cache key.

## Memory use and backpressure

**FACT:** Android processes have finite managed/native memory and are subject to process-kill pressure; allocations should be monitored rather than assuming a desktop-sized heap.

Source:
- https://developer.android.com/topic/performance/memory-overview

**RECOMMENDATION:** Every producer/consumer pipeline needs a bound:
- directory entries queued;
- thumbnails decoded;
- transfer buffers;
- archive entries indexed;
- media metadata jobs;
- network requests;
- database write batches.

A fast producer must not be able to enqueue unbounded work behind a slower consumer.

## I/O concurrency

Separate concurrency pools/limits are likely needed for:
- local metadata reads;
- thumbnails;
- network transfers;
- cloud API metadata calls;
- CPU-heavy archive decompression/conversion.

**RECOMMENDATION:** No single global `Dispatchers.IO` fan-out policy should decide all concurrency. The architecture should support provider/operation-specific semaphores or scheduling limits later.

## Performance measurement

**FACT:** Android provides Macrobenchmark/benchmark tooling and profiling guidance; performance should be measured on representative hardware rather than inferred from emulator speed alone.

Source:
- https://developer.android.com/topic/performance/benchmarking/benchmarking-overview

Suggested future metrics:
- time to first directory rows;
- time to complete enumeration;
- peak Java/native memory;
- scroll jank/frame time;
- sort latency;
- search latency p50/p95;
- index rows/sec and database size;
- transfer MB/s + operations/sec;
- CPU/battery/thermal behavior;
- thumbnail hit/miss latency;
- process-death recovery time.

## Future benchmark fixture matrix

| Workload | Minimum required fixture |
|---|---|
| large directory | 10k and 100k entries |
| index | 1M+ synthetic entries |
| large file | >4 GB and >100 GB where test storage permits |
| tiny files | 100k small files |
| names | Unicode/emoji/combining/RTL/long/control-like |
| nesting | deep tree + symlink/broken symlink cases |
| archive | 10k/100k archive entries |
| remote | latency/loss/disconnect/reconnect profiles |
| full storage | controlled near-ENOSPC destination |

Use generated test data rather than committing enormous binary fixtures to Git.

## Recommendation summary

**Strong:** lazy/incremental UI + bounded pipelines + 64-bit sizes/offsets.  
**Strong:** browsing must not depend on a global index; indexing is reconstructible acceleration.  
**Strong:** keep database/search technology unfrozen; Room/SQLite/FTS, AppSearch and SQLDelight remain candidates with different roles.  
**Strong:** test both throughput-heavy and operation-count-heavy workloads.  
**Prototype required:** 100k folder behavior, 1M+ index, >100 GB transfer, sparse/removable/network edge cases, provider-specific concurrency, thumbnail/cache memory behavior.
