# P05-003 Results

## Host harness

Command:

```text
PYTHONPATH=tools/p05_003_harness python3 -m unittest discover -s tools/p05_003_harness -p 'test_*.py' -v
```

Fresh result:

```text
Ran 10 tests in 0.003s
OK
```

The tests cover source-mode selection for explicitly readable native descriptors, random/range reads, seekable reads, sequential cached fallback, unsupported seek, empty capabilities, descriptor-presence rejection, collision-safe version-aware locator-free cache identity, and requested random-range precedence.

## TDD evidence

Before implementation, the focused command failed during test discovery with:

```text
ModuleNotFoundError: No module named 'p05_003_contract'
```

After the minimal implementation was added, the same command completed with 10 passing tests.

## Untested execution

The following remain `NOT_TESTED`: Media3 compilation, ExoPlayer playback, Android API 31/API 36 behavior, SAF descriptor behavior, process death/session reconnection, SMB/SFTP/WebDAV/cloud range playback, audio fidelity, and any ADB/device action. The host result does not substitute for those evidence classes.
