# P05-004 Archive Candidate Closure Scope

Status: `EVIDENCE PACKAGE COMPLETE / PRODUCTION CLOSURE NOT CLOSED`

## Objective

Audit the P0 archive evidence and define a truthful closure package for five candidates:

- Apache Commons Compress 1.28.0;
- Zip4j 2.11.6;
- Junrar 8.1.1;
- zstd-jni Android AAR;
- libarchive 3.8.9.

The branch-local P0 reconciliation states that host and physical Pixel 7a archive records contained 54 records and `RUN_DONE=PASS`, while exact engine selection, libarchive/native alternatives, SAF/remote origins, fuzz coverage, cancellation, and production scale remain open ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:51-62`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md)). Those facts are carried forward without reinterpretation.

## In scope

- candidate-by-candidate disposition;
- format and operation coverage;
- direct, SAF, seekable, non-seekable, and remote access gates;
- extraction-security responsibilities outside parser libraries;
- license, provenance, native ABI, 16 KiB, size, crash, and update gates;
- architecture impact and explicit `NOT_TESTED` / `UNKNOWN` findings;
- a disposable host/JVM manifest validator.

## Out of scope

- selecting or adding production dependencies;
- Android application or module creation;
- ADB, emulator, physical-device, or Android runtime execution;
- building libarchive, zstd-jni, or any archive library;
- claiming RAR creation;
- claiming that parser APIs provide extraction security;
- Technology Freeze.

## Authority and decision boundary

P0 permits review of archive principles and boundaries, but not freezing exact engines, versions, native alternatives, Junrar licensing, full-entry/multipart behavior, fuzz coverage, cancellation, or production scale ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:110-125`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md)). The architecture likewise keeps the engine stack `POC_REQUIRED` and all five candidates as research candidates only ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:43-60`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)).

## Truth policy

`PASS` means the cited P0 or repository evidence covers the exact claim. `CONDITIONAL` means a material gate remains open despite useful evidence. `NOT_TESTED` means the campaign did not perform the measurement. `UNKNOWN` means the available documents do not establish the fact. Open states are not inferred closed by a passing neighboring test.
