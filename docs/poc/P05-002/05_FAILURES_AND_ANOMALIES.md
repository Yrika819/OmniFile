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
- Android API 31/API 36 and Pixel 7a rows remain `NOT_TESTED`.
- The model has no real bounded-stream transfer, checksum-cost, memory, or
  cancellation-latency measurements.
