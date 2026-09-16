# Architecture V1 — Performance and Scale Invariants

Status: DESIGN REQUIREMENTS FROZEN / TECHNOLOGY AND NUMERIC BUDGETS UNFROZEN

Source authority: `12_PERFORMANCE_AND_LIMITS.md` plus related research at SHA `b03a2ea99f24206f847f513fa4106e90268f3fc4`.

## 64-bit size, offset, and progress — `ACCEPTED`

Architecture must support files larger than 4 GiB and very large aggregate operations. File length, byte offsets, ranges, and progress semantics must be 64-bit capable.

A universal 4 GiB application limit is `REJECTED`. Filesystem/provider-specific limits remain capabilities (for example FAT32).

## Bounded memory for large files — `ACCEPTED`

Large local/root/archive/network/cloud/conversion paths must consume memory approximately independent of total object size.

Whole-file `readBytes()`-style assumptions for large inputs are `REJECTED`.

Bounded buffers, streaming, backpressure, and bounded queues are required architectural properties.

## Lazy/incremental directory rendering — `ACCEPTED`

The UI must be able to show useful directory content before requiring one giant immutable complete list.

The provider/coordination layer should support incremental emission/paging/batching even when a backend internally enumerates eagerly.

Exact paging/list APIs and buffer sizes — `DEFERRED`.

## Bounded thumbnail and metadata work — `ACCEPTED`

Thumbnail extraction, media metadata parsing, checksums, and secondary metadata must not create unbounded fan-out during rapid scrolling or huge directories.

Architecture must allow bounded queues, cancellation, prioritization of visible work, and cache limits.

## Provider-specific concurrency limits — `ACCEPTED`

One global unlimited I/O fan-out policy is `REJECTED`.

Concurrency must be tunable by provider/workload because flash, SAF/FUSE, SMB, SFTP, WebDAV, cloud APIs, archives, and tiny-file workloads have different optimal limits and rate constraints.

Exact limits — `POC_REQUIRED` through benchmarks.

## Browsing remains independent from complete indexing — `ACCEPTED`

A missing, stale, rebuilding, or corrupt global index must not prevent direct provider browsing.

Index/search systems are acceleration and discovery layers, not prerequisites for basic navigation.

## Index is reconstructible — `ACCEPTED`

Indexes and metadata caches may be deleted/rebuilt. They are never destructive-operation authority.

Exact relational/search technology (Room, SQLite variants, FTS, AppSearch, SQLDelight, combinations) — `DEFERRED` pending benchmark/schema workload.

## Identity is not normalized display text — `ACCEPTED`

Unicode normalization/case folding may help search/sort presentation but must not silently change provider identity or destructive target selection.

## Deep traversal remains bounded — `ACCEPTED`

Recursive planning/traversal must avoid uncontrolled call-stack/memory growth. Iterative/bounded traversal and cycle/symlink policy are architectural requirements.

Materializing an entire million-node operation plan in memory is `REJECTED`.

## Sparse-file preservation is optional — `ACCEPTED`

Baseline copy guarantees logical content, not universal preservation of sparse extents. Sparse-layout preservation may later be a direct/root/provider-specific capability.

## Deterministic sorting — `ACCEPTED`

Sort order needs deterministic tie-breakers using stable provider identity. Asynchronously arriving thumbnails/metadata must not reorder rows unless the chosen sort explicitly depends on that data.

## Required future acceptance workloads

The architecture must be benchmarked and validated against representative workloads before relevant feature release:

- 10k-entry directory;
- 100k-entry directory;
- 1M indexed entries;
- file >4 GB;
- file >100 GB;
- large tiny-file workload;
- removable SD/USB disconnect;
- network interruption/reconnect;
- storage-full condition;
- rapid thumbnail/metadata scrolling;
- deep hierarchy;
- Unicode/very long names;
- zero-byte and sparse-file cases where applicable.

These workloads are `ACCEPTED` as future acceptance fixtures, not claims that every provider will meet identical latency.

## 100k directory / 1M index technology decision — `POC_REQUIRED`

Later benchmarks must measure at minimum:

- time to first visible results;
- enumeration throughput;
- peak managed/native memory;
- UI responsiveness/frame behavior;
- sort/search latency;
- index size/build/update cost;
- cancellation behavior;
- provider-specific overhead.

This evidence unlocks database/search technology and directory-listing implementation choices.

## >100 GB operation behavior — `POC_REQUIRED`

Validate bounded memory, checkpoint/recovery, progress overflow safety, storage-full handling, process death, and executor behavior on safe dedicated test media/providers.

## Tiny-file workload — `POC_REQUIRED`

Measure operation-count bottlenecks and safe concurrency independently from large sequential-throughput workloads.

## Performance measurement principle — `ACCEPTED`

Representative physical hardware and real providers are required for decisions that depend on filesystem, network, codec, thermal, background, or OEM behavior. Emulator-only inference is insufficient.

Exact benchmark framework/tool versions — `DEFERRED`.
