# Architecture V1 — Proposed Product Feature Scope

Status: RECOMMENDATION FOR USER REVIEW — NOT IMPLEMENTATION AUTHORIZATION

Source authority: `README.md`, `UI_DESIGN_V1.md`, the 16-document research pack, and research synthesis SHA `b03a2ea99f24206f847f513fa4106e90268f3fc4`.

This document classifies **product scope**, not technical modules, libraries, SDKs, package structure, or delivery dates.

Architecture support and product scope are different:

- **architecture must allow this** means the frozen boundaries must not make the feature impossible later;
- **V1 must implement this** would be a product commitment.

The categories below are recommendations for review, not permission to start implementation.

## Scope categories

- `CORE_V1` — foundational product behavior that should define the first coherent File Manager release.
- `V1_LATER` — fits the V1 product family but should follow the foundational core and/or required PoCs.
- `POST_V1` — architecture should allow it, but it should not burden the first V1 delivery.
- `RESEARCH_ONLY / UNCOMMITTED` — evidence or product justification is currently insufficient for a commitment.

## Proposed scope table

|Feature|Proposed scope|Reason / dependency|
|---|---|---|
|Normal local file browsing|`CORE_V1`|Core product purpose; must work without root.|
|SAF browsing/access|`CORE_V1`|Essential Android interoperability/removable/document-provider path; capability semantics already frozen.|
|Copy / move / delete / rename|`CORE_V1`|Fundamental file-manager actions; must use safe operation/finalization semantics.|
|Search|`CORE_V1`|Core usability; initial search must not require a complete global index.|
|Multiple selection|`CORE_V1`|Required for practical batch file operations and already central to UI design.|
|Archive browsing|`CORE_V1`|README/UI pillar; virtual-storage principle accepted. Exact engine coverage depends on PoC.|
|Archive extraction|`CORE_V1`|Natural partner to browsing; secure containment/limits are mandatory.|
|Archive creation|`V1_LATER`|Useful but not foundational; exact output formats depend on engine evidence.|
|FLACtify-integrated playback|`CORE_V1`|Explicit product pillar, but implemented through provider-neutral playback rather than wholesale ViewModel reuse.|
|Root access|`V1_LATER`|Important optional power-user capability; deliberately after a mature non-root baseline and root PoC.|
|SMB|`V1_LATER`|Highest-value NAS candidate; requires interoperability/fault/media-seek PoCs before commitment.|
|SFTP|`POST_V1`|Architecture-supported but lower priority than foundational local/SAF/archive/player and first NAS path.|
|WebDAV|`POST_V1`|Architecture-supported; server interoperability/range semantics need PoC.|
|Google Drive|`POST_V1`|First-class provider architecture allowed, but OAuth scope/policy/resume evidence should not block initial V1.|
|OneDrive|`POST_V1`|Same rationale as Drive; provider-specific auth/account behavior remains open.|
|Dropbox|`POST_V1`|Same rationale; SDK/REST and range/resume behavior remain open.|
|Image preview|`CORE_V1`|Low-regret local/SAF usability feature; must remain bounded for large images.|
|PDF/text preview|`CORE_V1`|Common file-manager viewing need; exact renderer/library remains implementation-time.|
|Audio conversion|`V1_LATER`|Useful extension; codec/output matrix must follow Media3/platform PoC.|
|Video conversion|`POST_V1`|Higher thermal/codec/device complexity and larger product surface.|
|Image conversion|`V1_LATER`|Platform-first path is plausible, but metadata/HDR/fidelity needs validation.|
|Archive conversion|`POST_V1`|Depends on mature archive engines and Operation Manager integration.|
|Office conversion|`RESEARCH_ONLY / UNCOMMITTED`|No credible high-fidelity local DOCX/PPTX/XLSX->PDF basis is proven.|
|Indexing|`V1_LATER`|Useful for scale/global search, but browsing/search cannot depend on it; DB/index technology needs benchmark.|
|Duplicate detection|`V1_LATER`|Useful after safe indexing/checksum infrastructure exists; not required for first browsing core.|
|Storage analysis|`V1_LATER`|Good file-manager feature but depends on scalable traversal/index/permission behavior.|

## CORE_V1 recommended shape

The first coherent V1 should therefore center on:

1. non-root local/direct browsing within the selected Android access model;
2. SAF sources;
3. capability-aware list/open/create/basic mutation;
4. durable copy/move/delete/rename semantics;
5. multi-select and basic search independent of global indexing;
6. image + text/PDF preview;
7. archive virtual browsing and secure extraction for the subset proven by archive-engine fixtures;
8. integrated playback using provider-neutral media-source resolution;
9. Material 3 stable-first adaptive UI with dense file navigation and expressive Home/player surfaces;
10. security, process-death, malformed-input, and scale tests alongside the above features.

`CORE_V1` does **not** mean every item may be implemented before its relevant PoC. It means these define the recommended first product boundary once evidence gates are satisfied.

## V1_LATER recommended shape

After the foundational core is stable:

- archive creation;
- optional root provider;
- SMB as the first NAS provider if its P1 PoCs pass;
- global/reconstructible indexing;
- duplicate detection;
- storage analysis;
- image/audio conversion where platform/Media3 evidence is satisfactory.

These features should not distort the initial storage/operation architecture or force premature dependency choices.

## POST_V1 recommendation

SFTP, WebDAV, first-class Drive/OneDrive/Dropbox, video conversion, and archive conversion remain architecturally supported but outside the recommended first V1 burden.

They are intentionally delayed because they introduce independent auth/trust/interoperability/codec/resume surfaces that can be added cleanly after the provider and Operation Manager boundaries are mature.

## RESEARCH_ONLY / UNCOMMITTED

### Office conversion

High-fidelity local Office-to-PDF remains uncommitted. Reopen only with a credible rendering engine plus reference-corpus fidelity evidence.

### RAR creation

RAR creation is not listed as a committed archive-creation subfeature. Current research does not establish a legitimate, maintainable encoder path. It remains unsupported/uncommitted unless later evidence changes this.

## Architecture allowances that must exist even when features are not V1

The V1 architecture must not block later:

- root as an isolated provider;
- SMB/SFTP/WebDAV providers;
- first-class cloud providers plus SAF fallback;
- ranged/random provider reads for playback/archive use;
- resumable cloud/network transfer;
- server-side copy/move when verified;
- global/reconstructible indexing;
- additional conversion engines;
- keyboard/mouse/two-pane large-screen UX.

Allowing these boundaries is not the same as implementing them now.

## Scope review triggers

Revisit this product-scope recommendation after:

- P0 SAF/direct/removable capability PoC;
- P0 durable-operation lifecycle PoC;
- P0 archive-engine/security matrix;
- P1 SMB media/fault PoCs if SMB is retained for V1_LATER;
- P1 root environment PoC before authorizing root implementation;
- P1 scale/index benchmark before choosing indexing technology;
- any user decision that materially changes distribution strategy or V1 product priorities.
