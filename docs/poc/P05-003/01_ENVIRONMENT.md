# P05-003 Environment

## Scope and checkout

This campaign ran only in:

```text
Path:   /Users/yuta/Desktop/File Manager-worktrees/p05-media3
Branch: poc/core-readiness-media3-v1
Continuation base: 3c4cf3b1e1b78911cb4714b88de1c0d818ae8e8c
```

The worktree was a linked worktree and was clean before the P05-003 files were added. No other File Manager worktree was edited.

## Available host toolchain

| Tool | Observed state | P05-003 use |
|---|---|---|
| Python | `Python 3.9.6` | Used for disposable standard-library harness. |
| `unittest` | Available through Python 3.9 | Used for the 15 host contract tests. |
| Java | Java 26.0.1 at `/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home` | Used by the disposable Android build. |
| Kotlin compiler | Not found | PoC uses Java; no Kotlin surface was added. |
| Gradle | Gradle 9.6.0 distribution under `/Users/yuta/.gradle/wrapper/dists/` | Used directly because this repository has no existing wrapper. |
| Android Gradle Plugin | Cached/managed `com.android.application` 9.4.0 | Used only by the disposable PoC. |
| Android SDK | `/Users/yuta/Library/Android/sdk`, platforms API 31/36/37 | PoC compiles with API 36, min API 31. |
| Android application source | Added only under `tools/p05_003_harness/media3-poc/` | Compile/package verified; no automated instrumentation run; parent manual device run is documented below. |
| ADB | Available at `/usr/local/bin/adb` | Used only by the explicitly authorized parent-controlled Pixel 7a runtime phase. |

Cached Media3 1.3.1 AARs were resolved for the disposable build:
`media3-common`, `datasource`, `decoder`, `container`, `database`, `extractor`,
`exoplayer`, `session`, and `datasource-okhttp`. The build resolved the P05-only
`1.3.1` pin online after the offline attempt showed that
`androidx.core:core:1.8.0` was not cached. This is compiled artifact evidence
only and does not freeze a production dependency version.

## FLACtify boundary

`/Users/yuta/Desktop/FLACtify` was inspected read-only. Its pre-existing state was dirty on `main` at `488f306`, including Gradle/IDE/release metadata and APK files. P05-003 did not modify, build, clean, or test FLACtify.

The observed FLACtify Media3 baseline remains reference evidence only: Media3 `1.3.1`, `PlaybackService`, `PlaybackController`, and a `PlayerViewModel`-coupled URI path. It is not a dependency or architecture freeze for File Manager.

## Execution status

Fresh executable evidence consists of the 15-test host contract suite, a
successful Android `:app:assembleDebug` build, and the separately recorded
parent-controlled Pixel 7a runtime phase. The APK was inspected with
`aapt2` as `org.omnifile.poc.media3`, version `0.5.0-poc`, compile/target API
36, min API 31. Android/Media3 playback, SAF playback, physical-device
playback, non-seekable runtime behavior, remote-provider playback, and player
recreation remain `NOT_TESTED` where not covered by the bounded API 36 run; they
are not inferred from build or host tests.
