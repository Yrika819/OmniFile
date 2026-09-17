# P05-001 — Test Matrix

**Label:** `POC-ONLY — NOT PRODUCTION AUTHORITY`

This is a comparison scaffold, not a technology selection. The executor names
are shapes used to keep the fixture scenarios readable.

| Executor shape | Intended question for a future Android run | P05-001 fixture coverage | Selection status |
| --- | --- | --- | --- |
| In-app foreground | What remains possible while the user-visible app is active? | API31/API36 start, checkpoint, background/screen-off, restart/re-discovery | Candidate for short interactive work only |
| WorkManager | How do persistent, deferrable/retryable jobs interact with durable state? | `NOT_APPLICABLE`; no AndroidX dependency in raw APK | Requires a separately authorized dependency harness |
| UIDT | How do explicit user data transfers stop/restart across supported APIs? | API36 Pixel start/checkpoint/complete; API<34 `NOT_APPLICABLE` | Candidate for supported explicit user transfers, subject to policy |
| FGS `dataSync` | How do permitted transfer service types and time limits affect long work? | API31/API36 start/checkpoint/complete and notification-backed service | Candidate fallback; duration limits remain unmeasured |
| FGS `mediaProcessing` | How do conversion time limits affect long work? | `NOT_TESTED`; no media-processing workload/service declared | Gate remains open |
| JobScheduler-backed path | What quota/constraint behavior is relevant to a future mapping? | API31/API36 schedule/start/checkpoint/complete | Candidate for deferred work; quota stress not measured |

## Required future measurements

A real Android campaign must measure, at minimum, start conditions, stop reason,
durable checkpoint timing, process-death recovery, notification/control behavior,
quota or time-limit interaction, memory, and provider error translation across
the intended API range. This continuation measures the first four executor
classes with a disposable raw APK; WorkManager, media-processing FGS, quota
stress, and provider translation remain separate gates.

P05-001 intentionally does not select one executor. Its host oracle remains
executor-independent, while the runtime evidence supports a workload/API router
shape rather than a universal mechanism.
