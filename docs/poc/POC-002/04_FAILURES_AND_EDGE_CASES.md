# POC-002 — Failures and Edge Cases

Status: RECORDED

## Observed failures that were expected and correctly contained

### Process termination

Five child processes were deliberately terminated with exit code `91`. No case produced a final destination before verification/finalization. All resumed successfully from durable state plus partial-file facts.

Classification: **measured fault behavior**.

### Synthetic ENOSPC

The engine recorded `FAILED` with `error.code=ENOSPC`, retained the partial, and later resumed when the injected limit was removed.

Classification: **measured injected-fault behavior**, not a physical disk-full result.

### Destination conflict

A pre-existing destination produced `BLOCKED` / `DESTINATION_CONFLICT` and was not overwritten.

Classification: **measured fault behavior**.

### Source mutation

A same-size source modification after a crash changed its stored fingerprint. Resume produced `BLOCKED` / `SOURCE_MUTATED` rather than appending to a potentially incompatible partial.

Classification: **measured fault behavior**.

## Edge cases and limitations

### Durable state can lag the partial

Because writes and state checkpoints are separate durability events, the actual partial length is treated as a reconciliation input. Blindly trusting the last `completed_bytes` value would unnecessarily rewrite data or risk inconsistent resume behavior.

### Crash after VERIFY

Verification may already be durable while finalization has not happened. Recovery therefore cannot equate `verified` with `complete`.

### MOVE crash window

The source-deletion operation is intentionally outside the destination-complete transition. If a future implementation crashes after persisting `COMPLETE` but before deleting the source, recovery should recognize the completed destination and finish source deletion idempotently after revalidation.

### Real storage full remains unmeasured

The campaign intentionally did not fill the Mac or emulator disk. Filesystem reserve behavior, provider-specific failures, and Android error translation remain unresolved.

### Emulator limitation

Android runtime evidence was not produced because the available emulator sessions were already shown to be resource-heavy/UI-unstable. No Android-specific conclusion is inferred from host timings.
