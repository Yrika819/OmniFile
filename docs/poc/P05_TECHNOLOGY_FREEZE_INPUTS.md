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
  remains `NOT_READY` while the gates below are open.

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
- archive library versions, Junrar/UnRAR legal acceptance, zstd source/build/
  APK/runtime and runtime 16 KiB acceptance, provider/format/security closure,
  the libarchive comparison, and native dependency selection.

## Technology Freeze gate

`NO-GO` from this campaign. Bounded Pixel 7a/API36 evidence exists for P05-001,
P05-002, and P05-003, but required API/provider/lifecycle/audio gates remain
open. P05-004 is now published at
`b0ccc2ca0459c6a92b128f80bb9f0fb8c2406898`, while its archive family remains
`NOT_READY / NOT_CLOSED`: Junrar/UnRAR licensing, zstd Android/source/runtime
acceptance, retained provider/format/security closure, and libarchive remain
open. Technology Freeze itself was not executed. A separately authorized
Technology Freeze task must re-check every SHA and rerun the required evidence
before creating production Android structure.
