# OmniFile Core V1 Files Browsing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the temporary OmniFile scaffold screen with a read-only, provider-neutral Files browser that navigates an app-private local root and a real user-selected SAF tree.

**Architecture:** Keep `StorageProvider` capability-based and provider-scoped. `LocalStorageProvider` owns `Path`/`File` details behind an opaque `EntryRef`; `SafStorageProvider` owns tree `Uri`/document IDs behind a session-scoped opaque reference and never advertises seekability from descriptor presence. `FilesRepository` and a cancellation-aware `FilesViewModel` own provider dispatch, navigation, error normalization, and UI state; Compose only renders state and emits user intents.

**Tech Stack:** Existing API 31/minSdk, target/compile SDK 36, Kotlin 2.2.10, Compose BOM 2025.12.00, Material3/Adaptive 1.2.0, Android `ContentResolver`/`DocumentsContract`, Kotlin coroutines already present in the scaffold dependency graph, JUnit host tests, and Android instrumentation tests with a controlled `DocumentsProvider`.

**Spec:** User-provided “OMNIFILE — CORE_V1 VERTICAL SLICE 01 FILE BROWSING FOUNDATION LOCAL + SAF READ-ONLY END-TO-END” brief, reconciled with `docs/architecture/02_STORAGE_MODEL.md`, ADR-001/002/003, `docs/research/02_STORAGE_PROVIDER_ARCHITECTURE.md`, and `TECHNOLOGY_FREEZE_V1.md`.

## Global Constraints

- Work only on `development/core-v1-files-browsing-v1`, created from scaffold SHA `4c997aa00d376be4298ebc361d5794803ddb6718`, in the isolated worktree.
- Preserve `OmniFile` and `com.omnifile`; do not modify the scaffold branch or main.
- No universal `java.io.File`, `java.nio.Path`, raw SAF URI, or document ID in provider-neutral model/UI identity.
- Read-only slice only: no create, write, rename, delete, move, copy, archive, Media3, Room, WorkManager, broad storage permission, or final bottom navigation.
- Unknown size, modified time, and MIME remain `null`; no fake zero values and no recursive counts/thumbnails.
- SAF persisted permission is read-only and scoped to the user-selected tree; invalid/revoked grants become recoverable UI errors.
- Stage only intended files; do not update frozen toolchain versions. Dependency lock/verification changes require a concrete newly added dependency.

### Task 1: Provider-neutral model and deterministic local provider

**Files:**
- Create: `app/src/main/java/com/omnifile/storage/StorageModel.kt`
- Create: `app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt`
- Test: `app/src/test/java/com/omnifile/storage/LocalStorageProviderTest.kt`
- Test: `app/src/test/java/com/omnifile/storage/StorageModelTest.kt`

**Interfaces:**
- `StorageProvider` exposes `id`, `root()`, and `listChildren(directory: EntryRef)` as suspend `StorageResult` operations.
- `ProviderId` identifies a provider instance; `EntryRef` is provider-scoped and opaque above the provider.
- `StorageEntry` contains `ref`, name, `EntryKind`, nullable size/modified/MIME, and `Set<StorageCapability>`.
- Capabilities are distinct values: `LIST_CHILDREN`, `READ_SEQUENTIAL`, `READ_SEEKABLE`, `WRITE`, `RENAME`, `DELETE`.
- `StorageError` has `NotFound`, `PermissionDenied`, `StaleReference`, `Unsupported`, `IoFailure`, and `Cancelled` cases; provider exceptions do not escape.

- [ ] Write failing tests for empty/nested directories, regular files, Unicode names, nullable metadata, deterministic name ordering, root containment, deleted references, and two-provider identity isolation.
- [ ] Implement the model and `LocalStorageProvider` using `java.nio.file.Path` only internally. Normalize the configured root once, reject references outside it, map regular files to sequential+seekable read capabilities, map directories to list capability, and sort direct children by display name without recursion.
- [ ] Run `ANDROID_HOME=~/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/omnifile-gradle-cache ./gradlew testDebugUnitTest --dependency-verification=off` and confirm the new host tests pass.

### Task 2: SAF provider and controlled Android provider tests

**Files:**
- Create: `app/src/main/java/com/omnifile/storage/SafStorageProvider.kt`
- Create: `app/src/main/java/com/omnifile/storage/SafTreeGrantStore.kt`
- Create: `app/src/androidTest/java/com/omnifile/storage/TestDocumentsProvider.kt`
- Create: `app/src/androidTest/java/com/omnifile/storage/SafStorageProviderInstrumentedTest.kt`
- Modify: `app/src/androidTest/AndroidManifest.xml` to register only the controlled test provider.

**Interfaces:**
- `SafStorageProvider(contentResolver, treeUri, providerId)` queries `DocumentsContract.Document` columns and creates opaque internal SAF refs containing the selected tree context and current document locator.
- `SafTreeGrantStore` stores only the selected tree URI string in narrow app preferences; it validates matching persisted read permission before restoring.

