# OmniFile VS02 Selection Mutations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add provider-scoped Files selection mode plus capability-aware Local/SAF Rename and Delete with truthful refresh and failure reconciliation.

**Architecture:** Extend the existing `StorageProvider` with only `rename` and `delete`, keep provider-specific `Path`/`Uri` logic inside providers, and model selection in `FilesViewModel` with `EntryRef` identity and a location boundary. Provider mutations return typed results; the ViewModel serializes them and refreshes from provider truth.

**Tech Stack:** Kotlin, Android SDK 36, Jetpack Compose/Material3, Kotlin coroutines, JUnit4, AndroidX instrumentation, controlled `DocumentsProvider`.

**Spec:** `docs/superpowers/specs/2026-09-19-omnifile-vs02-selection-mutations-design.md`

## Global Constraints

- Base exactly `97a63de6c7546b4dfec9439df328fba593a4cc0c`.
- Branch exactly `development/core-v1-selection-mutations-v1` in isolated worktree `~/Desktop/File Manager-worktrees/omnifile-selection-mutations-v1`.
- Preserve `com.omnifile`, `OmniFile`, minSdk 31, targetSdk 36, compileSdk 36, versionCode 1, versionName 0.1.0, and the existing Gradle/Kotlin/Compose toolchain.
- Keep strict dependency verification enabled for every final Gradle command; never use `--dependency-verification=off`.
- Do not add Copy, Move, Share, archive, media, network, cloud, or durable operation architecture.
- Do not mutate `~/Desktop/File Manager` root originals or the VS01 worktree.
- Root/session-root mutation must remain unavailable.
- `EntryRef` plus provider/location scope is the only selection identity authority.

---

### Task 1: Commit approved design and UI authority assets

**Files:**
- Create: `docs/superpowers/specs/2026-09-19-omnifile-vs02-selection-mutations-design.md`
- Create: `docs/ui-authority/v1/README.md`
- Add: `docs/ui-authority/v1/home/Home.png`
- Add: `docs/ui-authority/v1/files/単一選択.png`
- Add: `docs/ui-authority/v1/files/複数選択.png`
- Add: `docs/ui-authority/v1/music/*`, `docs/ui-authority/v1/search/*`, `docs/ui-authority/v1/settings/*`, `docs/ui-authority/v1/download/*`

**Interfaces:** The README is the durable mapping from user originals to authority paths and hashes. Files normal is explicitly absent and VS01 remains its authority.

- [ ] Step 1: Verify all 11 PNG hashes against the root originals and confirm no PNG content changed.
- [ ] Step 2: Write `docs/ui-authority/v1/README.md` with status `PROVISIONAL UI AUTHORITY V1`, priority rules, image mapping, all SHA-256 values, and implemented/deferred screen mapping.
- [ ] Step 3: Run `git status --short` and verify only the design/authority additions are present.
- [ ] Step 4: Commit with `docs(ui): add VS02 authority and approved design`.

### Task 2: Add failing selection state tests

**Files:**
- Modify: `app/src/test/java/com/omnifile/files/FilesViewModelTest.kt`
- Create: `app/src/test/java/com/omnifile/files/SelectionStateTest.kt` if pure state extraction is useful

**Interfaces:** Tests define `FilesViewModel.enterSelection`, `toggleSelection`, `clearSelection`, `isSelectionMode`, and selected `EntryRef` behavior without asserting private implementation details.

- [ ] Step 1: Add tests for long-press entry/single selection, multi-select toggle, deselect-last exits, explicit clear, navigation clear, provider/location boundary clear, Back-before-navigation, renamed old-ref removal, and deleted-ref removal.
- [ ] Step 2: Run `ANDROID_HOME=~/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/omnifile-gradle-cache ./gradlew testDebugUnitTest --tests com.omnifile.files.FilesViewModelTest --dependency-verification=strict` and confirm the new tests fail for missing behavior.
- [ ] Step 3: Commit only the red tests with `test(files): define VS02 selection transitions`.

### Task 3: Implement minimal selection model and navigation integration

**Files:**
- Modify: `app/src/main/java/com/omnifile/files/FilesViewModel.kt`
- Modify: `app/src/main/java/com/omnifile/files/FilesRepository.kt` only if location-scoped helpers are required

**Interfaces:** Add immutable selection fields to content/empty/error-capable UI state, expose `enterSelection(entry)`, `toggleSelection(entry)`, `clearSelection()`, `handleBack()`, and preserve existing `goBack()` behavior for normal mode.

- [ ] Step 1: Add the smallest state representation containing current provider ID, current location ref, and selected `Set<EntryRef>`.
- [ ] Step 2: Make provider selection, directory navigation, source Back, and zero-selection transitions clear selection.
- [ ] Step 3: Make Back consume selection mode before changing directory/source selection.
- [ ] Step 4: Run the focused selection tests and confirm green.
- [ ] Step 5: Run the existing `FilesViewModelTest` suite with strict verification and commit `feat(files): add provider-scoped selection state`.

