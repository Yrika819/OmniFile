# User-Visible Regression Review

## Scope

- Baseline: published VS03 `a95b3e28f47954b879b389ce6d5a5f17d4704407`
- Target: current VS04 working tree on `development/core-v1-saf-transfer-v1`
- Comparison: VS03 → final VS04 implementation/evidence delta
- Environment: Pixel 7a, Android 16/API 36; final direct runner 29/29
- Coverage mode: full scoped user-visible audit with runtime evidence for normal SAF Copy and unsupported gates
- Out of scope: VS05, directories, archives, background execution, cloud/root providers, unrelated redesign

## Gate Snapshot

- Recommendation: `Discuss`
- Completion: `Complete within reviewed scope; provider-loss/equal-depth-grant paths remain uncovered`
- Why now: `Normal Local and supported SAF→Local Copy journeys are green, but two interruption/restoration paths can still present misleading state.`
- Must-review now:
  1. `F1` Provider disappearance/revocation can look like stale/not-found
  2. `F2` Equal-depth persisted tree grants have no last-selected source discriminator
- Findings count: `Block 0 | Discuss 2 | Watch 0 | Intentional 3`
- Coverage confidence: `medium`
- Behavior graph coverage: `Direct traces for Local, SAF source, picker, Copy/Move gates, Operations, Back, conflict, and unsupported destination; runtime error-injection paths not reached.`
- Biggest blind spot: `Real provider disappearance/revocation and multiple unrelated persisted trees.`

## Complete Findings Index

| ID | Action | Surface | User-visible outcome | Confidence |
| --- | --- | --- | --- | --- |
| `F1` | `Discuss` | SAF browse/operation interruption | Provider loss can render as stale/not-found instead of recoverable provider/permission state. | High |
| `F2` | `Discuss` | SAF restart/source restoration | An equal-depth unrelated persisted tree can become the active browse source after restart. | Medium |

## Block

None.

## Discuss

### F1 Discuss - Provider loss can look like stale identity

User impact: `If the DocumentsProvider disappears or access is revoked outside the normal picker flow, the user may see “source or destination is no longer available” rather than a recoverable provider/permission interruption.`

Why this is a regression concern: `VS04 adds persisted SAF state and expected interruption handling; collapsing provider availability loss into stale/not-found weakens that user-visible recovery contract.`

Surface: `SAF browse, SAF→Local Copy interruption, Operations error state`

Evidence:

- `SafStorageProvider.isWithinSelectedTree()` catches non-security exceptions as false.
- Locator paths then classify the entry as stale, which maps to NOT_FOUND/stale UI text.
- Final Pixel verified grant acquisition and normal Copy, but did not inject provider disappearance/revocation.

Expected vs after:

- Before/contract: `Permission/provider loss is distinct from stale identity and remains retryable/recoverable without source deletion.`
- After: `SecurityException is permission-classified, but other provider failures can become stale/not-found.`

Reviewer action: `Discuss and require a typed provider-unavailable mapping or deterministic evidence proving the current classification is intentional.`

### F2 Discuss - Restart source selection is ambiguous for equal-depth grants

User impact: `After restart with multiple unrelated readable SAF trees at equal document-depth, OmniFile may reopen a different tree from the one the user last selected.`

Why this is a regression concern: `VS04 persists multiple tree grants; restoration must preserve selected-tree intent while still preferring a broad root over a child destination.`

Surface: `SAF source restoration after Activity/process recreation`

Evidence:

- `restoredReadTree()` chooses the minimum tree document depth.
- The final Pixel root-versus-child case correctly restored `OmniFile-SAF-Test` over `destination`.
- No selected-tree marker or equal-depth tie-breaker is persisted.

Expected vs after:

- Before/contract: `The last selected readable source remains the active source after restart.`
- After: `Equal-depth ties depend on platform persisted permission ordering.`

Reviewer action: `Discuss and add a selected-tree discriminator or deterministic equal-depth rule plus restart coverage.`

## Watch

None.

## Intentional Changes

### I1 - Conservative SAF destination support

- `SAF destination Use this folder` remains disabled while `finalizationProven=false`.
- This is intentional safety gating, not a regression; no SAF destination route is advertised.

### I2 - SAF Move capability gating

