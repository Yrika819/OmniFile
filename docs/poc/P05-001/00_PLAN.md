# P05-001 — Android Executor Readiness Campaign — Plan

Status: INCOMPLETE — HOST FIXTURE GREEN; ANDROID RUNTIME `NOT_TESTED`

Required label: `POC-ONLY — NOT PRODUCTION AUTHORITY`

Architecture authority: `6ec9e10` at campaign start (`poc/core-readiness-executors-v1`)

## Purpose

Provide the P0.5 executor-readiness evidence requested by D022 without selecting a production Android executor or making executor lifetime the source of operation truth. The campaign covers executor-shaped interruption fixtures, the required foreground/WorkManager/UIDT/FGS/JobScheduler comparison scaffold, and the unresolved very-long local-copy path.

The campaign has two layers:

1. deterministic host-only Python standard-library logic for destination-reality classification and durable recovery safety;
2. documentation of the Android lifecycle matrix, with Android runtime rows explicitly marked `NOT_TESTED` because this worktree has no Android application scaffold and no ADB/device execution was performed.

## Scope matrix

| Workload | Candidate mechanism | API range | Campaign disposition |
|---|---|---:|---|
| short interactive operation | foreground app execution | 31–36 | fixture oracle only |
| deferrable metadata/index refresh | WorkManager | 31–36 | fixture oracle only |
| user-initiated network transfer | UIDT | 34–36 | fixture oracle only; Android lifecycle `NOT_TESTED` |
| API 31–33 transfer fallback | FGS / other compatible strategy | 31–33 | unresolved; Android lifecycle `NOT_TESTED` |
| media conversion | `mediaProcessing` FGS candidate | 35–36 | fixture oracle only; timeout/device behavior `NOT_TESTED` |
| very long local-only copy | no mapping selected | 31–36 | unresolved; Android lifecycle `NOT_TESTED` |

## Safety and non-goals

- The harness is disposable and is not production Android code.
- No Android project, manifest, service, Worker, UIDT job, APK, AAB, emulator, device, or ADB operation was created or invoked.
- No production persistence technology, checkpoint cadence, retry policy, or D022 mapping is frozen.
- Fixtures are small generated metadata records; no user data or large binary files are committed.

## Evidence labels

- `PLANNING_FIXTURE_ONLY`: deterministic host classification/reconciliation behavior.
- `NOT_TESTED_ANDROID_RUNTIME`: Android framework, scheduler, service, quota, notification, device, or process-lifecycle behavior not exercised.
- `UNRESOLVED`: repository constraints require a later Android/API/device campaign.

## Exit status

The host evidence package is present and verified, but the campaign exit
criteria are not met because API 31/API 36 runtime evidence and Android 16
physical-device evidence were not collected. D022 remains `POC_REQUIRED`.
