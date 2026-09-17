# P0 Evidence Incorporation — Architecture V1 Reconciliation

Status: `P0_EVIDENCE_INCORPORATED — ARCHITECTURE CONFIRMED/REFINED; READY FOR USER REVIEW OF P0.5`

This document reconciles the completed P0 evidence with Architecture V1. It is an architecture/documentation record only. It does not select production technologies, authorize P0.5, initialize Android, or change any protected branch.

## Authority and evidence set

| Role | Ref | Use |
|---|---|---|
| Main baseline | `793d151f9608a684c1d0d4be2e58e5b49d26f823` | Protected baseline; unchanged. |
| Research authority | `b03a2ea99f24206f847f513fa4106e90268f3fc4` | Pre-P0 architecture research. |
| Architecture V1 authority | `79fc0f18c7f5e1d8e0ae714808f89977ff178b62` | Exact parent of this branch. |
| POC-001 storage | `2715f18c589723e8aa82d8907387646d51e36bfa` | Documentation, raw JSONL, emulator and Pixel 7a artifacts. |
| POC-002 durable operations | `8db0fde4996fef34e2d1f4b56bef0b8b6afcab0a` | Documentation, harness, event logs and state artifacts. |
| POC-003 archive engines | `f7e1e0a52309cc0be1d10e4086424bb7e85b295a` | Documentation, host/Pixel 7a records and harness. |
| P0 synthesis | `4016dea8a46d0ad993896fb49b3d8802a71c80d9` | Review input only; checked against the three source branches. |

The P0 branches were inspected with `git show` and detached/ref-pinned reads. Their implementation artifacts are not merged into this branch. The independent reviews and the parent inspection agree that no existing Architecture V1 principle is contradicted, but several synthesis statements required scope correction.

## Decision-by-decision disposition

### Storage

| Architecture question | Evidence | Disposition |
|---|---|---|
| Capability-based/provider-scoped storage | POC-001 `docs/poc/POC-001/03_RESULTS.md:21-29`, `05_CONCLUSIONS.md:23-29`; raw direct/SAF records | `CONFIRMED` on measured direct, local SAF and synthetic-provider surfaces. SD/USB, cloud and OEM behavior remain open. |
| Descriptor availability versus seekability | POC-001 `03_RESULTS.md:26,39`; raw `poc/POC-001-storage-capabilities/results/pixel7a-api36-direct-pipe.jsonl:29` records FIFO and `ESPIPE` | `CONFIRMED`. A valid descriptor can be sequential-only; seekability must be probed or explicitly advertised. |
| Direct local rename/move and identity | Raw `pixel7a-api36-direct-pipe.jsonl:21-22` | `REFINED`. The observed regular-file key stayed stable on tested internal/emulated storage; this is not removable/OEM/general scoped-storage evidence. |
| Local SAF operation capabilities | Raw `pixel7a-api36-saf.jsonl:11-22` | `PARTIALLY_RESOLVED`. The tested local provider supported seek/write/append/truncate/rename/move/delete, but provider capability remains runtime/provider-specific. |
| SAF URI/documentId stability | Raw `pixel7a-api36-saf.jsonl:18-19` records both stability flags false | `CONFIRMED` as a rejection of universal stability, not as a claim about every SAF provider. D055 explicitly rejects using either as a universal durable locator. |
| Requested versus resulting filename | POC-001 `03_RESULTS.md:62-70`; raw `pixel7a-api36-saf.jsonl:5,7` | `REFINED`. The tested provider sanitized quote/newline names; callers must observe the created/renamed result. |
| Persisted SAF grant behavior | POC-001 `03_RESULTS.md:72-74`; raw `pixel7a-api36-saf-probe.jsonl:1` and `saf.jsonl:23-24` | `PARTIALLY_RESOLVED`. Process-restart persistence was observed; explicit revocation and recovery were not exercised. |
| More-than-4-GiB semantics | Raw direct/SAF records at `direct-pipe.jsonl:25` and `saf.jsonl:20` | `REFINED`. 64-bit sparse logical lengths passed on tested surfaces; this is not a removable/cloud filesystem guarantee. |
| Direct-storage permission scope | POC-001 manifest `AndroidManifest.xml:5`, run script `run-emulator-tests.sh:12-13`, raw environment `isExternalStorageManager:true` | `REFINED`. Direct results used all-files access and do not establish ordinary scoped-storage behavior. |

