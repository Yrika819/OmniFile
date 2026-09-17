# POC-002 — Implementation Notes

Status: EXECUTED

## Disposable design

`engine.py` is deliberately a disposable host implementation. It uses an atomically replaced JSON state file, a uniquely named `.partial` destination, bounded streaming buffers, fsync at checkpoints, SHA-256 verification, and same-filesystem `os.replace` finalization.

This is **POC-ONLY — NOT PRODUCTION AUTHORITY**. JSON persistence, Python, checkpoint sizes, hash policy, naming, and finalization calls are not production selections.

## Durable truth versus executor

`campaign.py` launches the engine in child processes. Fault cases terminate the child with `os._exit(91)` so in-memory state disappears. Recovery starts a new process that reads only durable state plus filesystem facts.

The recovery path reconciles `completed_bytes` against the actual `.partial` length because a process may die after writing bytes but before persisting the next checkpoint.

## Source identity/mutation guard

The PoC records size, nanosecond mtime, device, and inode at PLAN. A changed fingerprint blocks resume as `SOURCE_MUTATED` rather than blindly continuing.

This fingerprint is evidence for a reconciliation strategy, not a universal production identity contract.

## Verification and finalization

Verification requires expected byte length plus SHA-256 equality between source and partial. Verification cost is measured separately.

Finalization uses same-filesystem `os.replace` and records device/inode before and after. The result is explicitly marked observational; it does not generalize atomic rename to SAF, cloud, network, or cross-filesystem moves.

## MOVE ordering

The engine first persists destination phase/status as `COMPLETE`. Only after that durable write succeeds does MOVE unlink the source and record `source_deleted_at_ns`.

A crash injected after VERIFY confirms that source deletion has not yet occurred.

## Fault injection

- process kill: real child process termination at five lifecycle points;
- cancellation: cooperative stop after a byte threshold, retaining the partial;
- ENOSPC: synthetic `errno.ENOSPC` after a byte threshold to avoid filling real storage;
- conflict: pre-existing final destination;
- source mutation: same-size source content modification after a mid-transfer crash.

## Memory model

Transfer buffer is 1 MiB. Peak RSS is read from `getrusage`; on Darwin `ru_maxrss` is treated as bytes. This is host-process memory evidence only.
