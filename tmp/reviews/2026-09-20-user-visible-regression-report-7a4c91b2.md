# User-visible regression review — VS03 → VS04

## Scope

- Baseline: published VS03 `a95b3e28f47954b879b389ce6d5a5f17d4704407`
- Target: VS04 worktree `development/core-v1-saf-transfer-v1`
- Scope: Local browsing/selection/mutation, Local→Local Copy/Move, SAF browsing/tree grants, destination picker, Copy/Move action gating, operations presentation, and new SAF route paths.
- Evidence: current diff, architecture/VS04 requirements, 61 host tests, Android-test compilation, final lint/build; no Android device attached.
- Completion: incomplete for runtime-only SAF journeys.

## Gate Snapshot

- **Recommendation:** `Discuss`
- **Completion:** `Incomplete — no connected Pixel/emulator; SAF runtime and DocumentsUI flows are unexecuted`
- **Why now:** Local behavior is host-verified, but SAF provider/user-visible paths cannot be declared regression-free from compilation alone.
- **Must-review now:**
  1. `F1` SAF destination/finalization policy is intentionally changed to remain disabled until provider proof.
  2. `F2` SAF runtime/error/restart journeys have no device evidence.
- **Findings count:** Block `0` | Discuss `1` | Watch `0` | Intentional `1`
- **Coverage confidence:** medium for Local; low/unknown for physical SAF.
- **Behavior graph coverage:** static graphs for Local selection→operation and SAF picker→provider registration; runtime graph not exercised.
- **Biggest blind spot:** real DocumentsUI persisted-grant, provider disappearance, revocation, and finalization behavior.

## Complete Findings Index

| ID | Action | Surface | User-visible risk | Confidence |
|---|---|---|---|---|
| `F1` | `Discuss` | SAF route runtime journeys | SAF runtime errors, retry/restart, and actual finalization identity behavior are not verified on a device. | Medium |

## Block

None.

## Discuss

### F1 Discuss — SAF runtime journeys remain unverified

User impact: Users may encounter provider-specific permission, non-seekable I/O, finalization, or restart behavior that is not represented by the host result; the implementation deliberately avoids claiming those routes until evidence exists.

Review reason: SAF is an external provider boundary. Passing host tests and compiling instrumentation does not establish DocumentsUI grants, provider flags, pipe closure, returned URI identity, or revocation behavior.

Surface: SAF browsing/tree grant flow, Local→SAF, SAF→Local, SAF→SAF, destination picker, Operations error/retry states.

Look here first:

- [`SafStorageProvider.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/storage/SafStorageProvider.kt#L22)
- [`SafTransferRuntimeInstrumentedTest.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/androidTest/java/com/omnifile/operations/SafTransferRuntimeInstrumentedTest.kt#L1)

Behavior delta:

- Before: VS03 did not advertise SAF durable transfer.
- After: SAF transfer code and UI integration exist, but production SAF destination finalization is conservatively disabled by default and SAF runtime support is not claimed.

Evidence:

- `:app:testDebugUnitTest` passed with 61 tests.
- `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest` passed.
- `adb devices -l` returned no attached devices, so controlled and real SAF flows did not execute.

Reviewer action:

Discuss until a connected device executes the controlled-provider and disposable real-SAF matrices, or keep the route matrix explicitly unsupported as documented.

## Watch

None.

## Intentional Changes

### I1 — Conservative SAF destination gating

- Production `SafStorageProvider` defaults `finalizationProven` to `false`.
- A writable grant alone does not enable SAF destination Copy/Move.
- This is intentional safety behavior required by the VS04 brief, not a VS03 Local regression.

## Coverage Ledger

| Area | Status | Evidence |
|---|---|---|
| Local browsing/selection | Reviewed — no user-visible regression found | Existing host FilesViewModel tests pass. |
| Rename/Delete | Reviewed — no user-visible regression found | Existing host tests pass; Local provider behavior unchanged except explicit directory WRITE capability. |
| Local→Local Copy/Move | Reviewed — no user-visible regression found | Existing OperationManager host tests pass. |
| Local destination picker | Reviewed — no user-visible regression found | Existing FilesViewModel picker tests pass; root loader self-cancellation was repaired. |
| SAF browsing/tree grant flow | Not covered at runtime | No device; only compile/static review. |
| SAF action gating | Discuss F1 | Static gating reviewed; runtime UI not executed. |
| SAF destination picker | Discuss F1 | Static destination create/write/finalization gate reviewed; no device. |
| Operations unknown-size/error presentation | Reviewed statically | UI now shows `size unknown`; Compose runtime not executed. |
| Back/cancel/conflict/permission/provider switching | Not covered for SAF | No device/provider runtime. |

## Evidence Appendix

### Local behavior graph delta

```text
Local source selection
  -> FilesViewModel selection
  -> Copy/Move action
  -> destination picker
  -> OperationManager enqueue
  -> existing Local provider
  -> durable Operations row
```

Static delta: destination provider registration and explicit directory WRITE capability were added; host tests covering the Local path remain green.

### SAF behavior graph delta

```text
ACTION_OPEN_DOCUMENT_TREE
  -> tree URI/result flag validation
  -> persisted grant registry
  -> SAF provider registration
  -> capability-gated browse/selection
  -> destination picker or durable engine
  -> provider create/pipe write/verify/rename
  -> Operations state and source-delete ordering
```

Runtime delta: all nodes after platform/provider interaction remain unexecuted because no device is attached.

### Verification

- Host: 61 passed, 0 failed, 0 errors.
- Lint: passed with 22 warnings, 0 errors.
- Debug APK SHA-256: `ff7acfe5f85111cfbf52a5859344441b10fe9468ea494193dc0294a316f9f109`.
- Android-test APK SHA-256: `f3a2d789e0587298fe79dffe836773d35663eccd2f9906b758575208c2602007`.
- Device: none attached.

### Dismissed candidates

| Candidate | Decision | Evidence |
|---|---|---|
| Local operation regression from new destination WRITE capability | Dismissed | Existing Local Copy/Move tests pass. |
| Destination-root loader cancellation | Dismissed | Loader now performs root and child listing in one owned job; host compilation/tests pass. |

## Next action

Attach a Pixel/emulator, run the controlled provider and real disposable SAF flows, then re-run this regression review. Until then, keep production SAF routes unclaimed as documented.
