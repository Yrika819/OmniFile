# P05-003 Results

## Host harness

Command:

```text
PYTHONPATH=tools/p05_003_harness python3 -m unittest discover -s tools/p05_003_harness -p 'test_*.py' -v
```

Fresh result:

```text
Ran 14 tests in 0.013s
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
14 passing tests, and the full discovery command completed with the same 14.

## Android build

Command:

```text
ANDROID_HOME=/Users/yuta/Library/Android/sdk ANDROID_SDK_ROOT=/Users/yuta/Library/Android/sdk /Users/yuta/.gradle/wrapper/dists/gradle-9.6.0-bin/42k10rwplmzkhuboz9kdazi7s/gradle-9.6.0/bin/gradle :app:assembleDebug
```

Fresh result: `BUILD SUCCESSFUL in 3m 21s` with 33 actionable tasks. `aapt2`
identified the artifact as `org.omnifile.poc.media3`, version `0.5.0-poc`,
compile/target SDK 36, min SDK 31. This verifies compilation and packaging,
not runtime playback.

## Untested execution

The following remain `NOT_TESTED`: ExoPlayer playback, Android API 31/API 36
runtime behavior, SAF descriptor behavior, process death/session reconnection,
SMB/SFTP/WebDAV/cloud range playback, audio fidelity, and any ADB/device
action. The host result and successful APK build do not substitute for those
evidence classes.
