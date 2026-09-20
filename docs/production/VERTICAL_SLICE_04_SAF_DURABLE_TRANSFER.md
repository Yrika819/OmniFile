# OmniFile CORE_V1 VS04 — SAF Durable Transfer

## Status

`OMNIFILE_CORE_V1_VS04_INCOMPLETE — CONTROLLED_PROVIDER_AND_REAL_SAF_PIXEL_VERIFICATION_PENDING`

This branch contains the SAF durable-transfer implementation and compiled controlled-provider/runtime test matrix, but no Android device was attached during this run. No SAF route is claimed supported in production from host compilation alone.

## Base and branch

- Published VS03 base SHA: `a95b3e28f47954b879b389ce6d5a5f17d4704407`
- VS04 branch: `development/core-v1-saf-transfer-v1`
- VS04 worktree: `/Users/yuta/Desktop/File Manager-worktrees/omnifile-saf-transfer-v1`
- Room schema: version `1`; no migration required

## Route matrix

| Route | Implementation state | Production claim | Reason |
|---|---|---|---|
| Local → SAF Copy | Implemented behind SAF provider adapter | Not supported/advertised yet | Production `finalizationProven` defaults to `false`; controlled and real provider finalization evidence is still pending. |
| Local → SAF Move | Implemented with durable source-delete ordering | Not supported/advertised yet | Same destination-finalization gate; source deletion remains after durable destination completion only. |
| SAF → Local Copy | Implemented in the generic engine | Not claimed supported yet | Requires controlled-provider and real Pixel source/grant/non-seekable evidence. |
| SAF → Local Move | Implemented in the generic engine | Not claimed supported yet | Requires source mutation/revocation/delete-failure evidence and real Pixel validation. |
| SAF → SAF Copy | Implemented behind destination finalization gate | Not supported/advertised yet | Same/different tree runtime evidence is pending. |
| SAF → SAF Move | Implemented with generic read/create/finalize/delete ordering | Not supported/advertised yet | Same/different tree and source-delete evidence is pending. |

The conservative production policy is intentional: a writable persisted grant alone does not prove that a created SAF partial supports safe rename/finalization.

## Durable SAF locator

Encoding: `saf-document-v1`.

The payload contains URL-safe, unpadded Base64 tokens for:

1. the selected tree URI string;
2. the current provider document ID.

The locator is scoped by a provider ID derived from SHA-256 of the selected tree URI. It is not a filesystem path, `DocumentFile`, `ContentResolver`, descriptor, or stream. Every decode checks provider ID, exact selected-tree URI, authority, and provider child containment.

Provider-returned final URI/document identity is encoded again and persisted in the existing operation `finalizationDescription` field using `finalization-v1`. No assumption is made that rename returns the same URI or requested display name.

## Persisted permission policy

- `ACTION_OPEN_DOCUMENT_TREE` result data is accepted only when the result is `RESULT_OK` and `DocumentsContract.isTreeUri(uri)` is true.
- The picker requests read plus persistable permission for source browsing.
- The destination picker additionally requests write permission.
- Only read/write bits actually returned by the platform are persisted.
- Multiple tree grants are restored independently.
- Non-tree persisted permissions are ignored.
- Restored grants are re-read from `ContentResolver.persistedUriPermissions` and may be absent/revoked.
- Missing/revoked provider access is mapped to a durable permission/retry classification; it is never converted into source deletion.

## Capability model

SAF entry capabilities are derived from current provider flags plus current persisted grant mode:

- source: sequential read;
- destination parent: create child and write only when the conservative destination-finalization policy is enabled and the provider advertises create;
- partial/final destination: provider write/rename/delete flags;
- Move: source delete is required both at provider and entry level.

Provider capabilities remain advertised capabilities, not proof of success. Runtime failures map to typed storage errors.

Production SAF destination/finalization support is explicitly opt-in via `finalizationProven = false` by default. Controlled tests use `true` only because the controlled provider has deterministic rename/identity behavior.

## Partial and finalization strategy

Lifecycle:

```text
create operation-owned partial
  -> bounded sequential transfer
  -> verify observed bytes
  -> provider rename/finalization
  -> verify returned identity and parent containment
  -> persist final locator
  -> Move: SOURCE_DELETE_PENDING
  -> revalidate final destination
  -> delete source
  -> COMPLETE
```

Partial names are collision-resistant and operation-bound: `.omnifile-<sanitized-operation-id>.partial`.

Partial writes, finalization, and cleanup carry the durable operation ID. Providers validate the exact operation token, destination parent, entry kind, and selected-tree containment. On restart, the engine can rediscover only the exact operation-named partial in the recorded destination parent; unrelated partial-like documents are not deleted.

`DocumentsContract.renameDocument` is treated as ambiguous when an acknowledgement or post-rename identity query is uncertain. A returned URI outside the expected authority/tree or parent is never accepted as final success.

## Transfer and metadata behavior

