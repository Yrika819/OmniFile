# Architecture V1 — Master Status

Status: ARCHITECTURE V1 PRINCIPLE REVIEW COMPLETE

Creation date: 2026-09-17

Architecture branch: `architecture/v1-principle-freeze`

Architecture synthesis parent SHA: `50febef2376ce7d48ce8d57effda3c233a7cd545`

Source research branch: `research/preimplementation-v1`

Source research SHA: `b03a2ea99f24206f847f513fa4106e90268f3fc4`

Main baseline preserved at: `793d151f9608a684c1d0d4be2e58e5b49d26f823`

## Authority SHA note

The architecture authority is the final tip of `architecture/v1-principle-freeze` containing this complete document set. Its exact full SHA is recorded in the task's final Git closure report after commit/push verification.

A Git commit cannot truthfully embed its own final SHA inside a tracked file: changing the file to add that SHA changes the commit SHA. For that reason this document records the exact source research SHA and exact architecture synthesis parent SHA, while the final architecture authority SHA is resolved mechanically from the branch tip at closure.

## Freeze boundary

This review freezes **principles and boundaries only**.

It does **not** freeze implementation technology unless explicitly stated otherwise. No Android application implementation, PoC, module scaffold, manifest, Gradle Android configuration, production dependency declaration, OAuth credential, signing configuration, CI/CD, APK, or AAB is authorized or created by this phase.

## Decision-state counts

The master table below contains 53 material architecture decisions:

- `ACCEPTED`: **26**
- `REJECTED`: **8**
- `POC_REQUIRED`: **10**
- `DEFERRED`: **9**

These counts cover architecture decisions, not the separate product-scope categories in `11_FEATURE_SCOPE_V1.md`.

## Master architecture decision table

