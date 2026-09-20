# Code Review Report

## Report Contract

- Report type: `code-review`
- Report ID: `cr-20260921-finalvs04`
- Review chain ID: `rc-20260921-finalvs04`
- Review generation: `0`
- Review trigger: `initial`
- Parent review report ID: `None`
- Parent review report path: `None`
- Parent resolution ID: `None`
- Parent resolution path: `None`
- Generated at: `2026-09-21T02:45:00+09:00`
- Report path: `/Users/yuta/Desktop/File Manager-worktrees/omnifile-saf-transfer-v1/tmp/reviews/2026-09-21-code-review-final-vs04.md`
- Source skill: `code-review`
- Status: `Review complete`
- Git mutation during review: `None`
- Scope fingerprint: `Unavailable - worktree is outside the registered file-tool root; baseline and target SHAs are recorded`

## Scope

- Review date: `2026-09-21`
- Scope kind: `commit range plus working tree`
- Scope description: `Final whole-diff review from published VS03 through current VS04 working tree, including runtime fixes, production documentation, and review artifacts.`
- Scope mode: `full frozen scope`
- Baseline: `a95b3e28f47954b879b389ce6d5a5f17d4704407`
- Target: `d6a82896b074cfbf2a5f958c086e462906157d0 plus current uncommitted runtime hardening/evidence`
- Changed paths: `storage, durable operations, Files/UI, controlled provider/tests, production documentation, review reports`
- Diff size: `reviewed by targeted trace; git diff --check passed`
- Completion: `Complete within reviewed scope; two findings remain actionable`
- Requirements consulted: `VS04 campaign, architecture/ADR contracts, debug, android-intent-security, code-review, regression-review requirements`
- Prior resolution consulted: `None`
- Assumptions: `SAF→Local Copy is the only claimed SAF production route; SAF destination and SAF Move remain Unsupported.`
- Excluded as unrelated: `VS05, directories, archives, background execution, cloud providers, root, unrelated UI redesign`

## Review Orchestration

- Assessment subagent: `Coordinator assessment - final scope spans durable state, SAF trust boundary, UI gates, runtime evidence, and docs; independent read-only safety review was also consulted`
- Orchestration decision: `Parallel specialists`
- Decision confidence: `high`
- Decision rationale: `SAF identity/grant risks and user-visible route/regression risks are distinct and both can affect approval.`
- Coordinator override: `None`
- Context or tool limits: `Real persisted grant revocation/provider disappearance and equal-depth multi-grant selection were not physically exercised.`

### Risk Dimensions

- `Provider-scoped identity, tree containment, and persisted grants.`
- `Finalization ambiguity, partial ownership, restart, and destructive ordering.`
- `Capability/UI gating and visible route claims.`
- `Local regression and production evidence/documentation truthfulness.`

### Reviewer Assignments

| Reviewer | Angle | Owned surfaces | Mandatory cross-checks | Status |
| --- | --- | --- | --- | --- |
| `R1` | `durable state/provider/security` | `SafStorageProvider.kt, SafTreeGrantStore.kt, OperationManager.kt, OperationModels.kt, TransferEngine.kt` | `source deletion, finalization, restart, grants` | `Complete` |
| `R2` | `user-visible route/regression` | `MainActivity.kt, FilesViewModel.kt, FilesScreen.kt, OperationsPanel.kt, docs` | `Pixel route matrix, Local behavior, unsupported gating` | `Complete` |

### Synthesis Statement

`The coordinator independently verified every candidate against current code, 63 host tests, controlled 18/18 runtime, full 29/29 runtime, final Pixel SAF→Local Copy, persisted permission diagnostics, and the documented route matrix. The supported SAF→Local Copy happy path is truthful, but provider disappearance classification and equal-depth grant selection remain approval-affecting gaps.`

## Review Snapshot

- Recommendation: `Changes requested`
- Completion: `Complete within reviewed scope`
- Why now: `The final route claim is conservative, but two SAF interruption/restore behaviors can still produce misleading user-visible state.`
- Must-review now:
  1. `F1` Provider disappearance is classified as stale/not-found
  2. `F2` Equal-depth persisted trees do not restore the last selected source
  3. `T1` No final Pixel provider-loss/revocation rendering run
