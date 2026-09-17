# POC-002 — Results

Status: COMPLETE

Raw evidence:
- host: `results/campaign.json`, `results/summary.json`;
- Pixel 7a: `results/android-pixel7a/*-events.jsonl`, `*-state.properties`, `summary.json`, device captures.

## Result matrix

| Scenario | Host | Pixel 7a API36 |
|---|---|---|
| baseline COPY | PASS | PASS |
| kill early | PASS | PASS |
| kill mid | PASS | PASS |
| kill near end | PASS | PASS |
| kill after transfer / before verify | PASS | PASS |
| kill after verify / before finalize | PASS | PASS |
| cancellation then resume | PASS | PASS |
| injected ENOSPC then resume | PASS | PASS |
| destination conflict | PASS | PASS |
| source mutation | PASS | PASS |
| MOVE source-delete ordering | PASS | PASS |
| 2 GiB streamed copy | PASS | PASS |

## Measured fact — Android process-death reconciliation

Pixel 7a kill scenarios intentionally let partial bytes get ahead of the durable checkpoint:
- early: durable 8,388,608; actual partial 13,631,488 bytes; resumed to COMPLETE;
- mid: durable 125,829,120; actual partial 134,217,728 bytes; resumed to COMPLETE;
- near: durable 251,658,240; actual partial 255,066,112 bytes; resumed to COMPLETE.

`RECOVER_RECONCILE` was emitted in each case.

**Inference:** durable recovery must reconcile state with partial-storage facts rather than blindly replay the last checkpoint.

## Phase-boundary kills

- after transfer/before verify: 128 MiB operation resumed, verified SHA-256 and atomically finalized;
- after verify/before finalize: persisted VERIFIED state resumed directly to finalization.

**Measured fact:** VERIFY and COMPLETE are distinct durable states.

## Cancellation

A 128 MiB operation entered `CANCELLED` at mid-transfer with its partial retained, then retried and completed.

**Recommendation input:** cancellation should be explicit durable truth; partial deletion should be a separate policy.

## Injected storage full

A controlled mid-transfer `INJECTED_ENOSPC` produced durable `BLOCKED`; retry without the injection reused the partial and completed.

**Limitation:** real Android filesystem/provider ENOSPC remains NOT_TESTED.

## Conflict and mutation

- destination conflict: durable `BLOCKED / DESTINATION_CONFLICT`; verified partial retained and final was not overwritten;
- source mutation after a mid-transfer kill: resume produced `BLOCKED / SOURCE_MUTATED`; no final destination was produced.

## MOVE ordering

Pixel 7a event order: `... VERIFY_DONE -> COMPLETE -> MOVE_SOURCE_DELETE`. The `COMPLETE` event recorded `sourceExistsAtComplete=true`; the later delete event recorded `afterPhase=COMPLETE` and `deleted=true`.

**Measured fact:** source deletion was not premature.

## Pixel 7a 2 GiB case

- bytes: 2,147,483,648;
- transfer: 51,262.543 ms;
- SHA-256 verify: 4,592.618 ms;
- peak Java heap: 12,595,728 bytes;
- peak PSS: 74,579 KiB;
- SHA-256 match: PASS;
- same-filesystem atomic finalize: PASS.

The source began sparse, but the destination was streamed byte-for-byte and reached the full 2 GiB size.

**Measured fact:** memory remained bounded far below file size on physical Android.

## Host checkpoint tradeoff

For 256 MiB with a 1 MiB transfer buffer:
- 1 MiB checkpoints: 256 checkpoints, ~0.314 s persistence time;
- 8 MiB checkpoints: 32 checkpoints, ~0.051 s;
- 64 MiB checkpoints: 4 checkpoints, ~0.006 s.

**Inference:** checkpoint cadence is a recovery-window/overhead tradeoff and should remain empirically chosen.

## Host 2 GiB verification cost

Host transfer ~7.22 s versus SHA-256 verification ~15.56 s. Pixel 7a showed a different ratio (51.26 s transfer vs 4.59 s verification).

**Inference:** verification cost is device/filesystem/cache dependent; universal mandatory hashing is not justified by these two environments.

## NOT TESTED

- real disk-full behavior;
- SAF/cloud/network destinations;
- cross-filesystem/provider finalization;
- power-loss durability;
- Android API31 execution of this harness;
- WorkManager/UIDT/FGS/Service/Activity executor mapping;
- tens/100 GiB transfers.
