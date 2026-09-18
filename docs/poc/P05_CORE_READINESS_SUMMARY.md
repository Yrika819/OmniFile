# P05 Core Readiness Summary

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Final status

`P05_CORE_READINESS_INCOMPLETE — bounded API 36 device evidence collected; archive technology-family closure and multiple runtime gates remain open`

The four independent branches were created from the sole authority
`6ec9e1037d0fd86afebdec6bd1a5be67b008ccbb`. Their host-side harnesses and
documentation were independently reviewed. Parent-controlled Pixel 7a/API 36
runs now provide bounded executor, SAF, and Media3 PoC evidence; they do not
close the untested API/provider/lifecycle/audio gates. P05-004 remains open.
Therefore this synthesis does not authorize CORE_V1 Technology Freeze or
production initialization.

## Branch disposition

| PoC | Branch | Final SHA | Status | Review disposition |
|---|---|---|---|---|
| P05-001 | `poc/core-readiness-executors-v1` | `41030aa7ddb089eb7b850fe85c1a0d682e3d6c8a` | `CLOSED_WITH_EXPLICIT_GATE` — fresh Pixel 7a/API36 sanity; API31 final-artifact equivalence, WorkManager, and media FGS remain open | Independent review PASS; host 5/5 and runtime contract 6/6 |
| P05-002 | `poc/core-readiness-saf-operations-v1` | `1912ee8a40a4182407fe9b9d14e21b26e64a6199` | `PARTIALLY_RESOLVED / NOT_CLOSED` — parent-observed Pixel 7a/API36 SAF directions and restart reconciliation; interrupt/cancel, provider/API31, and pre-finalization lifecycle remain open | Final review checks PASS; host 25/25; runtime artifact/logs not retained in branch |
| P05-003 | `poc/core-readiness-media3-v1` | `f649f1de14cea338f3471eafab43d10a9c736828` | `PARTIALLY_RESOLVED / NOT_CLOSED` — parent-observed Pixel 7a/API36 local WAV/FLAC/SAF and sequential seek-failure boundary; API31, pipe, remote, lifecycle, and human audio gates remain open | Final review PASS; host 15/15; FLAC fixture tracked |
| P05-004 | `poc/core-readiness-archive-v1` | `df624230483ee38fd28669ad37c54233b1efe841` | `NOT_READY / NOT_CLOSED` — exact Commons Compress 1.28.0, Zip4j 2.11.6, Junrar 8.1.1, and zstd-jni 1.5.7-17 host evidence plus disposable Pixel 7a/API36 4 KiB native/TAR.ZST/restart/SAF-PFD evidence; authorized API36 16 KiB guest identity and aligned x86_64 APK were verified, but Package Manager failed with `Broken pipe (32)` before app launch; Junrar remains `EXTERNAL_LICENSE_REVIEW_REQUIRED` / `RAR_FEATURE_FREEZE_BLOCKING_ONLY`, zstd source/build and 16 KiB runtime, and retained provider/format/security closure remain open; libarchive is an uninvoked-candidate guard with disposition `NOT_JUSTIFIED_FOR_CORE_V1`; `duplicate.tsv` is preserved intentional negative coverage | Strict matrix and checked-in real-engine harnesses PASS; 16 KiB app runtime `NOT_COMPLETED`; final review-agent `REVIEW_NOT_COMPLETED` (agent did not return a result before bounded wait and was shut down) |

## Device/API matrix

| Target | Evidence | Status |
|---|---|---|
| API 31 | No Android runtime harness executed | `NOT_TESTED` |
| API 33 | No Android runtime harness executed | `NOT_TESTED` |
| API 34 | No Android runtime harness executed | `NOT_TESTED` |
| API 35 | No Android runtime harness executed | `NOT_TESTED` |
| API 36 / Pixel 7a | P05-001 fresh sanity; parent-observed P05-002 SAF and P05-003 Media3 disposable runs; P05-004 exact disposable zstd APK native/TAR.ZST/restart/real Downloads PFD pass on a 4 KiB runtime; one authorized ps16k x86_64 guest reported `PAGE_SIZE=16384` and aligned APK packaging, but Package Manager failed during install | `PARTIAL — bounded to named APKs, fixture trees, disposable archive probe, and API 36; 16 KiB app runtime `NOT_COMPLETED`, not production acceptance` |

## Confirmed architecture principles

- Executor lifetime remains separate from durable operation truth.
- Recovery remains reconciliation over durable records plus observed storage reality.
- MOVE source deletion remains after independently verified/finalized destination state and source revalidation.
- Descriptor existence is not treated as a read or seek guarantee.
- Provider-scoped identity remains separate from path/URI/transport locator.
- Provider-neutral playback remains a boundary, not a frozen Media3 class or version.
- Archive engines remain separate from extraction security containment and expansion policy.
- `NOT_TESTED` and `UNKNOWN` remain explicit rather than promoted to `PASS`.

## Open foundational gates

- P05-001 API31 final-artifact equivalence, WorkManager, and media-processing FGS gates.
- P05-002 interrupt/cancel timing, pre-finalization process death, provider revocation/disconnect, API31, and independent runtime artifact retention.
- P05-003 API31, pipe-backed/provider-specific, remote/range, lifecycle, and human audio acceptance gates.
- P05-004 conditional RAR-feature legal review, Android provider and retained-format/security closure, zstd source/build attestation and runtime 16 KiB evidence; libarchive remains an uninvoked candidate not justified for CORE_V1 unless a concrete Java-first gap appears.
