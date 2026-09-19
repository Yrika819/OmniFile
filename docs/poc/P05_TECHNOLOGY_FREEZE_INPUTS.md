# P05 Technology Freeze Inputs

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Decisions supported for later review

These are architecture-direction inputs only; none is frozen by this campaign:

- Use an executor policy/router rather than one universal Android executor.
- Keep durable operation truth and recovery reconciliation independent from executor lifetime.
- Treat SAF operations as capability/provider-specific, with finalization and document identity re-resolved after restart.
- Keep playback source resolution provider-neutral and capability-aware before Media3.
- Retain a Java-first multi-engine archive evidence path as the frozen
  direction (not exact versions): Commons Compress for ZIP/Zip64/TAR and
  compressed TARs, Zip4j for encrypted/split ZIP, Junrar technically for
  RAR4/5 read gated by external license review, zstd-jni 1.5.7-17 for the
  ZSTD/TAR.ZST assist. Exact host evidence exists; PoC versions are not
  automatically production versions. The disposable Pixel probe passed on a
  4 KiB runtime and one real SAF/PFD route; the fixed probe APK produced
  7/7 expected outcomes on a `PAGE_SIZE=16384` guest (single process).

## User-frozen product identity

- Display name: `OmniFile`.
- Production Android applicationId: `com.omnifile`.
- No production Android application, module, or package was created by this
  campaign; disposable PoC identifiers remain PoC-only.

## Choices not frozen

- production namespace/package structure and module structure;
- minSdk, targetSdk, compileSdk, Kotlin/AGP/Gradle/JDK versions;
- Compose/Material/Media3/database versions and exact dependency set;
- release signing, CI/CD, release channel, persistence schema, executor classes;
- archive library versions, conditional Junrar/UnRAR legal acceptance for the
  RAR feature (feature gate, not family blocker), production hash-pinning
  and NOTICE/packaging revalidation for zstd-jni, second 16 KiB run and
  relaunch/device evidence (release gates), and the implementation corpus
  above. libarchive is currently an uninvoked candidate with disposition
  `NOT_JUSTIFIED_FOR_CORE_V1` and may reopen only for a concrete Java-first gap.

## Technology Freeze gate

`GO_WITH_EXPLICIT_GATES` from this campaign. Bounded Pixel 7a/API36 evidence exists for P05-001,
P05-002, and P05-003, with remaining API/provider/lifecycle/audio items classified as
implementation/release gates. P05-004 is now published at
`dd7ed4af9639de8011e6f1646c0b41eaf5e9f45c` (ancestry contains `df62423…`)
as `CLOSED_WITH_EXPLICIT_GATES`: conditional RAR-feature legal review stays a
feature gate; zstd provenance is sufficient-with-nonreproducible-build with
production hash-pinning as a release gate; 16 KiB single-process runtime is
verified with relaunch/device coverage as release gates; the retained
provider/format/security matrix is proven with the named implementation
corpus remaining. The exact disposable APK was aligned and the Pixel passed
native/TAR.ZST/restart/SAF-PFD checks on 4 KiB pages; the fixed APK produced
7/7 expected outcomes on a `PAGE_SIZE=16384` guest. A separately authorized
Technology Freeze task must still re-check every SHA and rerun the required
evidence before creating production Android structure.
