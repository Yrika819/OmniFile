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
4. Archive candidate closure remains conditional/open, including legal, parser-
   security, real-provider, native packaging, and 16 KiB gates. P05-004 final
   SHA `03d7c29e04932ecfbd926cd7934f8be66ab05fa2` (`OVERALL=NOT_CLOSED`;
   harness `PASS`); the duplicate-fixture removal hypothesis was rejected as
   false — `duplicate.tsv` is intentional negative coverage.

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
- Current architecture authority remains `6ec9e1037d0fd86afebdec6bd1a5be67b008ccbb`.
- `main` remains `793d151f9608a684c1d0d4be2e58e5b49d26f823`.
