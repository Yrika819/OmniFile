# P05-002 Android SAF Harness Design

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Goal and boundary

This is a disposable Android proof-of-concept for recording SAF capability and
durable-recovery observations. It is not an OMNIFILE app, does not use
`com.omnifile`, has no production module or dependency contract, and is not
automated against a device by this branch.

The isolated package is `dev.poc.safoperations`. The app is a single raw-SDK
Java activity built with `javac`, `aapt2`, `d8`, and `apksigner`; no Gradle
project is introduced.

## Runtime model

The activity has three fixture/operation surfaces:

1. Select source and destination trees with real
   `ACTION_OPEN_DOCUMENT_TREE`, request read/write/persistable flags, call
   `takePersistableUriPermission`, and re-query
   `getPersistedUriPermissions` on resume.
2. Create deterministic disposable local and SAF fixtures. SAF fixture
   metadata records authority, document ID, tree document ID, display name,
   MIME type, size, last-modified value, and URI string.
3. Run bounded stream copies for Local→SAF, SAF→Local, and SAF→SAF. Each run
   writes a temporary partial, checkpoints bytes, hashes/length-checks the
   destination, renames the partial to a final document where SAF permits it,
   and records the returned URI because rename may change identity.

The durable record is a JSON-lines file in the app-private files directory.
Each event contains an operation ID, direction, copy/move-shaped kind,
phase/status, source and destination identities, source version/length/hash,
partial/final locators, checkpoint, fault/result classification, verification,
finalization acknowledgement, and `deleteSource` (always false in this PoC).

Startup reads the last non-terminal record and reconciles observable source,
partial, and final state. Reconciliation may classify resume, restart,
conflict, blocked authority, ambiguous finalization, or completed destination;
it never performs source deletion. The UI exposes interruption at a checkpoint,
cooperative cancellation, deterministic source mutation, conflict-target
creation, and restart/reconcile so the parent can capture the resulting log.

## Safe non-automation boundary

The host contract test checks source/build/documentation shape only. The branch
does not attempt process kill, reboot, grant revocation, provider disconnect,
storage-full, OEM behavior, or human visual acceptance. Those require a
separately authorized parent run. The app records provider exceptions and
permission state but does not pretend a local fake reproduces provider
semantics.