### Durable operations

| Architecture question | Evidence | Disposition |
|---|---|---|
| Durable truth independent of executor lifetime | POC-002 `docs/poc/POC-002/00_PLAN.md:10-20`, `02_IMPLEMENTATION_NOTES.md:9-19` | `CONFIRMED` as a principle on the tested harness/device scope. Executor mapping remains open. |
| Checkpoint is not storage truth | Pixel 7a `kill_early`, `kill_mid`, and `kill_near` event logs show persisted completed bytes behind actual partial length; POC-002 `03_RESULTS.md:37-42` | `ACCEPTED` as new D054. Recovery must reconcile durable metadata with actual partial/final destination and source/version facts. |
| VERIFY / FINALIZE / COMPLETE separation | POC-002 `03_RESULTS.md:37-42`; raw event logs | `CONFIRMED` as the required state separation. |
| MOVE source deletion ordering | POC-002 `03_RESULTS.md:47-51`; `move-events.jsonl` | `CONFIRMED` for the tested local path: destination completion precedes source deletion. Universal provider atomicity is not established. |
| Cancellation, conflict, injected ENOSPC and source mutation | POC-002 `03_RESULTS.md:43-59`; `DurableAndroidPoc.java:66-67,82-85` | `REFINED`. Campaign state handling is supported; ENOSPC was synthetic, mutation detection was length+mtime only, and provider version-token semantics remain open. |
| Exact checkpoint cadence | POC-002 `03_RESULTS.md:60-62` | `STILL_POC_REQUIRED`. Overhead is measurable, but no universal interval is justified. |
| Universal full SHA-256 | POC-002 `03_RESULTS.md:60-62` and checksum-cost results | `REJECTED` as a mandatory invariant. Verification policy remains workload/provider/capability dependent. |
| Finalization crash window | POC-002 `DurableAndroidPoc.java:207-218`; documented kill points | `STILL_POC_REQUIRED`. The post-rename/pre-`COMPLETE` window was not proven safe or idempotent; recovery must handle an already-finalized destination conservatively. |
| 2-GiB streaming | POC-002 `03_RESULTS.md:49-59`; `large_2g` artifacts | `CONFIRMED` for bounded streaming at 2 GiB. It is not evidence for 100-GB behavior. |

### Archive engines and security

| Architecture question | Evidence | Disposition |
|---|---|---|
| Java-first multi-engine direction | POC-003 `03_RESULTS.md:56-62`, `06_ARCHITECTURE_IMPACT.md:17-22`; host and Pixel full records contain 54 records and `RUN_DONE=PASS` | `PARTIALLY_RESOLVED`. The direction is viable; exact engine selection is not frozen. |
| One universal archive engine | Commons encrypted ZIP failures versus Zip4j encrypted ZIP success and Junrar RAR coverage in `pixel7a-api36-full-results.jsonl:7-8,17-18,41-49` | `REJECTED` as D056. Use a capability/format matrix rather than one universal engine assumption. |
| Exact libraries and versions | POC-003 `00_PLAN.md:8-12` explicitly leaves versions/APIs/architecture open | `STILL_POC_REQUIRED` for production closure. Commons Compress, Zip4j, Junrar and zstd-jni are candidates only. |
| Parser library as extraction-security boundary | POC-003 `02_IMPLEMENTATION_NOTES.md:11-13`, `04_FAILURES_AND_EDGE_CASES.md:17-23`; harness `ArchivePoc.java:320-335` | `CONFIRMED` as an application-level invariant. The harness guards are fixture-specific and do not certify parser security. |
| RAR4/RAR5, solid and multipart | Raw `pixel7a-api36-full-results.jsonl:41-49`; harness `ArchivePoc.java:271-277` reads the first non-directory entry | `REFINED`. Listing and selected first-entry reads passed; complete all-entry/multipart production behavior remains open. |
| Virtual archive browsing | Architecture V1 `05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:7-20`; local ZIP/7z entry-open records | `CONFIRMED` as a virtual-storage direction with honest access-cost reporting. Tested origin coverage was local, not SAF/remote/non-seekable. |
| TAR.ZST/native and 16 KiB | POC-003 `03_RESULTS.md:19-20,51-54`; `pixel7a-environment.txt` reports 4 KiB pages | `REFINED`. zstd-jni worked on the tested arm64 Pixel 7a; 16 KiB compatibility and libarchive viability remain open. |
| Junrar licensing | POC-003 `05_CONCLUSIONS.md:44-48` | `STILL_POC_REQUIRED`. Technical survival is not a license/distribution decision. |

