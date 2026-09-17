# P05-001 — Conclusions

**Label:** `POC-ONLY — NOT PRODUCTION AUTHORITY`

## Runtime conclusion

The raw disposable APK is sufficient for the scoped framework comparison. API36
physical evidence and API31 emulator evidence show that foreground Activity,
dataSync FGS, and JobScheduler can start, checkpoint, and reach durable
completion in this harness; UIDT does the same on API36. API36
restart/re-discovery reaches completion without making the executor instance
the source of truth. API31 restart recovery is not proven by the preserved
capture and remains gated on a final-artifact emulator rerun. The host
classifier remains green and executor-independent.

## Evidence limitations

- WorkManager was not run because the raw APK has no AndroidX WorkManager dependency.
- Media-processing FGS was not run because the harness has no media workload or
  media-processing service declaration.
- Direct shell `kill -9` was blocked by Pixel device SELinux; `am force-stop` was
  used and recorded as a distinct fallback.
- API31/API36 evidence is scoped to the named emulator, Pixel, build, and APK;
  it is not a general compatibility result.
- Provider I/O, archive/media workload behavior, quota stress, thermal behavior,
  notification interaction, and user acceptance were not measured here.
- No SDK, dependency, namespace, application ID, or production version was
  selected.
- The classifier does not model persistence, checksums, source-version tokens,
  provider reconnect, cancellation cleanup, or crash windows beyond the named
  destination-reality cases.

## Follow-up gates

The evidence supports a workload/API router shape: foreground app for short
interactive work, UIDT for supported explicit user transfers on API34+, and
dataSync FGS or JobScheduler only where their policy and duration constraints
fit. A separately authorized WorkManager harness and media-processing FGS
campaign are still needed before those rows can be closed. This branch does not
freeze a production dependency, namespace, application ID, persistence schema,
or executor selection.
