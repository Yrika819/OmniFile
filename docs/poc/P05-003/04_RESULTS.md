# P05-003 Results

## Host harness

Command:

```text
PYTHONPATH=tools/p05_003_harness python3 -m unittest discover -s tools/p05_003_harness -p 'test_*.py' -v
```

Fresh result:

```text
Ran 15 tests
OK
```

The tests cover source-mode selection for explicitly readable native descriptors, random/range reads, seekable reads, sequential cached fallback, unsupported seek, empty capabilities, descriptor-presence rejection, collision-safe version-aware locator-free cache identity, requested random-range precedence, ordered runtime event capture, redacted failure detail, fixed-chunk sequential reads, EOF, unsupported seek, and reopen.

## TDD evidence

The new implementation cycle was RED before the host additions. The focused
command failed during import with:

```text
ModuleNotFoundError: No module named 'p05_003_contract'
```

After the minimal implementation was added, the focused suite completed with
15 passing tests, and the full discovery command completed with the same 15.

## Android build

Command:

```text
ANDROID_HOME=/Users/yuta/Library/Android/sdk ANDROID_SDK_ROOT=/Users/yuta/Library/Android/sdk /Users/yuta/.gradle/wrapper/dists/gradle-9.6.0-bin/42k10rwplmzkhuboz9kdazi7s/gradle-9.6.0/bin/gradle :app:assembleDebug
```

Fresh result: `BUILD SUCCESSFUL` with 33 actionable tasks. `aapt2`
identified the artifact as `org.omnifile.poc.media3`, version `0.5.0-poc`,
compile/target SDK 36, min SDK 31. The current rebuilt APK SHA-256 is
`1e5cd32f69a4c30d4b083ef6ca1f79b97e0b34026b73233ea1957bde846cc590`.

## Pixel 7a runtime evidence

The parent installed the earlier recorded disposable APK
`716a6ca2b5a949ee6793de676c970aba9c1ae5900631b95fdd7ac049078c25da` on Pixel 7a, API 36,
`BP4A.251205.006`, `arm64-v8a`, 4 KiB page size. Actual Media3/ExoPlayer
`1.3.1` playback produced duration 2000 ms, playback at 0 ms, seek to 1000
ms, EOF, and stop for the local WAV fixture. The committed primary FLAC asset
(`assets/p05_003_primary.flac`) produced the same direct-playback/seek/EOF
sequence. A real `ACTION_OPEN_DOCUMENT_TREE` selection resolved a playable
WAV child from a disposable device tree; direct SAF playback produced the
same duration, seek, and EOF evidence.

The SAF child was also opened through the custom sequential DataSource. It
produced prepare/start/duration/playback/EOF, then the deliberate 50% seek
probe produced `ERROR_CODE_IO_UNSPECIFIED:ExoPlaybackException:Source error`.
This is the expected non-seekable boundary, not evidence that sequential
playback supports seek. Runtime events were captured from the `P05-003`
logcat tag; the app-private JSONL was not readable through `run-as` on the
non-debuggable disposable package.

## Untested execution

The following remain `NOT_TESTED`: Android API 31, pipe-backed provider
behavior, process death/session reconnection, SMB/SFTP/WebDAV/cloud range
playback, and human audio fidelity. The runtime result does not substitute
for those evidence classes.