## Confirmed Architecture V1 principles

P0 provides direct empirical support, within its tested scope, for:

- capability-based/provider-scoped storage rather than universal path authority;
- separate sequential, seekable and random/ranged access capabilities;
- descriptor availability not implying seekability;
- opaque identity separated from display path and mutable provider locator;
- durable operation truth independent of executor instance lifetime;
- explicit partial destinations and reconciliation-based recovery;
- distinct TRANSFER/VERIFY/FINALIZE/COMPLETE phases;
- source deletion only after durably established destination completion for MOVE;
- bounded-memory streaming and 64-bit progress semantics;
- virtual archive browsing where the selected format/engine permits it;
- extraction security as application policy above parser/codec libraries;
- stronger acceptance gates for native and restricted-license components.

These are confirmations of architecture principles, not universal guarantees for every provider, device, filesystem, archive corpus or Android release.

## Refined principles

The following wording is now authoritative:

1. A valid descriptor may be a sequential pipe; seekability is an explicit capability/probe result.
2. SAF URI and `documentId` are provider locators, not universal durable object identity.
3. Requested names and resulting provider names may differ; observe the resulting entry.
4. Direct-storage measurements with all-files access must not be generalized to normal scoped-storage behavior.
5. Durable progress is a checkpoint, not storage truth; reconciliation inspects actual destination state and relevant source/version facts.
6. Verification is policy/capability/workload dependent; no universal full SHA-256 requirement is frozen.
7. Multi-engine archive support is a viable direction, while exact production engines, versions and licenses remain open.
8. Archive parser success and fixture guards do not establish complete extraction security.

## Newly accepted invariants

- `D054`: persisted operation progress/checkpoints are observations that must be reconciled against storage reality before resume, finalization or destructive source deletion.

This is an explicit strengthening of the existing durable-operation and recovery decisions, not authorization to choose a persistence implementation.

## Newly rejected assumptions

- `D055`: SAF URI or `documentId` is universally stable durable object identity.
- `D056`: one archive engine universally covers the required V1 matrix.
- `D057`: every operation must perform complete SHA-256 verification.

The existing rejections—universal local-path authority, universal atomicity/POSIX semantics, blind executor-owned truth, early MOVE source deletion, RAR creation as implied by RAR extraction, and abandoned opaque FFmpegKit—remain unchanged.

## Previous `POC_REQUIRED` decisions

