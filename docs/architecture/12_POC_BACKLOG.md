# Architecture V1 — Prototype Backlog

Status: BACKLOG DEFINED — **NO POC EXECUTED IN THIS PHASE**

Source authority: the research pack at SHA `b03a2ea99f24206f847f513fa4106e90268f3fc4`.

## Rules for the future PoC campaign

- Each PoC is separately authorized before execution.
- Production architecture must not be inferred merely because a disposable PoC works.
- PoC code is disposable by default and must not automatically become production code.
- Real destructive user data must never be used as test material.
- Credentials, OAuth projects, root-enabled devices, NAS accounts, and cloud accounts must be dedicated test resources where possible.
- Results must record device/API/provider/library versions and failure cases, not only success screenshots.
- A PoC may unlock an architecture decision; it does not authorize production implementation by itself.

## Priority P0 — blocks foundational implementation choices

### POC-001 — SAF / direct / SD / USB capability behavior

**Question / hypothesis:** Can the storage model reliably distinguish direct, SAF, removable, descriptor, seek, write, rename, and disconnect behavior without pretending provider semantics are uniform?

**Why documentation is insufficient:** `ContentResolver`/DocumentsProvider contracts permit provider-specific behavior, including pipe-like descriptors and provider-defined rename/write capabilities. OEM/removable behavior also varies.

**Dependencies:** none beyond safe test media/providers.

**Environment:** disposable test project only; Android 12/API 31 and Android 16/API 36; direct shared storage, AOSP/local SAF provider, representative cloud-backed SAF provider if available, SD card, USB OTG.

**Required device/API/provider:** at least one API 31 physical/emulated path plus Android 16 physical device; SD/USB hardware for removable tests.

**Exact measurements:**

- document flags per entry/provider;
- descriptor type/seek behavior;
- random-read success and latency;
- write/replace/append behavior;
- rename result and URI/identity change;
- list throughput at 10k/100k where feasible;
- disconnect/eject failure shape during read/write;
- persisted-grant behavior across process restart;
- >4 GB behavior on a supporting filesystem.

**Pass criteria:** capability probes predict observed behavior without requiring a fake universal path/seek/atomicity guarantee; disconnect and revoked access are classifiable/recoverable; bounded I/O works.

**Failure criteria:** the proposed capability distinctions cannot describe observed provider behavior, or identity/recovery assumptions become unsafe.

**Decision unlocked:** exact storage capability vocabulary, SAF adapter semantics, all-files/SAF interaction boundaries, descriptor/seek facets.

**Destructive-risk notes:** dedicated removable media only; no deletion outside generated fixtures; do not probe protected user/app data destructively.

**Production code reuse:** `NO` by default. Only test observations/specification are authoritative.

---

### POC-002 — Durable large operation + process death/reconciliation

**Question / hypothesis:** Can a durable Operation Manager safely recover large copy/move-like workflows across process death without tying truth to WorkManager/UIDT/FGS/coroutine lifetime?

**Why documentation is insufficient:** Android execution policies vary by API/workload; documentation cannot prove checkpoint cadence, ambiguous finalization handling, or real process-kill recovery for this product.

**Dependencies:** conceptual provider fake plus safe local/removable test storage; no production module structure required.

**Environment:** API 31 and API 36, with deterministic kill points around create-partial, transfer, checkpoint, verify, finalize, and move-source-delete boundaries.

**Required device/API/provider:** Android 12 and Android 16; local files first, then one removable/network-like fault-injectable source.

**Exact measurements:**

- recovery classification at every kill point;
- bytes of lost/repeated work after restart;
- checkpoint write frequency/cost;
- duplicate side effects;
- ambiguous finalization recovery;
- storage-full behavior;
- cancellation at each stage;
- memory use on >4 GB representative/sparse fixtures;
- executor stop reason and reconciliation behavior.

**Pass criteria:** no source deletion before verified/final destination; no duplicate destructive action after restart; state reconciles deterministically; memory stays bounded; incomplete work is resumable/restartable/blocked rather than silently marked complete.

**Failure criteria:** operation truth is lost with executor/process lifetime, or a crash can produce silent source loss/corrupt final output.

