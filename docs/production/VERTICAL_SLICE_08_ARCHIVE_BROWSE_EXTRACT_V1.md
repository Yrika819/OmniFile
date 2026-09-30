# OmniFile Core V1 — VS08 Archive Browse / Safe Extract V1

## Closure status and authority

- Base: `38d2f936c2a65c96c61f1754994de19c3c76f89a` (VS07 final)
- Branch: `development/core-v1-archive-v1`
- Scope: ZIP archive recognition, virtual browsing, and conservative extraction.
- Archive engine: Android/Java platform `java.util.zip.ZipInputStream`; no new production dependency.
- Supported format: ZIP only.
- Deferred formats: RAR, 7z, TAR, TAR.ZST, libarchive/native breadth, encrypted/split ZIP via Zip4j, and all other formats. They remain deferred because no current VS08 requirement proves the dependency, license, native, or capability tradeoff.
- Closure status: implementation and required host/focused-device evidence complete; full unrelated VS07 device baseline remains unstable as documented below.

## Engine and dependency rationale

`ZipInputStream` is available on the supported Android API range (minSdk 31), has no new native or licensing surface, and directly consumes the existing sequential provider handle. VS08 does not claim random access. ZIP metadata is parsed off the main dispatcher into an in-memory bounded index; payloads are never materialized as a whole archive or allocated from declared ZIP sizes.

The engine exposes only sequential semantics in this slice. It does not expose a safe archive-controlled symlink contract, so extraction never creates or follows archive links. Encrypted/unsupported ZIP features are reported as unsupported/encrypted rather than guessed through a password route.

## Archive identity and virtual hierarchy

An archive container retains the underlying provider-scoped `EntryRef` identity. An archive entry `EntryRef` contains the provider ID, outer archive identity, and an ordinal discriminator; virtual directories use a stable path discriminator scoped by the outer identity. A display path is never the sole identity, so duplicate archive entries and duplicate display names remain distinct.

The browser is a detail surface, not a top-level navigation destination:

```text
Files/Search -> Archive detail -> virtual root/nested folders -> Back -> originating Files/Search state
```

The index preserves root files, nested folders, explicit empty directories, duplicate records, Unicode display names, and unsafe names as visible-but-not-extractable root entries. It does not create a separate search index; Search remains filename-based over provider files and can locate archive containers only.

## Source and destination capability matrix

| Source | Destination | Browse | Extract | Proven behavior |
|---|---|---:|---:|---|
| Local | Local | Yes | Yes | Local sequential read plus proven Local partial/finalize/write/delete primitives |
| SAF | Local | Yes when current provider grants sequential read | Yes when source remains available | Controlled SAF pipe-backed read and Pixel 7a/API 36 SAF→Local extraction passed |
| Local | SAF | Yes | No | Default SAF construction does not universally prove directory creation/finalization for archive output |
| SAF | SAF | Yes when current provider grants sequential read | No | No implicit spooling or universal SAF destination claim |

Readable does not imply seekable. SAF archive sources are accepted through sequential read only; no descriptor or provider label upgrades the source to seekable. No arbitrary huge SAF archive is silently materialized to a local cache.

## Browse and parser safety

The parser uses fixed-size buffers and bounded counters:

- maximum entries: 10,000;
- maximum path component length: 4,096 characters;
- maximum depth: 128 components;
- metadata scan limit: 256 MiB;
- extraction per-entry limit: 512 MiB;
- extraction total limit: 1 GiB;
- counters: 64-bit with overflow-safe addition.

The parser requires a ZIP end record after draining the sequential source, and distinguishes corrupt/truncated, encrypted, unsupported, provider, and resource-limit states. Mid-stream source transport failures remain `ProviderUnavailable`; ZIP structural failures remain corrupt/unsupported. No parser operation runs on Compose/Main.

## Safe extraction behavior

Extraction is foreground/restart-required in VS08. It is not falsely resumable or process-durable. For each selected file:

```text
preflight every output target
 -> create provider-owned partial
 -> stream through bounded buffer
 -> verify actual/declared and indexed sizes
 -> flush/close
 -> finalize without overwrite
 -> record foreground result
```

Local output uses the established provider partial/finalization primitives. Multi-entry failures and cancellation roll back finalized files and operation-created empty directories on a best-effort basis and report whether cleanup completed. A partial or ambiguous result is never presented as successful completion.

The destination namespace is validated after normalization. The implementation rejects:

- `..` and `.` traversal components;
- absolute, leading-slash, UNC-like, and Windows drive-letter forms;
- NUL, empty, malformed, backslash/mixed-separator, and over-depth names;
- duplicate raw or NFC-normalized output targets;
- file/directory collisions, including normalized collisions;
- existing destination conflicts and type conflicts;
- corrupt/truncated/unsupported/encrypted source entries;
- declared-size mismatches, counter overflow, and resource-limit breaches.

