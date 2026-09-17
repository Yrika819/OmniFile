# POC-002 Durable Operation Harness

**POC-ONLY — NOT PRODUCTION AUTHORITY.**

Disposable host-side harness for validating durable COPY/MOVE state, process-death reconciliation, verification/finalization ordering, cancellation, conflict handling, source mutation detection, bounded-memory streaming, and checkpoint tradeoffs.

Run:

```sh
python3 campaign.py
```

The campaign uses only generated fixture directories under `work/`, deletes those fixtures after capture, and writes reviewable evidence to `results/`.

This harness does **not** freeze Android executors, persistence technology, package/module structure, or production interfaces.
