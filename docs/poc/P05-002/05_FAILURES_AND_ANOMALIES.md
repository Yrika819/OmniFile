# P05-002 Failures and Anomalies

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Resolved during this branch

- Wrong final output previously could be bypassed by a valid partial; final
  conflicts now take precedence.
- Durable record validation was previously absent; malformed/unknown/terminal
  records now fail closed.
- Final bytes were previously conflated with finalization acknowledgement; the
  model now represents an ambiguous final state explicitly.
- Source and destination provider/grant authority was previously coarse; the
  model now carries independent identities, availability, and grants.
- MOVE deletion authorization now revalidates source identity, version, length,
  and digest after observing a matching final destination.

## Remaining anomalies and blockers

- The harness is synthetic and does not reproduce Android `ContentResolver`,
  `DocumentsProvider`, URI mutation, provider sanitization, or grant semantics.
- The initial TDD RED evidence is recorded in this document/result summary but
  no external raw log artifact was retained.
- A bounded Pixel 7a/API36 capability probe now exists, but it is the existing
  P0-001 harness rather than a P05 operation-transfer app. Its successful
  rename/move result proves that returned URI/document identity can change on
  this provider; it does not authorize source deletion without a P05
  reconciliation record.
- Android API31, transfer-direction, and process-death recovery rows remain
  `NOT_TESTED`.
- The model has no real bounded-stream transfer, checksum-cost, memory, or
  cancellation-latency measurements.