No silent overwrite exists. Existing destination files remain unchanged. If a provider disappears while reading, the result is provider-unavailable rather than generic success/corruption. If true resume cannot be proven, the operation is restart-required.

## UI and edge-to-edge

- `DetailSurface.ARCHIVE` is a detail-only shell state with explicit `ArchiveOrigin` values for Home Files, top-level Search, and contextual Search.
- Top app bar, virtual breadcrumb, entry rows, selection state, Extract-to-Local action, progress/cancel state, corrupt/unsupported/encrypted state, and retry state use existing Material3/Compose shell conventions.
- Archive list content uses Scaffold padding and a weighted list so extraction status remains visible; `MainActivity` already calls `enableEdgeToEdge()` and applies navigation-bar contrast handling.
- No new IME or external intent surface was introduced.
- Compact/large behavior follows the existing adaptive shell and Scaffold/list inset pattern; no double inset path was added.

## Tests and evidence

### Host

- `:app:testDebugUnitTest`: **128 passed, 0 failed, 0 skipped**.
- Archive fixtures are generated in tests, not committed binaries. Coverage includes empty ZIP, nested/empty folders, duplicate records/display names, zero-byte and Unicode names, traversal/absolute/drive/mixed separators, NFC/NFD normalization collision, corrupt/truncated source, conflict/type collision, resource limits, partial cleanup, rollback, 64-bit accounting, and navigation origin/back state.

### Android test compilation

- `:app:assembleDebugAndroidTest`: **passed**.
- Added controlled SAF archive test and focused Compose callback tests for Files ZIP open and Search result identity.

### Pixel 7a / Android 16 API 36

- Focused `ArchiveStorageInstrumentedTest`: **1/1 passed** from the final repaired source/test revision.
- Proven on device: controlled SAF sequential archive browse, no seek capability claim, nested virtual listing, and SAF source extraction to Local destination.
- Full `:app:connectedDebugAndroidTest`: 78 cases ran; the archive case passed. 28 failures were unrelated pre-existing Activity/Compose lifecycle/harness failures (`Activity ... STOPPED`, `No compose hierarchies found`) and one pre-existing physical-SAF assumption (`no persisted readable SAF tree`). No failure entered the archive path. These failures prevent claiming a green whole-app device suite; they are not attributed to VS08.
- No personal files, user documents, or manual picker grants were used. No manual acceptance gate remains for the controlled-provider VS08 evidence.

### Lint and build

- `:app:lintDebug`: **passed**. Findings are baseline/tooling only: update notices, existing exported Media3 service notice, obsolete API checks, unused scaffold resources, missing application icon, existing KTX suggestions, and native strip packaging warning.
- `:app:assembleDebug`: **passed**.
- `:app:assembleDebugAndroidTest`: **passed**.
- Authoritative Java: Android Studio JBR `25.0.3`; SDK: `~/Library/Android/sdk`; Gradle was serialized with `--no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process --dependency-verification=strict`.

## Security, intent, and regression review

- No exported Activity, MIME filter, ACTION_VIEW handling, content URI ingress, FileProvider, PendingIntent, or provider export changed. The Android intent-security review is not applicable to this diff.
- Material Archive UI was reviewed against the edge-to-edge skill requirements; no new inset or IME issue was introduced.
- Code review generation 1: **Pass**. Report: `docs/review/VS08_CODE_REVIEW_V2_3d4783be.md`.
- Regression review VS07→VS08: **Pass**. Report: `docs/review/VS08_REGRESSION_REVIEW_V1_705c7fce.md`.

## Known limitations and deferred work

- ZIP only; no RAR/7z/TAR/native breadth.
- Encrypted ZIP entries are not opened; no password storage or cracking.
- No archive creation/modification/add/delete/rename.
- No recursive archive indexing or content search inside archives.
- No archive thumbnails/document preview.
- Extraction destination is Local-only in the current proven capability contract.
- Extraction is restart-required rather than resumable after process death.
- Full device UI/media baseline needs a separate harness/device-state repair before a green whole-app physical gate can be claimed.

Recommended VS09 frontier: repair and isolate the existing Activity/Compose physical test harness, then evaluate broader archive destination capability only with a provider-specific proof; do not add format breadth by count.

## Final artifacts

- Final implementation SHA: `110515a` (`feat(archive): add ZIP browse and safe Local extraction`).
- Evidence/review closure commit: `d0523d5` (`docs(vs08): record archive evidence and reviews`).
- Debug APK SHA-256: `884997ee85c3e9e47661c5860a5496b875263e60f3b5e4941b42775a46f6f932`.
- Debug Android-test APK SHA-256: `9939a99ab9cecc61706a5baaa91c010a4a6e11fe4d190d2d87f5adfc6164a602`.
- Push: `d0523d5` is pushed non-force to `origin/development/core-v1-archive-v1`; final closure commit is pushed after this evidence line is recorded.
