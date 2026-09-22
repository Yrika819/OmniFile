# Receiving Code Review Resolution

- Report type: `receiving-code-review`
- Review chain ID: `rc-20260923-7adfab01`
- Resolution ID: `rr-20260923-9739a4f8`
- Source report ID: `cr-20260923-7adfab01`
- Source report: `docs/review/VS08_CODE_REVIEW_V1_7adfab01.md`
- Scope fingerprint rechecked: `sha256:67fbbe45aab301cc50b3d5c08d53b76f9ec2e8e3c80c722c577c5aed7fd919ea`
- Continuation: `continue already-authorized fixes`
- Resolution status: `Resolved; ready for generation-1 terminal review`

## F1 — resolved

- Issue: mid-stream provider read failures were reported as generic archive I/O.
- Repair: source-stream `IOException`/transport state in the ZIP parser and extraction read path now maps to `ArchiveError.Provider(StorageError.ProviderUnavailable)`. Destination writer failures remain destination I/O failures.
- Evidence: focused host suite passes; controlled SAF browse/extraction passes on Pixel 7a/API 36; lint/build pass.

## F2 — resolved

- Issue: raw path equality allowed canonical Unicode output collisions.
- Repair: extraction planning and Local destination lookup use NFC-normalized path/component identities. Canonically equivalent archive targets fail before output and existing destination names are matched conservatively.
- Evidence: new deterministic NFC/NFD collision test passes; full host suite passes; lint/build pass.

## T1 — resolved

- Gap: Files/Search archive open callback coverage was absent.
- Repair: added focused Compose instrumentation cases for ZIP row open from Files and archive Search result callback identity, alongside the existing shell origin/back host tests.
- Evidence: Android-test source compiles; focused archive Pixel instrumentation passes. Full device UI baseline remains separately unstable with pre-existing Activity/Compose failures.

## Verification

- `:app:testDebugUnitTest`: 128 passed, 0 failed, 0 skipped.
- `:app:assembleDebugAndroidTest`: passed.
- `:app:lintDebug`: passed; only baseline/tooling findings.
- `:app:assembleDebug`: passed.
- Focused Pixel `ArchiveStorageInstrumentedTest`: 1/1 passed.
