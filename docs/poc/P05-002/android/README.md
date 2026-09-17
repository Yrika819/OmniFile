# P05-002 Disposable Android SAF Harness

`POC-ONLY — NOT PRODUCTION AUTHORITY`

This is a one-activity, raw-SDK Android proof of concept. Its isolated package
is `dev.poc.safoperations`; it is not an OMNIFILE app and must not be reused as
production code.

## Build

From this directory, with an Android SDK containing API 36 and build-tools
35.0.0:

```text
./build.sh
```

The signed disposable APK is written to
`docs/poc/P05-002/android/build/p05-saf-operations.apk`. The build uses
`javac`, `aapt2`, `d8`, `apksigner`, and a build-local throwaway keystore. It
does not install or launch anything.

## Parent-only runtime procedure

The following is intentionally a manual parent run on an authorized Android
12–16 environment. Install and launch the APK using the parent’s approved
Android tooling. This branch performs no runtime run and makes no device claim.

1. Press **Select source tree** and **Select destination tree**. In the real
   `ACTION_OPEN_DOCUMENT_TREE` picker, grant read/write access and accept the
   persistable permission. The app logs the returned URI, provider authority,
   document/tree IDs, metadata, and the persisted-grant re-query.
2. Press **Create local fixture**. It creates deterministic bytes in the app’s
   private fixture directory and records local identity evidence.
3. With a writable source tree selected, press **Create SAF fixture**. This
   creates a disposable provider document and records its returned URI and
   provider metadata. Use only a disposable tree selected for this run.
4. Run **Local→SAF copy**, **SAF→Local copy**, and **SAF→SAF copy** separately.
   The app records PLAN, CREATE_PARTIAL, TRANSFER, CHECKPOINT, VERIFY,
   FINALIZE, and COMPLETE events. SAF finalization records the URI returned by
   `DocumentsContract.renameDocument`, including any identity change.
5. **Run SAF→SAF move-shaped** performs copy/finalize only and records that
   source deletion requires a separate explicit parent step. The harness never
   deletes a source.
6. During a transfer, press **Interrupt current** or **Cancel current**.
   Interrupt leaves a checkpointed partial for reconciliation; cancellation
   records `CANCELLED` and preserves the partial. Relaunch the app and press
   **Restart / reconcile** to record the observed classification.
7. Press **Mutate SAF source** before reconciliation to overwrite the
   disposable SAF fixture and record `MUTATION`; reconciliation should record
   a source conflict when the source identity/version or hash changed.
8. Press **Arm destination conflict**, then run **SAF→SAF conflict copy**.
   The app creates a wrong-content final-name fixture and records
   `CONFLICT_DESTINATION` without overwriting it.
9. Press **Restart / reconcile** after any interruption or parent-controlled
   app restart. The current durable record is in the app-private files area;
   the visible event panel is also a copy of the JSONL evidence.

## Evidence capture and limits

Each JSONL event has the exact P05 label and includes `operationId`, direction,
`sourceUri`, `destinationUri`, `sourceProviderId`, `destinationProviderId`,
`sourceVersion`, `sourceLength`, `sourceSha256`, `checkpointBytes`, phase,
classification, verification, finalization acknowledgement, and
`deleteSource: false`. The parent should export or transcribe
`p05-saf-events.jsonl` from the app-private files directory using its approved
inspection method and retain the run’s tree/provider identity.

The app can record interruption/restart/reconciliation, cancellation, source
mutation, destination conflict, verification, finalization, URI identity, and
the three requested transfer directions. Process kill/reboot, actual grant
revocation, provider disconnect, storage-full, OEM behavior, and human visual
acceptance remain Parent-only cases and are not safely automated here.
