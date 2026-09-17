# POC-002 — Results

Status: MECHANICAL CORE PASS; ANDROID PLATFORM INTEGRATION NOT TESTED

Raw evidence:
- `poc/POC-002-durable-operations/results/campaign.json`
- `poc/POC-002-durable-operations/results/summary.json`

Campaign wall time: **53.394003 s**.

## Mechanical result matrix

| Scenario | Result | Evidence classification |
|---|---|---|
| Baseline COPY | PASS | measured fact |
| Process kill early | PASS | measured fact |
| Process kill mid-transfer | PASS | measured fact |
| Process kill near end | PASS | measured fact |
| Kill after transfer / before verify | PASS | measured fact |
| Kill after verify / before finalize | PASS | measured fact |
| Cancellation then resume | PASS | measured fact |
| Synthetic ENOSPC then resume | PASS | measured fact, injected fault |
| Destination conflict | PASS | measured fact |
| Source mutation before resume | PASS | measured fact |
| MOVE source-delete ordering | PASS | measured fact |
| 256 MiB checkpoint tradeoff | PASS | measured fact |
| 2 GiB transfer | PASS | measured fact |

All campaign mechanical assertions: **PASS**.

## Process-death reconciliation

The five forced-exit children returned code `91`. A fresh child then recovered and reached `COMPLETE` in every case.

- early kill: partial 8,388,608 bytes; resume reconciled 8,388,608 bytes;
- mid kill: partial 33,554,432 bytes; resume reconciled 33,554,432 bytes;
- near-end kill: partial 67,108,864 bytes; resume reconciled 67,108,864 bytes;
- after-transfer kill: phase `VERIFY`, partial 67,108,864 bytes; resume completed;
- after-verify kill: phase `VERIFY` with verification already persisted; resume completed.

**Measured fact:** durable truth did not require the original executor process to survive.

**Inference:** reconciliation must compare durable state with actual partial-storage facts because state can legitimately lag written bytes.

## Cancellation

Cancellation occurred at 20,971,520 bytes. State was persisted as `CANCELLED`, source remained present, final destination was absent, and the later resume reached `COMPLETE`.

**Recommendation:** cancellation should be an explicit durable state and should not imply deletion of a recoverable partial by default.

## Controlled storage-full fault

A synthetic ENOSPC was injected at 23 MiB. State recorded `code=ENOSPC` with `synthetic=true`; a later run without the injection reconciled the existing partial and completed.

**Limitation:** this is not a real filesystem-full measurement and does not characterize Android provider behavior.

## Destination conflict

A pre-existing final destination caused durable `BLOCKED` / `DESTINATION_CONFLICT`. The pre-existing destination hash was unchanged and source remained present.

**Measured fact:** the harness did not silently overwrite a conflicting final destination.

## Source mutation

After a mid-transfer process kill, the source was modified while retaining the same total length. Resume detected a changed fingerprint and produced durable `BLOCKED` / `SOURCE_MUTATED`; final destination remained absent and the partial remained identifiable.

**Recommendation:** resume must revalidate source identity/version assumptions before writing further bytes.

## MOVE source deletion ordering

A MOVE was killed after VERIFY and before FINALIZE. At that point:
- source existed: YES;
- final destination existed: NO.

After resume:
- destination reached `COMPLETE`;
- source was then deleted;
- `completed_at_ns = 1789625759044742000`;
- `source_deleted_at_ns = 1789625759049570000`.

Therefore the observed delete timestamp was later than the persisted destination-complete timestamp.

**Measured fact:** no premature MOVE source deletion occurred in the tested implementation.

## Checkpoint tradeoff — 256 MiB

All runs used a 1 MiB transfer buffer and size+SHA-256 verification.

| Checkpoint interval | Checkpoints | Wall s | Transfer s | Checkpoint persistence s | Verification s | Peak RSS bytes |
|---:|---:|---:|---:|---:|---:|---:|
| 1 MiB | 256 | 3.4973 | 1.4525 | 0.3143 | 1.8090 | 27,348,992 |
| 8 MiB | 32 | 3.1603 | 1.1030 | 0.0509 | 1.8348 | 27,312,128 |
| 64 MiB | 4 | 2.9049 | 0.8715 | 0.0064 | 1.8046 | 27,262,976 |

**Measured fact:** on this host and persistence implementation, more frequent durable checkpoints added visible persistence overhead.

**Inference:** checkpoint cadence is a recovery-window versus write-overhead tradeoff and should remain configurable/empirically chosen rather than frozen from documentation alone.

## 2 GiB large transfer

- logical source size: 2,147,483,648 bytes;
- source was sparse (`st_blocks=0` before copy);
- destination reached the full 2 GiB logical size through the normal streaming loop;
- wall time: 23.0034 s;
- transfer time: 7.2184 s;
- size+SHA-256 verification time: 15.5590 s;
- peak RSS: 27,348,992 bytes;
- result: PASS.

**Measured fact:** memory remained bounded far below file size in the host implementation.

**Measured fact:** full hashing dominated the large-file runtime in this test.

**Recommendation:** Architecture V1 should keep verification strategy capability/policy dependent; universal full hashing is not justified by this PoC.

## NOT TESTED

- Android API 31/36 runtime execution of POC-002;
- Android process kill via LMK/force-stop;
- WorkManager/UIDT/FGS/Service executor recovery;
- SAF/cloud/network destinations;
- true filesystem ENOSPC;
- tens/100 GiB transfers;
- cross-filesystem finalization;
- power loss during fsync/rename;
- physical-device throughput or memory.