- Findings count: `Blocker 0 | Major 1 | Minor 1 | Question 0`
- Standalone test gaps: `Blocker 0 | Major 1 | Minor 0`
- Coverage confidence: `medium`
- Biggest blind spot: `Real provider disappearance/revocation and multi-grant selection lifecycle`

## Complete Findings Index

| ID | Severity | Surface | Review risk | Confidence | Origin | Verification | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `F1` | `Major` | `SAF provider containment/error mapping` | `Provider loss can render as stale/not-found instead of recoverable provider/permission interruption` | `high` | `R1` | `static trace; runtime gap` | `behavior; entry=SAF provider disappearance; contract=provider availability loss is distinguishable from stale identity and retryable; effect=provider loss is shown as terminal not-found/stale state` | `ifp-sha256:81f17cf4ca3fc9e70dc990f554c6b37fbb640c4a5217904ae51338eea7724b56` | `kind:hard-invariant; strength:authoritative; evidence:VS04 provider disappearance/grant interruption requirements` |
| `F2` | `Minor` | `persisted SAF tree restoration` | `Restart can choose an arbitrary equal-depth readable tree when multiple grants exist` | `medium` | `R1` | `static trace; one real root/child runtime` | `behavior; entry=restored SAF tree selection; contract=the last user-selected readable tree is restored after restart; effect=an unrelated equal-depth grant can become the active source` | `ifp-sha256:2e9673ebfe4b9ecd440e4b8dc70b4e762837f35ef3d1f7b8be6275be49a509c5` | `kind:hard-invariant; strength:authoritative; evidence:SAF persisted-grant restoration requirements` |

## Blocker

None.

## Major

### F1 Major - Provider disappearance must not become stale identity

Impact: `A revoked or unavailable DocumentsProvider can be shown as “source or destination no longer available” instead of a recoverable provider/permission interruption.`

Review reason: `Stale identity and provider availability loss have different recovery and user-visible meanings. Provider loss must not be treated as evidence that a source was deleted or that a durable locator is merely stale.`

Surface: `SAF containment and locator resolution`
Issue key: `behavior; entry=SAF provider disappearance; contract=provider availability loss is distinguishable from stale identity and retryable; effect=provider loss is shown as terminal not-found/stale state`
Issue fingerprint: `ifp-sha256:81f17cf4ca3fc9e70dc990f554c6b37fbb640c4a5217904ae51338eea7724b56`
Expected basis: `kind:hard-invariant; strength:authoritative; evidence:VS04 provider disappearance/grant interruption requirements`
Confidence: `high`
Origin: `R1`
Coordinator verification: `isWithinSelectedTree() catches non-security provider exceptions as false; locator callers then return StaleReference/NOT_FOUND. Final Pixel only proved normal grant acquisition and Copy.`

