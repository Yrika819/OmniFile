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

## Runtime evidence and remaining anomalies

- The parent Pixel 7a run exposed and fixed a real tree-URI misuse in the
  disposable recorder: `ACTION_OPEN_DOCUMENT_TREE` returns a tree URI, while
  provider document creation requires its normalized parent document URI.
  The corrected run completed all three copy directions and the move-shaped
  copy with content verification.
- The recorder now bounds the visible event tail, coalesces UI refreshes, and
  truncates each `P05-SAF` entry to a 3500-byte UTF-8 payload below Android's
  single-entry logger limit; durable JSONL writes remain complete. This was
  required because the first 5 MiB run queued too many full-TextView refreshes
  for reliable UI automation.
- Interrupt/cancel taps were delivered after the fast transfer reached
  `COMPLETE`; no device interruption/cancellation result is claimed.

- The Python policy model remains synthetic. The raw APK now has provider
  results from one Pixel 7a API 36 run, but those results do not generalize to
  API 31, other providers, or production behavior.
- The initial TDD RED evidence is recorded in this document/result summary but
  no external raw log artifact or installed runtime APK was retained; device
  rows are therefore parent-observed evidence rather than clean-checkout
  reproductions.
- Android API 31, pre-finalization process death, provider disconnect,
  storage-full, and reliable interrupt/cancel runtime rows remain open.
- The model has no real bounded-stream transfer, checksum-cost, memory, or
  cancellation-latency measurements.
- The raw app deliberately does not automate force-stop/reboot, grant
  revocation, provider disconnect, storage-full, or visual acceptance; those
  actions could destroy or alter parent fixtures and require explicit control.
