# Background and Long-Running Operations Research

Status: COMPLETE — conceptual operation architecture only  
Last verified: 2026-09-17  
Critical compatibility range: Android 12 / API 31 through Android 16 / API 36

## Classification legend

**FACT** documented behavior; **INFERENCE** derived consequence; **RECOMMENDATION** proposed direction; **UNRESOLVED** requires prototype/device evidence.

## Executive finding

A file manager cannot choose one Android background primitive for every long-running task. Android 12–16 imposes different rules on foreground services, WorkManager/JobScheduler and user-initiated transfer jobs. The durable **Operation Manager state must be independent from whichever execution mechanism is currently running it**.

That architectural separation is the strongest recommendation in this research pack.

## Android 12 baseline

**FACT:** Starting with Android 12, apps targeting API 31+ are generally restricted from starting foreground services while in the background except for documented exceptions.

Source:
- https://developer.android.com/develop/background-work/services/fgs/changes

**Implication:** An operation that the user starts while visibly interacting with the app should acquire the appropriate execution mechanism immediately rather than assuming the app can start a foreground service later from an arbitrary background state.

## Android 14+ foreground-service types

**FACT:** Apps targeting Android 14+ must declare appropriate foreground-service types and type-specific permissions. Google Play also requires applicable foreground-service declarations in Play Console.

Sources:
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/declare

**RECOMMENDATION:** Future implementation should use purpose-built APIs where Android documents them, and use FGS only for work that matches a permitted service type and user-visible execution model.

## Android 15 time limits

**FACT:** Android 15 introduced time limits for `dataSync` and `mediaProcessing` foreground-service types. `mediaProcessing` is explicitly intended for time-consuming media conversion and is normally limited to a total of six hours in a 24-hour period; comparable limits apply to dataSync under the documented conditions.

Sources:
- https://developer.android.com/about/versions/15/changes/foreground-service-types
- https://developer.android.com/develop/background-work/services/fgs/timeout

**INFERENCE:** “Put every 100 GB copy/transcode in a foreground service forever” is not a valid architecture.

## Android 16 job quota changes

**FACT:** Android 16 changes job accounting so jobs executing while a foreground service is active can still consume JobScheduler runtime quota. WorkManager work that maps onto JobScheduler is affected by this platform behavior.

Source:
- https://developer.android.com/about/versions/16/behavior-changes-all

**FACT:** Android's long-running Worker documentation warns that on Android 16, long-running workers can exhaust job quota and recommends evaluating direct foreground services or User-Initiated Data Transfer jobs according to the use case.

Source:
- https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running

## User-Initiated Data Transfer (UIDT) jobs

**FACT:** UIDT jobs are available from Android 14 / API 34 for user-requested data transfer. They are designed for extended user-initiated uploads/downloads, require user-visible notification behavior, and are not governed like ordinary background quota in the same way. They can still stop for constraints/system health/thermal conditions.

**FACT:** If the user stops a UIDT job from the system surface, Android may terminate the app process without delivering a convenient final callback. Durable state therefore must already be persisted.

Source:
- https://developer.android.com/develop/background-work/background-tasks/uidt

**RECOMMENDATION:** UIDT is the leading mechanism for explicitly user-initiated network download/upload on API 34+, with a compatible fallback strategy for Android 12–13.

## WorkManager

**FACT:** WorkManager is designed for persistent work that should survive app exits/restarts and supports constraints, retries, chaining and observable state.

Sources:
- https://developer.android.com/develop/background-work/background-tasks/persistent
- https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/states

**RECOMMENDATION:** Use WorkManager where work is deferrable/retryable and fits platform constraints. Do not equate “persistent” with “unlimited execution time,” particularly on Android 16.

## JobScheduler

JobScheduler is the underlying platform scheduler for many deferrable workloads and exposes constraints/quota behavior. A direct JobScheduler implementation is not automatically superior to WorkManager; choose it only if future requirements need a platform feature WorkManager cannot express.

Source:
- https://developer.android.com/reference/android/app/job/JobScheduler

## Workload mapping — preliminary, not frozen

| Workload | Likely mechanism | Why / caveat |
|---|---|---|
| 100 GB cloud/NAS download | UIDT on API 34+ | explicit user transfer; resumable state mandatory |
| 100 GB cloud/NAS upload | UIDT on API 34+ | same; provider resumable sessions preferred |
| Google Drive upload | UIDT + Drive resumable upload | Android execution + provider checkpoint both required |
| short metadata sync/index refresh | WorkManager | deferrable/retryable |
| archive extraction | visible in-app/appropriate FGS strategy; persist Operation Manager | local processing is not automatically a UIDT network transfer |
| archive creation | same | can exceed ordinary task lifetime |
| video transcode | `mediaProcessing` FGS candidate | intended use, but time limit applies |
| FLAC batch conversion | media-processing strategy | codec workload can exceed limits; checkpoint/restart design required |
| 100 GB local copy | **prototype/policy unresolved** | local user-visible transfer lacks a perfect one-size Android primitive |
| delete/mkdir/rename | usually foreground app action; persist if batch/long | avoid scheduling overhead for trivial operations |

**UNRESOLVED:** Exact Android mechanism for extremely long local-only copies across Android 12–16 needs a prototype and current policy review. `dataSync` can describe local file processing on some platform docs, but time limits and background-start rules mean a durable resumable operation is still necessary.

## Conceptual Operation Manager

The Operation Manager should exist above Android execution mechanisms and provider details.

### Operation types

- Copy
- Move
- Delete
- Download
- Upload
- Extract
- Compress
- Convert

