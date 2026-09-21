# OmniFile CORE_V1 VS05 — Provider-Neutral Search V1

## Release identity

- Published VS04 base: `e6f1148b1b6dbe946228316861b6f6e382ca2af1`
- VS05 branch: `development/core-v1-search-v1`
- Worktree: `/Users/yuta/Desktop/File Manager-worktrees/omnifile-search-v1`
- Scope: provider-neutral, non-destructive filename search for the current Local or SAF folder
- Explicitly deferred: permanent Home/Search/Music/Settings navigation shell and executable `ThisDevice` aggregation

## Product and navigation scope

Search is modeled as an independent top-level destination that can later be attached to permanent Search navigation. VS05 uses the smallest transitional ingress from the existing Files surface; it does not add placeholder Home, Music, Settings, or bottom-navigation items.

The executable VS05 route is:

```text
Files -> Search -> SearchScope.CurrentFolder -> recursive filename search -> result -> existing Files navigation
```

`SearchScope` models both `CurrentFolder(providerId, directory)` and future `ThisDevice`. `ThisDevice` is intentionally unsupported in VS05 rather than presented as a misleading selectable scope. A future app-shell slice can aggregate only currently available OmniFile roots and persisted/granted SAF roots.

The Search state model is independent from `FilesViewModel`. Files supplies the initial scope and receives a result-navigation callback. A directory result opens that directory; a file result opens its containing directory. Search does not duplicate mutation controls or implement file preview.

## Query semantics

- Search matches entry display names only: files and directories are both eligible.
- File contents, hashes, OCR, media metadata, and persistent indexes are not used.
- Input is trimmed before matching; whitespace-only input does not traverse providers.
- Matching is case-insensitive `contains` using `Locale.ROOT` lowercasing.
- Unicode/Japanese names, symbols, and extension-like queries use the same predictable contains rule.
- Query changes are debounced by 150 ms; IME Search submits immediately.
- Clear returns to the scoped Idle state.

## Provider-neutral traversal

`SearchEngine` accepts only provider-neutral callbacks returning `StorageResult` and `StorageEntry` values. Generic Search code does not use `java.io.File`, `Path`, raw `Uri`, or concrete provider classes.

- Traversal is iterative with an explicit directory queue; it does not launch one coroutine per directory.
- Directory children are sorted deterministically by case-folded display name, display name, and provider identity key.
- Visited directories use `EntryRef.identityKey`, retaining provider identity and preventing cycles/duplicate traversal.
- Safety bounds are explicit: batch size 32, maximum depth 64, maximum entries visited 100,000, and maximum results 10,000. Bounds are surfaced as incomplete/truncated state rather than silently reported complete.
- Search results retain the `StorageEntry` and ordered ancestor list. Display name is never used as identity.
- Result publication is batched at 32 pending hits/failures, limiting UI publication pressure while retaining progressive results.

Local traversal delegates to the existing `LocalStorageProvider`, which preserves root containment and `NOFOLLOW_LINKS` behavior. Symlink entries can match by name but are not traversed as directories.

SAF traversal delegates to the existing `SafStorageProvider`, which validates provider identity, selected tree URI, and tree containment for every child listing. Persisted grant restoration remains the existing Files/SAF authority; Search does not infer permission from a remembered URI alone.

## Cancellation and request ownership

Each logical request receives a monotonically increasing generation. Query replacement, scope replacement, clear, leaving Search, and ViewModel clearing cancel prior jobs and advance ownership. Every batch and completion emission verifies both generation and active scope before publication. Coroutine cancellation is therefore supplemented by explicit stale-result ownership and cannot be replaced by timing assumptions.

Provider calls cooperatively check coroutine activity. `CancellationException` is rethrown and is never converted into a user-visible provider failure. Search owns no provider handles beyond the provider callback call boundary.

## Error behavior

- Root callback failure is returned as a typed root error.
- In `CurrentFolder`, failure to list the selected root is a root/provider error, not “no matches.”
- A nested directory failure preserves valid hits and emits `SearchSubtreeFailure`; the UI presents a partial-results warning.
- Provider unavailable, permission denied, stale reference, not found, unsupported, cancellation, and generic I/O failures are mapped to user-facing messages rather than raw enum/class names.
- A provider disappearing after valid results does not discard already found hits; a failed selected root is shown as unavailable/error.
- Unknown size and modified time remain unknown and are omitted from result metadata rather than fabricated.

