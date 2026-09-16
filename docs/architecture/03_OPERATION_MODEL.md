# Architecture V1 — Durable Operation Model

Status: PRINCIPLE FROZEN / EXECUTOR MAPPING UNFROZEN
Source authority: research SHA `b03a2ea99f24206f847f513fa4106e90268f3fc4`

## Architecture decision

A durable, executor-independent Operation Manager is `ACCEPTED`.

WorkManager, UIDT, foreground services, foreground coroutines, JobScheduler-backed work, and similar Android mechanisms are executors. They are not the durable source of truth for a user-visible file operation.

Exact executor mapping per workload/API level is `POC_REQUIRED` or `DEFERRED` and is not selected in this phase.

## Operation families

The architecture must be able to represent future long-running work such as:

- Copy
- Move
- Delete
- Rename
- Download
- Upload
- Extract
- Compress
- Convert

This list describes product/workflow categories, not enum names or a V1 implementation promise.

## Durable conceptual states

The operation model must be able to distinguish states equivalent to:

- planned;
- waiting;
- running;
- paused;
- interrupted;
- retryable failure;
- terminal failure;
- verifying;
- finalizing;
- complete;
- cancelled.

Exact state names, enum layout, transition representation, and persistence schema are `DEFERRED` implementation details.

## Safe output lifecycle — `ACCEPTED`

The default cross-provider mutation pattern is:

```text
create partial destination
        ↓
transfer / process
        ↓
verify
        ↓
finalize destination
        ↓
delete source only for move
```

A provider-native same-provider operation may use a stronger primitive when that provider explicitly exposes and validates it. The generic architecture must not infer atomicity from a method name.

## Durable state contents

Durable operation state may need to preserve conceptually:

- operation intent/type;
- source provider identity + opaque entry identity;
- destination provider identity + parent identity + intended name;
- source/version snapshot using the strongest available token;
- partial destination identity;
- bytes/chunks completed;
- resumable session/provider token where safe;
- conflict/retry state;
- verification/finalization stage;
- user-visible progress metadata;
- cancellation/pause request state;
- enough information to reconcile after process death.

The following are `REJECTED` as durable recovery state:

- open file descriptors;
- sockets;
- root shell processes;
- transient signed URLs used as identity;
- in-memory provider/session objects;
- coroutine/Worker/Service instances.

## Process death and app restart — `ACCEPTED`

Recovery is **reconciliation**, not blind replay.

After restart, an interrupted operation must be able to:

1. find durable non-terminal operations;
2. re-resolve source and destination through providers;
3. inspect partial/final destination reality;
4. validate source/version/checkpoint assumptions;
5. classify the operation as resumable, restart-required, conflicted, already-completed, cancelled, or terminally failed;
6. resume only from a safe boundary;
7. never blindly repeat a destructive step.

## Network loss/provider disconnect — `ACCEPTED`

Network loss, removable-media disconnect, SAF grant revocation, cloud authentication expiry, root-service loss, and provider/session failure are expected operation interruptions.

The architecture must preserve enough state to determine whether retry is safe. Reconnecting to the same hostname/account label does not prove the same object version is present.

## Storage-full behavior — `ACCEPTED`

A full destination must not cause source loss. Partial output may be preserved for recovery or cleaned according to explicit policy, but the operation is not complete until destination finalization succeeds.

## Cancellation — `ACCEPTED`

Cancellation is cooperative and may occur after partial side effects. It means:

- request stop;
- close/cancel active I/O/codec/network work where possible;
- preserve or remove partial output according to recoverability/user policy;
- persist the resulting state;
- never perform move-source deletion for an unfinished destination.

Cancellation is not equivalent to rollback unless a provider specifically supplies a safe rollback primitive.

## Pause/resume — `ACCEPTED` as capability-dependent

Pause/resume must only be offered where the operation can persist a safe checkpoint and later reopen/revalidate state. Unsafe process suspension or UI-only pause semantics are `REJECTED`.

Per-provider/per-operation resume support is capability-dependent.

## Retry — `ACCEPTED` with classification