|ID|Decision|State|Confidence|Evidence|Consequence|
|---|---|---|---|---|---|
|D001|Normal/non-root operation is independently functional; root is additive.|`ACCEPTED`|Very high|Research 03, 11, 15; `UI_DESIGN_V1.md`|Baseline product cannot require root authorization or root-manager presence.|
|D002|Universal local-filesystem / `java.io.File` semantic model across all providers.|`REJECTED`|Very high|Research 01, 02, 15|Providers retain non-local identity/access semantics.|
|D003|Storage behavior is capability-based and guarantees are explicit.|`ACCEPTED`|Very high|Research 01, 02, 07, 08, 15|UI/operations query real capabilities instead of inventing semantics.|
|D004|Entry identity is opaque and provider-scoped, separate from display path/name.|`ACCEPTED`|High|Research 01, 02, 08, 12, 15|Rename/path changes do not make display text destructive authority.|
|D005|Sequential, seekable, and random/ranged reads are distinct capabilities.|`ACCEPTED`|High|Research 01, 02, 04, 05|Playback/archive code cannot assume seek from readability alone.|
|D006|Long operations persist durable state independently from Android executor lifetime.|`ACCEPTED`|Very high|Research 09, 13, 15|Process/executor death triggers reconciliation rather than loss of operation truth.|
|D007|Large data flows use bounded streaming and 64-bit length/offset/progress semantics.|`ACCEPTED`|Very high|Research 01, 03, 06, 09, 12|Large files do not require memory proportional to object size.|
|D008|Cross-provider move deletes source only after destination verification/finalization.|`ACCEPTED`|Very high|Research 02, 09, 11, 15|Source loss is prevented across crashes, network loss, storage full, and cancellation.|
|D009|Indexes, metadata caches, and thumbnails are reconstructible accelerators, not mutation authority.|`ACCEPTED`|High|Research 12, 15|Browsing/mutation remains valid with stale/missing/rebuilding index.|
|D010|Playback resolves provider-neutral media sources rather than requiring local paths.|`ACCEPTED`|High|Research 02, 05, 15|Local/SAF/NAS/cloud media can share playback architecture without fake paths.|
|D011|Stable Android UI APIs form the baseline; expressive identity does not require alpha APIs.|`ACCEPTED`|High|Research 10, 15; `UI_DESIGN_V1.md`|Experimental APIs need explicit later justification.|
|D012|Security and hostile-input tests are designed alongside each feature.|`ACCEPTED`|Very high|Research 11, 13, 15|Security validation is continuous, not a final hardening pass.|
|D013|Unknown provider metadata remains unknown rather than synthesized.|`ACCEPTED`|High|Research 02|No fake timestamps/POSIX/inode-like semantics are created.|
|D014|Provider failures retain structured architecture-level categories.|`ACCEPTED`|High|Research 02, 09|Retry/reconcile/UI can distinguish permission, conflict, disconnect, quota, trust, auth, corruption, and cancellation.|
|D015|Provider/source lifecycle, disconnect, revocation, and reconnect are first-class concepts.|`ACCEPTED`|High|Research 01, 02, 07, 09|Open handles/sessions are ephemeral; durable operations re-resolve identities.|
|D016|Universal append/random-write semantics across providers.|`REJECTED`|High|Research 01, 02|Write modes are explicit capabilities, not one generic output-stream assumption.|
|D017|Universal atomic rename/move guarantee.|`REJECTED`|Very high|Research 01, 02, 07, 08|Atomicity is provider/filesystem specific and never inferred from method name.|
|D018|Universal POSIX metadata/chmod/chown/symlink semantics.|`REJECTED`|Very high|Research 01, 02, 03|Unix metadata stays optional/direct/root/provider-specific.|
|D019|Provider-native/server-side copy/move may be used when capability/guarantee is known.|`ACCEPTED`|High|Research 02, 07, 08|Operation planning may optimize same-provider work without making it universal.|
|D020|Representative SAF/direct/SD/USB seek/write/rename/disconnect behavior.|`POC_REQUIRED`|High uncertainty in provider behavior|Research 01, 02, 15|POC-001 unlocks exact SAF capability vocabulary and removable semantics.|
|D021|Use of `MANAGE_EXTERNAL_STORAGE` / exact standard-mode permission strategy.|`DEFERRED`|High policy evidence, product decision open|Research 01, 14, 15|Reopen before local-storage implementation/distribution review with current policy.|
|D022|Exact WorkManager/UIDT/FGS/foreground executor mapping.|`POC_REQUIRED`|Very high principle confidence, mechanism unresolved|Research 09, 15|POC-002 and lifecycle measurements must precede mapping freeze.|
|D023|Operation checkpoint cadence.|`POC_REQUIRED`|Medium|Research 09, 15|Measure write amplification vs lost-progress window in POC-002.|
|D024|Media3 / MediaSession concepts as playback foundation.|`ACCEPTED`|High|Research 05, 15|Exact Media3 version/class structure remains unfrozen.|
|D025|Wholesale reuse of FLACtify `PlayerViewModel` as File Manager architecture.|`REJECTED`|High|Research 05, 15|Reuse behavior/session concepts; separate responsibilities instead.|
|D026|Product-quality remote media random access/seek over NAS/cloud.|`POC_REQUIRED`|Medium|Research 02, 05, 07, 08, 15|POC-004/005 and cloud range tests precede playback promise.|
|D027|Stream cache, metadata cache, artwork/thumbnail cache, offline files, and operation partials are distinct categories.|`ACCEPTED`|High|Research 05, 11, 15|Generic cache clearing cannot destroy offline content or recovery data.|
|D028|Archives are browsable virtual storage where format/engine permits.|`ACCEPTED`|High|Research 04, 15; `UI_DESIGN_V1.md`|Archive entries participate in provider-style list/read with honest limitations.|
|D029|Archive extraction enforces containment, symlink policy, and expansion limits.|`ACCEPTED`|Very high|Research 04, 11, 15|Traversal/bombs/malformed input are baseline design concerns.|
|D030|Exact archive engine combination and supported edge-case matrix.|`POC_REQUIRED`|Medium-high|Research 04, 14, 15|POC-003 decides Java/native stack and concrete format guarantees.|
|D031|RAR creation as an assumed V1 capability.|`REJECTED`|High|Research 04, 14|Do not promise unless future independent licensed encoder evidence exists.|
|D032|Conversion/extraction/compression participate in durable Operation Manager semantics.|`ACCEPTED`|High|Research 06, 09, 15|Partial/verify/finalize/recovery apply beyond copy/upload.|
|D033|Platform/Media3-first direction for mainstream media conversion.|`ACCEPTED`|Medium-high|Research 06, 14, 15|Lower native/license burden; exact capability promise waits for device evidence.|
|D034|Abandoned opaque FFmpegKit binaries as a production basis.|`REJECTED`|High|Research 06, 14|Only maintained/reproducible source-based evaluation is eligible if FFmpeg becomes necessary.|
|D035|FFmpeg inclusion in production architecture.|`POC_REQUIRED`|Medium|Research 06, 14, 15|Only reconsider after POC-010 proves meaningful platform/Media3 gaps.|
|D036|High-fidelity local Office -> PDF feature/engine.|`DEFERRED`|High evidence against current assumptions|Research 06, 15|Remain unsupported/uncommitted until credible new engine plus reference-corpus evidence.|
|D037|First-class cloud providers may coexist with SAF fallback.|`ACCEPTED`|High|Research 08, 15|Advanced resume/search/change/version capabilities need not be lost to SAF-only design.|
|D038|Exact SMB/SFTP/WebDAV libraries and detailed protocol guarantees.|`POC_REQUIRED`|Medium-high|Research 07, 14, 15|POC-005 selects based on interoperability/security/fault behavior.|
|D039|Cloud SDK vs direct REST per provider.|`DEFERRED`|Medium|Research 08, 14, 15|Reopen during provider-specific implementation after PoCs/toolchain review.|
|D040|Google Drive least-privilege OAuth scope strategy.|`POC_REQUIRED`|High policy importance|Research 08, 14, 15|POC-008 determines whether desired UX needs broader restricted scope.|
|D041|Credential/trust boundary: protected secrets, strict TLS, strict SSH host keys, least privilege, no token logging.|`ACCEPTED`|Very high|Research 07, 08, 11, 14, 15|Security controls are mandatory regardless of chosen client library.|
|D042|Arbitrary shell-string construction as normal root file transport.|`REJECTED`|Very high|Research 03, 11, 15|Prefer structured privileged file/service transport; adversarial names remain safe.|
|D043|Exact root implementation/library/service/FD bridge across Magisk/KernelSU/APatch.|`POC_REQUIRED`|Medium-high|Research 03, 15|POC-006 required before root implementation freeze.|
|D044|Protected/system root mutations receive stronger safeguards and explicit elevation.|`ACCEPTED`|High|Research 03, 11; `UI_DESIGN_V1.md`|Normal user-storage operations stay simpler while privileged risk is visible.|
|D045|Adaptive navigation and future two-pane large-screen browsing are architecture requirements.|`ACCEPTED`|High|Research 10; `UI_DESIGN_V1.md`|Exact scaffold remains a later UX/implementation choice.|
|D046|Storage actions shown by UI are capability-aware.|`ACCEPTED`|Very high|Research 02, 10, 15|Unsupported actions cannot be presented as universally available.|
|D047|Exact experimental/new Material Expressive APIs.|`DEFERRED`|Medium|Research 10, 15|Reopen when stable or when POC-011 proves unique value.|
|D048|Database and search-index technology.|`DEFERRED`|Medium|Research 12, 15|Reopen after operation schema needs and POC-007 1M-scale evidence.|
|D049|Provider/workload concurrency limits.|`POC_REQUIRED`|Medium|Research 12, 15|Tune by 100k/tiny-file/network/cloud benchmarks, not one global thread count.|
|D050|Exact Android permissions, manifest structure, component exports, FileProvider/network config.|`DEFERRED`|High principle, implementation absent|Research 01, 11, 14|Reopen when concrete Android components/features are authorized.|
|D051|applicationId, display name, namespace, modules, Gradle/SDK/toolchain and dependency versions.|`DEFERRED`|N/A by design|Research index and 15; task scope|Remain intentionally unfrozen until initial implementation/scaffold and relevant PoCs.|
|D052|CI/CD, signing, release channel, Play publication, pricing/distribution.|`DEFERRED`|Premature|Research 14, 15|Reopen during release engineering/product distribution review.|
|D053|Exact test frameworks, device farm, and coverage thresholds.|`DEFERRED`|High test-strategy confidence, tooling premature|Research 13|Reopen after implementation/module architecture exists.|

