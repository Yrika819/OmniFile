# OmniFile Core V1 — VS09 File Preview V1

## Closure status and authority

- Acceptance status: **PASS**. Implementation, host validation, API 31–36 emulator acceptance, and the scoped physical acceptance are complete.
- Integration status: the final closure commit must pass fresh Host and API 31–36 workflows on the PR head and again on the merged `main` SHA. Those SHA-specific results are recorded with the pull request and its GitHub checks.
- Scope: bounded, read-only Preview for supported local files, persisted SAF files, and supported ZIP entries.
- Implementation candidate: `7fbca663b528eeae333418d422dfb86550b1fc44`.
- Active branch: `development/vs09-preview-v1-mainline`.
- Historical recovery branch: `development/core-v1-preview-v1`, retained at `6195819beba31e15012c1c6cf76c27e93347a971`.
- Remaining VS09 implementation or acceptance work: **none**. Do not start VS10 as part of this closure.

## Supported Preview behavior

| Content | V1 behavior | Bounds and evidence |
|---|---|---|
| Plain text | Selectable, scrollable UTF-8 text; malformed sequences are replaced and an incomplete UTF-8 tail at the byte limit is dropped. | 8 KiB classification sample, at most 256 KiB read, at most 64 Ki UTF-16 code units displayed. Truncation is explicit. |
| Still images | PNG, JPEG, and BMP, recognized from content signatures and decoded with Android `BitmapFactory`. | At most 32 MiB encoded input per decode pass; dimensions above 100,000 are rejected; sampled decode targets 1600×1600 and at most 8 MP. |
| Other or binary content | Unsupported unless bounded content evidence identifies text. PDF and ZIP signatures are explicitly unsupported. | MIME type and extension are hints, not authority. |

The image UI preserves aspect ratio. Preview does not edit, extract, index, OCR, or cache content. PDF, Office documents, video, audio preview, animated images, RAW, cloud/network sources, and thumbnails remain out of scope.

## Providers, bounds, and errors

- Local and SAF adapters expose provider-owned, reopenable sequential reads. SAF access uses the current persisted grant. Preview does not infer seekability, create grants, request broad storage permissions, or spool a source to local storage.
- Archive Preview is limited to supported regular-file ZIP records. It reopens the outer source, validates the archive identity and record ordinal, checks the safe virtual path and current local header, then streams the entry. It does not extract the entry.
- Reads are cancellation-aware and bounded. Repeated zero-progress reads stop after eight retries. Bitmap decode is sampled and capped; a result from an obsolete request is discarded even if a platform decode finishes after cancellation.
- Provider-unavailable, missing permission/grant, malformed content, resource limit, I/O, cancellation, unsupported, and unknown states remain distinguishable internally. User-facing errors do not expose provider URIs or filesystem paths. Retry is offered for recoverable provider, permission, and I/O errors.

## Navigation and request ownership

- Files and Search open supported non-audio files in Preview; directories, ZIP containers, and audio retain their existing navigation and playback routes.
- Supported Archive entries use the same Preview surface. Files, Search, and Archive state remain underneath Preview, so Back returns to the originating state.
- Preview owns one request generation. Opening another item cancels the previous request; Back or top-level navigation cancels work and releases the ready payload, including decoded bitmaps.
- Activity recreation retains ViewModels. A process-death restore cannot reconstruct an in-memory source or archive index, so a stale Preview route is dismissed safely.

## Verification evidence

### Cloud and GitHub baseline on the implementation candidate

Cloud Host verification on `7fbca663b528eeae333418d422dfb86550b1fc44`:

- `:app:testDebugUnitTest`: **153 passed, 0 failures, 0 errors, 0 skipped**.
- `:app:lintDebug`: **PASS**.
- `:app:assembleDebug`: **PASS**.
- `:app:assembleDebugAndroidTest`: **PASS**.
- Strict Gradle dependency verification: **PASS**.

