# P05-001 — Environment

**Label:** `POC-ONLY — NOT PRODUCTION AUTHORITY`

## Question

Can executor-shaped interruption scenarios be classified from durable operation
state plus destination reality without making the executor instance the source of
truth?

This POC concerns the executor boundary from Architecture V1. The disposable
runtime harness also measures selected Android scheduling and lifecycle paths,
but its observations are scoped to the exact raw APK, API 31 emulator, API 36
Pixel 7a, and generated workload. It is not provider or production-policy
evidence.

## Hypotheses

| ID | Hypothesis | Host fixture oracle |
| --- | --- | --- |
| H1 | A partial destination after interruption is not completion. | Classify as `RESUMABLE`; source deletion is false. |
| H2 | Ambiguous finalization must not be replayed blindly. | Classify as `NEEDS_ATTENTION`; source deletion is false. |
| H3 | A verified/finalized destination is the prerequisite for the MOVE source-delete step. | Classify as `COMPLETE`; source deletion is true only for MOVE with source present. |
| H4 | Executor names do not select architecture. | Report keeps `selected_executor` null. |
| H5 | Executor recreation must not erase operation truth. | API36 Pixel restart rediscovered `RUNNING` durable state and completed; API31 restart recovery was not proven because the preserved row was `ABSENT`. |

## Success criteria

- The host tests prove H1–H4 deterministically.
- The report identifies itself as planning/fixture evidence.
- Runtime reports identify device/API/build and remain POC-only observations.
- Unsupported mechanisms and untested branches are labeled explicitly.

## Explicit non-claims

This POC does not establish a universal choice among WorkManager, UIDT,
foreground service, JobScheduler, or in-app execution. It does not establish
WorkManager behavior, media-processing FGS behavior, provider recovery,
notification UX acceptance, or production readiness. Direct shell `kill -9`
was blocked by the Pixel's SELinux policy; the restart scenario therefore uses
the documented `am force-stop` fallback and is not equivalent to arbitrary
kernel process death.
