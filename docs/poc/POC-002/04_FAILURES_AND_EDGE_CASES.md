# POC-002 — Failures and Edge Cases

Status: COMPLETE

## Expected measured faults

### Process termination

Real Android process termination was injected at early/mid/near transfer and after transfer/verify phase boundaries. Fresh processes recovered using durable state and partial-file facts.

### Durable state lag

The Android kill cases deliberately demonstrated that successfully written partial bytes can exceed persisted progress. Blind replay of durable byte count would be wrong; reconciliation is mandatory.

### Cancellation

`CANCELLED` retained a resumable partial. Retry completed without treating cancellation as final-destination success.

### Synthetic ENOSPC

The fault is explicitly injected and does not represent physical disk-full/provider error translation. It demonstrates durable blocked/retry semantics only.

### Destination conflict

A pre-existing final destination caused `BLOCKED / DESTINATION_CONFLICT`; no silent overwrite occurred.

### Source mutation

Mutation between crash and resume caused `BLOCKED / SOURCE_MUTATED`; no unsafe continuation occurred.

### Crash after VERIFY

A verified partial is still not COMPLETE. Restart finalized it without conflating verified and complete states.

### MOVE crash window

The tested ordering writes COMPLETE before source deletion. A crash after COMPLETE but before source deletion would safely leave a duplicate source, not data loss; cleanup would need idempotent recovery in a production design.

## Remaining edge cases

- power loss during fsync/state rename/final rename;
- actual Android ENOSPC and provider-specific exceptions;
- SAF/cloud/network partial object semantics;
- cross-filesystem finalization;
- executor policies under OS background restrictions;
- API31 runtime parity.

These remain unresolved rather than inferred.
