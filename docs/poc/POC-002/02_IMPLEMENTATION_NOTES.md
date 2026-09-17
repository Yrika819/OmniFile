# POC-002 — Implementation Notes

Status: COMPLETE

## Two disposable harnesses

The existing Python harness remains the host reference for controlled checkpoint-cadence and durable-state experiments. The Android supplement uses Java/D8 with the same conceptual state machine. Both are **POC-ONLY — NOT PRODUCTION AUTHORITY**.

## Durable truth versus executor

The Android state is atomically replaced in `state.properties`; event history is fsync'd JSONL; transfer bytes live in `destination.partial`. The executor may die and be replaced. Resume reloads only durable state and filesystem facts.

## Reconciliation

Process-kill injection deliberately occurs after writing bytes but before the next checkpoint. On restart, actual partial length is compared to durable `completedBytes`; divergence emits `RECOVER_RECONCILE` and the actual partial length becomes the resume point.

## Checkpoint and transfer memory

Android uses a fixed 256 KiB buffer and 8 MiB checkpoints. Peak Java heap and `Debug.getPss()` are sampled. Host cadence comparison remains 1/8/64 MiB. Exact intervals are evidence inputs, not production choices.

## Verification

Expected byte length plus SHA-256 equality is measured. Verification time is recorded separately. The PoC intentionally does not freeze universal full hashing as production policy.

## Fault injection

- early/mid/near process kill uses real Android process termination (`Process.killProcess`) before checkpoint persistence;
- after-transfer and after-verify kills exercise phase boundaries;
- cancellation persists `CANCELLED` and keeps the partial;
- ENOSPC is a controlled injected `BLOCKED` condition, not a real disk-fill test;
- conflict creates a pre-existing final destination;
- source mutation modifies the source after a process death and is rejected on resume.

## Finalization and MOVE

Same-filesystem finalization attempts `ATOMIC_MOVE`, recording whether it succeeded. For MOVE, the harness durably records `COMPLETE` first; only then may it delete the source. Events record that the source still existed at `COMPLETE`, followed by a separate `MOVE_SOURCE_DELETE`.

## Production boundary

No persistence library, Android background executor, retry policy, checkpoint interval, verification policy, package/module/SDK choice or provider abstraction is frozen.
