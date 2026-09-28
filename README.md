# OmniFile

OmniFile is an Android file manager focused on fast, non-root file browsing and
durable file operations through the Storage Access Framework (SAF), with
integrated media playback and safe archive browsing.

> **Status: active development.** The features below reflect the currently
> published slice. Work beyond it is in progress and is not yet available in
> released builds.

## Requirements

- Android 12 (API 31) or newer (`minSdk 31`)
- Built and verified against Android 16 (API 36) (`compileSdk` / `targetSdk 36`)

## Current functionality

- **File browsing** — local filesystem and SAF providers, with a unified model
- **Selection and mutations** — rename and delete with confirmation
- **Durable copy / move** — restartable SAF transfers with persisted state
- **Search** — local and SAF-backed search
- **Media playback** — audio playback via Media3 with a media session
- **Archive browsing** — browse archive contents and extract to a
  user-chosen destination

Cloud storage, root-level filesystem access, and additional archive formats are
design goals, not shipped features.

## Building

Prerequisites:

- JDK 17 or newer
- Android SDK with platform API 36 and matching build tools

The repository includes a Gradle wrapper, so no local Gradle installation is
required:

```bash
./gradlew :app:assembleDebug          # build the debug APK
./gradlew :app:testDebugUnitTest      # run unit tests
./gradlew :app:lintDebug              # run Android lint
./gradlew :app:assembleDebugAndroidTest   # build the instrumentation test APK
```

Instrumentation tests require a connected device or running emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Dependencies are locked and checksum-verified
(`app/gradle.lockfile`, `gradle/verification-metadata.xml`).

## License

OmniFile is licensed under the [Apache License 2.0](LICENSE).

Third-party dependencies remain governed by their own licenses. See
[NOTICE](NOTICE) for details.
