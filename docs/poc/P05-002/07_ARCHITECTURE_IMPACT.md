# P05-002 Architecture Impact

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Disposition

`PARTIALLY_RESOLVED — HOST POLICY PLUS PIXEL 7A SAF RUNTIME EVIDENCE`

The host harness supports the accepted architectural direction that operation
recovery is reconciliation over durable metadata plus observed provider reality.
The isolated APK provides a disposable way for a parent to collect real SAF
observations, and the parent run supplied a bounded Pixel 7a API 36 sample.
This changes no production decision.

## Supported implications

- Keep opaque provider-scoped identity separate from display paths and host filesystem paths.
- Treat durable checkpoints as observations, not storage truth.
- Make provider/grant loss, source-version changes, wrong final outputs, missing finalization acknowledgement, malformed records, and mismatched partials explicit non-completion states.
- Preserve source deletion as a later, independently authorized move step after destination verification/finalization.
- Keep recovery logic independent from executor lifetime and avoid persisting live descriptors, sockets, or Android service objects.

## Unchanged/open decisions

- Exact Android SAF adapter interfaces and dependency versions remain deferred.
- Persisted-grant revocation, URI/document-ID mutation across providers,
  descriptor/seek capability, atomicity, provider-native commit, and checkpoint
  cadence remain open.
- Executor selection, process-death semantics, storage-full handling, power-loss handling, and physical-device acceptance remain open.
- No host harness type, function, or test is production authority or a candidate for direct reuse without a later design and review gate.

## Required next evidence

A later POC must add API 31, provider-fault, pre-finalization lifecycle, and
reliable interrupt/cancel evidence. Results must be reviewed independently
before changing architecture or initializing production code.
