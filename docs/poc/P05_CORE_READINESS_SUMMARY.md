# P05 Core Readiness Summary

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Final status

`P05_CORE_READINESS_READY_WITH_EXPLICIT_GATES — P05-004 closed with gates after 16 KiB single-process runtime, zstd provenance/build, and retained route/provider/security evidence; remaining items classified as implementation/release gates`

The four independent branches were created from the sole authority
`6ec9e1037d0fd86afebdec6bd1a5be67b008ccbb`. Their host-side harnesses and
documentation were independently reviewed. Parent-controlled Pixel 7a/API 36
runs provide bounded executor, SAF, and Media3 PoC evidence; they do not
close the untested API/provider/lifecycle/audio gates, which are classified
below as implementation/release gates rather than family-changing blockers.
P05-004 is now `CLOSED_WITH_EXPLICIT_GATES` at
`dd7ed4af9639de8011e6f1646c0b41eaf5e9f45c` (ancestry contains `df62423`).

## Branch disposition

| PoC | Branch | Final SHA | Status | Review disposition |
|---|---|---|---|---|
| P05-001 | `poc/core-readiness-executors-v1` | `41030aa7ddb089eb7b850fe85c1a0d682e3d6c8a` | `CLOSED_WITH_EXPLICIT_GATE` — fresh Pixel 7a/API36 sanity; API31 final-artifact equivalence, WorkManager, and media FGS remain open | Independent review PASS; host 5/5 and runtime contract 6/6 |
| P05-002 | `poc/core-readiness-saf-operations-v1` | `1912ee8a40a4182407fe9b9d14e21b26e64a6199` | `PARTIALLY_RESOLVED / NOT_CLOSED` — parent-observed Pixel 7a/API36 SAF directions and restart reconciliation; interrupt/cancel, provider/API31, and pre-finalization lifecycle remain open | Final review checks PASS; host 25/25; runtime artifact/logs not retained in branch |
| P05-003 | `poc/core-readiness-media3-v1` | `f649f1de14cea338f3471eafab43d10a9c736828` | `PARTIALLY_RESOLVED / NOT_CLOSED` — parent-observed Pixel 7a/API36 local WAV/FLAC/SAF and sequential seek-failure boundary; API31, pipe, remote, lifecycle, and human audio gates remain open | Final review PASS; host 15/15; FLAC fixture tracked |
| P05-004 | `poc/core-readiness-archive-v1` | `dd7ed4af9639de8011e6f1646c0b41eaf5e9f45c` | `CLOSED_WITH_EXPLICIT_GATES` — exact Commons Compress 1.28.0, Zip4j 2.11.6, Junrar 8.1.1, zstd-jni 1.5.7-17 host evidence; Pixel 7a/API36 4 KiB native/TAR.ZST/restart/SAF-PFD pass; authorized API36 ps16k x86_64 guest: `Broken pipe (32)` root-caused to guest PackageManager/system_server instability, then fixed-v2 APK (SHA `ecc8f271…`) produced 7/7 expected outcomes (5 PASS + 2 controlled errors) on `PAGE_SIZE=16384`, no probe-attributed crash; zstd provenance `SUFFICIENT_WITH_NONREPRODUCIBLE_BUILD` (no v1.5.7-17 tag; master-`b00e4d3` inference) + host source build and verified smoke; retained routes + spool/containment/cleanup/cancel/lifecycle/error evidence (Spool 18/18, RealEngine 13/13 on exact artifacts); Junrar `EXTERNAL_LICENSE_REVIEW_REQUIRED` / `RAR_FEATURE_FREEZE_BLOCKING_ONLY`; libarchive machine `UNRESOLVED` retained with disposition `NOT_JUSTIFIED_FOR_CORE_V1` (not a blocker); `duplicate.tsv` preserved | Adversarial review 7 findings dispositioned; independent review conditionally approved (9 findings fixed) |

## Device/API matrix

| Target | Evidence | Status |
|---|---|---|
| API 31 | No Android runtime harness executed | `NOT_TESTED` |
| API 33 | No Android runtime harness executed | `NOT_TESTED` |
| API 34 | No Android runtime harness executed | `NOT_TESTED` |
| API 35 | No Android runtime harness executed | `NOT_TESTED` |
| API 36 / Pixel 7a | P05-001 fresh sanity; parent-observed P05-002 SAF and P05-003 Media3 disposable runs; P05-004 exact disposable zstd APK native/TAR.ZST/restart/real Downloads PFD pass on a 4 KiB runtime; authorized ps16k x86_64 guest: `PAGE_SIZE=16384` with 7/7 expected outcomes from the fixed probe APK (single process; relaunch + broader ABI/device coverage = release gates) | `READY_WITH_GATES — bounded to named APKs, fixture trees, disposable probes, and API 36; not production acceptance` |

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

Classified per the P05 key decision rule (a missing test blocks the freeze
only if failure could force an architecture/dependency-family/engine/
provider-model change):

- P05-001 API31 final-artifact equivalence, WorkManager, and media-processing FGS gates → RELEASE_GATEs.
- P05-002 interrupt/cancel timing, pre-finalization process death, provider revocation/disconnect, API31, and independent runtime artifact retention → IMPLEMENTATION/RELEASE_GATEs.
- P05-003 API31, pipe-backed/provider-specific, remote/range, lifecycle, and human audio acceptance gates → IMPLEMENTATION/RELEASE_GATEs.
- P05-004 explicit gates: split/multipart ZIP + RAR solid/multipart corpus, ZIP/Junrar symlink fixtures, BZ2/XZ scale corpus, nested-depth accounting, device-scale cancel latency, error-wrapper ambiguity resolution (IMPLEMENTATION); transitive NOTICE register, production packaging + second 16 KiB run/relaunch/device evidence, API 12–16 coverage, update ownership, external Junrar license approval before RAR enablement, production hash-pinning of zstd-jni (RELEASE).
- libarchive: `NOT_JUSTIFIED_FOR_CORE_V1`; reopens only on a concrete demonstrated Java-first gap.