## Accepted summary

The accepted core is deliberately small in concept and broad in consequence: capability-based providers, provider-scoped identity, separate access semantics, durable/reconcilable operations, safe move finalization, bounded/64-bit data flow, reconstructible caches/indexes, provider-neutral playback, secure hostile-input handling, strict credential/trust boundaries, virtual archives, additive root, stable-first adaptive UI, and capability-aware actions.

## Rejected summary

Architecture V1 explicitly rejects false-uniformity assumptions: one universal local-filesystem model, universal append/atomic/POSIX guarantees, root shell-string transport as the normal path, wholesale FLACtify ViewModel reuse, RAR creation as an assumed capability, and abandoned opaque FFmpegKit binaries.

## Highest-priority `POC_REQUIRED` items

P0:

1. `POC-001` — SAF/direct/SD/USB capability behavior on API 31 and API 36.
2. `POC-002` — durable large operation + process death/reconciliation.
3. `POC-003` — archive engine fixture/security matrix.

P1/P2 items are defined in `12_POC_BACKLOG.md` and must not start during this phase.

## Explicit still-unfrozen implementation choices

At architecture-freeze completion, the following remain intentionally unfrozen:

- `applicationId`;
- app display name;
- namespace/package;
- Gradle module structure and Gradle architecture;
- `minSdk`, `targetSdk`, `compileSdk`;
- Kotlin, AGP, Gradle, JDK versions;
- Compose, Material3, Material3 Adaptive, Navigation versions;
- Media3 version;
- archive libraries and versions;
- root library/transport/version;
- SMB/SFTP/WebDAV libraries;
- database and search-index technology;
- cloud SDK vs REST choices;
- OAuth scopes, registrations, credentials;
- FFmpeg inclusion/configuration;
- metadata/tagging library;
- image loading/conversion libraries;
- exact Android permissions and manifest structure;
- exported components and FileProvider/network configuration;
- operation durable-state schema and checkpoint cadence;
- WorkManager/UIDT/FGS/foreground executor mapping;
- exact retry/conflict/cache policies;
- CI/CD;
- signing;
- release model/channel;
- Play publication;
- pricing/distribution model;
- all PoC-dependent guarantees.

