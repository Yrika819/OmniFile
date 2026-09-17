# P05-001 — Question and Hypothesis

**Label:** `POC-ONLY — NOT PRODUCTION AUTHORITY`

## Question

Can executor-shaped interruption scenarios be classified from durable operation
state plus destination reality without making the executor instance the source of
truth?

This POC concerns the executor boundary from Architecture V1. It does not test
Android scheduling, notification behavior, quotas, lifecycle callbacks, or
provider I/O. Those require a permitted Android project and runtime campaign.

## Hypotheses

| ID | Hypothesis | Host fixture oracle |
| --- | --- | --- |
| H1 | A partial destination after interruption is not completion. | Classify as `RESUMABLE`; source deletion is false. |
| H2 | Ambiguous finalization must not be replayed blindly. | Classify as `NEEDS_ATTENTION`; source deletion is false. |
| H3 | A verified/finalized destination is the prerequisite for the MOVE source-delete step. | Classify as `COMPLETE`; source deletion is true only for MOVE with source present. |
| H4 | Executor names do not select architecture. | Report keeps `selected_executor` null. |

## Success criteria

- The host tests prove H1–H4 deterministically.
- The report identifies itself as planning/fixture evidence.
- No result is phrased as Android runtime confirmation.
- The docs state the exact blocker preventing an Android harness run.

## Explicit non-claims

This POC does not establish the correct choice among WorkManager, UIDT,
foreground service, JobScheduler, or in-app execution. It does not establish
API 31–36 behavior, process-kill delivery semantics, quota/timing limits,
provider recovery, notification UX, or production readiness.
