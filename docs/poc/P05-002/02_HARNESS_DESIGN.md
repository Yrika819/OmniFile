# P05-002 Harness Design

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Model boundary

The harness models a provider, not Android. `InMemorySafProvider` stores documents under opaque locators and exposes only the facts needed by reconciliation: source bytes/version, partial bytes, final bytes, grant availability, and provider availability. The locator is deliberately not convertible to a host path.

The recovery logic is a pure function over a durable record and a provider snapshot. It does not retain open handles, executor objects, or provider instances in durable state.

## Hand-written interfaces

```text
OperationRecord(
    operation_kind: "copy" | "move",
    source_uri: str,
    source_version: str,
    partial_uri: str,
    final_uri: str,
    expected_sha256: str,
    expected_length: int,
    checkpoint_bytes: int,
    phase: str,
    finalization_acknowledged: bool,
    source_provider_id: str,
    destination_provider_id: str,
)

ProviderSnapshot(
    provider_available: bool,
    grant_available: bool,
    source_available: bool,
    source_version: str | None,
    source_bytes: bytes | None,
    partial_bytes: bytes | None,
    final_bytes: bytes | None,
    source_provider_available: bool | None,
    destination_provider_available: bool | None,
    source_grant_available: bool | None,
    destination_grant_available: bool | None,
    source_provider_id: str,
    destination_provider_id: str,
)

reconcile(record, snapshot) -> RecoveryDecision
```

`RecoveryDecision` contains a stable enum classification, a string next action, an optional resume offset, a short reason, and a `delete_source` flag that remains false for every reconciliation decision. The decision function never mutates the provider.

The disposable `InMemorySafProvider` only creates opaque `content://p05/<id>`
locators, stores byte/version fixtures in memory, and returns immutable
`ProviderSnapshot` values. It has no filesystem-path conversion, Android API
dependency, automatic retry, or source-deletion method.

## Decision order

1. Reject malformed, unknown, or terminal-without-ack durable records as `INVALID_RECORD`.
2. Check destination provider identity/grant/availability before using destination facts.
3. A wrong final destination is `CONFLICT_DESTINATION` and wins over a valid partial.
4. A matching final destination without a durable acknowledgement is `FINALIZATION_AMBIGUOUS`.
5. A matching, acknowledged final destination may complete a copy without reopening the source; a move still requires an explicit source-delete step and source authority.
6. Check source provider identity/grant/version/digest/length before resuming a non-finalized operation.
7. If partial bytes are a valid source prefix, return `RESUME_FROM_PARTIAL` at the observed partial length, even when the durable checkpoint is smaller.
8. If partial bytes exist but are not a valid prefix, return `RESTART_REQUIRED` with `RECREATE_PARTIAL`.
9. Otherwise return `RESTART_REQUIRED` with offset zero.

This ordering makes provider authority and final-destination reality stronger than stale progress metadata. It also makes source deletion impossible as a side effect of reconciliation.

## Fault injection

The tests construct snapshots directly to represent faults. This is intentional: a host test can deterministically exercise policy branches without pretending that a local fake reproduces Android provider internals. No test writes to the host filesystem.