See `13_DEFERRED_DECISIONS.md` for reopen milestones.

## Product-scope recommendation

`11_FEATURE_SCOPE_V1.md` recommends a restrained first V1 centered on non-root local/SAF browsing, core safe file operations, search/multi-select, archive browse/extract, integrated provider-neutral playback, and common previews. Root, SMB, indexing, storage analysis, duplicate detection, and selected conversion features are recommended later within the V1 family; additional NAS/cloud and broader conversion features are post-V1 or uncommitted.

This is a recommendation for user review, not authorization to implement.

## Consistency result

The architecture review found **no objective contradiction among the 16 research documents that required rewriting historical research**.

Apparent tension between `UI_DESIGN_V1.md` saying “Material 3 Expressive” and the research recommending stable-first APIs is resolved without rewriting either source: the expressive requirement is frozen as a product/UI policy, while a specific alpha API/dependency is not.

Likewise, broad archive/player/root/network/cloud product intent is preserved as architecture allowance without converting every discussed feature into `CORE_V1` or selecting unproven libraries.

## End-state statement

**ARCHITECTURE V1 PRINCIPLES REVIEWED AND FROZEN; IMPLEMENTATION TECHNOLOGY AND POC-DEPENDENT DECISIONS REMAIN UNFROZEN.**
