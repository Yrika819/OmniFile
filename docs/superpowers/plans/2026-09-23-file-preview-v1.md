# OmniFile VS09 File Preview V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded Local, SAF, and safe ZIP-entry text/image previews to the existing OmniFile detail navigation.

**Architecture:** Provider-owned optional preview-source adapters expose sequential reads and conservative capabilities while generic preview identity remains `EntryRef`. Text uses a capped incremental UTF-8 path; images use signature checks, bounded reopenable streams, and sampled platform decoding; ZIP entries are re-streamed by validated ordinal. A lifecycle ViewModel owns cancellation/generation and a detail route records origin.

**Tech Stack:** Kotlin 2.2.10, Android API 31+, Jetpack Compose Material 3, Android BitmapFactory, Kotlin coroutines, JUnit 4, existing controlled DocumentsProvider instrumentation.

**Spec:** `docs/production/VERTICAL_SLICE_09_FILE_PREVIEW_V1.md` (campaign requirements supplied by the user; this document is completed at closure).

## Global Constraints

- Base exactly `9b97149c2c921a8fe4523be51624ebaad4fe1d18`; work only in `development/core-v1-preview-v1` worktree.
- No new image library, external intent, storage permission, broad path/URI identity, extraction-to-preview, PDF, or VS10.
- Keep text reads at or below 256 KiB; image encoded input at or below 32 MiB and decoded dimensions bounded to the target viewport and 8 MP.
- Preserve audio Play, directory navigation, archive browsing, and existing search/folder view-model state.
- After each code change run relevant compile/diagnostics before focused tests; strict dependency verification; serialize Gradle with JBR 25.0.3 and one worker.

---

### Task 1: Provider-neutral preview source and capability model

**Files:**
- Create: `app/src/main/java/com/omnifile/preview/PreviewModels.kt`
- Create: `app/src/main/java/com/omnifile/preview/PreviewSourceProvider.kt`
- Modify: `app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt`
- Modify: `app/src/main/java/com/omnifile/storage/SafStorageProvider.kt`
- Modify: `app/src/main/java/com/omnifile/files/FilesRepository.kt`
- Test: `app/src/test/java/com/omnifile/preview/PreviewCapabilityTest.kt`

- [x] Add capability/source-identity tests.
- [x] Add optional provider preview sources backed by provider-owned sequential handles and `EntryRef` identity.
- [x] Implement Local and selected read-grant SAF adapters; retain typed failures.
- [x] Compile and run the focused host suite.

### Task 2: Bounded text and sampled image engines

**Files:**
- Create: `app/src/main/java/com/omnifile/preview/PreviewEngine.kt`
- Create: `app/src/test/java/com/omnifile/preview/PreviewEngineTest.kt`

- [x] Add deterministic source tests for UTF-8/BOM/error/truncation/binary/empty/large text, spoofed content claims, and bounded reads.
- [x] Implement PNG/JPEG/BMP signature-first classification, UTF-8 replacement decoding, explicit truncation, capped streams, and sampled Android bitmap decoding. PDF/ZIP signatures remain unsupported.
- [x] Compile changed sources and run focused host tests. Valid bitmap decoder runtime is covered in compiled instrumentation sources; no host BitmapFactory claim is made.

### Task 3: ZIP entry stream adapter and tests

**Files:**
- Modify: `app/src/main/java/com/omnifile/archive/ArchiveRepository.kt`
- Test: `app/src/test/java/com/omnifile/archive/ArchiveRepositoryTest.kt`

- [x] Cover duplicate ordinals, unsupported unsafe entries, large-entry truncation, and a changed compression method after indexing.
- [x] Reopen the provider stream, validate listed archive identity/ordinal/safe path/name/method, skip under the metadata bound, and expose a sequential entry stream without extraction.
- [x] Compile changed sources and run focused host tests.

### Task 4: Lifecycle request owner and navigation integration

**Files:**
- Create: `app/src/main/java/com/omnifile/preview/PreviewModels.kt`
- Create: `app/src/main/java/com/omnifile/preview/PreviewSourceProvider.kt`
- Create: `app/src/main/java/com/omnifile/preview/PreviewEngine.kt`
- Create: `app/src/main/java/com/omnifile/preview/PreviewViewModel.kt`
- Create: `app/src/main/java/com/omnifile/ui/preview/PreviewScreen.kt`
- Modify: `app/src/main/java/com/omnifile/ui/shell/AppNavigation.kt`
- Modify: `app/src/main/java/com/omnifile/MainActivity.kt`
- Modify: `app/src/main/java/com/omnifile/ui/files/FilesScreen.kt`
- Modify: `app/src/main/java/com/omnifile/ui/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/omnifile/ui/archive/ArchiveScreen.kt`
- Test: `app/src/test/java/com/omnifile/ui/shell/AppNavigationStateTest.kt`

- [x] Add origin-return tests and an A/B stale-completion ViewModel test.
- [x] Implement generation ownership and cancellation on replacement, close/navigation, retry, and ViewModel clear.
- [x] Add a Material3 detail surface with loading, typed error, unsupported, truncation, retry, text selection, image scaling, and accessibility semantics.
- [x] Wire Files/Search non-audio file entry points and safe Archive entries while preserving directory, ZIP, selection, and explicit audio Play paths.
- [x] Compile changed sources and run focused host tests.

### Task 5: Integration instrumentation and regression coverage

**Files:**
- Create: `app/src/androidTest/java/com/omnifile/ui/preview/PreviewScreenInstrumentedTest.kt`
- Create: `app/src/androidTest/java/com/omnifile/ui/archive/ArchiveScreenComposeInstrumentedTest.kt`
- Create: `app/src/androidTest/java/com/omnifile/storage/PreviewStorageInstrumentedTest.kt`
- Modify: `ArchiveStorageInstrumentedTest.kt`, `FilesScreenComposeInstrumentedTest.kt`; Search callback identity uses existing coverage.

- [x] Add disposable controlled Local/SAF fixtures, UI states/callback tests, Archive source tests, Search identity/origin host coverage, and stale request coverage.
- [x] Compile Android instrumentation sources and assemble the Android-test APK. Runtime device execution is not closed because `adb devices -l` returned no device/emulator.
- [x] Run full host, lint, app APK, and Android-test APK gates under strict dependency verification.

### Task 6: Adversarial review, repair, evidence, and publication

**Files:**
- Create: `docs/production/VERTICAL_SLICE_09_FILE_PREVIEW_V1.md`
- Create: `docs/review/VS09_CODE_REVIEW_V1_6b72d4a1.md`
- Create: `docs/review/VS09_CODE_REVIEW_RECEIVING_6b72d4a1.md`
- Create: `docs/review/VS09_CODE_REVIEW_V2_4ddf952e.md`
- Create: `docs/review/VS09_REGRESSION_REVIEW_V1_2c4028f1.md`

- [x] Audit the full changed source and affected Files/Search/Archive/Media/operation/security paths.
- [x] Freeze generation 0, repair the no-progress finding, then complete generation 1; add the PDF signature guard and stale archive method check before the final review snapshot.
- [x] Complete the regression review. Intent review found no external intent/manifest/MIME change and is therefore not applicable.
- [x] Record exact host/build/diagnostic/review/APK evidence and the open device limitation.
- [ ] Finish the docs commit, normal push, and fresh local/remote SHA equality check.
