# POC-002 — Conclusions

Status: COMPLETE

## Proven by measurement

1. Durable operation truth survives executor-process death on both the host harness and physical Android 16 when state and partial storage are independent of the executor.
2. Recovery must reconcile persisted progress with actual partial length.
3. COPY recovered from early/mid/near kills and kills after transfer and verification.
4. Cancellation preserves a resumable partial without producing a final destination.
5. Conflict, synthetic storage-full and source mutation become explicit durable blocked states.
6. MOVE source deletion occurred only after destination COMPLETE was durably recorded.
7. Bounded streaming remained far below file size; the Pixel 7a 2 GiB run peaked at ~12.6 MB Java heap and ~74.6 MiB PSS.
8. Same-filesystem atomic finalization succeeded in the tested Android path.
9. Checkpoint frequency has measurable persistence overhead.
10. Verification cost varies substantially across environments; universal full SHA-256 should not be frozen from this PoC.

## Not proven

- WorkManager/UIDT/FGS/Service/Activity/coroutine mapping;
- API31 Android execution;
- SAF/cloud/network recovery;
- real ENOSPC;
- cross-filesystem/provider atomicity;
- power-loss durability;
- production persistence choice.

## Exit assessment

The requested durable discovery/reconciliation, bounded memory, identifiable partials, process-death recovery, cancellation/fault handling, finalization, MOVE ordering, checkpoint tradeoff, verification cost and multi-GB transfer are now evidenced, including physical Android 16. POC-002 is COMPLETE for P0 architecture-decision input.

## Artifact classification

- Python harness and Android raw-DEX harness: `DISPOSABLE`;
- raw results: `REFERENCE_ONLY`;
- state-machine/lifecycle concepts: `POTENTIALLY_REUSABLE_AFTER_REVIEW` as design input only;
- production-ready code: NONE.

Production implementation remains unstarted.
