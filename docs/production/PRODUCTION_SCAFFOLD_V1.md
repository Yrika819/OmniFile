# OmniFile Production Scaffold V1

Status: implementation scaffold only. This document is the authority for the
initial Android project setup; it does not authorize the first CORE_V1
vertical slice.

## Identity and lineage

- Base commit: `5b6d4f9c025ab86917be77d5e96663aa7c414d87`
- Branch: `development/core-v1-production-scaffold`
- Project name and application label: `OmniFile`
- `applicationId`: `com.omnifile` (historically frozen)
- Kotlin `namespace`: `com.omnifile` (an explicit decision made for this new
  scaffold; it must not be recorded as historically frozen)
- Module graph: one `app` module

The production scaffold is isolated from the root worktree. No implementation
from the deferred storage, media, archive, network, cloud, root, operation, or
saved-UI authorities is included here.

## Selected stable baseline

The scaffold uses the latest mutually compatible stable baseline that passes
against the installed stable Android API 36 platform. Android API 37 was not
selected because the platform release documentation identifies it as preview
at this phase.

| Area | Selection |
| --- | --- |
| Android Gradle Plugin | 9.4.0 |
| Gradle wrapper | 9.6.0 binary distribution |
| Kotlin / Compose compiler plugin | 2.2.10, supplied through AGP built-in Kotlin compatibility |
| Build JDK | Android Studio JBR 25.0.3 |
| `compileSdk` / `targetSdk` | 36 / 36 |
| `minSdk` | 31 |
| Compose BOM | 2025.12.00 |
| Material3 | 1.4.0 through the Compose BOM |
| Material3 Adaptive | 1.2.0 |
| AndroidX Core KTX | 1.17.0 |
| Activity Compose | 1.12.3 |

The current later Compose/AndroidX line was first evaluated, but its resolved
AAR metadata required compileSdk 37. The selected versions are the stable
API36-compatible line, not a claim that they are the globally newest stable
versions.

## Included implementation

- A single exported launcher `MainActivity`.
- Stable Compose and Material3 theme foundation, including light/dark color
  schemes, typography, and guarded dynamic color.
- One temporary shell text, `Production scaffold operational`.
- Unit test and plain Android instrumentation test foundations.
- Gradle dependency locking in `app/gradle.lockfile` and settings locking in
  `settings-gradle.lockfile`.
- SHA-256 dependency verification in `gradle/verification-metadata.xml`.
- `docs/production/NOTICE.md` as the OSS/NOTICE register entry point.

The platform manifest contains no storage, network, media, archive, root, or
cloud capability. The debug APK includes the transitive
`libandroidx.graphics.path.so` native library from the selected UI dependency
graph; no native code was authored by OmniFile in this scaffold.

## Explicitly not included

The following remain outside this phase: SAF/files browser, StorageProvider,
copy/move/delete/rename, archive, Media3, Home/Search/Music/Settings product
surfaces, saved UI authority implementation, Durable Operation Manager, root,
network/cloud providers, production signing, and release configuration.

## Verification record

Commands were run with the local Android SDK supplied through environment
variables; no developer-specific `local.properties` was committed.

| Check | Result |
| --- | --- |
| `:app:checkDebugAarMetadata` | PASS |
| `:app:testDebugUnitTest` | PASS |
| `:app:lintDebug` | PASS |
| `:app:assembleDebug` | PASS |
| `:app:connectedDebugAndroidTest` on Pixel 7a, API 36 | PASS, 2 tests |
| APK install and launch on Pixel 7a | PASS |
| APK application id | `com.omnifile` |
| APK label | `OmniFile` |
| APK version | code `1`, name `0.1.0` |
| APK min/target SDK | `31` / `36` |
| APK SHA-256 for the installed artifact | `df8ddccdf31245b14a5376ea1c33a38de7a9d09a7121c519af12df92597f5769` |
| Production signing / release artifact | NOT INCLUDED |

The installed-artifact hash above refers to the APK installed after the final
Pixel 7a validation. Any later rebuild is a different artifact and must be
recorded separately.

## Authority boundary

This scaffold is ready for review and publication of the scaffold branch. It
is not a product-readiness claim and does not close legal, provider,
lifecycle, native, scale, security, or human visual/audio acceptance gates for
future features.
