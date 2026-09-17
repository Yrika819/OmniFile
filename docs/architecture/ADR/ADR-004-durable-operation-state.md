# ADR-004 — Durable Operation State

## Status
`ACCEPTED`

## Context
Android process death, user/system stops, background restrictions, network loss, removable disconnect, and executor limits make one worker/service/coroutine lifetime insufficient as operation truth.

## Decision
Persist operation intent, progress/checkpoints, partial destination identity, verification/finalization stage, and reconciliation state independently from Android executor lifetime.

## Consequences
WorkManager, UIDT, foreground services, and foreground coroutines can be selected per workload later without redefining operation correctness. Recovery is reconciliation rather than blind replay.

## Alternatives rejected
Treating an executor object's lifecycle/state as the durable operation model is `REJECTED`.

## Evidence references
- `docs/research/09_BACKGROUND_OPERATIONS.md`
- `docs/research/13_TEST_STRATEGY.md`
- `docs/research/15_ARCHITECTURE_RECOMMENDATIONS.md`

## P0 evidence update

POC-002 observed persisted progress lagging actual partial bytes after process death on the physical Pixel 7a. The decision is therefore explicit: checkpoint/progress metadata is not storage truth; recovery reconciles durable metadata with actual partial/final destination state and source/version facts. Exact persistence technology, checkpoint cadence, executor mapping, provider recovery, and power-loss behavior remain open.