**Decision unlocked:** durable state schema requirements, checkpoint/finalization boundaries, later executor mapping experiments.

**Destructive-risk notes:** generated fixtures only; test move source must be disposable and checksummed.

**Production code reuse:** `NO` by default.

---

### POC-003 — Archive engine fixture/security matrix

**Question / hypothesis:** Which engine combination can meet required browse/extract/create coverage while preserving bounded memory, Android compatibility, and hostile-input safety?

**Why documentation is insufficient:** format support tables do not prove encrypted/solid/multipart correctness, random-access cost, malformed behavior, 100k-entry scale, or Android native integration quality.

**Dependencies:** licensed/generated fixture corpus and safety harness.

**Environment:** Android 12 and Android 16; pure-Java candidates first, native alternative only if justified.

**Required device/API/provider:** local direct + SAF seekable/non-seekable cases; Android 16 for any native 16 KB checks.

**Exact measurements:**

- format fixture pass/fail by ZIP/ZIP64/AES/split, TAR+compressors, 7z, RAR4/RAR5/solid/multipart/encrypted where legally/testably available;
- first-list latency and peak memory at 10k/100k entries;
- single-entry access cost;
- non-seekable origin behavior;
- path traversal/symlink/decompression-limit enforcement;
- malformed/truncated corpus crash/failure behavior;
- native ABI/page-size checks if applicable.

**Pass criteria:** a candidate set provides a clearly documented supported matrix, safe extraction containment/limits, bounded memory, and acceptable failure isolation without unsupported promises.

**Failure criteria:** critical target formats cannot be supported safely/reliably, or native complexity/security cost outweighs proven benefit.

**Decision unlocked:** archive engine stack, supported browse/extract/create matrix, native-vs-Java direction.

**Destructive-risk notes:** extraction sandbox on dedicated temporary storage with hard byte/count limits.

**Production code reuse:** `NO` by default.

## Priority P1 — important capability/risk decisions

### POC-004 — SMB Media3 FLAC streaming and seek

**Question / hypothesis:** Can provider-backed SMB reads deliver usable FLAC playback start/seek/reconnect without full-file download?

**Why documentation is insufficient:** protocol offset I/O does not prove library/Media3/device latency or cache behavior.

**Dependencies:** POC-001 read-capability vocabulary; isolated test NAS/SMB server; disposable player harness.

**Environment:** realistic Wi-Fi latency/jitter; several FLAC sizes/bitrates; disconnect/reconnect injection.

**Required device/API/provider:** Android 16 primary, API 31 compatibility check; Windows/Samba/representative NAS where feasible.

**Exact measurements:** startup latency, seek latency at multiple offsets, bytes fetched per seek, cache hit ratio, reconnection time, behavior on source version change, memory use.

**Pass criteria:** seeking remains responsive under agreed later UX budget, no full-file prerequisite, bounded cache/memory, source-change conflicts detected.

**Failure criteria:** practical playback requires whole download or has unrecoverable/unsafe seek semantics.

**Decision unlocked:** first-class SMB playback scope, random-read adapter/cache architecture requirements.

**Destructive-risk notes:** read-only media fixtures.

**Production code reuse:** `NO` by default.

---

### POC-005 — SMB / SFTP / WebDAV fault and reconnect matrix

**Question / hypothesis:** Can each protocol provider expose truthful capabilities and recover safely from network/session/server failures?

**Why documentation is insufficient:** server interoperability, extensions, redirects, durable handles, Range/ETag, MOVE/COPY, and reconnect semantics vary.

**Dependencies:** dedicated servers/accounts; fault injection.

**Environment:** Windows/Samba/NAS SMB; OpenSSH SFTP; at least two WebDAV implementations; network toggles and server restart.

**Required device/API/provider:** Android 16 primary plus API 31 compatibility path.

**Exact measurements:** list/open latency, random read, write/replace, rename guarantee, server copy/move behavior, resume offset, ETag/version behavior, reconnect success, trust failures, timeout behavior.

**Pass criteria:** capability advertisement matches observed semantics; failures are classifiable; resume validates source version; no insecure trust downgrade is required.

**Failure criteria:** provider cannot safely distinguish unsupported/ambiguous semantics or requires trust-all behavior.