- [ ] Write instrumentation tests for accepted root, child and nested listing, file/folder metadata, provider flags, and stale/permission failure normalization.
- [ ] Implement root and child queries with `ContentResolver`/`DocumentsContract`; preserve the current navigation root and never list above it. Map `FLAG_SUPPORTS_WRITE`, `FLAG_SUPPORTS_RENAME`, and `FLAG_SUPPORTS_DELETE` only to capability values; never infer `READ_SEEKABLE` from a `ParcelFileDescriptor` and do not perform per-row content reads.
- [ ] Persist only `FLAG_GRANT_READ_URI_PERMISSION` from `takePersistableUriPermission`; restore only when `persistedUriPermissions` proves the grant is still present. Map `SecurityException`, missing documents, invalid locators, and provider I/O into `StorageError`.
- [ ] Run the focused connected test when a device is available; keep controlled-provider results labeled synthetic and separate from Pixel real-SAF evidence.

### Task 3: Repository, navigation, lifecycle state, and race handling

**Files:**
- Create: `app/src/main/java/com/omnifile/files/FilesRepository.kt`
- Create: `app/src/main/java/com/omnifile/files/FilesViewModel.kt`
- Test: `app/src/test/java/com/omnifile/files/FilesRepositoryTest.kt`
- Test: `app/src/test/java/com/omnifile/files/FilesViewModelTest.kt`

**Interfaces:**
- `FilesRepository` dispatches by `ProviderId`, lists roots/children, and exposes normalized `StorageResult` values; UI never sees a `ContentResolver` or `Path`.
- `FilesUiState` is one sealed state: `SourceSelection`, `Loading`, `Content`, `Empty`, or `Error`.
- `FilesViewModel` accepts `selectLocal()`, `selectSaf(uri)`, `openDirectory(entry)`, `goBack()`, `retry()`, and `restorePersistedSaf()` intents.

- [ ] Write tests for provider dispatch, root navigation boundary, stack back behavior, empty results, recoverable failures, and a delayed fake provider where Folder A finishes after Folder B but cannot overwrite B.
- [ ] Implement repository/provider registration and a single navigation stack rooted at the selected source; cancel the previous listing job and use a monotonically captured location token before publishing results.
- [ ] Keep the ViewModel lifecycle-scoped, perform provider work off the main thread through structured coroutines, and preserve the current location in `Error` for retry/back.
- [ ] Run focused host tests, then the full host unit task with the diagnostic verification flag until the repository’s verification metadata is intentionally regenerated as part of dependency work (if any).

### Task 4: Production Files screen and real tree-picker flow

**Files:**
- Modify: `app/src/main/java/com/omnifile/MainActivity.kt`
- Create: `app/src/main/java/com/omnifile/ui/files/FilesScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/androidTest/java/com/omnifile/ProductionScaffoldInstrumentedTest.kt`

- [ ] Replace the temporary scaffold with a Material3 Files screen: title/breadcrumb presentation, local source button, “フォルダを選択” tree-picker button, loading, empty, error/retry, and compact rows with folder/file placeholders, name, known size, and known modified date.
- [ ] Register `ActivityResultContracts.OpenDocumentTree`, pass the selected URI to the ViewModel, and launch it only from the source-selection UI. Do not add Home/Search/Music/Settings navigation or final visual-polish assets.
- [ ] Make row clicks open only directories; make Android Back pop within the selected root and otherwise return to source selection. Do not expose raw URI/path identity in displayed labels.
- [ ] Update instrumentation smoke coverage to assert package identity and that the real Files screen is rendered without broad storage permissions.

### Task 5: Documentation, verification, and Pixel 7a evidence

**Files:**
- Create: `docs/production/VERTICAL_SLICE_01_FILE_BROWSING.md`
- Modify only if needed: `docs/production/NOTICE.md`, `gradle/libs.versions.toml`, `app/gradle.lockfile`, `gradle/verification-metadata.xml`

- [ ] Record scaffold/architecture authority SHAs, provider model, identity and capability semantics, SAF grant behavior, UI scope, host/controlled-provider tests, explicit deferred work, and exact device evidence fields.
- [ ] Run lint, host unit tests, debug assemble, and connected instrumentation with the exact commands and environment; keep dependency verification enabled for the final reproducible run and report any pre-existing metadata blocker separately.
- [ ] On the authorized Pixel 7a/API 36 only, install the freshly built APK, record its SHA-256, launch, select a disposable test tree through the real picker, navigate nested/empty folders, verify Unicode/file metadata/back/relaunch grant behavior, and capture production FATAL/logcat results. Never use personal files or claim a manual step was completed if it was not.
- [ ] Recheck manifest permissions, provider boundary, no recursive listing/thumbnail work, repository status, branch ancestry, and exact staged allowlist before committing the slice.

