# P05-001 — Harness Design

**Label:** `POC-ONLY — NOT PRODUCTION AUTHORITY`

The fixture model represents evidence inputs as immutable metadata. A separate
disposable raw-APK runtime harness starts actual Activity, foreground-service,
JobScheduler, and UIDT components and persists checkpoint state. It does not
open user files, delete sources, or select a production scheduler.

## Fixture fields

| Field | Meaning | Allowed POC values |
| --- | --- | --- |
| `operation` | Conceptual operation family | `COPY`, `MOVE` |
| `executor` | Executor-shaped label only | `IN_APP_FOREGROUND_SHAPED`, `WORK_MANAGER_SHAPED`, `UIDT_SHAPED`, `FGS_DATA_SYNC_SHAPED`, `FGS_MEDIA_PROCESSING_SHAPED`, `UNRESOLVED_LOCAL_COPY_SHAPED` |
| `destination` | Observed synthetic destination reality | `PARTIAL`, `VERIFIED_FINAL`, `AMBIGUOUS_FINALIZATION`, `UNRESOLVED` |
| `source_present` | Whether the source still exists in the synthetic record | boolean |
| `api_level` | Scenario label, not a runtime measurement | `31`, `34`, `35`, or `36` in checked-in fixtures |
| `stop_event` | Synthetic interruption label | `PROCESS_DEATH`, `EXTERNAL_STOP` |

## Decision output

`classify_fixture` returns:

- `classification`: `RESUMABLE`, `NEEDS_ATTENTION`, `COMPLETE`, or
  `RESTART_REQUIRED`;
- `source_delete_allowed`: the narrow permission to perform the final MOVE
  source-delete step.

The classifier is deliberately smaller than a production Operation Manager. It
only encodes the safety boundary needed by this POC.

## Runtime harness

`runtime/build.sh` assembles and signs the POC APK with installed Android
platform/build tools. `runtime/run-pixel7a.sh` captures API36 Pixel evidence
and requires an explicit `ADB_SERIAL`; `runtime/run-lifecycle-pixel7a.sh`
captures background, screen, and Recents behavior;
the API31 capture is isolated under `runtime/results/api31/`. The report and
state are written atomically in app-scoped storage. API36 uses the shell-readable
external app-files directory because this ROM rejects `run-as` for the manually
signed package; API31 uses app-private storage and is collected through
`run-as` because its emulator external-storage provider was unavailable during
the first boot.

Observed mechanisms are foreground Activity, foreground `dataSync` service
with notification, JobScheduler, and UIDT on API36 only. WorkManager is
recorded `NOT_APPLICABLE` because this framework-only APK has no AndroidX
WorkManager dependency; JobScheduler is not substituted for it.

## Default scenarios

`default_fixtures()` supplies six generated metadata records spanning API-label
31/34/35/36 and six executor-shaped candidates. They remain synthetic fixture
rows. The runtime report is separate and adds device identity, API, PID,
lifecycle, scheduler return, and durable checkpoint/completion fields.
