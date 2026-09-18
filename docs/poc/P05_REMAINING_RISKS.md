# P05 Remaining Risks

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Blockers

1. Executor evidence is bounded: fresh Pixel 7a/API36 sanity exists, while API31
   final-artifact equivalence, WorkManager, and media-processing FGS remain open.
2. SAF evidence is parent-observed on one Pixel 7a/API36 run; interruption,
   cancellation, provider revocation/disconnect, API31, and pre-finalization
   lifecycle evidence remain open, with raw runtime artifacts not retained.
3. Media3 evidence is parent-observed on one Pixel 7a/API36 run; API31,
   pipe/provider, remote, lifecycle, and human audio acceptance remain open.
4. Archive technology-family closure remains `NOT_READY` and `OVERALL=NOT_CLOSED`.
   P05-004 final SHA is `df624230483ee38fd28669ad37c54233b1efe841`; strict
   matrix and checked-in real-engine harnesses pass for exact Commons Compress
   1.28.0, Zip4j 2.11.6, Junrar 8.1.1, and zstd-jni 1.5.7-17 host evidence,
   with a disposable Pixel 7a/API36 4 KiB native/TAR.ZST/restart/SAF-PFD pass.
   One authorized API36 16 KiB guest confirmed `PAGE_SIZE=16384` and APK
   alignment, but Package Manager failed with `Broken pipe (32)` before app
   launch, so 16 KiB app runtime is `NOT_COMPLETED`. Junrar/UnRAR remains
   `EXTERNAL_LICENSE_REVIEW_REQUIRED` / `RAR_FEATURE_FREEZE_BLOCKING_ONLY`;
   Android provider and retained-format/security closure and zstd source/build
   attestation remain open. The libarchive validator result remains
   `libarchive=UNRESOLVED` and `OVERALL=NOT_CLOSED`; this is the expected guard
   for an uninvoked candidate, not a libarchive failure, while the separate
   campaign disposition is `NOT_JUSTIFIED_FOR_CORE_V1`. The duplicate-fixture
   removal hypothesis was rejected as false — `duplicate.tsv` is intentional
   negative coverage.

## Post-scaffold non-blockers

These remain separate later campaigns unless new evidence makes them foundational:

- root access;
- SMB, SFTP, WebDAV, and cloud providers;
- remote media and NAS seek behavior;
- one-million-entry indexing;
- SD/USB disconnect behavior beyond the required SAF campaign.

## Scope confirmations

- Production File Manager implementation: not started.
- Production Android scaffold: not created.
- Technology Freeze: not executed.
- P05-004 skeptical review: completed; it required and received the
  `RAR_FEATURE_FREEZE_BLOCKING_ONLY` and libarchive wording corrections.
- P05-004 independent final review-agent: `REVIEW_NOT_COMPLETED`; the
  dispatched agent did not return a result within the bounded wait and was
  shut down. No approval is claimed.
- Current architecture authority remains `6ec9e1037d0fd86afebdec6bd1a5be67b008ccbb`.
- `main` remains `793d151f9608a684c1d0d4be2e58e5b49d26f823`.