## UI behavior

The dedicated Material 3 Search surface includes:

- Back action
- Search TextField with IME Search action
- Clear action
- Disabled Current-folder scope chip that accurately communicates the active scope
- Idle guidance, searching/progressive state, no-match state, results, partial subtree warning, truncation warning, and provider error state
- Location context for duplicate names and lightweight metadata when available

`MainActivity` uses `Surface.FILES` and `Surface.SEARCH` routing without creating a second browser stack. Back from Search stops active traversal and returns to Files. `enableEdgeToEdge()` is already used; the Activity declares `adjustResize`; Search consumes Scaffold padding and applies IME padding once so the field and result content remain usable.

## Test evidence

### Host

Environment: Android Studio JBR 25.0.3, SDK `/Users/yuta/Library/Android/sdk`, serialized Gradle workers, strict dependency verification, offline isolated cache used because an unrelated Gradle process held the shared cache locks.

- `:app:testDebugUnitTest`: **80 passed, 0 failed, 0 errors, 0 skipped** across 15 suites.
- Search-focused host tests: **11 passed, 0 failed, 0 errors, 0 skipped**.
- `:app:lintDebug`: **BUILD SUCCESSFUL**. The report contains 15 existing project warnings; no Search-specific lint failure was introduced.
- `:app:assembleDebug`: **BUILD SUCCESSFUL**.
- `:app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL**.
- `:app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**.

Search host coverage includes query normalization, Unicode/case behavior, duplicate identities, recursive traversal, root and subtree failures, cancellation, Local disposable-tree traversal, symlink non-traversal, stale query suppression, and scoped ViewModel behavior.

### Controlled SAF

`SearchSafProviderInstrumentedTest` uses the existing controlled `DocumentsProvider` and covers recursive selected-tree search plus root provider outage classification. It does not create a parallel fake-provider architecture.

### Pixel runtime

Target: **Pixel 7a, API 36**, direct `AndroidJUnitRunner`, debug APK SHA-256:

`973f1b52fa558fc15b53e5f2abe9ba4afec622c889d3c7fc291f05aad05c6195`

Android test APK SHA-256 at final assembly:

`bd9cd6070b19a540c2854a5c4c9aa9815a5ef6c28e41f48198b5477b359e9a4b`

Evidence:

- Disposable app-private Local provider instrumentation: **1 passed, 0 failed**; nested duplicate names remained distinct.
- Controlled SAF Search instrumentation: included in the full run and passed.
- Search Compose instrumentation: **3 passed, 0 failed**; scope, duplicate context/result identity, clear, Back, and Idle scope are covered.
- Final full instrumentation: **37 tests, 37 passed, 0 failed, 0 skipped**; runner output ended with `OK (37 tests)`.
- Manual Pixel smoke evidence showed the Current-folder scope chip before query, the IME-visible Search field with no overlap, no-match state, and Back returning to Files. The device contained only disposable test/provider data from the existing runtime harness.

## Reviews

- Review A historical report remains unchanged: `tmp/reviews/2026-09-21-code-review-vs05-review-a.md`.
- Review A product resolution: `tmp/reviews/2026-09-21-receiving-code-review-vs05-review-a.md`, resolution `rr-20260921-vs05reviewa`.
- Review B traversal/concurrency/error report: `tmp/reviews/2026-09-21-code-review-vs05-review-b.md`, validator-clean, **Pass**, 0 findings.
- Review C UI/navigation report: `tmp/reviews/2026-09-21-code-review-vs05-review-c.md`, validator-clean, **Pass**, 0 findings.
- Final whole-diff code review and final regression review remain release-closure gates after the final focused commits are assembled.

## Unsupported Search V1 features

- Executable cross-root `ThisDevice` aggregation
- Background or persistent indexing
- Full-text/content search
- OCR, semantic/fuzzy ranking, duplicate detection
- Media metadata search
- Cloud/network providers, SMB/SFTP/WebDAV
- Archives
- File preview
- Directory Copy/Move, SAF Move, or any transfer-route broadening

## Future frontier

A later app-shell slice can attach Search to permanent bottom navigation and implement truthful `ThisDevice` aggregation from currently available OmniFile roots. A separate future indexing slice may add persistent metadata/full-text search only after provider lifecycle, permissions, freshness, storage, and cancellation contracts are explicitly designed.