**Decision unlocked:** network library choices, provider capability details, timeout/reconnect policies.

**Destructive-risk notes:** isolated test shares/directories only.

**Production code reuse:** `NO` by default.

---

### POC-006 — Root behavior across Magisk / KernelSU / APatch

**Question / hypothesis:** Can one isolated structured privileged provider model work safely across major root environments without degrading normal mode?

**Why documentation is insufficient:** authorization, SELinux, app profiles, mount namespaces, privileged service lifetime, and descriptor behavior differ.

**Dependencies:** dedicated root-capable test devices/images; no personal primary device/system data.

**Environment:** representative Magisk, KernelSU, APatch setups; safe generated files plus read-only protected-path observation.

**Required device/API/provider:** Android versions supported by chosen root environments, with Android 16 included where practical.

**Exact measurements:** authorization/deny/revoke, service restart, list/read/write generated protected fixture, large-file stream throughput/memory, chmod/chown/symlink, mount-view differences, SELinux denials, optional FD bridge.

**Pass criteria:** normal mode remains unaffected; structured transport handles arbitrary filenames/binary content; bounded streaming works; failures are explicit; no hidden SELinux disable/trust bypass.

**Failure criteria:** architecture depends on one manager's private behavior or requires unsafe shell interpolation/hidden elevation.

**Decision unlocked:** root transport/library/process model and any FD bridge decision.

**Destructive-risk notes:** no mutation of live system/app-private data; generated dedicated fixture locations only; system partitions read-only observation unless a later separately approved destructive test exists.

**Production code reuse:** `NO` by default.

---

### POC-007 — 100k directory / 1M index benchmark

**Question / hypothesis:** Which directory-enumeration, persistence, and search design keeps browsing responsive and memory bounded at scale without making indexing authoritative?

**Why documentation is insufficient:** candidate DB/search technologies have different Android/runtime characteristics; provider enumeration dominates some workloads.

**Dependencies:** generated 10k/100k directory and 1M metadata fixture.

**Environment:** direct local, SAF, one network/cloud-like paged provider fake or real provider; candidate persistence/search designs.

**Required device/API/provider:** midrange Android 16 physical device plus API 31 compatibility path.

**Exact measurements:** time-to-first-visible, full-enumeration time, peak heap/native memory, sort/search latency, index build/update time, index size, incremental update cost, UI responsiveness, cancellation, rebuild behavior.

**Pass criteria:** browsing begins incrementally, no OOM/unbounded list, index can be absent/rebuilt, selected candidate meets later documented latency/storage budgets.

**Failure criteria:** chosen technology forces global-index dependency or unbounded memory/latency.

**Decision unlocked:** relational/search technology, index schema direction, directory-listing strategy.

**Destructive-risk notes:** generated fixture only.

**Production code reuse:** `NO` by default.

## Priority P2 — feature-expansion decisions

### POC-008 — Google Drive least-privilege OAuth capability

**Question / hypothesis:** Can intended first-class Drive UX operate with a narrow/non-restricted scope strategy, or is broader restricted access genuinely required?

**Why documentation is insufficient:** scope sufficiency depends on exact UX/account flows and Google policy behavior.

**Dependencies:** dedicated non-production Google Cloud project/test account; finalized test UX questions.

**Environment:** test credentials only; personal and shared-drive scenarios if relevant.

**Required device/API/provider:** Android 16 test device; Google Drive API.

**Exact measurements:** list/search/open/create/update/delete capabilities by scope, Picker/user-selected flow coverage, token refresh, background access, shared-drive behavior, policy/verification classification.

**Pass criteria:** narrowest viable scope is documented with capability gaps and no hidden broad permission.

**Failure criteria:** desired UX cannot be supported without broader restricted scopes, in which case product/distribution review is required rather than silent escalation.

**Decision unlocked:** Drive scope strategy and possibly first-class Drive product scope.

**Destructive-risk notes:** dedicated test account/files only.

**Production code reuse:** `NO`; credentials must never enter production source.

---

### POC-009 — Cloud resumable transfer

