# P0 PoC Evidence Summary

Status: COMPLETE — EVIDENCE READY FOR ARCHITECTURE REVIEW

Architecture authority remains unchanged at `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`.

This synthesis branch is documentation-only. It does not merge PoC code, amend Architecture V1, start production implementation, or perform Technology Freeze.

## Source branches

- POC-001 `poc/storage-capabilities-v1` — end SHA `2715f18`.
- POC-002 `poc/durable-operations-v1` — end SHA `8db0fde`.
- POC-003 `poc/archive-engines-v1` — end SHA `f7e1e0a`.

Each PoC began from the same Architecture V1 authority SHA and remained independent.

## POC-001 — Storage capabilities

Status: COMPLETE for P0 decision input.

Evidence includes Android 12/API31 emulator compatibility, Android 16/API36 emulator evidence, and a physical Pixel 7a on Android 16/API36 using a real `ACTION_OPEN_DOCUMENT_TREE` selection of the local `Documents` provider.

Measured conclusions:
- direct files, local SAF documents and pipe-backed provider descriptors expose different capability sets;
- a valid `ParcelFileDescriptor` does not imply seekability; the synthetic FIFO descriptor was sequentially readable while `lseek` returned `ESPIPE`;
- the tested local SAF provider was seekable, but this is provider-specific rather than universal;
- direct filesystem path changed across rename/move while the observed file key remained stable;
- the tested SAF provider changed both URI and `documentId` on rename and move;
- filename behavior is provider-specific: direct storage rejected some requested names while local SAF sanitized them;
- persisted SAF permission survived process restart;
- >4 GiB logical-size handling was measured without filling storage with 4+ GiB of physical data.

Still unresolved: removable SD/USB disconnect/reconnect, cloud-backed providers, explicit persisted-grant revocation, OEM/provider variance, universal atomicity and universal seekability.

## POC-002 — Durable operations

Status: COMPLETE for P0 decision input.

Host evidence was supplemented by a physical Pixel 7a / Android 16 raw-DEX harness without choosing a production Android executor architecture.

Pixel 7a campaign completed 12 scenarios, including:
- baseline COPY;
- process death early, mid and near end;
- death after transfer before verification;
- death after verification before finalization;
- cancellation and resume;
- controlled injected ENOSPC and resume;
- destination conflict;
- source mutation;
- MOVE source-delete ordering;
- a 2 GiB streamed transfer.

Measured conclusions:
- durable truth can survive executor death when state and partial storage are independent of the executor;
- recovery must reconcile persisted progress against actual partial length because writes can outrun the latest durable checkpoint;
- VERIFY, FINALIZE and COMPLETE must remain distinct phases;
- MOVE source deletion occurred only after destination COMPLETE was durably recorded;
- cancellation, conflict, storage failure and mutation require explicit durable states;
- bounded streaming stayed far below file size;
- same-filesystem atomic finalization succeeded on the tested Android path, but is not generalized;
- checkpoint cadence has measurable overhead;
- full SHA-256 verification cost varies significantly by device/storage environment and should not be universally mandated from this evidence.

Still unresolved: production persistence choice, WorkManager/UIDT/FGS/Service/Activity mapping, API31 execution of the Android harness, real ENOSPC, SAF/cloud/network recovery, cross-filesystem/provider finalization and power-loss durability.

## POC-003 — Archive engines

Status: COMPLETE for P0 decision input.

The host and Pixel 7a / Android 16 runs both completed the archive matrix. The final Pixel run emitted 54 result records and `RUN_DONE=PASS`.

Measured candidate disposition:
- Apache Commons Compress 1.28.0 — SURVIVED for broad ZIP/TAR/7z coverage;
- Zip4j 2.11.6 — SURVIVED for encrypted/split ZIP coverage;
- Junrar 8.1.1 — SURVIVED TECHNICALLY WITH LICENSE GATE for RAR read/extract;
- libarchive — UNRESOLVED / NOT TESTED on Android;
- single universal archive library — CHALLENGED by measured gaps.

Security conclusions:
- archive parser/codec libraries are not the extraction-security boundary;
- normalized destination containment, link policy, entry/nesting limits, streaming expansion limits, cancellation and malformed-input normalization remain application-level requirements;
- RAR creation was not supported by Junrar and was not claimed.

Native conclusions:
- TAR.ZST worked on the Pixel 7a with the Android zstd-jni AAR/native library;
- 16 KiB page-size compatibility remains unmeasured;
- libarchive Android build/runtime, JNI cost, crash isolation, binary size and patch burden remain unmeasured.

## Cross-P0 architecture result

No frozen Architecture V1 principle was contradicted.

Strongly confirmed across the campaign:
- capability-based/provider-scoped storage rather than a universal path/filesystem abstraction;
- opaque identity separate from display path/URI;
- sequential, seekable and random access as separate capabilities;
- durable operation truth separate from executor lifetime;
- explicit partial destinations and reconciliation-based recovery;
- VERIFY/FINALIZE/COMPLETE separation and post-COMPLETE MOVE source deletion;
- bounded-memory streaming;
- archive browsing as virtual storage;
- extraction safety outside parser/codec engines;
- native dependencies and restricted-license components require additional acceptance gates.

Refined by measurement:
- tested SAF rename/move can change both URI and document ID;
- providers may sanitize requested names instead of rejecting them;
- durable progress can lag actual partial bytes;
- checkpoint and verification policies are workload/device dependent;
- a multi-engine archive stack is technically viable;
- large archive metadata needs paging/lazy-index thinking rather than assuming whole-index cost is negligible.

## Candidate approaches challenged or rejected by evidence

- universal local-path / `java.io.File` authority for all storage;
- assuming `ParcelFileDescriptor` implies seekability;
- assuming stable SAF URI/documentId across rename or move;
- equating executor/process lifetime with operation truth;
- blind replay from persisted byte count without partial reconciliation;
- deleting MOVE source before durable destination completion;
- universal mandatory full-file hashing;
- one fixed tiny checkpoint cadence for all operations;
- treating archive library extraction APIs as the security boundary;
- assuming one universal archive library covers the full V1 matrix;
- assuming RAR read support implies RAR creation.

## Evidence gaps preserved as NOT TESTED / unresolved

- removable SD/USB lifecycle and reconnect;
- cloud-backed DocumentsProvider semantics;
- explicit persisted SAF grant revocation;
- OEM/provider storage variance;
- Android background executor choice and OS policy behavior;
- real Android/provider ENOSPC;
- cross-filesystem/provider finalization and power-loss recovery;
- libarchive Android viability;
- 16 KiB native page-size compatibility;
- long-running solid-archive cancellation latency;
- larger/fuzz archive corpora;
- final library versions, wrappers and production dependency choices.

## Artifact classification

- disposable harness/runtime artifacts: `DISPOSABLE`;
- raw measurements and PoC documentation: `REFERENCE_ONLY`;
- selected state-machine and archive-security concepts: `POTENTIALLY_REUSABLE_AFTER_REVIEW` as design inputs only;
- production-ready implementation: NONE.

Production Android application implementation remains unstarted.