| Existing decision | P0 disposition |
|---|---|
|D020 representative SAF/direct/SD/USB behavior|`PARTIALLY_RESOLVED`; local/direct capability vocabulary and tested behavior are incorporated, but removable/cloud/OEM/revocation/scoped-storage/atomicity remain open.|
|D022 Android executor mapping|`STILL_POC_REQUIRED`; P0 deliberately did not select WorkManager, UIDT, FGS, Service or foreground execution.|
|D023 checkpoint cadence|`STILL_POC_REQUIRED`; overhead was observed but no universal interval was established.|
|D026 remote media seek|`STILL_POC_REQUIRED`; P0 did not test remote media playback.|
|D030 archive engine matrix|`PARTIALLY_RESOLVED`; multi-engine direction is supported, exact stack and production matrix remain open.|
|D035 FFmpeg inclusion|`STILL_POC_REQUIRED`; P0 did not run the Media3/device conversion campaign.|
|D038 SMB/SFTP/WebDAV libraries|`STILL_POC_REQUIRED`; no P1 network PoC was executed.|
|D040 Google Drive OAuth scope|`STILL_POC_REQUIRED`; no cloud scope PoC was executed.|
|D043 root implementation|`STILL_POC_REQUIRED`; root was out of P0 scope.|
|D049 provider/workload concurrency|`STILL_POC_REQUIRED`; P0 does not establish a global concurrency limit.|

No entire P0 question is marked fully resolved. Specific principles are accepted or rejected only where the evidence supports that narrower decision.

## Synthesis overclaims corrected

The P0 synthesis statement that no Architecture V1 principle was contradicted survives independent review. The following stronger readings do not:

- POC-001 is not universal SAF, removable, cloud or OEM evidence; its direct path used all-files access.
- POC-002 is not proof of provider-neutral recovery, real ENOSPC, power-loss durability, strong source-version detection, or the finalization crash window.
- The 2-GiB result is not 100-GB evidence.
- POC-003 completed the defined fixture campaign, not the full production archive matrix.
- `RUN_DONE=PASS` includes expected unsupported-feature outcomes and harness-level guards.
- Junrar records list and read selected first entries; they do not prove complete all-entry multipart extraction.
- Traversal, symlink and expansion results are fixture-specific application/harness evidence, not a universal parser security certification.
- A surviving candidate library is not a selected production dependency.

## Product-scope impact

P0 strengthens the case for a restrained CORE_V1 centered on non-root local/SAF capability-aware browsing, safe local operations, durable/reconcilable transfer semantics, virtual archive browsing with explicit security policy, and provider-neutral media boundaries. It does not expand the product promise to removable, cloud, network, root, native archive, or large-global-index behavior.

The following remain explicitly out of the technology decision made here: applicationId, display name, namespace/package, module/Gradle architecture, SDK/toolchain versions, dependency versions, persistence technology, executor mapping, cloud SDK/REST, OAuth scopes, FFmpeg, signing, CI/CD and release architecture.

## P0.5 readiness assessment

The architecture is ready for user review of a narrowly scoped P0.5 campaign; P0.5 must not be started automatically.

Before a CORE_V1 production scaffold, the minimum readiness evidence should close or explicitly accept the implementation boundaries for:

1. Android 12–16 executor comparison for the intended core workloads, while keeping operation truth executor-independent;
2. Local ↔ SAF durable operation recovery, including mutable locator re-resolution, source-version/conflict handling, finalization crash recovery and real provider error translation;
3. Local/SAF provider-neutral Media3 playback boundaries, if playback is in CORE_V1;
4. Archive production-candidate closure, including licensing, packaging/native acceptance, security corpus, and the supported format matrix.

Useful but non-blocking for foundational core work: SD/USB lifecycle, cloud DocumentsProvider behavior, SMB/SFTP/WebDAV, Root, 1M/global index scale, broad remote media seek, and expanded conversion breadth. They remain P1/P2 work unless product scope later promotes them.

P0.5 is an evidence campaign recommendation only. No P0.5 experiment, production Android scaffold, dependency selection or Technology Freeze occurred on this branch.

## Final disposition

Architecture V1 is **confirmed where P0 measured the principle, refined where P0 exposed provider/device/fixture limits, and still open where the campaign did not provide production-general evidence**. The next state is user review of P0.5 readiness, not automatic implementation.