GitHub Host CI run [36680322036](https://github.com/Yrika819/OmniFile/actions/runs/36680322036) and GitHub Emulator CI run [36680321993](https://github.com/Yrika819/OmniFile/actions/runs/36680321993) both passed on that same candidate SHA.

| Emulator API | Total | Passed | Failed | Skipped | Exclusions |
|---:|---:|---:|---:|---:|---:|
| 31 | 87 | 86 | 0 | 1 expected | 1 audio-output test |
| 32 | 87 | 86 | 0 | 1 expected | 1 audio-output test |
| 33 | 87 | 86 | 0 | 1 expected | 1 audio-output test |
| 34 | 87 | 86 | 0 | 1 expected | 1 audio-output test |
| 35 | 84 | 83 | 0 | 1 expected | 4 audio tests |
| 36 | 87 | 86 | 0 | 1 expected | 1 audio-output test |

The one expected skip on each API is the persisted-device SAF audio test that requires a human picker grant. All APIs exclude `MediaPlaybackInstrumentedTest#wavPlaysToEndedState`, which needs emulator audio output to finish a clip. API 35 additionally excludes three audio-focus tests because that emulator cannot grant audio focus. No Preview test was skipped or excluded, and no Preview failure occurred. The final closure commit is separately revalidated by the PR-head and merged-main workflow runs.

### Pixel 7a / Android 17 / API 37 beta physical acceptance

Final classification: **`VS09_PIXEL_ACCEPTANCE_GREEN_WITH_LIMITED_UNEXERCISED_CASES`**.

Passed physically:

- Local short text and bounded/truncated long-text Preview.
- PNG, JPEG, and BMP Preview.
- Unsupported local state; Files → Preview → Back; Search → Preview → Back.
- Archive text Preview, Archive image Preview, and Archive unsupported state.
- Real DocumentsUI SAF grant; persisted SAF text and image Preview; Local and SAF recreation; app relaunch with the persisted SAF grant.
- Real source-unavailable error and Retry recovery.
- Selection and audio regression checks.
- Targeted physical Preview instrumentation: **8/8 passed**.
- **0 crashes, 0 ANRs**, and no user-facing path or provider-URI leakage.

Limited unexercised cases:

1. A deterministic stale-request race could not be forced reliably because the physical device completed requests too quickly. Automated generation/cancellation coverage passed, and repeated rapid real-device attempts showed no stale overwrite.
2. Explicit SAF-tree grant revocation could not be exercised because Android 17 Settings exposed no suitable per-tree revoke action. The real source-unavailable / `ProviderUnavailable` / Retry path was exercised.

These are bounded physical-test limitations; automated ownership/cancellation coverage and the real provider-unavailable recovery path passed. Physical acceptance found **0 Blocker, 0 Major, 0 Minor, 0 Nit**.

## Regression, privacy, and final findings

- VS08→VS09 regression review passed within its reviewed scope. Files selection/mutations, Search query/scope/results, Archive selection/extraction, and explicit audio playback retain their existing behavior.
- Code review generation 1 passed within reviewed scope; the no-progress read finding was closed and the PDF-signature guard was verified. No manifest, permission, exported component, external intent, or URI-ingress surface was added.
- Final finding count: **0 Blocker / 0 Major / 0 Minor / 0 Nit**.
- The public record contains no device serial, personal filesystem listing, private screenshot, local absolute path, recovery bundle, or `docs/ui-authority/` material.
- Emulator authority is limited to API 31–36. Physical authority is one Pixel 7a running Android 17 / API 37 beta; this is not a general API 37 emulator or device-matrix claim.
- The recovered VS09 lineage remains intact: `7ddf10863dda5120837c1898d19387a9341fbba2` and `7fbca663b528eeae333418d422dfb86550b1fc44` are in the active branch history. The historical recovery branch remains preserved separately.

## Closure rule

VS09 is closed only after the final PR head and resulting `main` SHA each pass the complete Host suite and API 31–36 emulator matrix, the PR is merged with a merge commit, the existing physical acceptance remains green, and no Blocker or Major finding remains. SHA-specific integration checks and the merge commit are recorded in the PR and this task's closure report.
