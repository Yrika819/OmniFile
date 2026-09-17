# P05-001 — Fixture Model

**Label:** `POC-ONLY — NOT PRODUCTION AUTHORITY`

The fixture model represents evidence inputs as immutable metadata. It does not
open files, start Android work, delete sources, or emulate an Android scheduler.

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

## Default scenarios

`default_fixtures()` supplies six generated metadata records spanning API-label
31/34/35/36 and six executor-shaped candidates. They are not observations from
an Android device or emulator. The report sets `platform_runtime_observed` and
`adb_used` to `false` so downstream readers cannot mistake them for platform
evidence.
