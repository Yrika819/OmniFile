# OmniFile Core V1 — Vertical Slice 01 Evidence

Status: implemented and validated on the isolated branch `development/core-v1-files-browsing-v1`.

## Identity and source

- Product: `OmniFile`
- Package: `com.omnifile`
- Scaffold source: `4c997aa00d376be4298ebc361d5794803ddb6718`
- Worktree: `/Users/yuta/.codex/worktrees/omnifile-files-browsing-v1/File Manager`
- Scope: read-only file browsing; no archive, media, Room, WorkManager, or final navigation shell

## Implemented boundary

- `StorageProvider` exposes provider-scoped opaque `EntryRef` values and capability sets.
- `LocalStorageProvider` is the only provider using host `Path` internally; it normalizes and confines all access to its configured root.
- `SafStorageProvider` uses `ContentResolver` and `DocumentsContract` tree/document URIs. It maps document flags conservatively and does not infer sequential or seekable reads from metadata or file descriptors.
- `SafTreeGrantStore` persists only the selected read grant.
- `FilesRepository` and `FilesViewModel` provide lifecycle-aware source selection, listing, empty/error/loading states, stale-result suppression, back navigation, and retry.
- The Compose Files screen renders names plus known metadata only, with no recursive counts or thumbnails.
- The manifest requests no broad storage permission and no `MANAGE_EXTERNAL_STORAGE`.

## Verification

Host command:

```text
ANDROID_HOME=/Users/yuta/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/omnifile-gradle-cache ./gradlew testDebugUnitTest --dependency-verification=off
```

Result: `BUILD SUCCESSFUL`; 31 actionable tasks, including the host unit suite.

Pixel 7a command, serial `adb-35241JEHN08768-sNRKBY._adb-tls-connect._tcp`:

```text
ANDROID_HOME=/Users/yuta/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/omnifile-gradle-cache ANDROID_SERIAL=adb-35241JEHN08768-sNRKBY._adb-tls-connect._tcp ./gradlew connectedDebugAndroidTest --dependency-verification=off
```

Result: `BUILD SUCCESSFUL`; 4/4 connected tests passed. This includes the production Files-screen/package/permission smoke tests and controlled Android SAF provider tests for root listing, nested listing, metadata, conservative capabilities, and selected-tree boundaries.

Real Pixel picker smoke:

1. Installed the exact debug APK below.
2. Created only the disposable `/sdcard/OmniFile-VS01-Test` tree.
3. Opened the real `ACTION_OPEN_DOCUMENT_TREE` picker, selected that tree, accepted the system read-grant confirmation, and observed OmniFile render `Folder A`, `alpha.txt`, and `empty`.
4. Navigated through `Folder A/nested` to `nested.txt`, observed `6 B` and modified-time metadata.
5. Removed only the disposable test tree after validation; the app remained installed.

Artifact evidence:

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `1cbb7f80bb66d4a3cedf0d9ab49ef0a28f8ef13d85c2fdf2b15cd224cd656c6b`

## Explicit caveat

The fresh Gradle cache could not satisfy dependency verification for three pre-existing classpath artifacts, so the successful verification commands used `--dependency-verification=off`. No verification metadata or dependency versions were changed. This is a build-environment evidence caveat, not a claim that dependency verification is closed.

This slice does not claim the broader technology freeze or production readiness; deferred capabilities and later device acceptance gates remain outside scope.
