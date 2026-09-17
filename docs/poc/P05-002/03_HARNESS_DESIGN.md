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
)

ProviderSnapshot(
    provider_available: bool,
    grant_available: bool,
    source_available: bool,
    source_version: str | None,
    source_bytes: bytes | None,
    partial_bytes: bytes | None,
    final_bytes: bytes | None,
)

reconcile(record, snapshot) -> RecoveryDecision
```

`RecoveryDecision` contains a stable enum classification, a string next action, an optional resume offset, a short reason, and a `delete_source` flag that remains false for every reconciliation decision. The decision function never mutates the provider.

The disposable `InMemorySafProvider` only creates opaque `content://p05/<id>`
locators, stores byte/version fixtures in memory, and returns immutable
`ProviderSnapshot` values. It has no filesystem-path conversion, Android API
dependency, automatic retry, or source-deletion method.

## Decision order

1. If the provider is unavailable, return `BLOCKED_PROVIDER` with `WAIT_FOR_PROVIDER`.
2. If the grant or source is unavailable, return `BLOCKED_PERMISSION` with `WAIT_FOR_AUTHORIZATION`.
3. If the source version or source digest differs from the durable expectation, return `CONFLICT_SOURCE_CHANGED` with `REQUIRE_USER_REVIEW`.
4. If the final bytes match the expected digest and length, return `FINAL_DESTINATION_OBSERVED`; for `move`, require `REQUIRE_EXPLICIT_SOURCE_DELETE`, while `copy` may use `MARK_COMPLETE`.
5. If partial bytes are a valid source prefix, return `RESUME_FROM_PARTIAL` at the observed partial length, even when the durable checkpoint is smaller.
6. If partial bytes exist but are not a valid prefix, return `RESTART_REQUIRED` with `RECREATE_PARTIAL`.
7. Otherwise return `RESTART_REQUIRED` with offset zero.

This ordering makes provider authority and final-destination reality stronger than stale progress metadata. It also makes source deletion impossible as a side effect of reconciliation.

## Fault injection

The tests construct snapshots directly to represent faults. This is intentional: a host test can deterministically exercise policy branches without pretending that a local fake reproduces Android provider internals. No test writes to the host filesystem.
