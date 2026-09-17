# POC-002 — Conclusions

Status: PARTIAL — MECHANICAL CORE EVIDENCE COMPLETE; ANDROID INTEGRATION UNTESTED

## Proven by measurement

1. Durable operation truth can survive executor-process death when source/destination/partial references, phase, completed bytes, and verification state are persisted independently of the executor.
2. Recovery must reconcile persisted progress against the actual partial destination rather than blindly replaying from the last state value.
3. The tested COPY implementation recovered from kills early, mid, near-end, after transfer, and after verification.
4. Cancellation can preserve a resumable partial without producing a final destination.
5. Conflict and source-mutation conditions can be converted into explicit durable blocked/error states instead of unsafe continuation.
6. The tested MOVE ordering preserved the source through verification/finalization and deleted it only after destination `COMPLETE` had been durably recorded.
7. Bounded streaming held peak host RSS around 27.3 MB while copying a 2 GiB logical source.
8. Full SHA-256 verification was substantially more expensive than transfer for the 2 GiB sparse-source case, so universal mandatory hashing is not supported by this evidence.
9. Checkpoint frequency created a measurable recovery-window/overhead tradeoff.

## Not proven

- Android lifecycle/executor behavior;
- WorkManager, UIDT, FGS, Service, Activity or coroutine mapping;
- Android API 31/API 36 parity;
- SAF/cloud/network recovery;
- real ENOSPC;
- cross-filesystem or provider finalization semantics;
- power-loss durability;
- production persistence choice.

## Exit assessment

The requested durable-operation mechanics, fault matrix, MOVE ordering, bounded memory, large-file transfer, checkpoint tradeoff, verification cost, partial identification and reconciliation are evidenced on the host harness.

POC-002 remains **PARTIAL** as an Android PoC because platform/runtime integration was intentionally not inferred from the unstable emulator environment.

## Artifact classification

- `engine.py`, `campaign.py`: `DISPOSABLE`.
- raw `results/*.json`: `REFERENCE_ONLY`.
- documentation: `REFERENCE_ONLY`.
- lifecycle/state ideas: `POTENTIALLY_REUSABLE_AFTER_REVIEW` as architecture concepts only, not source code.
- production-ready code: NONE.