- SAF source Move is disabled while `sourceVersionProven=false`.
- SAF→Local Copy remains available for readable regular files; Move requires the additional `MOVE_SOURCE` proof.

### I3 - Supported SAF→Local Copy

- Normal production flow now supports sequential SAF source→Local Copy for the verified provider/grant path.
- Final Pixel evidence: source retained, Local output visible at 41 B, Operations `COMPLETE · 41 B / 41 B`.

## Coverage Ledger

| Surface | Classification | Evidence | Result |
| --- | --- | --- | --- |
| Local browse | Reviewed - no regression found | Full 29/29 instrumentation; final Local `files` browse | Pass |
| Local selection/rename/delete | Reviewed - no regression found | Full Compose instrumentation | Pass |
| Local→Local Copy/Move | Reviewed - no regression found | VS03 preserved; full instrumentation | Pass |
| Local picker/destination | Reviewed - no regression found | Existing UI tests and code trace | Pass |
| SAF tree grant flow | Reviewed with gap | Pixel normal DocumentsUI flow; persisted `mode=0x3/persisted=0x3` | Pass for acquisition; F1 gap for revocation |
| SAF browsing/source | Reviewed with gap | Pixel source tree browse and restart root restoration | F2 gap for equal-depth grants |
| SAF→Local Copy | Intentional supported route | Final Pixel 41 B COMPLETE, source retained | Pass |
| SAF→Local Move | Intentional unsupported route | Move disabled without source version proof | Pass |
| Local→SAF | Intentional unsupported route | SAF Use this folder disabled without finalization proof | Pass |
| SAF→SAF | Intentional unsupported route | Destination gate remains disabled | Pass |
| destination conflict | Reviewed - no regression found | Final UI conflict `CONFLICTED · 0 B / 41 B`; existing destination preserved | Pass |
| cancellation/restart | Reviewed with controlled coverage | Controlled provider 18/18; no real fault injection | Pass with evidence gap |
| Operations UI | Reviewed - no regression found for exercised states | COMPLETE and CONFLICTED final UI | Pass; provider-error rendering gap remains |
| Android Back | Reviewed - no regression found | Full Compose/UI suite and code trace | Pass |
| permission error/provider switching | Not covered for provider disappearance | No final real fault injection | Discuss via F1 |

## Evidence Appendix

### Runtime evidence

- Host: `63 passed, 0 failed, 0 errors, 0 skipped`
- Controlled provider direct runner: `18/18 OK`
- Full instrumentation direct runner: `29/29 OK`
- Final Pixel: Android 16/API 36, `ExternalStorageProvider`, persisted tree grants `mode=0x3`, `persisted=0x3`
- Final supported route: SAF→Local Copy `41 B COMPLETE`, source retained, Local output present
- Final unsupported gates: Move disabled; SAF destination `Use this folder` disabled

### Behavior graph deltas

```text
SAF picker -> persisted grant -> restored tree -> source selection -> Copy -> Local partial -> verify -> Local final -> COMPLETE
                    |                                      |
                    |                                      +-> Move gate requires MOVE_SOURCE (false in production)
                    +-> provider loss / equal-depth restore remain Discuss paths
```

### Dismissed candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| `SAF destination disappearing from the picker is a regression` | `Intentional` | `finalizationProven=false intentionally prevents unsafe destination acceptance` |
| `SAF Move being disabled is a regression` | `Intentional` | `sourceVersionProven=false intentionally prevents destructive Move` |
| `SAF→Local Copy source deletion` | `Dismissed` | `Copy retains source in final Pixel run and code path does not call source delete` |

### Validation commands

- `adb devices -l` -> Pixel 7a/API 36 `device`
- `:app:testDebugUnitTest` -> `63 passed`
- `:app:lintDebug` -> `PASS; 22 warnings; 0 errors`
- `:app:assembleDebug` -> `PASS`
- `:app:assembleDebugAndroidTest` -> `PASS`
- direct AndroidJUnitRunner controlled/full -> `18/18`, `29/29`

## Review conclusion

The VS03 Local journeys did not regress, and the supported SAF→Local Copy journey is runtime-green. The review cannot be `Pass` while provider-loss classification and equal-depth persisted-tree selection remain unproven; neither gap justifies enabling SAF Move or SAF destination finalization.
