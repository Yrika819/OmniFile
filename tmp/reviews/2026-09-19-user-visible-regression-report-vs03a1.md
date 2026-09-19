# VS02 -> VS03 User-Visible Regression Review

## Scope

- Baseline: `14732fd8f120b93380e624143e534cceff6d09c4` (published VS02)
- Target: `93608f9` (`development/core-v1-durable-copy-move-v1`)
- Scope: final VS02-to-current VS03 working/committed diff, user-visible Files selection/mutation/navigation and new Copy/Move/Operations surfaces.
- Method: static behavior-graph review plus strict main Kotlin compilation; no adb/device runtime and no completed Compose/connected test result.
- Completion: `Incomplete` for runtime-sensitive UI behavior.

## Gate Snapshot

- Recommendation: `Discuss`
- Completion: `Incomplete - physical/runtime UI coverage unavailable and test variants time out during KSP.`
- Why now: `VS03 adds destructive Copy/Move and a new destination/status journey; passing host compilation is not proof of user-visible parity.`
- Must-review now:
  1. `A5` `New Copy/Move/picker journey runtime coverage`
  2. `A6` `Operations terminal/history UI runtime coverage`
- Findings count: `Block 0 | Discuss 0 | Watch 0 | Intentional 4`
- Coverage confidence: `medium`
- Behavior graph coverage: `Static direct-path traces for Files actions, picker, operations panel, provider switching, selection, Back, and mutation refresh; runtime graph execution unavailable.`
- Biggest blind spot: `Actual Android Back, picker cancel/confirm, duplicate start suppression, IME, and terminal panel rendering.`

## Complete Findings Index

No confirmed user-visible regression identified by static review. Runtime-sensitive surfaces remain explicitly not covered in the ledger.

## Block

None.

## Discuss

None as a confirmed regression; incomplete runtime coverage is the gate reason.

## Watch

None.

## Intentional Changes

### I1 — Copy/Move action hierarchy

Regular selected files now expose Copy and Move; directories remain disabled. This matches the VS03 capability requirement and does not add Share or dead actions.

### I2 — Destination picker

Copy/Move enters a separate in-app destination-picker state, preserving source selection until the user confirms a destination. Back/cancel returns to source browsing.

### I3 — SAF transfer gating

Current SAF files do not advertise sequential transfer capability, so Copy/Move remains disabled rather than pretending that read-only SAF navigation supports durable writes/finalization.

### I4 — Operations panel

A bottom Operations panel shows recent durable operation rows, stage/state, byte progress, durable error text, and cancellation. It is inset with navigation-bar padding.

## Coverage Ledger

| Area | Status | Evidence / gap |
| --- | --- | --- |
| Normal Files browsing | Reviewed - no user-visible regression found | Existing Files state/navigation paths unchanged except new DestinationPicker branch. Static trace only. |
| Local source selection | Reviewed - no user-visible regression found | Existing selection state remains source-owned until destination confirmation. Static trace. |
| SAF source selection/provider switching | Intentional change | Transfer actions disabled for current unsupported SAF capability route; browse flow preserved. No runtime provider test. |
| Long-press/single/multi selection | Reviewed - no user-visible regression found | Existing selection callbacks retained; Copy/Move gates regular-file selections. No Compose execution. |
| Close selection / Android Back | Not covered | Existing Back callback remains; DestinationPicker adds separate Back route. Physical/emulator unavailable. |
| Rename/Delete | Reviewed - no user-visible regression found | Existing callbacks/dialogs preserved; no runtime result. |
| Copy/Move | Intentional change / Not covered runtime | New actions, explicit picker, durable enqueue path; no completed UI test. |
| Destination picker | Not covered | Static state path reviewed; cancel, directory navigation, confirmation, and source-selection restoration need Compose/runtime execution. |
| Conflict/error/cancellation UI | Not covered | Backend durable facts exist; no completed Operations UI runtime test. |
| Operations status/history | Intentional change / Not covered runtime | Recent rows are queried and rendered; no runtime proof of terminal refresh/update behavior. |
| Empty/loading/error states | Reviewed - no user-visible regression found | Existing Files branches retained; new destination error branch is static-only. |
| Provider switching | Reviewed - no user-visible regression found | SAF remains browse-only and route is truthfully gated. No device test. |
| Mutation refresh/selection reconciliation | Reviewed - no user-visible regression found | Source selection is cleared only after enqueue succeeds; refresh uses existing listing path. No runtime test. |
| Navigation after operations | Not covered | Current source folder refresh is requested after durable enqueue/execution; no runtime execution. |

## Evidence Appendix

### Behavior graph deltas

```text
Files selection
  -> Copy/Move action
  -> DestinationPicker state
  -> browse eligible directories
  -> confirm current directory
  -> durable enqueue
  -> foreground OperationManager
  -> Operations panel
```

The baseline had no Copy/Move branch. The new guards are regular-file capability gating and explicit destination confirmation. The missing evidence is execution of the after-change graph on Android.

### Verification

- `:app:compileDebugKotlin --dependency-verification=strict --no-daemon` -> passed.
- `:app:testDebugUnitTest` / Android test compilation attempts -> timed out during `kspDebugUnitTestKotlin` before producing test results.
- `adb` -> unavailable due local smartsocket permission failure.
- No Pixel/emulator claim is made.

### Dismissed regression leads

| Lead | Disposition | Evidence |
| --- | --- | --- |
| Copy/Move enabled for directories | Dismissed | UI gate requires `EntryKind.FILE` and sequential read capability. |
| SAF Copy/Move falsely exposed | Dismissed | Current SAF route lacks transfer capability and actions remain disabled. |
| Source selection lost on picker cancel | Dismissed statically | Pending transfer retains source entries and cancel reloads source location; runtime not executed. |

## Next verification required

1. Run focused Compose tests for Copy/Move gating, picker confirm/cancel/Back, and Operations panel semantics.
2. Run Room/engine tests and connected tests.
3. Execute emulator or Pixel runtime flow; retain `PHYSICAL_PIXEL_GATE_PENDING` if Pixel remains unavailable.
