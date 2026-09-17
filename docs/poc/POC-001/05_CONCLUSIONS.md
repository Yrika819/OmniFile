# POC-001 — Conclusions

Status: PARTIAL

## What was proven

1. Direct shared-storage behavior on the tested API 31 and API 36 emulator images supports bounded sequential read, explicit seek/random read, append, truncate, same-filesystem rename/move, nested directories, delete, and >4 GiB logical file length.
2. A valid `ParcelFileDescriptor` can be non-seekable. The synthetic provider produced a FIFO descriptor that read sequentially but failed `lseek` with `ESPIPE` on both API 31 and API 36.
3. The tested direct filesystem preserved the observed file key across rename and cross-directory move within the same filesystem, while the presentation path changed.
4. Android shared-storage filename acceptance differs from a naive universal filesystem model: the tested double-quote and newline names were rejected with `EPERM`, while Unicode/emoji and several shell metacharacters were accepted.
5. Bounded streaming/cancellation patterns operate without requiring whole-file buffering in the direct test.

## What was not proven

- user-selected local SAF tree read/write/seek/rename/move/URI stability;
- persisted SAF grant behavior across process death and explicit grant revocation;
- SD/USB disconnect and reconnect;
- removable filesystem differences;
- cloud-backed DocumentsProvider behavior;
- OEM-specific behavior;
- direct-operation atomicity as a universal Android guarantee;
- physical-device performance.

## Architecture implication

The empirical evidence supports Architecture V1's capability model rather than a universal filesystem abstraction. In particular, descriptor presence and seekability must remain independent facts, and filename validity must not be inferred from path-string syntax alone.

The missing SAF/removable/cloud evidence means exact SAF adapter semantics and URI/identity guarantees remain unsafe to freeze.

## Exit assessment

POC-001 is **PARTIAL**, not COMPLETE, because required user-selected SAF and disconnect/revocation evidence could not be completed in the available environment. Android 12 compatibility direct/descriptor evidence and Android 16 primary direct/descriptor evidence are present.

## Artifact classification

- `poc/POC-001-storage-capabilities/`: `DISPOSABLE`.
- raw captured evidence: `REFERENCE_ONLY`.
- conclusion documents: `REFERENCE_ONLY`.
- production-ready classification: NONE.