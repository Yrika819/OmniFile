# OmniFile Core V1 — VS09 File Preview V1

## Status and authority

- Status: `INCOMPLETE — MANUAL DEVICE ACCEPTANCE REQUIRED`
- Base: `9b97149c2c921a8fe4523be51624ebaad4fe1d18` (VS08)
- Branch: `development/core-v1-preview-v1`
- Scope: bounded read-only text/image Preview from Files, Search, and Archive.
- Implementation commit: `64a28e5` (`feat(preview): add bounded file preview v1`).
- No VS10 work was started.

Host/compiler/build gates pass. `adb devices -l` listed no Android device or emulator, so connected instrumentation, physical SAF/image acceptance, and device lifecycle acceptance were not run. VS09 remains incomplete until the required device checks below run against the final APKs.

## Architecture and identity

Preview is a nested detail surface. `PreviewItem` carries the existing provider-scoped `EntryRef`; it does not use a display path, absolute path, or raw URI as identity. `PreviewSource` owns the provider-specific reopening closure and labels, and advertises sequential readability/reopen separately. Local and SAF sources use their existing provider-owned durable locators and sequential transfer APIs. `FilesRepository` routes only to providers implementing the optional preview adapter.

`PreviewViewModel` owns one request generation, cancels the prior request, and publishes only while the request still owns the current generation. Closing Preview cancels work and drops the `Ready` payload, including decoded bitmaps. Files, Search, and Archive ViewModels remain alive beneath Preview, so Back restores their current state. Rotation retains the Activity ViewModels. A process-death restore cannot reconstruct an in-memory source/archive index; the stale Preview route is dismissed safely.

## Supported classes and bounds

| Class | V1 behavior | Limits and notes |
|---|---|---|
| Plain text | Bounded UTF-8 preview with selectable, scrollable text | 8 KiB classification sample; at most 256 KiB read and 64 Ki UTF-16 code units shown; truncation is explicit. UTF-8 BOM is removed, malformed input is deliberately replaced, and an incomplete UTF-8 tail at truncation is dropped. |
| Still image | PNG, JPEG, and BMP recognized by signature and decoded with Android `BitmapFactory` | At most 32 MiB encoded input per decode pass; dimensions over 100,000 are rejected; sampled decode targets 1600×1600 and at most 8 MP. Aspect ratio is preserved in the UI. |
| Other/binary | Unsupported unless bounded content evidence identifies text | MIME and extension are hints, not authority. PDF and ZIP signatures are explicitly rejected by Preview classification. Image signatures override spoofed MIME/extension. |

The byte reader caps consecutive zero-progress reads at eight retries and returns a typed I/O error. Cancellation is checked during sequential reads. Bitmap decoding is bounded and runs on the Preview I/O dispatcher; generation ownership prevents a late decode result from replacing a newer request. Android bitmap decoding itself is a platform call and may finish its bounded decode after cancellation; its result is then discarded by the generation check.

Deferred classes/features: PDF rendering, Office documents, video, audio preview, animated images, RAW, editing, OCR, thumbnails, content indexing, archive editing, and cloud/network sources.

## Provider capability matrix

| Source | Text | Image | Evidence boundary |
|---|---:|---:|---|
| Local | Yes | Yes | Reopens using the Local provider's sequential source API and its checked durable locator. No Preview UI direct file access. |
| SAF | Yes | Yes where the provider exposes a readable stream | Uses the current selected tree's persisted read-grant/provider path. Sequential only; no seekability assumption, fabricated grant, broad storage permission, or local spooling. |
| ZIP entry | Yes | Yes where the decoded entry content is supported | Reopens the outer provider source and walks the validated ordinal. It checks the node is listed and supported, archive identity, safe virtual path, raw name, and method; the current reopened local header must still match raw name and method. Preceding entry scan is capped at the VS08 256 MiB metadata bound. Nothing is extracted for Preview. |

Provider failures remain typed as unsupported, unavailable, permission/grant missing, malformed/corrupt, resource limit, I/O, cancellation, or unknown where evidence is insufficient. Retry is exposed for provider/permission/I/O errors. No external intent, URI ingress, component, MIME filter, permission, or manifest surface was added.

## UI and navigation

