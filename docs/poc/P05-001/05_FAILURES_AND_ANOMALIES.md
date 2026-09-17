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

JobScheduler has no executable fixture in this branch and remains `NOT_TESTED`;
the six rows above must not be generalized to it.