### Task 4: Add failing capability and mutation contract tests

**Files:**
- Modify: `app/src/test/java/com/omnifile/storage/StorageModelTest.kt`
- Modify: `app/src/test/java/com/omnifile/files/FilesRepositoryTest.kt`

**Interfaces:** Define `StorageProvider.rename(entry, requestedName): StorageResult<StorageEntry>` and `StorageProvider.delete(entry): StorageResult<Unit>` plus typed invalid-name/name-conflict/partial-delete result data.

- [ ] Step 1: Add tests proving capability mapping is independent of provider type, root entries lack Rename/Delete, and repository delegates only to the entry provider.
- [ ] Step 2: Add tests for human-readable typed errors and per-item delete classification.
- [ ] Step 3: Run the focused tests and confirm they fail because the new contract is absent.
- [ ] Step 4: Commit `test(storage): define VS02 mutation contract`.

### Task 5: Implement typed mutation contract and repository delegation

**Files:**
- Modify: `app/src/main/java/com/omnifile/storage/StorageModel.kt`
- Modify: `app/src/main/java/com/omnifile/files/FilesRepository.kt`
- Modify: all `StorageProvider` implementations and test fakes to compile with the minimal contract

**Interfaces:** `FilesRepository.rename(entry, requestedName)` and `FilesRepository.delete(entry)` return provider-neutral typed results. No provider-specific ref escapes the boundary.

- [ ] Step 1: Add only `InvalidName`, `NameConflict`, and required structured delete outcome types.
- [ ] Step 2: Add the two interface methods with conservative default unsupported behavior only where test fakes need it.
- [ ] Step 3: Implement repository provider-ID lookup and stale-provider failure.
- [ ] Step 4: Run storage/repository tests and the complete host unit suite with strict verification.
- [ ] Step 5: Commit `feat(storage): add minimal rename and delete boundary`.

### Task 6: Add Local provider red tests for safe Rename/Delete

**Files:**
- Modify: `app/src/test/java/com/omnifile/storage/LocalStorageProviderTest.kt`

**Interfaces:** Tests use only `StorageProvider` operations and temporary directories; no real user paths or shell commands.

- [ ] Step 1: Add red tests for file/directory/Unicode rename, empty/separator/NUL/dot/dotdot invalid names, conflict, new identity/listing truth, root protection, file delete, empty directory delete, nested directory policy, Unicode delete, stale/not-found, symlink deletion without following, and containment.
- [ ] Step 2: Run the Local test class with strict verification and confirm each new test fails for the intended missing behavior.
- [ ] Step 3: Commit `test(storage): specify safe Local mutations`.

### Task 7: Implement Local capability-aware Rename/Delete

**Files:**
- Modify: `app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt`

**Interfaces:** Local entries expose only descriptor-relative `DELETE` when safely eligible; Local `RENAME` remains conservatively unsupported until an atomic no-replace primitive is proven. `delete` never follows symlinks and never deletes the configured root.

- [ ] Step 1: Validate the configured root itself with no-follow metadata and reject root mutation at both capability and operation time.
- [ ] Step 2: Keep Local rename unsupported; do not expose a precheck-plus-move sequence without a proven atomic no-replace primitive.
- [ ] Step 3: Anchor supported delete operations to securely opened root/parent directories and no-follow descriptor-relative traversal.
- [ ] Step 4: Delete regular files, symbolic links, and empty directories only; withhold `DELETE` when secure directory operations are unavailable and keep non-empty recursion unsupported.
- [ ] Step 5: Preserve exact error mapping for stale reference, permission, unsupported, and I/O failure.
- [ ] Step 6: Run Local tests, then the full host suite; commit `feat(storage): add safe Local rename and delete`.

### Task 8: Add controlled SAF mutation tests and provider implementation

**Files:**
- Modify: `app/src/androidTest/java/com/omnifile/storage/TestDocumentsProvider.java`
- Modify: `app/src/androidTest/java/com/omnifile/storage/SafStorageProviderInstrumentedTest.kt`
- Modify: `app/src/main/java/com/omnifile/storage/SafStorageProvider.kt`

**Interfaces:** Controlled provider models rename support/no-support, delete support/no-support, provider-sanitized names, changed returned document IDs/URIs, stale refs, and per-entry delete failures.

