# OmniFile VS03 — Durable Copy/Move

Status: **IMPLEMENTED / NON-PIXEL VERIFICATION IN PROGRESS**

## Base and persistence

- VS02 base: `14732fd8f120b93380e624143e534cceff6d09c4`
- Branch: `development/core-v1-durable-copy-move-v1`
- Persistence: AndroidX Room `2.8.4`
- Database: `OperationDatabase`, schema version `1`
- Schema artifact: `app/schemas/com.omnifile.operations.persistence.OperationDatabase/1.json`
- Migration policy: exported schemas are committed; no destructive migration is configured. Android `MigrationTestHelper` foundation exists, but connected execution remains pending.

Room rows use provider-controlled strings and `Long` counters. They never persist `Path`, `Uri` instances, descriptors, streams, coroutines, workers, or UI objects.

## Durable operation model

Each selected regular file gets an independent operation row with a shared `batchId`. Durable fields include source/destination provider IDs and locator encodings, intended name, operation-owned partial locator, expected and completed byte counts, source version fact, verification/finalization descriptions, source-delete state, cancellation intent, error classification, message, and timestamps.

States:

```text
PLANNED
  -> TRANSFERRING
  -> VERIFYING
  -> FINALIZING
  -> DESTINATION_COMPLETE
  -> SOURCE_DELETE_PENDING (MOVE only)
  -> COMPLETE
```

Interruption, conflict, retryable failure, failure, and cancellation are explicit states. `OperationStateMachine` centrally rejects illegal transitions. `DESTINATION_COMPLETE` requires an explicit provider-established evidence flag. Move completion requires `SOURCE_DELETE_STATE=DELETED`.

Room state updates use compare-and-set on the expected prior state. Progress checkpoints are advisory and never treated as storage truth.

## Durable locators

- Local: `root-relative-v1`, normalized root-relative value, validated against the configured root and `NOFOLLOW_LINKS` ancestor checks.
- SAF: production transfer locator/resolution is not yet implemented; current SAF navigation remains read-only and does not advertise transfer capabilities.

## Transfer boundary

`StorageTransferProvider` adds only capability-specific primitives:

- sequential read;
- operation-owned partial creation;
- sequential write;
- partial/final inspection;
- explicit finalization;
- durable source deletion;
- operation-owned partial deletion;
- durable locator encoding/resolution.

The generic engine does not see `Path`, `Uri`, `ContentResolver`, or provider sessions.

Local transfer uses:

- fixed 256 KiB buffer;
- `Long` byte counters;
- 8 MiB advisory checkpoint cadence;
- root-relative durable locators;
- collision-resistant `.omnifile-<operation-id>.partial` files;
- descriptor-relative secure creation, finalization, source deletion, and partial cleanup;
- no overwrite; existing intended final names classify as conflict;
- safe restart rather than true offset resume (`RESUME_WRITE` is not advertised).

A finalization result is explicit: finalized, ambiguous, or unsupported. Source deletion is never triggered by stream close.

## Operation Manager and recovery

`OperationManager` creates durable intent before execution, validates complete provider capability routes, runs the foreground transfer engine, persists partial locators and checkpoints, persists error classification, handles cancellation, and exposes durable snapshots through `OperationsViewModel`.

Restart reconciliation:

1. query recent/non-terminal Room rows;
2. resolve source and destination parent locators;
3. inspect existing intended final output for a `FINALIZING` record;
4. adopt destination completion only when provider facts match the expected regular-file size;
5. otherwise remove only the operation-owned partial and safe-restart;
6. reconcile `DESTINATION_COMPLETE`/`SOURCE_DELETE_PENDING` without replaying bytes;
7. delete a Move source only after durable destination completion.

The executor is foreground/in-app only. If the process is gone, the operation intent survives and is reconciled after restart; background continuation is not promised.

## Cancellation, conflicts, and errors

Cancellation persists intent, cooperates at transfer boundaries, never finalizes incomplete data, never deletes a Move source, and retains operation-owned partial output for reconciliation.

Default conflict policy is no overwrite. Error classes include permission denied, not found/stale, source changed, destination conflict, unsupported route, provider I/O, cancellation, retryable interruption, and ambiguous finalization. Error code and user-readable message are durable.

Batch items remain independent; one failed item does not roll back completed copies. Aggregate UI remains a follow-up once per-item history is stable.

## Files UI

- Copy/Move actions appear only for selected regular files with truthful read/delete capabilities.
- Directories remain unsupported and are not exposed as working transfers.
- Copy/Move enters an explicit destination-picker mode reusing Files provider-neutral navigation.
- Back/cancel exits the picker without discarding source selection.
- Confirming the current eligible directory is the only point that creates durable operations.
- SAF Copy/Move remains disabled because the current SAF route has no sequential read, write grant persistence, partial creation, or safe finalization proof.
- A minimal Operations panel displays recent durable rows, progress, stage/state, error text, and cancellation.
- Activity already uses `enableEdgeToEdge`; the picker/panel use Scaffold/PaddingValues and navigation-bar padding. `adjustResize` is set for the existing rename TextField path.

## Route matrix

| Route | Current VS03 status | Reason |
|---|---|---|
| Local -> Local | Supported in engine/provider; UI path is implemented | Secure Local primitives and durable locators exist |
| Local -> SAF | Unsupported | SAF destination write/partial/finalization not proven |
| SAF -> Local | Unsupported | SAF sequential read not implemented |
| SAF -> SAF | Unsupported | Neither transfer side is complete |

Directory Copy/Move is deferred; no recursive traversal or directory deletion is exposed.

## Tests and evidence

- Strict `:app:compileDebugKotlin`: passed after durable model, provider, engine, coordinator, and UI integration.
- Focused state-machine host test: passed before later test-source additions.
- Room schema generated successfully and inspected; Room instrumentation source covers close/reopen, CAS, terminal filtering, Long counters, and migration-helper foundation.
- Local transfer and OperationManager host test sources cover partial ownership, conflict, containment, Copy, Move fault safety, and capability rejection.
- Repeated `testDebugUnitTest` / Android-test compile attempts timed out in `kspDebugUnitTestKotlin` under the only installed JDK 26 before producing test results. These are not claimed as passing.
- `adb` could not start in this environment (`smartsocket` permission denied); no Pixel or emulator evidence exists.
- APK assemble, lint, connected tests, APK hash, native inventory, and app startup/logcat checks remain pending fresh execution after the test-pipeline blocker is resolved.

## Review artifacts

- Review A: `tmp/reviews/2026-09-19-code-review-review-a.md`
- Review B: `tmp/reviews/2026-09-19-code-review-review-b.md`
- Review C: `tmp/reviews/2026-09-19-code-review-review-c.md`
- Review E: `tmp/reviews/2026-09-19-code-review-review-e.md`
- Required final full review and VS02→VS03 regression review remain pending.

## Explicit remaining gates

- Complete deterministic host transfer/engine tests despite the KSP test-variant timeout.
- Execute Room instrumentation/migration tests.
- Add controlled SAF transfer primitives and write-grant handling before enabling SAF actions.
- Run lint, assembleDebug, assembleDebugAndroidTest, connected/Compose tests, and runtime sanity.
- Obtain physical Pixel evidence if adb/device access becomes available; do not relabel emulator evidence as Pixel evidence.
