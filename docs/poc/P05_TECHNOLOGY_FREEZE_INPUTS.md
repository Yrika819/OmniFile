# P05 Technology Freeze Inputs

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Decisions supported for later review

These are architecture-direction inputs only; none is frozen by this campaign:

- Use an executor policy/router rather than one universal Android executor.
- Keep durable operation truth and recovery reconciliation independent from executor lifetime.
- Treat SAF operations as capability/provider-specific, with finalization and document identity re-resolved after restart.
- Keep playback source resolution provider-neutral and capability-aware before Media3.
- Retain a Java-first multi-engine archive evidence path as a future option, not
  a frozen technology choice. Exact host evidence exists for Commons Compress
  1.28.0, Zip4j 2.11.6, Junrar 8.1.1, and zstd-jni 1.5.7-17, but the family
  remains `NOT_READY` while the gates below are open. The disposable Pixel
  probe passed on a 4 KiB runtime and one real SAF/PFD route; that is not 16 KiB
  runtime or production acceptance.

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
- archive library versions, Junrar/UnRAR legal acceptance, zstd source/build
  attestation and runtime 16 KiB acceptance, provider/format/security closure,
  and native dependency selection. libarchive is currently
  `NOT_JUSTIFIED_FOR_CORE_V1` and may reopen only for a concrete Java-first gap.

## Technology Freeze gate

`NO-GO` from this campaign. Bounded Pixel 7a/API36 evidence exists for P05-001,
P05-002, and P05-003, but required API/provider/lifecycle/audio gates remain
open. P05-004 is now published at
`516308346d36c1c958a2bfa85a5fb1a2412379d9`, while its archive family remains
`NOT_READY / NOT_CLOSED`: Junrar/UnRAR licensing, zstd source/build and 16 KiB
runtime acceptance, and retained provider/format/security closure remain open.
The exact disposable APK and installed hash matched, and the Pixel passed
native/TAR.ZST/restart/SAF-PFD checks on 4 KiB pages; those results do not close
16 KiB runtime or production acceptance. Technology Freeze itself was not
executed. A separately authorized Technology Freeze task must re-check every
SHA and rerun the required evidence before creating production Android
structure.
