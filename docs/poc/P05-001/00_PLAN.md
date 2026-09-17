# P05-001 — Android Executor Readiness Campaign — Plan

Status: RUNTIME EVIDENCE COMPLETE WITH EXPLICIT GATES — READY FOR INDEPENDENT REVIEW

Required label: `POC-ONLY — NOT PRODUCTION AUTHORITY`

Architecture authority: `6ec9e10` at campaign start (`poc/core-readiness-executors-v1`)

## Purpose

Provide the P0.5 executor-readiness evidence requested by D022 without selecting a production Android executor or making executor lifetime the source of operation truth. The campaign covers executor-shaped interruption fixtures, the required foreground/WorkManager/UIDT/FGS/JobScheduler comparison scaffold, and the unresolved very-long local-copy path.

The campaign has two layers:

1. deterministic host-only Python standard-library logic for destination-reality classification and durable recovery safety;
2. a disposable raw-APK Android runtime harness exercised on the physical Pixel 7a (API 36) and an API 31 emulator, with unsupported or unavailable mechanisms recorded explicitly rather than inferred.

## Scope matrix

| Workload | Candidate mechanism | API range | Campaign disposition |
|---|---|---:|---|
| short interactive operation | foreground app execution | 31–36 | runtime observed; durable state remains independent |
| deferrable metadata/index refresh | WorkManager | 31–36 | `NOT_APPLICABLE` in framework-only harness; JobScheduler measured separately |
| user-initiated network transfer | UIDT | 34–36 | API 36 Pixel runtime observed; API <34 `NOT_APPLICABLE` |
| API 31–33 transfer fallback | FGS / other compatible strategy | 31–33 | API 31 FGS and JobScheduler observed; policy remains workload-specific |
| media conversion | `mediaProcessing` FGS candidate | 35–36 | `NOT_TESTED` — no media-processing workload in this executor harness |
| very long local-only copy | no mapping selected | 31–36 | unresolved; no universal executor selected |

## Safety and non-goals

- The harness is disposable and is not production Android code.
- The Android harness is disposable, raw-platform, and POC-only; it does not define a production Android project or dependency set.
- No production persistence technology, checkpoint cadence, retry policy, or D022 mapping is frozen.
- Fixtures are small generated metadata records; no user data or large binary files are committed.

## Evidence labels

- `PLANNING_FIXTURE_ONLY`: deterministic host classification/reconciliation behavior.
- `RUNTIME_OBSERVED_POC_ONLY`: behavior observed only for the named harness, device/API, workload, and run.
- `NOT_APPLICABLE`: mechanism/API combination rejected because the platform contract does not support it or the harness intentionally has no required dependency.
- `NOT_TESTED_ANDROID_RUNTIME`: a required candidate or lifecycle branch not exercised by this harness.
- `UNRESOLVED`: repository constraints require a later Android/API/device campaign.

## Exit status

The host and runtime evidence packages are verified for the covered mechanisms:
API 36 physical Pixel 7a, API 31 emulator, foreground app, data-sync FGS,
JobScheduler, UIDT, API36 process restart/re-discovery, background/screen-off,
and durable completion. API31 restart recovery and final-artifact equivalence
remain explicitly gated by the unstable AVD. WorkManager, media-processing
FGS, and a direct shell signal remain explicit gates. D022 is refined into a
workload/API router question; no single executor is selected.
