# P05-001 — Executor Matrix

**Label:** `POC-ONLY — NOT PRODUCTION AUTHORITY`

This is a comparison scaffold, not a technology selection. The executor names
are shapes used to keep the fixture scenarios readable.

| Executor shape | Intended question for a future Android run | P05-001 fixture coverage | Selection status |
| --- | --- | --- | --- |
| In-app foreground | What remains possible while the user-visible app is active? | Interruption and recovery classification only | Not selected |
| WorkManager | How do persistent, deferrable/retryable jobs interact with durable state? | Interruption and recovery classification only | Not selected |
| UIDT | How do explicit user data transfers stop/restart across supported APIs? | External-stop label and recovery classification only | Not selected |
| FGS `dataSync` | How do permitted transfer service types and time limits affect long work? | API 31-shaped interruption and recovery classification only | Not selected |
| FGS `mediaProcessing` | How do conversion time limits affect long work? | API 35-shaped interruption and recovery classification only | Not selected |
| JobScheduler-backed path | What quota/constraint behavior is relevant to a future mapping? | `NOT_TESTED`; no JobScheduler fixture exists | Not selected |

## Required future measurements

A real Android campaign must measure, at minimum, start conditions, stop reason,
durable checkpoint timing, process-death recovery, notification/control behavior,
quota or time-limit interaction, memory, and provider error translation across
the intended API range. It must use a disposable Android project and generated
fixtures, with a permitted runtime target.

P05-001 intentionally does not provide those measurements. Its host oracle covers
the six named fixture shapes only; JobScheduler remains an explicit untested
candidate and no executor is selected.
