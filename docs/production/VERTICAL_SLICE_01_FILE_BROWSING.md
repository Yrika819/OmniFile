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

## Pre-closure verification record

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

## Dependency-verification closure

The historical caveat was reproduced from a new Gradle cache with:

```text
ANDROID_HOME=/Users/yuta/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/omnifile-gradle-final-verify.Q0GjP8 ./gradlew testDebugUnitTest --dependency-verification=strict
```

The root cause was missing SHA-256 metadata, not a changed artifact, signature failure, repository change, lockfile mismatch, or dependency-version drift. Strict verification first rejected these three `MavenRepo` classpath metadata artifacts:

- `com.google.guava:guava-parent:33.4.0-jre` — `guava-parent-33.4.0-jre.pom`, introduced by the AGP 9.4.0 classpath's resolved `com.google.guava:guava:33.4.0-jre`.
- `org.junit:junit-bom:5.10.2` — `junit-bom-5.10.2.module`, reached through Apache Commons parent 69 used by the AGP classpath's `commons-io:2.16.1` graph.
- `org.junit:junit-bom:5.11.0-M2` — `junit-bom-5.11.0-M2.module`, reached through Apache Commons parents 71/72 used by the AGP classpath's `commons-codec:1.17.1`, `commons-compress:1.27.1`, and `commons-lang3:3.16.0` graph.

The reviewed `gradle/verification-metadata.xml` repair adds only SHA-256 entries for those artifacts and the alternate POM metadata required by the same resolved graph: `org.apache.groovy:groovy-bom:4.0.29`, `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.8.0`, and `org.junit:junit-bom:5.13.3`. No trusted-group rule, repository, verification mode, production dependency declaration, version catalog entry, or lockfile was broadened or changed. The existing policy remains `verify-metadata=true` and `verify-signatures=false`, with SHA-256 verification.

VS01 added no Gradle dependency/configuration delta relative to the Production Scaffold. The closure also fixed two API-31 lint errors exposed by the strict final lint path: `FilesViewModel` now uses `removeAt(navigation.lastIndex)` instead of the API-35 `List.removeLast()` call; navigation behavior is unchanged.

## Final closure verification

All final Gradle commands below used dependency verification enabled with `--dependency-verification=strict`; none used `--dependency-verification=off`:

```text
ANDROID_HOME=/Users/yuta/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/omnifile-gradle-cache ./gradlew clean testDebugUnitTest lintDebug assembleDebug --dependency-verification=strict
ANDROID_HOME=/Users/yuta/Library/Android/sdk GRADLE_USER_HOME=/private/tmp/omnifile-gradle-cache ANDROID_SERIAL=adb-35241JEHN08768-sNRKBY._adb-tls-connect._tcp ./gradlew connectedDebugAndroidTest --dependency-verification=strict
```

- Independent fresh-cache host run: `testDebugUnitTest` — `BUILD SUCCESSFUL`; 31 actionable tasks.
- Clean host run: `clean testDebugUnitTest lintDebug assembleDebug` — `BUILD SUCCESSFUL`; 58 actionable tasks. Host XML reports contain 10/10 unit tests with zero failures/errors.
- Pixel 7a instrumentation, serial `adb-35241JEHN08768-sNRKBY._adb-tls-connect._tcp`: `connectedDebugAndroidTest` — `BUILD SUCCESSFUL`; 4/4 tests passed.
- Focused final-artifact regression: exact APK installation succeeded; cold launch returned `Status: ok`; the rendered UI exposed `Files`, `Browse files`, `Local files`, and `フォルダを選択`; a cleared crash buffer remained empty. The installed `base.apk` hash matched the local build hash.

Final artifact evidence:

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `ae480c0ed8bbf67fafd68183a4a99638f2d820289bc0493040eaffa256a102ff`
- Application ID: `com.omnifile`
- Version: `0.1.0` / versionCode `1`
- minSdk `31`; targetSdk `36`; compileSdk `36`
- Native inventory: `libandroidx.graphics.path.so` for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`; this is the locked `androidx.graphics:graphics-path:1.0.1` transitive dependency of Compose UI and is covered by verification metadata.

The earlier real `ACTION_OPEN_DOCUMENT_TREE` evidence remains historical evidence for the pre-closure APK above. The final closure changed only verification metadata and a behavior-preserving API-31 source spelling; the final focused regression and 4/4 instrumentation run passed, while no new disposable SAF tree was created.

## Historical caveat disposition

Earlier VS01 runs used `--dependency-verification=off` because the three classpath metadata artifacts above were not yet recorded. That historical fact is retained here; the caveat was subsequently closed by the reviewed metadata repair and the strict fresh-cache and final-device verification above.

This slice does not claim the broader technology freeze or production readiness; deferred capabilities and later device acceptance gates remain outside scope.
