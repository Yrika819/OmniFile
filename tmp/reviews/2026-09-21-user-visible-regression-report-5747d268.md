# VS04 SAF Transfer Read-only Review

## Scope

- Baseline: `a95b3e28f47954b879b389ce6d5a5f17d4704407`
- Target: current worktree `~/Desktop/File Manager-worktrees/omnifile-saf-transfer-v1`, including `HEAD d6a8289` and unstaged implementation/test/documentation changes.
- Review mode: read-only; no source, staging, commit, push, or Git metadata changes.
- Focus: SAF containment, persisted grants, finalization ambiguity, operation-owned partials, source-version Move gating, retry/restart state, and route-matrix truthfulness.
- This is a scoped SAF/transfer review, not a general repository review.

## Gate Snapshot

- **Recommendation: Discuss**
- **Completion:** Complete for the requested SAF/transfer surfaces; remaining candidates are bounded to provider-error classification, partial cleanup API hardening, ambiguity retry entrypoints, and multi-grant source restoration.
- **Evidence:** Controlled SAF runner `18/18`; full direct runner `29/29`; final real Pixel `ExternalStorageProvider` SAF→Local Copy `41 B COMPLETE` with source retained; production defaults remain `finalizationProven=false` and `sourceVersionProven=false`; host tests `63` passed; lint/build passed.
- **Why now:** The supported production route is conservatively narrow and the happy path is proven, but static paths still leave user-visible recovery/security ambiguity outside that happy path.
- **Findings:** 2 Discuss, 2 Watch.
- **Coverage confidence:** Medium-high for the requested code paths; lower for provider revocation/disappearance because those failures were not physically exercised.

## Complete Findings Index

| ID | Action | Severity | Surface | Short conclusion |
|---|---|---|---|---|
| F1 | Discuss | Major | SAF containment/provider failure classification | Provider disappearance can collapse to stale/not-found and terminal failure rather than permission/retry classification. |
| F2 | Discuss | Moderate | Operation-owned partial cleanup API | Nullable operation IDs allow cleanup/write/finalize helpers to accept any operation-looking partial, contrary to exact ownership. |
| F3 | Watch | Major | Ambiguous finalization retry entrypoint | Restart reconciliation is guarded, but direct `execute()` can replay an ambiguous finalization when no final locator was persisted. |
| F4 | Watch | Minor | Multiple persisted SAF grants | Restoration chooses the shallowest readable tree, not the previously selected source, when unrelated grants coexist. |

## Block

None.

## Discuss

### F1 Major / Discuss - Provider disappearance can be misclassified as stale/not-found

User impact: A supported SAF→Local Copy interrupted by provider disappearance or a provider-side `IllegalStateException` can become a terminal failed/not-found operation instead of a permission/provider interruption that the user can retry or regrant.

Surface: SAF containment and durable operation error mapping.