- Buffer: bounded 256 KiB.
- Advisory checkpoint: 8 MiB.
- SAF sequential descriptors may be pipe-like/non-seekable; no true offset resume is advertised.
- Unknown size is retained as unknown. Transfer proceeds until EOF and verifies observed bytes; unverifiable unknown-size output fails closed.
- Operations UI displays `size unknown` rather than fabricated `0 / 0` progress.

## Cancellation, conflict, and restart

- Cancellation does not finalize incomplete output or delete a Move source.
- Only exact operation-owned partials may be cleaned.
- Intended final-name conflicts are detected before finalization; existing user data is not overwritten.
- Restart does not blindly replay destructive stages.
- `DESTINATION_COMPLETE` and `SOURCE_DELETE_PENDING` revalidate the persisted final locator and byte facts before source deletion.
- Missing source is treated as already absent only when the provider returns definite `NotFound`; stale/permission failures do not prove deletion.
- Ambiguous finalization remains interrupted/reconcilable; it does not create a second destination or delete a source.
- Ambiguous source deletion remains retryable unless a subsequent observation proves the source is absent.

## Room/schema impact

No schema change. Existing generic provider locator fields and `finalizationDescription` safely carry versioned Local and SAF payloads. Existing VS03 operations, batch IDs, progress, states, and errors remain on schema version 1.

## Controlled provider evidence

Extended the existing `TestDocumentsProvider` with:

- deterministic file contents and dynamic size reporting;
- create-document support and create denial/failure;
- pipe-backed sequential read/write descriptors;
- open/read/write failures and interruption thresholds;
- unknown-size metadata;
- create/write/rename/delete capability toggles;
- same-identity and changed-identity rename behavior;
- ambiguous rename/delete acknowledgement modes;
- provider disappearance, conflicts, and deterministic I/O completion latch.

Added instrumentation coverage for sequential read, partial write, changed final identity, unknown size, read-only grant gating, conflict preservation, and Room-backed Local→SAF, SAF→Local, and SAF→SAF Copy/Move flows.

**Execution status:** sources compile successfully, but no instrumentation test executed because `adb devices -l` reported no attached devices.

## Pixel and real SAF evidence

- Connected devices: none (`adb devices -l` returned an empty device list).
- Controlled-provider instrumentation: pending.
- Real Pixel 7a DocumentsUI/provider flows: pending.
- No persisted grant was fabricated.
- No root/broad storage permission was used.
- Disposable real SAF tree validation remains required before any route is marked supported.

## Verification

Final serialized invocation, using Android Studio JBR `25.0.3`, SDK `/Users/yuta/Library/Android/sdk`, `--no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process --dependency-verification=strict`:

- `:app:testDebugUnitTest`: passed, `61` tests, `0` failures, `0` errors.
- `:app:lintDebug`: passed with `22` warnings and `0` errors. Warnings are baseline/dependency/update/manifest/resource notices; no new SAF lint error was reported.
- `:app:assembleDebug`: passed.
- `:app:assembleDebugAndroidTest`: passed.
- `:app:compileDebugAndroidTestKotlin`: passed.
- Instrumentation execution: not run; no device attached.

APK metadata:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Debug APK SHA-256: `ff7acfe5f85111cfbf52a5859344441b10fe9468ea494193dc0294a316f9f109`
- Android-test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- Android-test APK SHA-256: `f3a2d789e0587298fe79dffe836773d35663eccd2f9906b758575208c2602007`

## Reviews and security

- Review A — SAF architecture: `Changes requested` before implementation; findings addressed in current code and rechecked.
- Review B — Local→SAF: `Changes requested` before conservative finalization gate; runtime gate pending.
- Review C — SAF→Local: `Changes requested`; source mutation/revocation runtime evidence pending.
- Review D — SAF→SAF: `Changes requested`; same/different tree runtime evidence pending.
- Review E — Files/destination picker/Operations UI: `Changes requested`; focused SAF UI/device evidence pending.
- `android-intent-security` review covered tree picker result validation, grant flags, persistable permission handling, tree-only restoration, and no broad storage permission.
- `debug` was used for provider/finalization/restart ambiguity analysis; no temporary runtime probes were left in production sources.

## Remaining gates

1. Attach a Pixel/emulator and execute all controlled-provider instrumentation.
2. Execute controlled fault matrix: revoked grant, provider disappearance, non-seekable read/write, unknown size, create/write/rename/delete failures, conflict, cancellation, restart, ambiguous finalization, and source-delete failure.
3. Execute real Pixel DocumentsUI flows against a disposable SAF tree.
4. Decide provider-specific finalization proof. Only then set a production provider policy that enables SAF destination routes.
5. Re-run Reviews B–E and final whole-diff code/regression reviews after runtime evidence.

## Next production frontier

The next safe frontier is provider evidence, not more route code: controlled and real Pixel proof of finalization identity, persisted grant revocation behavior, source mutation facts, and same/different tree semantics. Background continuation, WorkManager, UIDT, FGS, directories, and non-SAF providers remain out of scope.
