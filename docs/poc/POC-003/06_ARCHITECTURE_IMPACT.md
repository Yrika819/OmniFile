# POC-003 — Architecture Impact

Status: COMPLETE

Architecture authority remains unchanged at `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`.

## CONFIRMED

- Archive browsing should remain modeled as a virtual-storage layer rather than requiring full extraction first.
- A single archive engine should not be assumed to cover every desired format/feature.
- Extraction policy must be independent of parser success and must enforce destination containment, link handling, expansion limits, entry-count limits and nested-archive limits.
- RAR creation must not be implied by RAR read/extract support.

## REFINED

- For the measured Java stack, Commons Compress + Zip4j + Junrar is a credible multi-engine direction for further review: Commons Compress covers broad archive/TAR/7z needs, Zip4j fills encrypted/split ZIP gaps, and Junrar covers tested RAR variants.
- TAR.ZST introduces a native dependency when using zstd-jni on Android. That native boundary must be treated explicitly rather than hidden behind the Java archive API.
- Large directory listing is feasible in the measured 10k/100k range, but the retained-memory differences between engines justify keeping lazy/indexed browsing as a design concern.

## CHALLENGED

- Any assumption that Commons Compress alone can provide encrypted ZIP support is challenged by the measured AES and ZipCrypto failures.
- Any assumption that 'Java library' necessarily means 'no native packaging' is challenged by TAR.ZST through zstd-jni.

## CONTRADICTED

- None of the frozen Architecture V1 principles were contradicted.

## UNAFFECTED

- Production module structure, package names, dependency injection, UI architecture, final dependency versions, minSdk/targetSdk/compileSdk and release packaging remain intentionally unfrozen.

## Still unsafe to freeze

- exact archive-engine API abstraction;
- exact dependency versions;
- exact native codec packaging;
- libarchive inclusion/exclusion;
- 16 KiB-page-size native policy;
- final archive limits and cancellation thresholds.

The appropriate next step is architecture review using this evidence, not automatic technology freeze.