# P05-001 — Failures and Anomalies

**Label:** `POC-ONLY — NOT PRODUCTION AUTHORITY`

## Host harness result

The deterministic Python stdlib suite passed:

```text
Ran 5 tests ...
OK
```

The five passing behavior groups are:

- partial MOVE output is `RESUMABLE` and cannot delete the source;
- ambiguous finalization is `NEEDS_ATTENTION` and cannot delete the source;
- verified-final MOVE output permits the source-delete step;
- all six executor-shaped candidates are represented without runtime claims;
- the report is explicitly fixture-only and leaves executor selection null.

## Runtime result

The disposable APK was built and exercised on both authorities. The runtime
contract test passed:

```text
Ran 6 tests ...
OK
```

API36 Pixel evidence shows `START`/`COMPLETE` for foreground app, foreground
`dataSync` service, JobScheduler, and UIDT. API31 emulator evidence shows the
same for foreground app, foreground service, and JobScheduler; UIDT is
`NOT_APPLICABLE_API_LT_34`. The Pixel restart run shows `REDISCOVER` with
`RUNNING` durable state and completion; the preserved API31 restart row begins
from `ABSENT`, so API31 recovery is not claimed.

The Pixel lifecycle probe observed `ACTIVITY_STOP` after Home, and the explicit
power-state capture observed `mWakefulness=Dozing` then `Awake` for screen-off
and wake. The direct shell process signal was rejected by device SELinux:

```text
DIRECT_SIGNAL=BLOCKED_BY_DEVICE_SELINUX; FALLBACK=AM_FORCE_STOP
```

The fallback is a reproducible mid-operation app-stop/restart scenario: the
runner waits for a `CHECKPOINT` while `COMPLETE` is absent before stopping, and
the resumed report reaches `REDISCOVER` then `COMPLETE`. It is not a claim that
all kernel process-death paths were exercised. A Recents swipe removed the task
and process, but no Activity `onTaskRemoved` callback is claimed. WorkManager
and media-processing FGS remain explicit gates.

After the final APK hardening, repeated attempts to refresh the API31 matrix
ended with the `poc_filemanager_api31` AVD exiting before it stayed connected to
ADB. The existing API31 rows remain real prior-emulator observations, but
`final-artifact-refresh.txt` records that final-artifact equivalence is not
claimed until the AVD is repaired or replaced.

## Fixture observations

The generated report contains six synthetic observations:

| API label | Executor shape | Synthetic outcome | Classification | Source delete |
| ---: | --- | --- | --- | --- |
| 31 | In-app foreground | Partial | `RESUMABLE` | false |
| 34 | UIDT | Ambiguous finalization | `NEEDS_ATTENTION` | false |
| 36 | WorkManager | Verified final MOVE | `COMPLETE` | true |
| 31 | FGS `dataSync` | Partial | `RESUMABLE` | false |
| 35 | FGS `mediaProcessing` | Verified final COPY | `COMPLETE` | false |
| 36 | Unresolved local copy | No selected mechanism | `RESTART_REQUIRED` | false |

These rows demonstrate the safety oracle only. They do not report platform
behavior, timing, quota, lifecycle, notification, or provider results.

## Result

The hypotheses are supported within the narrow host fixture model. The POC
does not close the executor-mapping decision and does not authorize production
implementation.

The runtime evidence must not be generalized to arbitrary devices, workloads,
quotas, notification settings, or providers. No executor is selected as a
universal mechanism.

## Latest-hardening evidence boundary

The preserved Pixel/API36 and API31 rows were produced before the final
cancellation/provenance hardening commit `c9fcf2d`; the documentation boundary
was recorded at `81f886e`. They remain valid only for the exact earlier APK and
are not retroactively attributed to the latest head.

On 2026-09-17 the parent rebuilt runtime source from `81f886ed` and freshly installed APK
`1daaedd0280b4841e26ae35959170acd3ccd9b234bd131f5d48a0c9633ace7c0` on the
Pixel 7a. The focused sanity passed for startup, all four available executor
modes, durable-state discovery, and app restart/re-discovery. This is fresh
latest-hardened-runtime evidence, but it is intentionally narrower than the preserved
lifecycle campaign. The direct shell `kill -9` limitation reproduced again as
`Operation not permitted`; the existing `am force-stop` fallback completed the
restart scenario.

The fresh FGS report records `COMPLETE` followed by `SERVICE_DESTROY` with
`durableState=RUNNING`. The runtime service writes that lifecycle field before
the final persisted state is independently checked; the authoritative state
file was `phase=COMPLETE`. This report-shape anomaly is retained as a harness
limitation and is not used as evidence that completion regressed.