- Files normal non-audio file row opens Preview; directories retain folder navigation; ZIP files retain Archive routing; audio retains its explicit Play action; selection and mutations are unchanged.
- Search normal non-audio file results open Preview; directories, ZIPs, and audio keep their established routes. The existing Search ViewModel retains query, scope, and result state underneath Preview.
- Supported Archive regular-file rows open the same Preview surface. Archive virtual path/index and extraction/selection state remain in the existing Archive ViewModel.
- Preview has a top app bar and Back action, source/location context, loading, text, fitted image with content description, unsupported, truncation, typed error, and meaningful retry states.
- The surface uses Material3 Scaffold insets under the existing `enableEdgeToEdge` Activity. Primary navigation is hidden on detail surfaces as established by the shell. Text is selectable and scrollable; images preserve aspect ratio.

## Review and security

- Code review generation 0: `Fix and re-review`; F1 found and closed. Report: [`VS09_CODE_REVIEW_V1_6b72d4a1.md`](../review/VS09_CODE_REVIEW_V1_6b72d4a1.md).
- Receiving/fix record: [`VS09_CODE_REVIEW_RECEIVING_6b72d4a1.md`](../review/VS09_CODE_REVIEW_RECEIVING_6b72d4a1.md). It records the no-progress fix and the supplemental PDF-signature guard discovered before generation 1.
- Code review generation 1: `Pass within reviewed source scope`; no open code findings. Report: [`VS09_CODE_REVIEW_V2_4ddf952e.md`](../review/VS09_CODE_REVIEW_V2_4ddf952e.md).
- Regression review VS08→VS09: `Pass within available evidence`; no unintended Files/Search/Archive/media/mutation regression found. Physical UI evidence remains open because no device was attached.
- Android intent-security review: not triggered by the diff. No external component/intent/URI ingress or manifest change was made.
- Edge-to-edge/inset and accessibility review: source review plus instrumentation test compilation passed; physical layout remains untested.

## Verification evidence

Environment: Android Studio JBR 25.0.3; SDK `/Users/yuta/Library/Android/sdk`; Gradle ran serialized with `--offline --no-daemon --max-workers=1`, both Kotlin in-process properties, and `--dependency-verification=strict`.

- Available diagnostics: no editor/LSP diagnostics interface was exposed. Main Kotlin, unit-test Kotlin, and Android-test Kotlin compilation passed; no relevant compile error remains.
- `:app:testDebugUnitTest`: `150 tests, 0 failures, 0 errors, 0 skipped`.
- `:app:lintDebug`: passed, `0 errors, 17 warnings`. Remaining warnings are repository/toolchain baseline advisories (SDK/version notices, existing exported Media3 service notice, existing SDK checks/resources/KTX suggestions, missing app icon); none is introduced by Preview.
- `:app:assembleDebug`: passed.
- `:app:assembleDebugAndroidTest`: passed.
- Android-test sources compile controlled Local text/image, SAF text/image/read-grant, ZIP Preview, Preview UI states, Files Preview callback, Archive Preview callback, and the existing Search result callback. Search origin and scope return are covered by host navigation tests.
- Connected instrumentation: `NOT RUN`. `adb devices -l` returned an empty device list. Do not read these compiled test sources as device-pass evidence.
- `git diff --check`: passed before implementation commit.

### Physical acceptance still required

Run the final `app-debug.apk` and `app-debug-androidTest.apk` on the awake/unlocked Pixel with `com.omnifile` as the actual foreground target. Execute controlled Local text/image Preview, controlled SAF text/image Preview using its persisted read grant, Files/Search/Archive return journeys, unsupported/loading/error/retry states, rapid A/B navigation, rotation/recreation, and the existing connected suite. Record the device identity, lock/foreground baseline, exact APK hashes, and test result. Until then the device and lifecycle rows remain `NOT TESTED`.

## Final artifacts

- Implementation commit: `64a28e5`.
- Debug APK SHA-256: `69fe7093e4115144f7309e8336b9e8d8b7d65853e1ad7f7373b2ef0a0a9a1d84`.
- Debug Android-test APK SHA-256: `f5c4f62d4d6fab0c5ae7b09cf865c1a70d13c6083c1935278f3300bb59f9e3ff`.
- Documentation/review commit and normal-push equality are recorded in the final task report after remote verification.

## VS10 frontier

Do not begin VS10 until the VS09 physical acceptance is complete. Then choose a separate requirement-led slice; format breadth, content indexing, Office/PDF rendering, media preview, and thumbnails remain outside VS09.
