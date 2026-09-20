# OmniFile VS03 — Durable Copy/Move

Status: **IMPLEMENTED / HOST-VERIFIED / PIXEL-INSTRUMENTED — FINAL REVIEW AND PUSH PENDING**

## Scope and base

- VS02 base: `14732fd8f120b93380e624143e534cceff6d09c4`
- Branch: `development/core-v1-durable-copy-move-v1`
- Supported production route: Local regular file -> Local regular file.
- Intentionally unsupported: all SAF transfer routes, directory Copy/Move, and background continuation.
- No WorkManager, UIDT, foreground service, archive, cloud, root, or network transfer was added.

## Persistence and durable truth

- AndroidX Room `2.8.4`.
- `OperationDatabase` schema version `1`.
- Exported schema: `app/schemas/com.omnifile.operations.persistence.OperationDatabase/1.json`.
- No destructive migration is configured.
- Schema version 1 has no v0->v1 production migration. The Android foundation test verifies that the exported schema asset is packaged and that a version-1 database opens; it does not claim a nonexistent migration path.
- Durable rows contain provider IDs and encoded locators, operation/batch IDs, type/state/stage, intended name, operation-owned partial locator, expected/completed `Long` byte counts, source version facts, verification/finalization facts, source-delete state, cancellation, error classification, and timestamps.
- Runtime objects (`Path`, `Uri` instances, streams, descriptors, jobs, workers, activities, and ViewModels) are not persisted.

State graph:

```text
PLANNED -> TRANSFERRING -> VERIFYING -> FINALIZING
                                      -> DESTINATION_COMPLETE
                                      -> SOURCE_DELETE_PENDING (MOVE only)
                                      -> COMPLETE
```

Interruption, conflict, retryable failure, failure, and cancellation are explicit states. A conflict discovered after execution begins is a legal `TRANSFERRING -> CONFLICTED` transition and remains durable without terminating the UI. CAS transitions validate the expected prior state. `DESTINATION_COMPLETE` requires provider-established evidence; Move source deletion is allowed only after that durable truth.

## Local transfer boundary

- Fixed 256 KiB bounded buffer.
- 8 MiB advisory checkpoint cadence.
- Progress and expected sizes use `Long`.
- Partial files are `.omnifile-<operation-id>.partial` and are operation-owned.
- Local locators use `root-relative-v1`, root containment, `NOFOLLOW_LINKS`, and descriptor-relative mutation checks.
- Local finalization now checks the destination name descriptor-relatively before moving and maps conflicts to the requested name; existing content is never silently overwritten.
- Safe restart is used; true offset resume is not advertised.
- Verification combines source/destination regular-file facts, expected size, reopen/stat checks, and finalization success; universal SHA-256 is not required.

## Operation Manager and recovery

`OperationManager` creates durable intent before execution, validates the complete provider capability route, executes foreground/in-app transfers, persists partials/checkpoints/errors, handles cancellation, exposes snapshots, and reconciles non-terminal rows after restart.

Recovery inspects current source/destination reality rather than blindly trusting checkpoints. It can adopt a matching finalized destination, safe-restart only an operation-owned partial, classify conflict/source change/provider failure, and handle durable destination completion before Move source deletion. Background continuation after process death is not promised.

## Cancellation, conflict, and batch behavior

- Cancellation intent is durable and cooperative.
- Incomplete transfers are never finalized and Move sources are never deleted.
- Partial output is retained for reconciliation according to the documented policy.
- Default conflict policy is no overwrite; conflict remains durable and existing destination bytes remain intact.
- Error classes include permission denied, not found/stale, source changed, destination conflict, unsupported route, provider I/O, cancellation, retryable interruption, and ambiguous finalization.
- Multiple selected regular files retain independent operation rows; one failure does not roll back completed independent copies.

## UI behavior

- Copy and Move are enabled only for supported selected regular files.
- Directories are gated and do not expose a working-looking transfer action.
- Destination picker reuses the existing provider-neutral Files navigation and preserves source selection until durable enqueue succeeds.
- Picker supports current-directory confirmation, nested browse/back, cancel, and eligible-directory gating.
- Operations UI shows type, source/destination, progress, stage/state, errors/conflicts, recent history, and cancellation where valid.
- Edge-to-edge and IME/inset handling remain in the existing Compose architecture.
- SAF Copy/Move remains disabled because sequential read/write, durable grants, operation-owned partials, and safe finalization are not proven.

## Route matrix

| Route | Status | Reason |
|---|---|---|
| Local -> Local | Supported | Secure Local transfer primitives and durable locators are implemented. |
| Local -> SAF | Unsupported | Destination write/partial/finalization proof is absent. |
| SAF -> Local | Unsupported | SAF sequential read transfer is absent. |
| SAF -> SAF | Unsupported | Neither transfer side is complete. |
| Directory routes | Unsupported | Recursive traversal and safe directory deletion are deferred. |

## Verification evidence

### Gradle/KSP diagnosis