Look here first:
- [`SafStorageProvider.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/storage/SafStorageProvider.kt#L622)
- [`OperationManager.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/operations/OperationManager.kt#L435)

Failure mode:
- Expected: `Provider disappearance/revocation remains a distinct retryable permission/provider error; it never masquerades as stale identity or source absence.`
- Current: `SecurityException is classified as PermissionDenied, but other containment/provider exceptions are caught and converted to false; callers map that to StaleReference/NOT_FOUND.`

Evidence:
- `The controlled provider and real Pixel happy path passed, but provider disappearance/revocation was not physically injected. The static path is sufficient to establish the classification risk.`

Assumptions and limits:
- `This does not permit source deletion: Move is unsupported in production. It still affects recoverable user-visible state and future route safety.`

Reviewer action:
`block final review closure until provider-unavailable classification is preserved or the exact controlled error contract proves stale is intentional.`

## Minor

### F2 Minor - Persisted tree restoration needs an explicit selected-tree discriminator

Impact: `After restart with multiple unrelated readable tree grants at equal depth, OmniFile may open a different tree than the one the user last selected.`

Review reason: `The current shallowest-depth rule correctly repairs root-versus-child ordering but cannot encode last-selection intent for equal-depth grants.`

Surface: `SafTreeGrantStore.restoredReadTree`
Issue key: `behavior; entry=restored SAF tree selection; contract=the last user-selected readable tree is restored after restart; effect=an unrelated equal-depth grant can become the active source`
Issue fingerprint: `ifp-sha256:2e9673ebfe4b9ecd440e4b8dc70b4e762837f35ef3d1f7b8be6275be49a509c5`
Expected basis: `kind:hard-invariant; strength:authoritative; evidence:SAF persisted-grant restoration requirements`
Confidence: `medium`
Origin: `R1`
Coordinator verification: `The final Pixel had broad root and child destination grants and restored the broad root; equal-depth unrelated grants were not exercised.`

Look here first:
- [`SafTreeGrantStore.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/storage/SafTreeGrantStore.kt#L54)

Failure mode:
- Expected: `Persisted selected-tree intent is restored while child grants remain available for destinations.`
- Current: `Restoration filters readable grants and chooses minimum slash depth; equal-depth ties depend on platform permission ordering.`

Evidence:
- `The root/child Pixel case passes, but it does not prove deterministic selection among unrelated equal-depth grants.`

Assumptions and limits:
- `This is a restart/source-selection issue, not a path-containment escape; every selected provider still applies authority/tree containment.`

Reviewer action:
`request a selected-tree preference or deterministic tie-breaker and an equal-depth restart test before broadening multi-tree claims.`

## Questions

None.

## Test Gaps

| ID | Severity | Surface | Missing coverage | Risk | Origin | Evidence | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `T1` | `Major` | `provider disappearance/revocation` | `No final Pixel run revokes the disposable persisted grant or makes the provider disappear while the durable error/UI state is observed.` | `Provider-loss classification and retry presentation can escape runtime validation.` | `R1` | `Controlled happy paths 18/18 and full 29/29 passed; real grant acquisition/Copy passed; fault injection was not executed.` | `test-gap; entry=SAF provider disappearance and revocation; contract=provider loss renders recoverable permission/provider error state; gap=final Pixel error injection` | `ifp-sha256:4fd8f18afb44eecdcfb97665f297161157897512e480aaf74e292dcdbcfcbff9` | `kind:requirement; strength:authoritative; evidence:VS04 provider disappearance/grant revocation requirements` |

## Review Coverage Ledger

| Area ID | Area / path | Touched files or entry points | Owner | Depth | Status | Result | Evidence / next step |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `A1` | `SAF locator/containment` | `SafDurableLocatorCodec.kt, SafStorageProvider.kt` | `R1` | `contract trace` | `Finding F1` | `Authority/tree containment is present; provider-unavailable exception class is not preserved.` | `Add typed provider-unavailable mapping and deterministic test.` |
| `A2` | `persisted grants` | `SafTreeGrantStore.kt` | `R1` | `runtime verified` | `Finding F2` | `Root-versus-child restoration passed; equal-depth selection is unproven.` | `Persist selected tree or deterministic tie-breaker.` |
| `A3` | `durable operation state` | `OperationManager.kt, OperationModels.kt` | `R1` | `runtime verified` | `Reviewed - no issue found` | `Unknown-size verification, finalization v2, repeated ambiguous guard, and source-delete ordering are covered by host/controlled tests.` | `Keep production SAF Move unsupported.` |
| `A4` | `partial ownership` | `LocalStorageProvider.kt, SafStorageProvider.kt, TransferEngine.kt` | `R1` | `targeted test` | `Reviewed - no issue found` | `Operation ID is required for partial access/cleanup after final hardening.` | `Host tests passed.` |
| `A5` | `Files/destination UI` | `FilesViewModel.kt, FilesScreen.kt, MainActivity.kt` | `R2` | `runtime verified` | `Reviewed - no issue found` | `SAF Copy enabled, Move disabled, SAF destination confirm disabled.` | `Final Pixel UI evidence.` |
| `A6` | `Operations UI/Local regression` | `OperationsPanel.kt, LocalStorageProvider.kt` | `R2` | `runtime verified` | `Reviewed - no issue found` | `SAF→Local Copy COMPLETE and Local browsing remained functional.` | `Full 29/29 instrumentation.` |

## Subagent Candidate Adjudication

| Candidate ID | Proposed by | Decision | Final ID | Coordinator evidence | Reason |
| --- | --- | --- | --- | --- | --- |
| `R1-C1` | `R1` | `accepted` | `F1` | `SafStorageProvider.isWithinSelectedTree trace` | `Non-security provider failures are collapsed to stale.` |
| `R1-C2` | `R1` | `accepted` | `F2` | `SafTreeGrantStore restoration trace` | `Equal-depth grant order is not selected-tree intent.` |
| `R2-C1` | `R2` | `dismissed` | `None` | `final Pixel Copy and UI evidence` | `Supported Copy and Local behavior show no regression.` |

## Evidence Appendix

### Diff Inventory

| File or area | Classification | Semantic review area considered |
| --- | --- | --- |
| `app/src/main/java/com/omnifile/storage/SafStorageProvider.kt` | `surface` | `SAF provider trust/error mapping` |
| `app/src/main/java/com/omnifile/storage/SafTreeGrantStore.kt` | `surface` | `persisted grant restoration` |
| `app/src/main/java/com/omnifile/operations/OperationManager.kt` | `surface` | `durable state/restart/destructive ordering` |
| `app/src/main/java/com/omnifile/ui/files/FilesScreen.kt` | `surface` | `route gating` |
| `docs/production/VERTICAL_SLICE_04_SAF_DURABLE_TRANSFER.md` | `docs-only` | `evidence/claim truthfulness` |

### Verification Commands

- `:app:testDebugUnitTest` -> `63 passed, 0 failed, 0 errors, 0 skipped`
- `:app:lintDebug` -> `PASS; 0 errors; 22 warnings`
- `:app:assembleDebug` -> `PASS; strict dependency verification`
- `:app:assembleDebugAndroidTest` -> `PASS; strict dependency verification`
- direct controlled runner -> `18/18 OK`
- direct full runner -> `29/29 OK`
- final Pixel SAF→Local Copy -> `41 B COMPLETE; source retained; Local output visible`
- final Pixel UI -> `Move disabled; SAF destination Use this folder disabled`

### Dismissed Coordinator Candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| `Enable SAF destination from writable ExternalStorageProvider` | `dismissed` | `finalizationProven=false and UI gate disabled` |
| `Claim SAF Move from pre-gate historical run` | `dismissed` | `sourceVersionProven=false and final UI Move disabled` |

## Prior Resolution Reconciliation

None - final whole-diff generation 0.

## Receiving Handoff

- Handoff status: `Ready for receiving-code-review`
- Automatic receiving permitted: `No`
- Source report ID: `cr-20260921-finalvs04`
- Scope fingerprint to recheck: `Unavailable - worktree is outside the registered file-tool root; baseline and target SHAs are recorded`
- Actionable finding IDs: `F1,F2`
- Deferred finding IDs: `None`
- Actionable test-gap IDs: `T1`
- Deferred test-gap IDs: `None`
- Open question IDs: `None`
- Open coverage area IDs: `None`
- Highest-risk verification to repeat: `provider disappearance/revocation and equal-depth persisted tree restore`
- Suggested implementation boundaries: `Preserve unsupported SAF destination/Move gates; fix provider-unavailable classification and selected-tree persistence without weakening containment.`
- Re-review note: `Generation 1 would be required after fixes.`
- Chain rule: `Generation 0 receiving remains opt-in and must not be treated as closure.`

## Report Self-Check

- `yes` Assessment mode and rationale recorded.
- `yes` Changed review-relevant areas mapped in coverage ledger.
- `yes` Findings and test gaps appear in index/cards/ledger.
- `yes` Route claims are tied to runtime evidence and conservative gates.
- `yes` Recommendation follows the skill mapping.
- `yes` Git state was not mutated during review.
