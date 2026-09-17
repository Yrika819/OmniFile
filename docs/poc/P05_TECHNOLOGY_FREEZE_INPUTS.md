# P05 Technology Freeze Inputs

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Decisions supported for later review

These are architecture-direction inputs only; none is frozen by this campaign:

- Use an executor policy/router rather than one universal Android executor.
- Keep durable operation truth and recovery reconciliation independent from executor lifetime.
- Treat SAF operations as capability/provider-specific, with finalization and document identity re-resolved after restart.
- Keep playback source resolution provider-neutral and capability-aware before Media3.
- Retain a Java-first multi-engine archive candidate path as a future option, subject to the open candidate gates below.

## Choices not frozen

- applicationId, display name, namespace/package, module structure;
- minSdk, targetSdk, compileSdk, Kotlin/AGP/Gradle/JDK versions;
- Compose/Material/Media3/database versions and exact dependency set;
- release signing, CI/CD, release channel, persistence schema, executor classes;
- archive library versions, Junrar legal acceptance, native dependency selection.

## Technology Freeze gate

`NO-GO` from this campaign. The required runtime/device evidence is missing and
P05-004 is not closed. A separately authorized Technology Freeze task must
re-check every SHA and rerun the required evidence before creating production
Android structure.