Retry requires error classification and idempotency/reconciliation rules. Permission denial, trust failures, source version change, quota/storage-full, malformed input, and authentication problems must not all become an undifferentiated retry loop.

Fixed blind retry for every failure — `REJECTED`.

## Conflicts — `ACCEPTED` as an explicit state

Conflicts may include destination existence, source changes after checkpoint, remote version mismatch, ambiguous non-atomic finalization, and provider-native conflict responses.

The architecture must retain conflict state rather than silently overwriting or restarting when correctness is uncertain.

Exact UX/default conflict policy is `DEFERRED` until feature/UX design.

## Partial-output cleanup — `ACCEPTED` as policy-driven

Partial destinations are neither generic cache nor ordinary final user files. They belong to operation recovery state.

A generic “clear cache” action must not destroy operation-recovery data — `ACCEPTED`.

Cleanup timing/retention UX is `DEFERRED`.

## Resume checkpoints — `ACCEPTED` conceptually

When a provider/engine supports safe resume, checkpoints may include offsets, chunk/session tokens, source versions, destination versions, and current processing unit.

Checkpoint cadence is `POC_REQUIRED` because write amplification, provider behavior, and acceptable lost-progress windows require measurement.

## Move semantics — `ACCEPTED`

### Same-provider native move

Use only if advertised with sufficient guarantees. Atomicity is never inferred.

### Cross-provider move

Cross-provider move is a copy/finalize/delete plan. Source deletion occurs only after destination verification/finalization succeeds.

### Ambiguous finalization

Where finalization is not atomic, reconciliation must represent and inspect ambiguous state rather than assuming success or deleting the source.

## Verification — `ACCEPTED`

Verification is provider/workload dependent and may include:

- expected length;
- provider commit acknowledgement;
- strongest available version token;
- checksum/hash where practical and semantically useful;
- parser/container close/finalization success;
- destination re-stat/reopen.

A universal full-file hash requirement is not frozen; it may be too expensive or unavailable for some providers.

## Conversion/extraction/compression integration — `ACCEPTED`

These workloads participate in the same partial -> verify -> finalize semantics as copy/upload. Engines that cannot resume mid-file may safely restart the current unit while retaining batch-level durable progress.

## Progress semantics — `ACCEPTED`

Progress must support:

- 64-bit-capable byte counts;
- item counts;
- determinate and indeterminate stages;
- multi-stage operations where verification/finalization remains after byte transfer reaches 100%;
- provider/engine phases that do not have a reliable total.

The UI must not report “complete” merely because transfer bytes reached the expected length before verification/finalization.

## Executor boundary

### Accepted principle

Operation truth is independent from Android executor lifetime — `ACCEPTED`.

### Current evidence-based directions, not freezes

- UIDT is a leading mechanism for explicit user-initiated network transfer on API 34+.
- WorkManager fits some deferrable/retryable workloads.
- foreground-service types may fit specific allowed workloads and are constrained by Android/Play policy.
- media-processing FGS may fit conversion under documented limits.
- foreground in-app execution may fit short/interactive work.

These are not selected mappings.

### Unresolved executor decisions

- extremely long local-only copy across API 31–36 — `POC_REQUIRED`;
- UIDT stop/process-death behavior in real workloads — `POC_REQUIRED`;
- Android 15/16 timeout/quota interactions under very large operations — `POC_REQUIRED`;
- fallback mapping for API 31–33 user-initiated transfers — `DEFERRED` until executor architecture review.

## Required failure/recovery scenarios

Future implementation acceptance must include at least:

- process death at every irreversible boundary;
- app restart during active operation;
- source/destination provider disappearance;
- SD/USB disconnect;
- network interruption and reconnect;
- authentication expiry;
- storage/quota full;
- source mutation/version conflict;
- cancellation during transfer, verification, and finalization;
- partial output with stale/missing checkpoint;
- already-completed destination after lost final acknowledgement;
- retryable vs terminal failure classification.

The exact test frameworks are `DEFERRED`; the scenarios themselves are Architecture V1 requirements.