- [ ] Step 1: Add red instrumentation cases for missing flags, root protection, rename return identity changes, sanitized display names, delete success, provider failure, and stale references.
- [ ] Step 2: Run `connectedDebugAndroidTest` focused to the SAF class and confirm red behavior for missing provider methods.
- [ ] Step 3: Implement `DocumentsContract.renameDocument` and `DocumentsContract.deleteDocument` inside `SafStorageProvider` only.
- [ ] Step 4: Treat the returned rename URI/document ID as a new `SafEntryRef`, re-query its metadata, and strip root Rename/Delete capabilities.
- [ ] Step 5: Map provider/framework failures without exposing raw exceptions.
- [ ] Step 6: Run controlled SAF instrumentation and commit `feat(storage): add capability-aware SAF mutations`.

### Task 9: Add mutation race, refresh, and partial-delete tests

**Files:**
- Modify: `app/src/test/java/com/omnifile/files/FilesViewModelTest.kt`
- Modify: `app/src/test/java/com/omnifile/files/FilesRepositoryTest.kt`

**Interfaces:** ViewModel mutation methods serialize conflicting submissions, use a mutation token, refresh current parent state, clear successful selection, and retain failed surviving selection where possible.

- [ ] Step 1: Add red tests for duplicate rename/delete suppression, stale completion after navigation, rename reconciliation with a new ref, full-success delete clearing, and partial delete classification/retention.
- [ ] Step 2: Run the focused tests and confirm red failures identify missing race/reconciliation behavior.
- [ ] Step 3: Implement structured coroutine mutation jobs and token checks.
- [ ] Step 4: Implement provider-truth refresh after each mutation sequence and map per-item results.
- [ ] Step 5: Run focused and complete host tests; commit `feat(files): reconcile mutations and partial outcomes`.

### Task 10: Add failing Compose/UI tests and implement selection UI

**Files:**
- Modify: `app/src/main/java/com/omnifile/ui/files/FilesScreen.kt`
- Modify: `app/src/main/java/com/omnifile/MainActivity.kt`
- Create/modify: focused Compose instrumentation test under `app/src/androidTest/java/com/omnifile/ui/files/`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:** `FilesScreen` receives state and callbacks for selection, rename, delete, confirmation, and dismissal. UI actions are derived from current implementation plus entry capabilities.

- [ ] Step 1: Add red UI tests for long-press, selected count, selected semantics, close, Back, single-only Rename, capability-disabled actions, Rename dialog validation/progress, Delete confirmation, and refresh.
- [ ] Step 2: Run the focused UI tests and confirm red failures.
- [ ] Step 3: Preserve the existing normal Files layout and add selection top app bar, selected row treatment, contextual action area, and accessible semantics.
- [ ] Step 4: Add Rename dialog and Delete confirmation with human-readable error/status text and no dead future actions.
- [ ] Step 5: Wire Android Back to selection-first handling and run focused UI instrumentation.
- [ ] Step 6: Commit `feat(ui): add Files selection and mutation actions`.

### Task 11: Authority/evidence documentation and security review

**Files:**
- Create: `docs/production/VERTICAL_SLICE_02_SELECTION_MUTATIONS.md`
- Modify: `docs/ui-authority/v1/README.md` only if evidence mapping requires correction

- [ ] Step 1: Record base SHA, branch/worktree, authority paths/hashes, exact shipped actions, deferred actions, selection/identity model, Local/SAF semantics, provider sanitization, partial outcomes, process-death limitation, and test commands/results.
- [ ] Step 2: Record remote equality as `NOT_VERIFIED` until `git ls-remote` succeeds; never infer equality from local refs.
- [ ] Step 3: Run static searches for `Copy`, `Move`, `Share`, broad storage permissions, shell mutation, raw exceptions, and root mutation paths.
- [ ] Step 4: Verify no new production dependency or native `.so` was introduced; record the actual inventory.
- [ ] Step 5: Commit `docs(vs02): record selection mutation evidence`.

### Task 12: Independent review and final verification

**Files:** Review-only first; implementation files change only for actionable findings.

- [ ] Step 1: Run the architecture-focused review against the complete branch diff and the review-agent instructions.
- [ ] Step 2: Dispatch the independent code review using the requesting-code-review workflow.
- [ ] Step 3: Evaluate every finding against repository behavior before implementing it; use receiving-code-review discipline and systematic-debugging for failures.
- [ ] Step 4: Run strict final host verification: `clean testDebugUnitTest lintDebug assembleDebug --dependency-verification=strict`.
- [ ] Step 5: Run strict `connectedDebugAndroidTest` on the available Pixel 7a if connected.
- [ ] Step 6: Install and exercise the exact final APK against only the disposable VS02 SAF tree; record rename/delete/back/root-protection/cleanup evidence.
- [ ] Step 7: Record APK SHA-256, metadata, native inventory, Git status, protected refs, and root-worktree status.
- [ ] Step 8: Commit any final evidence correction, then stop. Do not push until explicit normal-push authorization is provided.
