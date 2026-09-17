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

- The Python policy model is synthetic. The raw APK contains real
  `ContentResolver`/`DocumentsContract` paths, but those paths have not been
  executed in this continuation and therefore supply no provider result.
- The initial TDD RED evidence is recorded in this document/result summary but
  no external raw log artifact was retained.
- The raw APK build is verified, but Android API31/API36 runtime,
  transfer-direction, process-death, and provider-grant rows remain
  `NOT_TESTED` until the parent run.
- The model has no real bounded-stream transfer, checksum-cost, memory, or
  cancellation-latency measurements.
- The raw app deliberately does not automate force-stop/reboot, grant
  revocation, provider disconnect, storage-full, or visual acceptance; those
  actions could destroy or alter parent fixtures and require explicit control.
