# POC-002 — Durable Large Operation Recovery — Plan

Status: MECHANICAL CORE EXECUTED; ANDROID PLATFORM INTEGRATION NOT TESTED

Architecture authority: `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`
Branch: `poc/durable-operations-v1`

## Purpose

Validate the Architecture V1 operation lifecycle as evidence, without selecting production persistence, executor, package, module, or Android background-work architecture.

Harness identifiers and implementation are **POC-ONLY — NOT PRODUCTION AUTHORITY**.

## Lifecycle under test

`PLAN -> CREATE PARTIAL -> TRANSFER -> CHECKPOINT -> VERIFY -> FINALIZE -> COMPLETE`

For MOVE, source deletion is permitted only after destination `COMPLETE` has been durably persisted.

## Durable state under test

The disposable state record persists:
- operation id and COPY/MOVE type;
- source and destination references;
- partial destination reference;
- total/completed bytes;
- current phase/status;
- checkpoint size/count;
- retry count;
- source fingerprint;
- verification result;
- error state;
- timestamps;
- transfer/checkpoint/verification metrics.

## Failure/recovery matrix

Mechanically exercise:
- process death early transfer;
- process death mid transfer;
- process death near end;
- process death after transfer before verification;
- process death after verification before finalization;
- user cancellation;
- controlled synthetic ENOSPC;
- destination conflict;
- source mutation between crash and resume;
- MOVE ordering with process death after verification;
- checkpoint cadence tradeoff;
- small, hundreds-of-MiB, and 2 GiB logical transfers.

## Safety

All destructive actions are confined to `poc/POC-002-durable-operations/work/` and generated destinations inside that directory. The campaign deletes only its own fixtures.

The ENOSPC case is an injected write-limit failure, not a real disk-fill event.

## Platform boundary

Because the available Android emulators were resource-constrained and UI-unstable during POC-001, this phase first validates executor-independent durable mechanics on the macOS host. No Android executor, Activity, Service, WorkManager, UIDT, FGS, SAF, or provider behavior is inferred from this host evidence.