Look here first:
- [`SafStorageProvider.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/storage/SafStorageProvider.kt#L622)
- [`OperationManager.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/operations/OperationManager.kt#L435)

Evidence and behavior delta:
- `isWithinSelectedTree()` preserves `SecurityException` as permission loss, but catches every other `Exception` and returns `false` at `SafStorageProvider.kt:630-631`.
- A provider-unavailable `IllegalStateException` therefore makes `decodeLocator()` return null (`SafStorageProvider.kt:595-599`), which callers report as `StaleReference` rather than `PermissionDenied`.
- `StorageError.StaleReference` maps to `NOT_FOUND` (`OperationManager.kt:435-439`) and the failure state falls through to terminal `FAILED` (`OperationManager.kt:401-410`), not retryable failure.
- Expected basis: VS04’s stated policy says missing/revoked grants are permission loss and must not be treated as source deletion or broad-storage authorization. The final document also records revocation/provider-error evidence as deferred.

Reviewer action: Discuss before approval of provider-error/revocation behavior. Preserve typed provider/permission failure through containment instead of converting provider availability failures to `false`; add a deterministic disappearance/revocation test. This does not invalidate the proven normal Copy path or enable any destructive route.

### F2 Moderate / Discuss - Nullable operation IDs weaken partial ownership

User impact: A caller that omits the operation ID can write, finalize, or delete another operation-looking partial whose name merely matches `.omnifile-*.partial`, risking cleanup of the wrong partial output.

Surface: SAF and Local transfer-provider partial ownership boundary.

Look here first:
- [`SafStorageProvider.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/storage/SafStorageProvider.kt#L503)
- [`StorageModel.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/storage/StorageModel.kt#L147)

Evidence and behavior delta:
- `StorageTransferProvider.deleteOperationPartial()` exposes `operationId: String?` (`StorageModel.kt:147-150`), and analogous write/finalize APIs also accept nullable IDs.
- SAF’s `isOperationPartial()` returns true for any `.omnifile-*.partial` when `operationId == null` (`SafStorageProvider.kt:688-692`); the Local provider has the same null-permissive behavior around `LocalStorageProvider.kt:284-288`.
- The normal `TransferEngine` path does pass the operation ID (`TransferEngine.kt:35`, `214`, `160`), so this is not demonstrated on the final Pixel Copy path. It is nevertheless inconsistent with the documented invariant that name resemblance alone never authorizes cleanup.

Reviewer action: Discuss whether these are intentionally unsafe legacy/test overloads. Remove the nullable default or require a persisted ownership token on every write/finalize/delete path; add a cross-parent/cross-operation partial test. Keep exact operation ID plus destination-parent containment as the authorization check.

## Watch

### F3 Major / Watch - Ambiguous finalization without a persisted locator can replay on direct execute

User impact: If a provider rename/finalization takes effect but returns no usable final identity, a later direct retry can recopy or hit a destination conflict instead of remaining explicitly ambiguous.

Surface: Finalization acknowledgement and retry/restart state.

Look here first:
- [`TransferEngine.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/operations/TransferEngine.kt#L155)
- [`OperationManager.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/operations/OperationManager.kt#L124)

Evidence and limits:
- `FinalizationResult.Ambiguous` returns `Failed(AmbiguousFinalization, partial)` without a final locator (`TransferEngine.kt:162-165`).
- `execute()` only enters finalization reconciliation when `stage == FINALIZING` **and** a finalization description exists (`OperationManager.kt:128-129`). An `INTERRUPTED` operation with no description can later transition back to `TRANSFERRING` (`OperationManager.kt:147-151`), where the engine deletes/rewrites the partial and checks the intended final name first (`TransferEngine.kt:35-67`).
- Startup `reconcileNonTerminal()` does retain an ambiguous finalizing record (`OperationManager.kt:224-228`), so the normal restart path is safer than an explicit direct `execute()` retry. Production SAF destination finalization is disabled, which limits this to opt-in/controlled finalization today.

Reviewer action: Watch and cover before enabling any production SAF destination finalization. Make all retry entrypoints honor an ambiguity record, including the no-final-locator case, or require an explicit reconciliation path rather than replaying transfer.

### F4 Minor / Watch - Multiple persisted SAF grants do not preserve the selected source

User impact: After process recreation with multiple unrelated readable tree grants, OmniFile may restore the shallowest tree rather than the tree the user last selected.

Surface: Persisted grant restoration.

Look here first:
- [`SafTreeGrantStore.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/storage/SafTreeGrantStore.kt#L54)
- [`SafTreeGrantStore.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/storage/SafTreeGrantStore.kt#L70)

Evidence and limits:
- Restoration returns all platform-persisted tree grants (`SafTreeGrantStore.kt:54-67`), then `restoredReadTree()` selects `minByOrNull { treeDepth(uri) }` (`SafTreeGrantStore.kt:70-74`).
- Depth is only meaningful as “broadest” among trees in the same provider/tree hierarchy; it does not identify the previously selected source across unrelated authorities or equal-depth grants.
- The final Pixel case had a broad root plus child destination under the same provider and correctly restored the broad root, so the supplied runtime evidence does not reproduce this candidate.

Reviewer action: Watch; either persist the selected source URI separately and revalidate it against platform truth, or document that restoration is intentionally heuristic when multiple unrelated grants exist.

## Intentional Changes

- Local→Local Copy/Move remains supported; this is consistent with the VS03 baseline and the supplied full `29/29` instrumentation result.
- SAF→Local Copy is the only production SAF transfer claim. The final Pixel run completed `41 B / 41 B`, retained the SAF source, and produced the Local output.
- SAF→Local Move is intentionally unsupported in production. The UI and enqueue path require `MOVE_SOURCE`, while production `sourceVersionProven=false`; historical pre-gate Move evidence must not be counted as the final claim.
- Local→SAF Copy/Move and SAF→SAF Copy/Move are intentionally unsupported in production because `finalizationProven=false`; the SAF destination “Use this folder” action remains disabled.
- Controlled tests opt into `finalizationProven=true` and `sourceVersionProven=true`; that is provider-fixture evidence, not a generic SAF production claim.

## Coverage Ledger

| Surface | Status | Result / dismissal |
|---|---|---|
| SAF locator encoding and tree/authority containment | Reviewed - no separate finding | Versioned tree+document locator, provider ID, exact tree URI, authority checks, and child containment are present. F1 remains for exception classification, not ordinary containment bypass. |
| Persisted grant acquisition | Reviewed - no finding | Picker requires a tree URI, content scheme, authority, actual read/write result bits, and verifies the platform persisted grant before storing metadata. |
| Persisted grant restoration | F4 Watch | Broad-root restoration works for the tested root+child case; last-selected source is not preserved across unrelated grants. |
| SAF→Local Copy | Reviewed - no visible regression found | Final Pixel `41 B COMPLETE`, source retained, Local output present. |
| Local→Local Copy/Move | Reviewed - no visible regression found | Existing route retained; full runtime and host/build evidence pass. |
| SAF source Move capability gate | Reviewed / dismissed | `sourceVersionProven=false` production default removes `MOVE_SOURCE` from SAF entries and transfer capabilities; UI Move is disabled. |
| SAF destination capability/finalization gate | Reviewed / dismissed | `finalizationProven=false` production default removes destination create/write/finalize capabilities; destination confirmation is disabled. |
| Operation-owned partial creation and cleanup | F2 Discuss | Normal engine calls pass the operation ID, but nullable API paths accept name-only ownership. |
| Finalization identity and returned-provider identity | F3 Watch | Versioned finalization records and persisted returned locators are present; no-locator ambiguity is not honored by every execute/retry entrypoint. |
| Retry/restart state | F1 Discuss, F3 Watch | Normal startup ambiguity guard exists; provider-unavailability classification and direct retry edge remain. |
| Controlled provider matrix | Reviewed - evidence scoped | `18/18` passed, but controlled opt-in proofs do not authorize generic SAF finalization or Move. |
| Real provider route matrix | Reviewed - evidence scoped | `ExternalStorageProvider` proves SAF browsing/persisted grants and SAF→Local Copy only; no real destination finalization or final production Move claim. |

## Evidence Appendix

- Host verification supplied: `63` unit tests passed; lint passed with `0 errors` and `22 warnings`; debug and android-test APK builds passed.
- Runtime supplied: controlled SAF `18/18`; full direct runner `29/29`; final Pixel `ExternalStorageProvider` SAF→Local Copy `41 B COMPLETE`, source retained.
- Production safety defaults verified in source: `SafStorageProvider` constructor defaults `finalizationProven=false` and `sourceVersionProven=false` (`SafStorageProvider.kt:22-29`).
- No claim was made that controlled provider tests prove all SAF providers. The supported route matrix is therefore truthful as written, with the caveat that “supported” means the tested sequential SAF-source → Local-destination Copy path under a current persisted grant, not universal SAF behavior.
- Prior review artifacts were treated as context only; their “closed” inherited findings do not erase the two current Discuss candidates above.