**Question / hypothesis:** Can Drive/OneDrive/Dropbox-like providers safely resume very large transfer after process/network interruption while validating object version and provider session lifetime?

**Why documentation is insufficient:** provider session expiration, SDK/REST behavior, retry/rate limits, and Android lifecycle need empirical validation.

**Dependencies:** POC-002 durable-operation semantics; dedicated cloud accounts.

**Environment:** large generated sparse/compressible/incompressible fixtures as provider permits; controlled network/process interruption.

**Required device/API/provider:** Android 16; representative Drive/OneDrive/Dropbox APIs selected for testing.

**Exact measurements:** resumable offset/session persistence, restart time, bytes replayed, token/session expiration, rate limits, conflict on source/destination change, memory use, partial cleanup.

**Pass criteria:** no silent corruption/source loss; resume/restart decision is deterministic; memory bounded; expired sessions reconcile safely.

**Failure criteria:** provider state cannot be persisted/reconciled safely or requires keeping ephemeral connection objects as durable truth.

**Decision unlocked:** cloud transfer adapter/session semantics and first-class provider implementation readiness.

**Destructive-risk notes:** dedicated test files/accounts.

**Production code reuse:** `NO` by default.

---

### POC-010 — Media3 Transformer device codec matrix

**Question / hypothesis:** Which desired audio/video conversions are reliably covered by platform/Media3 across Android 12–16, and which real gaps remain?

**Why documentation is insufficient:** actual codec/profile/HDR/thermal support varies by device/vendor/API.

**Dependencies:** desired conversion matrix defined for the test, not as product promise.

**Environment:** API 31–36 representative hardware; generated/licensed media corpus.

**Required device/API/provider:** at least Android 12 and Android 16 physical devices, including midrange hardware.

**Exact measurements:** supported input/output codecs/containers, remux vs transcode, throughput, output correctness, HDR/SDR behavior, peak memory, thermal throttling, cancellation, failure codes.

**Pass criteria:** stable platform/Media3 coverage is documented and remaining gaps are concrete enough to judge FFmpeg necessity.

**Failure criteria:** target conversions are unreliable/device-fragmented beyond acceptable product scope.

**Decision unlocked:** conversion format promise and whether an FFmpeg PoC/inclusion review is justified.

**Destructive-risk notes:** read-only input corpus; generated outputs.

**Production code reuse:** `NO` by default.

---

### POC-011 — Stable UI / adaptive / two-pane usability

**Question / hypothesis:** Can the intended Material 3 Expressive product identity, adaptive navigation, dense file lists, two-pane browsing, large fonts, keyboard/mouse, and predictive back be achieved acceptably using stable APIs?

**Why documentation is insufficient:** ergonomic quality and interaction density require hands-on usability/accessibility evaluation, especially for file-manager-specific two-pane behavior.

**Dependencies:** architecture-approved interaction scenarios only; no production backend required.

**Environment:** disposable UI prototype with fake data; phone portrait/landscape, tablet/foldable-like windows, large font, keyboard/mouse.

**Required device/API/provider:** Android 16 primary; API 31 visual/interaction compatibility where practical.

**Exact measurements:** navigation task completion, file-density/row readability, large-font breakage, focus/keyboard selection behavior, predictive-back correctness, frame responsiveness with 10k fake rows, pane usability.

**Pass criteria:** stable APIs satisfy baseline interaction/accessibility/performance needs; any experimental API has a narrowly documented unique benefit.

**Failure criteria:** a core UX requirement cannot be met without unstable APIs or the proposed two-pane model causes significant usability/accessibility problems.

**Decision unlocked:** exact stable/experimental UI surface, pane/scaffold direction, later dependency review.

**Destructive-risk notes:** none; fake data only.

**Production code reuse:** `NO` by default.

## Backlog ordering summary

|Priority|PoC IDs|Purpose|
|---|---|---|
|P0|POC-001..003|Foundational storage, durability, archive-engine decisions before production architecture details.|
|P1|POC-004..007|Remote media/network/root/scale risks that unlock major V1_LATER choices.|
|P2|POC-008..011|Cloud scope/resume, conversion breadth, and UI technology refinement.|

No PoC in this backlog has been executed by the Architecture V1 principle-freeze phase.
