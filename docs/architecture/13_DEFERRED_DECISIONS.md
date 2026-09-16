# Architecture V1 — Deferred Decisions

Status: IMPLEMENTATION CHOICES INTENTIONALLY UNFROZEN

Source authority: research SHA `b03a2ea99f24206f847f513fa4106e90268f3fc4` plus this Architecture V1 review.

Every item below is intentionally **not** selected by the principle-freeze phase. Reopening a decision does not itself authorize implementation; it identifies the milestone at which evidence/product requirements are expected to be sufficient.

|Decision|State|Why deferred|Reopen milestone|
|---|---|---|---|
|Android `applicationId`|`DEFERRED`|No implementation scaffold/release identity is needed for principle review.|Immediately before initial Android project/scaffold authorization.|
|App display name|`DEFERRED`|Repository/product working title does not need to become the final installed-app name yet.|Initial product/branding + Android scaffold review.|
|Kotlin package / namespace|`DEFERRED`|Depends on implementation/repository organization, not architecture principles.|Initial Android scaffold/module review.|
|Gradle module structure|`DEFERRED`|Concrete modules must follow implementation needs, not be guessed from conceptual providers.|After P0 architecture evidence and before production code creation.|
|Gradle architecture / convention plugins|`DEFERRED`|Build organization is an implementation concern.|Initial Android build-system design review.|
|`minSdk`|`DEFERRED`|Android 12 compatibility is a product/test requirement, but the exact configured minimum is not needed yet.|Before Android scaffold; recheck current product requirement and dependency support.|
|`targetSdk`|`DEFERRED`|Play policy is time-sensitive and not architecture authority.|Before Android scaffold/release configuration; recheck current Android/Play policy.|
|`compileSdk`|`DEFERRED`|Toolchain choice should use current supported SDK at implementation time.|Before Android scaffold.|
|Kotlin version|`DEFERRED`|Fast-moving toolchain detail.|Initial build-system freeze.|
|Android Gradle Plugin version|`DEFERRED`|Fast-moving toolchain detail tied to Gradle/JDK/SDK.|Initial build-system freeze.|
|Gradle version|`DEFERRED`|Toolchain compatibility decision.|Initial build-system freeze.|
|JDK version|`DEFERRED`|Depends on selected AGP/Kotlin/dependencies.|Initial build-system freeze.|
|Compose version/BOM strategy|`DEFERRED`|UI principle is stable-first; exact artifacts should be current at implementation time.|UI shell implementation authorization.|
|Material3 version|`DEFERRED`|No reason to freeze the September 2026 research version.|UI shell implementation authorization.|
|Material3 Adaptive version|`DEFERRED`|Same; stable capability is the principle, not a version.|Adaptive UI implementation authorization.|
|Navigation library/version|`DEFERRED`|Exact navigation stack depends on implementation/UI review.|UI shell/navigation design.|
|Media3 version|`DEFERRED`|Media3 concept is accepted, exact artifact must be current and validated.|Playback implementation authorization.|
|Archive libraries|`POC_REQUIRED`|Format/security/performance behavior cannot be settled from docs alone.|After POC-003 archive matrix.|
|Root library/transport|`POC_REQUIRED`|Magisk/KernelSU/APatch, SELinux, mount namespace, streaming and FD behavior need evidence.|After POC-006 root matrix.|
|SMB library|`POC_REQUIRED`|Interoperability/reconnect/security/server-operation behavior must be measured.|After POC-005 plus POC-004 if playback is required.|
|SFTP library|`POC_REQUIRED`|Server extension/host-key/random-I/O behavior must be measured.|After POC-005.|
|WebDAV library|`POC_REQUIRED`|Range/ETag/MOVE/COPY/interoperability varies by server.|After POC-005.|
|Database / durable persistence technology|`DEFERRED`|Operation/index schemas and workloads are not frozen; several credible options exist.|After POC-002 state requirements and POC-007 scale benchmark.|
|Search index technology|`DEFERRED`|Room/SQLite/FTS/AppSearch/SQLDelight roles require workload benchmarks.|After POC-007.|
|Cloud SDK vs direct REST per provider|`DEFERRED`|Android/JVM compatibility, feature coverage, auth, binary weight, update cadence differ.|Provider-specific implementation planning after relevant P2 PoCs.|
|Google Drive OAuth scopes|`POC_REQUIRED`|Narrowest viable scope depends on intended UX/policy.|After POC-008.|
|OneDrive OAuth scopes/account matrix|`DEFERRED`|Depends on selected product scope and account types.|Before OneDrive implementation PoC/authorization.|
|Dropbox OAuth/access model details|`DEFERRED`|Depends on selected product scope and current API/SDK state.|Before Dropbox implementation PoC/authorization.|
|OAuth client registrations / credentials|`DEFERRED`|No production credentials belong in architecture documentation.|Only during separately authorized provider integration, using non-production credentials first.|
|FFmpeg inclusion|`POC_REQUIRED`|Only justified by proven gaps after platform/Media3 device matrix.|After POC-010 identifies concrete unmet requirements; then separate FFmpeg build/license PoC.|
|FFmpeg configure flags/build|`DEFERRED`|Meaningless unless inclusion is justified.|Only after FFmpeg inclusion decision.|
|Metadata/tagging library|`DEFERRED`|FLACtify dependency cannot be inherited automatically; Android/security/write behavior needs review.|Playback metadata/tag-editing implementation planning.|
|Image loading library|`DEFERRED`|No architecture need to select Coil or alternative yet.|UI thumbnail/image implementation planning after performance requirements are specified.|
|Image conversion codec/library stack|`DEFERRED`|Platform-first principle accepted; fidelity/HDR gaps require evidence.|Image conversion feature planning / P2 conversion evidence.|
|Exact Android permissions|`DEFERRED`|Permissions depend on feature scope, SDK, distribution and provider strategy.|Before manifest creation; re-review Play/platform policy.|
|`MANAGE_EXTERNAL_STORAGE` use|`DEFERRED`|Plausible for a core file manager but policy/distribution/UX decision is separate.|Before local-storage implementation/distribution decision, informed by POC-001 and current Play policy.|
|Manifest structure|`DEFERRED`|No Android components exist in this phase.|Initial Android implementation design.|
|Exported component declarations|`DEFERRED`|Least-privilege principle is frozen; exact components do not yet exist.|When each component is designed/manifested.|
|FileProvider paths/configuration|`DEFERRED`|Share/open surface is not implemented yet.|Share/open feature implementation security review.|
|Network Security Config|`DEFERRED`|Strict trust principles are frozen; exact domains/anchors do not exist yet.|Network provider implementation.|
|Credential-store implementation|`DEFERRED`|Keystore-backed protected boundary is frozen; key/schema/library details are not.|First provider requiring persisted secrets.|
|Operation durable-state schema|`DEFERRED`|Conceptual requirements are frozen; exact schema should follow POC-002.|After POC-002.|
|Operation checkpoint cadence|`POC_REQUIRED`|Trade-off between lost work and write amplification requires measurement.|POC-002.|
|Executor mapping: UIDT / WorkManager / FGS / foreground|`POC_REQUIRED`|Android 12–16 workload/policy behavior varies and very long local copy remains unresolved.|POC-002 plus dedicated executor lifecycle measurements.|
|Exact retry/backoff timings|`DEFERRED`|Provider/server-specific and should honor current platform/provider guidance.|Per provider/workload implementation.|
|Exact conflict UX/default overwrite policy|`DEFERRED`|Correctness boundary is frozen but product UX requires later review.|Operation UX implementation.|
|Cache sizes/eviction/key strategy|`DEFERRED`|Needs actual playback/thumbnail/provider workloads.|After media/network/scale PoCs.|
|Offline/pinned file storage policy|`DEFERRED`|Durability distinction is frozen; product storage UX is not.|Playback/offline feature planning.|
|Archive creation format matrix|`DEFERRED`|Depends on POC-003 and product scope.|After POC-003 plus feature-scope review.|
|7z creation support|`DEFERRED`|Engine capability/performance/product value not yet established.|After POC-003.|
|RAR creation support|`DEFERRED` / currently unsupported|No proven legitimate maintainable encoder path; must not be implied from extraction support.|Only if future independent licensing/technology evidence identifies a credible encoder.|
|Office -> PDF implementation|`DEFERRED` / uncommitted|No credible high-fidelity local Android rendering basis established.|Only after new technology evidence plus reference-corpus PoC.|
|Playback format promise|`DEFERRED`|Should follow selected current Media3/platform capabilities and validation.|Playback implementation/test phase.|
|Remote media seek product promise|`POC_REQUIRED`|Protocol support does not prove usable latency/reconnect behavior.|POC-004/005 and cloud range PoC where applicable.|
|Adaptive two-pane exact scaffold|`DEFERRED`|Principle is accepted; file-manager ergonomics require usability evidence.|POC-011 / large-screen UI implementation.|
|Experimental Material Expressive APIs|`DEFERRED`|Stable-first baseline is sufficient; experimental API must justify unique value.|POC-011 or when relevant API stabilizes.|
|Keyboard shortcut map|`DEFERRED`|Input-support requirement accepted, exact bindings are UX detail.|Large-screen/keyboard implementation.|
|Drag-and-drop behavior|`DEFERRED`|Must route through Operation Manager; not needed for initial core.|Large-screen interaction phase.|
|Exact performance budgets|`DEFERRED`|Architecture fixtures are frozen, numeric budgets need measured baselines.|After POC-007 and representative physical-device benchmarks.|
|Exact test frameworks|`DEFERRED`|Test strategy principles are frozen, module/toolchain not yet selected.|Initial implementation/test architecture.|
|CI/CD|`DEFERRED`|Explicitly out of scope and depends on toolchain/release plan.|After initial implementation/toolchain freeze.|
|Signing configuration|`DEFERRED`|No release artifact exists; secrets must not be introduced now.|Release-engineering phase.|
|Release model/channel|`DEFERRED`|Public/private/Play/other distribution not yet selected.|Product distribution review before release engineering.|
|Google Play publication|`DEFERRED`|Architecture preserves the option but does not authorize publication.|Release/distribution phase with current policy review.|
|Pricing/distribution model|`DEFERRED`|Product/business choice, not required for technical principles.|Product/release planning.|

## Explicit still-unfrozen implementation set

At the close of Architecture V1 principle review, the following categories remain unfrozen by design:

- identifiers/naming: applicationId, app display name, namespace/package;
- build/toolchain: modules, Gradle architecture, SDKs, Kotlin/AGP/Gradle/JDK;
- UI dependencies: Compose/Material/Adaptive/Navigation versions;
- media/archive/root/network/cloud dependency/library versions and exact stacks;
- persistence/search/cache technology;
- Android permissions/manifest/components/exported surfaces;
- durable-state schema and Android executor mapping;
- OAuth scopes/registrations/credentials;
- CI/CD/signing/release/publication/pricing;
- all PoC-dependent behavioral guarantees such as remote seek, SAF seek, archive edge cases, root behavior, large-operation lifecycle, and scale budgets.

Principles may be frozen while all of the above remain intentionally open.
