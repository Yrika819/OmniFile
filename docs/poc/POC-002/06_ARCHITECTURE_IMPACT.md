# POC-002 — Architecture Impact

Status: READY FOR ARCHITECTURE REVIEW

Architecture authority remains `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`; this document does not amend it.

## CONFIRMED

- durable operation truth must be independent of the current executor instance;
- COPY/MOVE need an identifiable partial destination;
- recovery is reconciliation, not blind replay;
- VERIFY, FINALIZE and COMPLETE are distinct;
- MOVE source deletion occurs only after destination completion is durably established;
- cancellation, conflict, mutation and storage failures require explicit durable states;
- transfer memory must be bounded independently of file size.

These are now confirmed by both host and physical Android 16 evidence.

## REFINED

- persisted progress can lag actual partial bytes by multiple MiB; resume must inspect the partial;
- source identity/version assumptions must be revalidated before resume;
- checkpoint cadence is tunable; host measurements show materially different persistence overhead at 1/8/64 MiB;
- verification policy is device/storage dependent: host and Pixel 7a produced very different transfer/hash ratios;
- same-filesystem atomic finalize can be exploited when actually available, but capability must be measured rather than assumed.

## CHALLENGED

No accepted V1 principle was contradicted. The evidence challenges any future design that mandates universal full hashing, fixed tiny checkpoints, or equates executor liveness with operation truth.

## CONTRADICTED

None.

## UNAFFECTED / STILL OPEN

- production persistence technology;
- Android background executor mapping;
- retry policy;
- provider-specific SAF/cloud/network semantics;
- cross-filesystem atomicity;
- production checkpoint and verification policies;
- package/module/applicationId/SDK/dependency choices.

## Safe to carry into later freeze discussion

Phase ordering, durable truth separation, explicit partials, reconciliation, source-delete ordering, and bounded-memory requirements.

## Unsafe to freeze

Executor technology, persistence library, checkpoint interval, universal hash policy, and provider-specific finalization.
