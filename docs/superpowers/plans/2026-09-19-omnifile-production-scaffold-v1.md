# OmniFile Production Scaffold V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the first real, buildable, testable OmniFile Android production project without implementing file-management functionality or final product UI.

**Architecture:** Use one root Gradle project with one `app` Android application module. Keep startup construction plain, keep production dependencies minimal, and establish package boundaries only where real scaffold code requires them. Use `com.omnifile` as both the frozen applicationId and an explicitly documented scaffold namespace decision.

**Tech Stack:** Current stable mutually supported Android SDK/Build Tools, JDK, Kotlin, Android Gradle Plugin, Gradle, Jetpack Compose, Material3, Material3 Adaptive foundation, AndroidX lifecycle, JVM unit tests, Android instrumentation tests, and Gradle dependency locking/verification.

**Spec:** User-provided `OMNIFILE — PRODUCTION SCAFFOLD V1 REAL ANDROID PROJECT INITIALIZATION ONLY` request and frozen Technology Freeze `5b6d4f9c025ab86917be77d5e96663aa7c414d87`.

## Global Constraints

- Base the branch on `5b6d4f9c025ab86917be77d5e96663aa7c414d87`.
- Branch name is `development/core-v1-production-scaffold`.
- Preserve the current root worktree on `poc/p0-evidence-synthesis` unchanged.
- Display name is `OmniFile`; applicationId is exactly `com.omnifile`.
- Namespace is `com.omnifile`, documented as a new scaffold implementation decision, not as a historical user freeze.
- Use `minSdk = 31`; do not raise the Android 12 floor.
- Resolve compileSdk, targetSdk, JDK, Kotlin, AGP, Gradle, Compose, Material3, and Adaptive versions from current stable supported sources at execution time.
- Start with one `app` module; do not create conceptual feature/core modules.
- Add no file browser, SAF provider, StorageProvider, file operation, archive, Media3, database, WorkManager, search, home, settings, root, network, cloud, durable-operation, signing, or release implementation.
- Do not implement saved final UI authority; the screen is a temporary scaffold shell.
- Keep production native `.so` inventory empty unless a required scaffold dependency proves otherwise.
- Do not invent OSS notices for dependencies that are not included.
- Use focused commits and a normal non-force push only after verification and review.

---

### Task 1: Reconcile authority and freeze build-system choices

**Files:**

- Read: `TECHNOLOGY_FREEZE_V1.md`
- Read: `docs/architecture/00_ARCHITECTURE_STATUS.md`
- Read: `docs/architecture/01_CORE_PRINCIPLES.md`
- Read: `docs/architecture/10_UI_ARCHITECTURE_PRINCIPLES.md`
- Read: `docs/architecture/13_DEFERRED_DECISIONS.md`
- Read: `docs/architecture/ADR/ADR-001-capability-based-storage.md`
- Read: `docs/architecture/ADR/ADR-003-separate-access-capabilities.md`
- Read: `docs/architecture/ADR/ADR-004-durable-operation-state.md`
- Read: `docs/architecture/ADR/ADR-007-provider-neutral-playback.md`
- Create: `docs/superpowers/plans/2026-09-19-omnifile-production-scaffold-v1.md`

**Interfaces:**

- Consumes: Technology Freeze and Architecture V1 authority documents.
- Produces: A verified decision record for identifiers, API floor, stable toolchain, minimal module shape, stable UI foundation, dependency policy, and deferred decisions.

- [ ] Verify branch, HEAD, and protected Technology Freeze ref. Expected: branch `development/core-v1-production-scaffold`, HEAD `5b6d4f9c025ab86917be77d5e96663aa7c414d87`, and the protected ref points to the same SHA.
- [ ] Inspect locally installed Android/JDK toolchains and current official stable compatibility guidance before writing Gradle files. Reject alpha, beta, RC, and canary releases.
- [ ] Confirm that namespace `com.omnifile` is a new explicit scaffold decision and that only applicationId was historically frozen.
- [ ] Leave deferred database, navigation, archive, Media3, network, cloud, root, signing, and release decisions unresolved.
- [ ] Commit the plan with `git add docs/superpowers/plans/2026-09-19-omnifile-production-scaffold-v1.md && git commit -m "docs: plan OmniFile production scaffold v1"`.

### Task 2: Initialize the minimal Android Gradle project

**Files:**

- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml` if justified by the selected stable graph
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: Gradle wrapper files under `gradle/wrapper` plus `gradlew` and `gradlew.bat`
- Modify: `.gitignore` only when required for generated Gradle output

**Interfaces:**

- Consumes: Task 1’s selected stable JDK/Kotlin/AGP/Gradle/SDK/Compose versions.
- Produces: A single `:app` application with `namespace = "com.omnifile"`, `applicationId = "com.omnifile"`, `minSdk = 31`, current stable compile/target SDK, and reproducible dependency resolution.

- [ ] Write the smallest root settings and plugin declarations; add no future feature libraries.
- [ ] Configure `:app` for Android and Compose, frozen identity values, stable compile/target SDK, debug defaults, and no signing/release configuration.
- [ ] Configure dependency locking and dependency verification for the actual resolvable graph; verify both mechanisms with Gradle.
- [ ] Generate and pin the selected stable Gradle wrapper; verify `./gradlew --version`.
- [ ] Run `./gradlew :app:assembleDebug` and confirm a debug APK is produced.
- [ ] Commit the build foundation as `build: initialize OmniFile Android production scaffold`.

### Task 3: Add the temporary production shell and stable Material3 foundation

**Files:**

- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/omnifile/MainActivity.kt`
- Create: `app/src/main/java/com/omnifile/ui/theme/Color.kt`
- Create: `app/src/main/java/com/omnifile/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/omnifile/ui/theme/Type.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: required Android backup/theme resources only when the selected stable setup needs them

**Interfaces:**

- Consumes: `:app` configuration and stable Compose/Material3 APIs.
- Produces: A launcher activity rendering a deliberately temporary shell with label `OmniFile` and text equivalent to `Production scaffold operational`.

- [ ] Add only the launcher activity and required application attributes; do not add storage, network, root, provider, service, receiver, or unrelated exported declarations.
- [ ] Implement `MainActivity` with plain construction, edge-to-edge Compose startup, and no global mutable state or DI framework.
- [ ] Add stable Material3 light/dark theme, dynamic color capability where stable, and typography; avoid experimental Material Expressive APIs.
- [ ] Search production source and manifest for `StorageProvider`, `Media3`, `WorkManager`, archive engines, database APIs, file operations, network/cloud/root components, and final UI screens; all must be absent.
- [ ] Commit as `feat: add temporary OmniFile production shell`.

### Task 4: Establish real unit and instrumentation test lanes

**Files:**

- Create: `app/src/test/java/com/omnifile/ProductionScaffoldTest.kt`
- Create: `app/src/androidTest/java/com/omnifile/ProductionScaffoldInstrumentedTest.kt`
- Modify: `app/build.gradle.kts` only for required stable test dependencies/options

**Interfaces:**

- Consumes: `MainActivity` and application identity.
- Produces: One deterministic host test and one Android test proving the shell launches and the Compose root exists.

- [ ] Add a production-owned deterministic host test for the frozen application identity or another real scaffold invariant.
- [ ] Run `./gradlew :app:testDebugUnitTest` and record the result.
- [ ] Add an instrumentation test that launches `MainActivity`, checks the Compose root, and checks the temporary scaffold text.
- [ ] Run `./gradlew :app:connectedDebugAndroidTest` when an authorized device/emulator exists; otherwise record `NOT_TESTED` with the exact environment reason.
- [ ] Commit as `test: establish OmniFile scaffold verification lanes`.

### Task 5: Add OSS/NOTICE and scaffold authority documentation

**Files:**

- Create: `NOTICE.md` or `docs/production/NOTICE.md` according to repository convention
- Create: `docs/production/PRODUCTION_SCAFFOLD_V1.md`
- Create: `docs/production/dependency-inventory.md` only if needed for an auditable register

**Interfaces:**

- Consumes: Actual resolved production dependency graph, verification metadata, test results, and device/build evidence.
- Produces: A concise baseline record distinguishing frozen authority, new scaffold decisions, verified facts, explicit gates, and deferred decisions.

- [ ] Record Technology Freeze SHA, branch/base, applicationId, explicit namespace rationale, single-module decision, and the statement that namespace was not historically user-frozen.
- [ ] Record JDK, Kotlin, AGP, Gradle, compileSdk, targetSdk, minSdk, Compose/Material3/Adaptive family, exact production dependencies, locking/verification mechanism, and actual notice inventory.
- [ ] Record unit, lint, assemble, instrumentation, Pixel 7a, API31, native `.so`, APK metadata, review, protected ref, and remote SHA results without overclaiming.
- [ ] Record deferred decisions and state that the next campaign may begin the first authorized CORE_V1 slice while this campaign stops.
- [ ] Commit as `docs: record OmniFile production scaffold authority`.

### Task 6: Complete verification, review, and controlled publication

**Files:**

- Review: complete branch diff from Technology Freeze base
- Modify: only files required to resolve actionable findings

**Interfaces:**

- Consumes: Complete scaffold branch and all evidence from Tasks 1–5.
- Produces: Verified final scaffold commits, review disposition, pushed branch, and local/remote SHA equality.

- [ ] Run the supported final set: `./gradlew clean`, `./gradlew test lintDebug :app:assembleDebug`, dependency verification/locking checks, and any supported build-health/resource/manifest checks.
- [ ] Inspect APK metadata mechanically: applicationId `com.omnifile`, label `OmniFile`, versionCode/versionName, minSdk, targetSdk, compileSdk evidence, and SHA-256.
- [ ] List `lib/**/*.so` inside the APK; expected production inventory is empty. If not empty, record the dependency and native/16 KiB gate.
- [ ] Install the exact final APK on Pixel 7a if connected and authorized; verify package, label, launch, relaunch, force-stop/relaunch, and startup logcat without touching personal data or system/root configuration.
- [ ] Run the independent review-agent pass against the Technology Freeze base; resolve every actionable finding and rerun affected checks.
- [ ] Verify protected refs and branch ancestry; do not modify protected history.
- [ ] Push normally with `git push -u origin development/core-v1-production-scaffold`, fetch the branch, and prove local HEAD equals remote HEAD. Do not force-push or merge main.
- [ ] Stop at the scaffold boundary and report either `OMNIFILE_PRODUCTION_SCAFFOLD_V1_COMPLETE — READY FOR FIRST CORE_V1 VERTICAL SLICE` or `OMNIFILE_PRODUCTION_SCAFFOLD_V1_INCOMPLETE — <exact blocker>`.
