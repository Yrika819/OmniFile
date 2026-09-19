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
4. Archive technology-family closure is `CLOSED_WITH_EXPLICIT_GATES` at
    P05-004 final SHA `dd7ed4af9639de8011e6f1646c0b41eaf5e9f45c`
    (ancestry contains `df624230483ee38fd28669ad37c54233b1efe841`):
    strict matrix and checked-in real-engine harnesses pass for exact
    Commons Compress 1.28.0, Zip4j 2.11.6, Junrar 8.1.1, and zstd-jni
    1.5.7-17 host evidence, with a disposable Pixel 7a/API36 4 KiB
    native/TAR.ZST/restart/SAF-PFD pass. The `Broken pipe (32)` failure
    was root-caused to guest PackageManager/system_server instability
    (triple evidence: pushed-APK `pm install` reproduction, server-side
    `installation completed` vs client timeout, `DeadSystemException`
    cascades under load 15–65); the fixed v2 APK then produced 7/7
    expected outcomes on `PAGE_SIZE=16384` (single process; relaunch =
    release gate). Junrar/UnRAR remains `EXTERNAL_LICENSE_REVIEW_REQUIRED`
    / `RAR_FEATURE_FREEZE_BLOCKING_ONLY`; zstd provenance is
    `SUFFICIENT_WITH_NONREPRODUCIBLE_BUILD` with hash-pinning as a release
    gate. The libarchive validator result remains `libarchive=UNRESOLVED`
    and `OVERALL=NOT_CLOSED`; this is the expected guard for an uninvoked
    candidate, not a libarchive failure, while the separate campaign
    disposition is `NOT_JUSTIFIED_FOR_CORE_V1`. The duplicate-fixture
    removal hypothesis was rejected as false — `duplicate.tsv` is
    intentional negative coverage.

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
- P05-004 skeptical review: completed (7 findings, all dispositioned with
  new evidence); it required and received the `RAR_FEATURE_FREEZE_BLOCKING_ONLY`
  and libarchive wording corrections in the prior campaign.
- P05-004 independent final review: completed with conditional approval
  (9 findings, all fixed; 16 KiB relaunch downgrade approved subject to the
  single-process qualifier and a second-run release gate).
- Current architecture authority remains `6ec9e1037d0fd86afebdec6bd1a5be67b008ccbb`.
- `main` remains `793d151f9608a684c1d0d4be2e58e5b49d26f823`.
