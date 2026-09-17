# POC-002 — Environment

Status: RECORDED

## Authority

- Repository: `/Users/yuta/Desktop/File Manager`
- Branch: `poc/durable-operations-v1`
- Starting SHA: `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`
- Harness: `poc/POC-002-durable-operations/`
- Runtime: host Python standard library only.

Exact `platform.platform()`, machine architecture, Python version, and executable are recorded in `results/campaign.json`.

## Android status

POC-001 established that the available API 31 emulator consumed roughly a full CPU core continuously and that the API 36 emulator repeatedly produced System UI/Launcher ANRs during SAF UI work. Per campaign safety/quality requirements, POC-002 did not claim Android runtime evidence from an unstable emulator session.

Therefore the following are `NOT_TESTED` in POC-002:
- Android process/lifecycle integration;
- Activity/Service/WorkManager/UIDT/FGS executor behavior;
- Android filesystem/provider-specific finalization;
- Android storage-full behavior;
- Android memory accounting;
- API 31/API 36 runtime parity.

This does not invalidate the host mechanical evidence; it limits its scope to executor-independent operation-state semantics.

## Test input sizes

- baseline: 4 MiB;
- process-death/cancellation/fault cases: 64 MiB;
- checkpoint cadence benchmark: 256 MiB per run;
- large-file case: 2 GiB logical sparse source, copied through the normal bounded streaming loop.

No tens-of-GB or 100-GB transfer was performed. Those remain `NOT_TESTED`.
