# P05-003 Environment

## Scope and checkout

This campaign ran only in:

```text
Path:   /Users/yuta/Desktop/File Manager-worktrees/p05-media3
Branch: poc/core-readiness-media3-v1
Continuation base: e27e88168314a0de9b3d4809574da60e5aec72b0
```

The worktree was a linked worktree and was clean before the P05-003 files were added. No other File Manager worktree was edited.

## Available host toolchain

| Tool | Observed state | P05-003 use |
|---|---|---|
| Python | `Python 3.9.6` | Used for disposable standard-library harness. |
| `unittest` | Available through Python 3.9 | Used for the 10 host contract tests. |
| Java | `/usr/bin/java` present | Available for a disposable raw build. |
| Kotlin compiler | Not found | Android/Kotlin harness not feasible in this checkout. |
| Gradle wrapper/project | Not present in this worktree | No reproducible project build; cached AAR inspection only. |
| Android application source | Not present | Instrumentation/device tests not feasible. |
| ADB | Available and used only by the parent runtime campaign | No Media3 ADB run was performed in this branch. |

Cached Media3 1.3.1 AARs are present for a future disposable build:
`media3-common`, `datasource`, `decoder`, `container`, `database`, `extractor`,
`exoplayer`, `session`, and `datasource-okhttp`. Their presence is not a
compiled or runtime result and does not freeze a dependency version.

## FLACtify boundary

`/Users/yuta/Desktop/FLACtify` was inspected read-only. Its pre-existing state was dirty on `main` at `488f306`, including Gradle/IDE/release metadata and APK files. P05-003 did not modify, build, clean, or test FLACtify.

The observed FLACtify Media3 baseline remains reference evidence only: Media3 `1.3.1`, `PlaybackService`, `PlaybackController`, and a `PlayerViewModel`-coupled URI path. It is not a dependency or architecture freeze for File Manager.

## Execution status

The only executable evidence in this worktree is the pure host contract
harness. Android, Media3, SAF playback, physical-device playback,
non-seekable runtime behavior, remote-provider playback, and player recreation
remain `NOT_TESTED`, not inferred from host tests.
