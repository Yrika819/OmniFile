# POC-001 — Failures and Edge Cases

Status: RECORDED

## 1. Direct shared-storage filename restrictions

### Measured fact

On both API 31 and API 36 emulator shared storage, creation of the following fixtures failed with `java.io.FileNotFoundException` wrapping `EPERM (Operation not permitted)`:
- a filename containing `"`;
- a filename containing a newline.

Unicode, emoji, spaces, and the tested shell metacharacters succeeded.

### Inference

Filename admissibility is not equivalent to raw Linux filesystem admissibility. Android storage layers may reject names even when an underlying filesystem could theoretically encode them.

### Recommendation

Future production validation/sanitization should be provider/capability aware and should report rejected names explicitly rather than assuming a universal POSIX filename domain.

## 2. SAF picker / emulator UI instability

### Observed anomaly

On the API 36 Google APIs x86_64 AVD, starting normal `ACTION_OPEN_DOCUMENT_TREE` selection repeatedly surfaced ANR dialogs including:
- `Process system isn't responding`;
- `System UI isn't responding`;
- `Pixel Launcher isn't responding`.

A cold boot without snapshots did not make the picker path sufficiently reliable for evidence collection.

### Decision

No shell-grant or hidden URI-permission bypass was used. Such a bypass would not prove real user-selected SAF permission behavior.

### Result

The local user-selected SAF matrix, persisted-grant restart test, and persisted-grant revocation test remain `NOT_TESTED` for this run.

## 3. API 31 emulator host load

### Measured fact

After API 31 boot completion, the headless QEMU process was observed at approximately 99.9% CPU and ~2.6 GB RSS on the Intel Mac host. The host load average was elevated.

### Action

Only the already-booted direct-storage and synthetic-pipe measurement set was collected. The API 31 emulator was then stopped rather than extending unreliable UI-heavy testing.

### Result

This is an environment limitation, not evidence that Android 12 itself performs poorly.

## 4. Physical removable media

No SD card or USB OTG device was attached to the test environment. Unplug/unmount behavior, lazy descriptor failure, URI recovery, and reconnect behavior are `NOT_TESTED`.

## 5. Cloud-backed DocumentsProvider

No cloud-backed provider was available in the emulator. Provider-specific flags, non-seekable remote streams, offline behavior, auth expiry, and reconnect semantics are `NOT_TESTED`.

## 6. Direct permission revocation

The harness records the direct `MANAGE_EXTERNAL_STORAGE` special-access case as CONDITIONAL. A complete revoke-during-active-I/O and deterministic recovery sequence was not completed, so it is not promoted to PASS.

## 7. >4 GiB sparse-file interpretation

Both emulator environments accepted a 4,831,838,208-byte logical length. This proves 64-bit length handling for the tested direct path and sparse operation; it does not prove equivalent behavior for FAT variants, removable media, SAF providers, cloud providers, or real allocation of >4 GiB physical data.

## 8. Emulator performance numbers

Sequential throughput and microsecond-scale seek timings are useful only for harness sanity and comparative observations. Emulator caching and host storage dominate them. They must not become production performance budgets.