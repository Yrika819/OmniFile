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
   P05-004 final SHA is `516308346d36c1c958a2bfa85a5fb1a2412379d9`; strict
   matrix and checked-in real-engine harnesses pass for exact Commons Compress
   1.28.0, Zip4j 2.11.6, Junrar 8.1.1, and zstd-jni 1.5.7-17 host evidence,
   with a disposable Pixel 7a/API36 4 KiB native/TAR.ZST/restart/SAF-PFD pass.
   Junrar/UnRAR legal approval, Android provider and retained-format/security
   closure, zstd source/build attestation and runtime 16 KiB remain open.
   libarchive is explicitly `NOT_JUSTIFIED_FOR_CORE_V1` absent a concrete
   Java-first capability gap. The duplicate-fixture removal hypothesis was
   rejected as false — `duplicate.tsv` is intentional negative coverage.

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
- P05-004 independent final review: `REVIEW_NOT_COMPLETED` because the
  requested review-agent dispatch hit the host subagent thread limit; no review
  approval is claimed.
- Current architecture authority remains `6ec9e1037d0fd86afebdec6bd1a5be67b008ccbb`.
- `main` remains `793d151f9608a684c1d0d4be2e58e5b49d26f823`.
