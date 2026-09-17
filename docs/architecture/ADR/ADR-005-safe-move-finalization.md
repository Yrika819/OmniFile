# ADR-005 — Safe Move Finalization

## Status
`ACCEPTED`

## Context
Cross-provider moves can be interrupted by process death, storage full, network loss, conflicts, and non-atomic provider finalization.

## Decision
The default cross-provider move sequence is: create partial destination -> transfer/process -> verify -> finalize destination -> delete source.

## Consequences
Source deletion is the final irreversible step. Same-provider native/atomic move can replace the generic plan only when the provider exposes a sufficiently strong guarantee.

## Alternatives rejected
Deleting the source merely because a destination write stream ended successfully is `REJECTED`.

## Evidence references
- `docs/research/02_STORAGE_PROVIDER_ARCHITECTURE.md`
- `docs/research/09_BACKGROUND_OPERATIONS.md`
- `docs/research/11_SECURITY.md`
- `docs/research/15_ARCHITECTURE_RECOMMENDATIONS.md`

## P0 evidence update

POC-002 observed `VERIFY_DONE` -> `COMPLETE` -> source deletion ordering on the tested local Android path. This confirms the required ordering direction, not universal provider atomicity or every crash window. Recovery must remain conservative when finalization is ambiguous and must handle an already-finalized destination idempotently.