Potential future types may be added without changing the core lifecycle model.

### Durable operation state

Suggested conceptual states:

```text
QUEUED
PREPARING
RUNNING
PAUSING
PAUSED
RETRY_WAIT
FINALIZING
COMPLETED
FAILED
CANCELLED
NEEDS_ATTENTION
```

These names are recommendations, not frozen API names.

Persist at minimum:
- operation ID/type;
- source(s) and destination provider identities;
- source version/size snapshot where available;
- progress/checkpoints;
- provider resumable-session token reference where needed;
- partial destination identity;
- conflict policy;
- retry count/last structured error;
- timestamps;
- finalization status.

Never persist live FDs, sockets, `MediaCodec` objects, shell process objects or Android Service instances as operation state.

## Progress

### Byte progress

Preferred for copy/upload/download when total size known:
- bytes completed / total;
- current item;
- aggregate item count;
- smoothed throughput;
- optional ETA shown only when stable enough.

### Indeterminate work

Archive scanning, cloud server-side copy or codec preparation can lack meaningful byte progress. Report stage plus indeterminate state rather than fake percentages.

### Persistence cadence

Checkpoint often enough to tolerate sudden process death, but not on every tiny buffer write. The correct interval depends on operation type and backing store.

**PROTOTYPE REQUIRED:** checkpoint write amplification vs maximum lost progress.

## Pause/resume

Pause is only truly supported when the underlying operation can stop at a safe checkpoint.

- stream copy: close at committed offset, reopen source/destination and validate versions;
- cloud upload: provider resumable session;
- archive extraction: often resume at entry boundary rather than arbitrary compressed byte;
- transcode: generally not arbitrary byte-resumable; may restart current output/segment.

**RECOMMENDATION:** Capability-model pause/resume per operation. Never show a Pause control that is implemented as unsafe process suspension.

## Cancellation

Cancellation should:
1. request cooperative stop;
2. close handles/codecs/network calls;
3. preserve or delete partials according to user/policy;
4. mark durable state;
5. never delete a completed original source during unfinished “move”.

For UIDT, assume the process can be terminated externally; cleanup must be recoverable next launch.

## Process death / crash recovery

On startup/reconnection:

1. find operations left RUNNING/PREPARING/FINALIZING;
2. query source/destination reality;
3. validate source version and partial output;
4. classify resumable / restart-required / conflict / already-completed;
5. never blindly replay destructive action.

**RECOMMENDATION:** Recovery is reconciliation, not “run the last method again.”

## Retry

Retry transient failures:
- network temporary unavailable;
- server 5xx/throttle where safe;
- recoverable provider disconnect;
- timeout after state reconciliation.

Do not automatically retry:
- authentication denied until user resolves;
- TLS/host-key failure;
- permission revoked;
- source content changed;
- storage full without new space;
- user cancellation;
- corrupt archive/media that deterministically fails.

## Partial files and finalization

Ideal write plan:
1. create provider-appropriate temporary/partial destination;
2. write/flush;
3. verify expected length and optional hash/version when practical;
4. finalize by atomic rename where guaranteed, otherwise provider-native commit/replace;
5. only then mark operation complete.

**INFERENCE:** Since not every provider offers atomic rename, Operation Manager must represent non-atomic finalization and recover ambiguous states.

## Move semantics

Same-provider move may be native/atomic. Cross-provider move should be:

`copy -> verify/finalize destination -> delete source`

Source deletion is the last irreversible step.

## Storage-full handling

Preflight space where meaningful, but do not trust free-space estimates as guarantees because:
- other apps can consume space;
- cloud quotas can change;
- sparse/compressed sizes differ;
- archive expanded size can be unknown/malicious.

On ENOSPC/quota failure:
- stop cleanly;
- preserve safe partial state where useful;
- keep original source;
- expose required user action.

## Notifications

Long user-visible work should have meaningful notifications according to the active Android execution mechanism. Avoid notification spam by grouping/aggregating operations while still making cancellation/control discoverable.

## Doze/battery restrictions

Doze/App Standby and vendor battery policy can delay/stop ordinary background work. An operation that must continue should use the mechanism appropriate to its user-visible purpose and still remain resumable after forced stop.

References:
- https://developer.android.com/training/monitoring-device-state/doze-standby
- https://developer.android.com/develop/background-work/background-tasks

## Media conversion constraints

`mediaProcessing` foreground service is a natural Android 15+ category for time-consuming conversions, but its time budget means a queue of enormous transcodes cannot assume continuous unlimited execution.

**RECOMMENDATION:** Make conversion outputs transactional and recoverable; for engines that cannot resume mid-file, restart the current file rather than the entire batch.

## Test scenarios required

- kill app process during every major operation stage;
- system-stop UIDT;
- force-stop vs ordinary process death semantics;
- reboot with queued/partial operations;
- storage removed during local copy;
- network transition during transfer;
- cloud session expiration;
- storage full at 1%, 50%, 99% completion;
- destination conflict appears during transfer;
- Android 12, 13, 14, 15, 16 execution behavior;
- Android 15/16 timeout/quota behavior;
- 100 GB transfer benchmark on real hardware.

## Recommendation summary

**Strong:** durable Operation Manager is separate from WorkManager/Service/UIDT.  
**Strong:** UIDT first for user-requested network transfers on API 34+.  
**Strong:** Media-processing FGS is a candidate for conversion, respecting limits.  
**Strong:** operation recovery is state reconciliation with partial/finalization semantics.  
**Postpone:** exact scheduler/service split and local-copy execution mechanism until prototypes and final targetSdk policy are reviewed.