- Previous JDK 26 runs stalled at `kspDebugUnitTestKotlin` for up to 30 minutes and left single-use daemons busy.
- Process sampling showed the daemon stuck in Gradle/Kotlin worker/class-loading activity; disk space and cache locks were not the cause.
- An isolated KSP run under installed Android Studio JBR `25.0.3` passed in 3m36s. This established a JDK 26/Kotlin-KSP compatibility/resource interaction.
- Remediation: serialized `--no-daemon --max-workers=1` Gradle invocations under JBR 25. No permanent toolchain upgrade was made.
- Confirmed closure defects fixed during verification: Kotlin test collection compatibility, Local finalization overwrite/conflict classification, API-31-incompatible `Path.of` calls, Room test coroutine invocation, schema asset packaging, and the no-migration Room foundation test shape.

### Host

Final exact-tree command:

```text
:app:testDebugUnitTest --dependency-verification=strict --no-daemon --max-workers=1
```

Result: **58 tests, 58 passed, 0 failed, 0 errors, 0 skipped**.

Covered areas include state transitions and illegal transitions, durable records/CAS, Local locators and containment, partial creation, bounded transfer, no-overwrite conflict, Copy, Move fault safety, cancellation, reconciliation/checkpoint behavior, and Files/ViewModel selection/mutation behavior.

### Room/database and migration foundation

Pixel instrumentation executed the Room test class successfully after closure repairs. It covered close/reopen persistence, `Long` counters, CAS stale-state rejection, non-terminal filtering, and the packaged schema/version-1 database-open foundation. No v0->v1 migration PASS is claimed because no such production migration exists.

### Lint and builds

- `:app:lintDebug --dependency-verification=strict`: **PASS**, warnings only; no errors.
- `:app:assembleDebug --dependency-verification=strict`: **PASS**.
- `:app:assembleDebugAndroidTest --dependency-verification=strict`: **PASS**.
- Gradle execution used JBR `25.0.3`, no daemon, and one worker. Final runs remained serialized.

### Android instrumentation

Direct `AndroidJUnitRunner` execution on the physical Pixel 7a (`Pixel 7a`, Android `16`, API `36`) produced:

```text
OK (21 tests)
```

The 21 tests include production startup, real Room-backed Local Copy/Move completion with durable Move ordering, Room schema foundation, controlled DocumentsProvider/SAF navigation behavior, and Compose Files behavior. This is physical Pixel evidence; it is not emulator evidence.

The Gradle `connectedDebugAndroidTest` task was also attempted. One API-36 emulator attempt initially failed while core services were unavailable; a later Gradle retry exited 0 but generated a zero-test report. That zero-test report is not counted as a pass. Direct Pixel runner evidence is the authoritative instrumentation result for this closure.

### Pixel runtime sanity and UI gates

- Pixel install of both APKs: **PASS**.
- Pixel launch of `com.omnifile/.MainActivity`: **PASS**; process remained alive and activity displayed in approximately 633 ms.
- No app FATAL/ANR was observed in the bounded startup logcat check.
- Disposable Pixel Local browse displayed the app-owned `profileInstalled` file.
- Long-press selection exposed Copy, Move, and Delete.
- Actual Android Back exited selection mode while remaining in the directory; it did not navigate away on the first Back.
- Copy launched `COPY destination`; Back returned to the selected source without enqueueing.
- The first same-folder Copy confirmation reproduced a real crash (`TRANSFERRING -> CONFLICTED` was missing from the state machine). The conflict transition, durable `SOURCE_DELETE_PENDING` Move ordering, and post-execution Operations refresh were repaired. Focused/host tests passed, and the repaired Pixel conflict repeat kept `MainActivity` alive and rendered `Operations`, `COPY: profileInstalled`, `CONFLICTED`, and the durable conflict message. No overwrite occurred and no personal data was touched.
- Pixel test APKs and app APK were uninstalled after the runtime run.
- Pixel instrumentation directly proved successful Room-backed Local Copy and Move completion, including source preservation/deletion ordering. A picker-confirmed successful UI Copy/Move run, cancellation runtime, and process-death runtime remain unclaimed.

## APK evidence

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Application ID: `com.omnifile`
- Version name/code: `0.1.0` / `1`
- minSdk: `31`
- targetSdk: `36`
- APK SHA-256: `9fb0d868241a811e84c1806f89e125979fd885a0a26307d75ba41215fabb838c`
- Native libraries: `libandroidx.graphics.path.so` for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
- Android-test APK contains `assets/com.omnifile.operations.persistence.OperationDatabase/1.json`.

## Review and remaining closure work

Existing staged review artifacts remain under `tmp/reviews/` for Reviews A-E, final code review, and the previous regression review. A fresh final code-review report and a fresh VS02->VS03 regression-review report are required after this closure delta; both must account for the exact evidence limitations above.

Remaining intentionally unsupported work: SAF transfer implementation, directory Copy/Move, background continuation, WorkManager/UIDT/FGS selection, archives, and unrelated media/cloud/root features.
