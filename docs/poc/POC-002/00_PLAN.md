# POC-002 — Durable Large Operation Recovery — Plan

Status: COMPLETE

Architecture authority: `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`
Branch: `poc/durable-operations-v1`

## Purpose

Validate durable COPY/MOVE mechanics without selecting production persistence, package/module structure, or Android executor technology. All harness code is **POC-ONLY — NOT PRODUCTION AUTHORITY**.

## Lifecycle under test

`PLAN -> CREATE PARTIAL -> TRANSFER -> CHECKPOINT -> VERIFY -> FINALIZE -> COMPLETE`

For MOVE, source deletion is permitted only after destination `COMPLETE` has been durably persisted.

## Durable state under test

Persist source/destination/partial references, operation type, total/completed bytes, phase, retry/error state, timestamps, source identity/version observations and verification result independently of the executor process.

## Failure matrix

- process death early/mid/near end;
- death after transfer before verify;
- death after verify before finalize;
- cancellation;
- controlled injected ENOSPC;
- destination conflict;
- source mutation;
- MOVE source-delete ordering;
- small, hundreds-of-MiB and 2 GiB transfers;
- checkpoint/verification cost and bounded memory.

## Environments

1. host Python harness for deterministic checkpoint-cadence and fault mechanics;
2. physical Pixel 7a / Android 16 API 36 raw-DEX harness executed with `app_process`, deliberately avoiding a production Android app structure.

## Safety

Host destructive work stays under the PoC work directory. Android destructive work stays under `/data/local/tmp/filemanager-poc002`. Real primary storage was not filled; ENOSPC is explicitly synthetic.
