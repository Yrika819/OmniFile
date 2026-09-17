# P0 Decision Inputs

Status: READY FOR ARCHITECTURE REVIEW

This document converts P0 evidence into review inputs only. It does **not** modify Architecture V1, select production technologies, or authorize production implementation.

## Safe to carry into the next architecture review

### Storage model

- Keep storage capability-based and provider-scoped.
- Keep sequential read, seekable read and random-offset access distinct.
- Keep descriptor availability separate from descriptor seekability.
- Keep identity separate from path/URI presentation.
- Treat exact filename preservation as provider-dependent.
- Do not assume SAF URI/documentId stability across rename/move.
- Treat persisted grants and provider lifecycle as durable state concerns.

### Durable operation model

- Keep durable operation truth independent of executor instances.
- Keep an explicit partial destination.
- Recover by reconciling durable state with actual storage facts.
- Keep TRANSFER, VERIFY, FINALIZE and COMPLETE distinct.
- For MOVE, allow source deletion only after destination COMPLETE is durably established.
- Represent cancellation, conflict, mutation and storage failure explicitly.
- Require bounded-memory transfer behavior.
- Permit same-filesystem atomic finalization only when the provider/filesystem capability is actually established.

### Archive model

- Keep archive browsing as virtual storage.
- Keep extraction containment/security policy outside parser/codec libraries.
- Preserve explicit traversal, link, expansion, entry-count, nesting and malformed-input controls.
- Treat a multi-engine archive stack as viable architecture input.
- Keep RAR creation outside the demonstrated V1 capability set.
- Apply stronger acceptance gates to native dependencies and restricted-license components.

## Technology candidates that survived P0

These are candidates, not selections:

- Apache Commons Compress — broad ZIP/TAR/7z role survived measured host + Android testing.
- Zip4j — encrypted and split ZIP role survived measured host + Android testing.
- Junrar — RAR4/RAR5 read/extract survived technically, subject to explicit license review.
- zstd-jni Android AAR — TAR.ZST path worked on the tested arm64 Pixel 7a, subject to native/page-size review.

## Candidate approaches not supported by P0

- one universal archive library for all required formats;
- universal direct-path filesystem authority;
- universal seekability for descriptors/SAF;
- stable SAF URI/document ID as an identity contract;
- executor-owned operation truth;
- blind transfer restart from persisted byte counters;
- source deletion before durable MOVE completion;
- mandatory full SHA-256 for every operation;
- fixed universal checkpoint cadence;
- treating codec extraction APIs as sufficient extraction security;
- RAR creation through Junrar.

## Intentionally unresolved / unsafe to freeze

### Storage

- removable SD/USB disconnect/reconnect behavior;
- cloud-backed DocumentsProvider behavior;
- explicit persisted-grant revocation;
- OEM/provider deviations;
- universal rename/move atomicity;
- provider-specific reconnect policy.

### Operations

- Room/SQLite/file/other production persistence technology;
- WorkManager vs UIDT vs FGS vs Service/coroutine mapping;
- production retry/backoff policy;
- exact checkpoint interval;
- exact verification/hash policy;
- real Android/provider ENOSPC handling;
- cross-filesystem/provider finalization;
- power-loss durability semantics;
- SAF/cloud/network resume semantics.

### Archives

- final dependency versions;
- final archive abstraction/API wrappers;
- libarchive adoption or rejection;
- native crash-isolation strategy;
- 16 KiB page-size compatibility;
- production archive safety thresholds;
- final Junrar/UnRAR licensing decision;
- long-running solid-archive cancellation behavior;
- production-scale 100k+ metadata/indexing strategy.

### Product/release architecture

Still intentionally unfrozen:
- applicationId;
- package/namespace;
- module architecture;
- minSdk/targetSdk/compileSdk;
- dependency versions;
- production executor architecture;
- signing/release architecture;
- CI/CD and APK/AAB structure.

## Recommended next evidence work

Without performing it here, the next evidence backlog should prioritize:

1. removable SD/USB lifecycle and reconnect behavior on physical Android;
2. representative cloud DocumentsProvider capability/revocation/offline tests;
3. production-background-execution PoC comparing Android executor mechanisms without letting any executor become durable truth;
4. provider/SAF durable COPY recovery and real provider error translation;
5. native archive 16 KiB-page-size validation and libarchive cost study;
6. larger solid archives, cancellation latency and adversarial/fuzz archive corpus;
7. 100k+ archive metadata paging/index behavior on representative devices.

These are follow-up PoCs, not automatic implementation tasks.

## Architecture review disposition

P0 provides sufficient evidence to review and potentially freeze **principles and boundaries** around storage capabilities, durable operation semantics and archive safety.

P0 does **not** provide sufficient evidence to freeze concrete production libraries, persistence technology, Android executor mapping, provider-specific behavior, native archive stack, SDK versions or application/module/release structure.

No automatic Technology Freeze is authorized by this document.
