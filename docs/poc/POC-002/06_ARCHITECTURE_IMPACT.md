# POC-002 — Architecture Impact

Status: READY FOR ARCHITECTURE REVIEW

## CONFIRMED

- Durable operation truth should be independent of the current executor instance.
- COPY/MOVE should use an identifiable partial destination rather than writing directly to the final destination.
- Recovery should be reconciliation, not blind replay.
- VERIFY and FINALIZE are distinct phases.
- MOVE source deletion must occur only after destination completion has been durably established.
- Cancellation, conflicts, mutation and storage failures require explicit durable states.
- Transfer memory should be bounded independently of file size.

## REFINED

- Durable progress must be reconciled with actual partial length because persisted byte count can lag successfully written bytes.
- Source identity/version should be revalidated on resume; simple stable-path assumptions are insufficient.
- Checkpoint cadence is a tunable tradeoff. On this host, 1 MiB checkpoints spent ~0.314 s in state persistence for 256 MiB, versus ~0.006 s at 64 MiB.
- Verification policy should remain configurable. Full SHA-256 consumed ~15.56 s of a ~23.00 s 2 GiB operation in this test.

## CHALLENGED

No accepted Architecture V1 principle was contradicted. The measurements challenge any future attempt to make universal full-file hashing or extremely fine-grained checkpoints mandatory without provider/device evidence.

## CONTRADICTED

None.

## UNAFFECTED / STILL OPEN

- selection of SQLite/Room/files/other durable persistence;
- executor selection and Android background-work mapping;
- WorkManager/UIDT/FGS policies;
- SAF/cloud/network partial/finalization semantics;
- production retry policy;
- production checkpoint interval;
- production verification policy;
- cross-filesystem atomicity;
- package/module/applicationId/SDK/dependency choices.

## Safe-to-carry-forward decision input

Architecture review can safely retain the phase ordering, durable truth separation, explicit partial, reconciliation requirement, source-delete ordering and bounded-memory requirements.

It is **unsafe to freeze** Android executor mapping, persistence library, checkpoint interval, hash policy, or provider-specific finalization based on this host-only PoC.
