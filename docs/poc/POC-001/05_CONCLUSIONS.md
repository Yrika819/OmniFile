# POC-001 — Conclusions

Status: COMPLETE

## Proven

1. Direct shared storage on API31/API36 and Pixel 7a supports the tested list/stat, bounded sequential/random read, append/truncate, same-filesystem rename/move, delete, nested directories and >4 GiB logical length.
2. A valid `ParcelFileDescriptor` does not imply seekability: the synthetic provider returned a readable FIFO whose `lseek` failed with `ESPIPE`.
3. The tested direct filesystem preserved observed `fileKey` through rename/move while path text changed.
4. The Pixel 7a local SAF provider supported list/metadata, sequential and random read, PFD/AFD, seek, writes, append/truncate, rename, move, delete, cancellation and >4 GiB logical length.
5. On that SAF provider, rename and move changed both URI and `documentId`; callers must not assume stable URI/document ID.
6. Provider filename handling can sanitize rather than reject requested names.
7. A persisted SAF grant survived process restart.

## Still not proven

- SD/USB disconnect/reconnect;
- cloud-backed DocumentsProvider behavior;
- explicit persisted-grant revocation;
- OEM/provider variations beyond the tested local Pixel provider;
- universal atomicity or universal seekability.

## Architecture implication

Architecture V1's capability-oriented storage model is confirmed. Sequential access, seek/random access, descriptors, identity, name preservation, lifecycle and reconnect must remain independent/provider-scoped capabilities.

## Exit assessment

POC-001 is COMPLETE for its P0 decision purpose: Android12 compatibility evidence exists; Android16 primary evidence now includes a physical Pixel 7a and real local SAF tree; seek was measured rather than inferred; rename/move identity behavior was measured; unavailable removable/cloud/revocation cases remain explicitly NOT_TESTED.

## Artifact classification

- harness/source/scripts: `DISPOSABLE`;
- raw JSONL/getprop/package captures: `REFERENCE_ONLY`;
- conclusions/architecture impact: `REFERENCE_ONLY`;
- `POTENTIALLY_REUSABLE_AFTER_REVIEW`: none by default.

Production implementation remains unstarted.
