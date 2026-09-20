# Code Review Report

## Report Contract

- Report type: `code-review`
- Report ID: `cr-20260920-7f4c91b2`
- Review chain ID: `rc-20260920-7f4c91b2`
- Review generation: `0`
- Review trigger: `initial`
- Parent review report ID: `None`
- Parent review report path: `None`
- Parent resolution ID: `None`
- Parent resolution path: `None`
- Generated at: `2026-09-20T12:00:00Z`
- Report path: `/Users/yuta/Desktop/File Manager-worktrees/omnifile-saf-transfer-v1/tmp/reviews/2026-09-20-code-review-review-b-local-to-saf.md`
- Source skill: `code-review`
- Status: `Review complete`
- Git mutation during review: `None`
- Scope fingerprint: `sha256:e35d350de6d5f7e33b427b7ecfbc44b69d803b5cc4f151bd81b723d4c67a0fe1`

## Scope

- Review date: `2026-09-20`
- Scope kind: `working tree`
- Scope description: `Review B of the VS04 Local→SAF implementation delta, controlled DocumentsProvider extensions, durable operation integration, and affected destination-picker paths.`
- Scope mode: `full frozen scope`
- Baseline: `a95b3e28f47954b879b389ce6d5a5f17d4704407`
- Target: `development/core-v1-saf-transfer-v1 working tree`
- Changed paths: `20 tracked/untracked review-relevant paths`
- Diff size: `1592 additions / 175 deletions in tracked diff; untracked files reviewed separately`
- Completion: `Complete within reviewed scope; runtime execution is an explicit gate below`
- Requirements consulted: `VS04 campaign brief; frozen Review A; ADR-001 through ADR-005; storage/operation/security/performance architecture documents; debug, testing-setup, android-intent-security guidance`
- Prior resolution consulted: `None`
- Assumptions: `Local source semantics remain VS03-proven; SAF provider capability flags remain advertised rather than guaranteed runtime success.`
- Excluded as unrelated: `SAF→Local and SAF→SAF source-mutation review; reserved for Reviews C and D.`

## Review Orchestration

- Assessment subagent: `Coordinator assessment - changed surface is broad but Review A already established the architecture partitions; current review was independently performed with prior read-only audit cross-checks`
- Orchestration decision: `Parallel specialists`
- Decision confidence: `high`
- Decision rationale: `Provider/finalization semantics, durable state/restart behavior, and UI/picker integration are independent risk dimensions.`
- Coordinator override: `None`
- Context or tool limits: `No connected Android device; runtime findings are explicitly separated from static findings.`

### Risk Dimensions

- `SAF provider capability and finalization semantics can expose a route before destination completion is mechanically proven.`
- `Operation-owned partials and returned document identities cross provider and Room boundaries.`
- `A Local→SAF Move must never delete its Local source before durable SAF destination completion.`
- `Controlled-provider and Pixel evidence are required to validate non-seekable I/O and actual DocumentsUI/provider behavior.`

### Reviewer Assignments

| Reviewer | Angle | Owned surfaces | Mandatory cross-checks | Status |
| --- | --- | --- | --- | --- |
| `R1` | Coordinator correctness/contracts | `SafStorageProvider.kt`, `TransferEngine.kt`, `OperationManager.kt`, `StorageModel.kt` | `Review A invariants; provider-returned identity; Move ordering` | `Complete` |
| `R2` | Provider/harness reliability | `TestDocumentsProvider.java`, `SafStorageProviderInstrumentedTest.kt`, runtime test | `Pipe I/O, fault boundaries, test determinism` | `Complete` |
| `R3` | UI/security integration | `MainActivity.kt`, `SafTreeGrantStore.kt`, `FilesViewModel.kt`, `FilesScreen.kt` | `picker flags, grant persistence, destination gating` | `Complete` |

### Synthesis Statement

`The coordinator independently re-read every accepted candidate against the current implementation and the Review A contract. The runtime gate remains open because adb reports no connected device; no route is marked supported from compilation alone.`

## Review Snapshot

- Recommendation: `Changes requested`
- Completion: `Complete within reviewed scope`
- Why now: `Local→SAF has an executable implementation and compiled controlled tests, but finalization capability proof and runtime evidence are not yet sufficient for route support.`
- Must-review now:
  1. `F1` Finalization capability advertisement versus provider proof
  2. `T1` Controlled-provider and Pixel runtime gate
- Findings count: `Blocker 0 | Major 1 | Minor 0 | Question 0`
- Standalone test gaps: `Blocker 0 | Major 1 | Minor 0`
- Coverage confidence: `medium`
- Biggest blind spot: `No connected Pixel/emulator runtime evidence.`

## Complete Findings Index

| ID | Severity | Surface | Review risk | Confidence | Origin | Verification | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `F1` | `Major` | SAF destination capability/finalization | SAF Copy/Move can be exposed before rename/finalization is proven for the created partial | `high` | `Coordinator` | `static trace + compiled tests; runtime unavailable` | `behavior; entry=Local-to-SAF destination eligibility; contract=finalization capability is proven before route advertisement and destructive Move continuation; effect=operation is enqueued then reaches unsupported/ambiguous finalization after destination-side side effects` | `ifp-sha256:f36f4be6f72529279d95d9402ff9c9b8d7baef82cfd2a2f019280ad6645412d8` | `kind:hard-invariant; strength:authoritative; evidence:VS04 finalization and destination-picker requirements; ADR-005` |

## Blocker

None.

## Major

### F1 Major - SAF finalization capability is advertised from grant mode before created-document proof

Impact: `A user can select a writable SAF folder and enqueue Local→SAF even when the provider-created partial does not advertise rename/finalization. The operation may create/write a partial before becoming Unsupported or Ambiguous; Move remains source-safe but the route is not truthfully advertised as eligible.`

Review reason: `VS04 requires destination picker and Files actions to show only destinations whose actual provider capabilities satisfy the route. A write grant proves neither rename support on a newly created document nor provider finalization semantics.`

Surface: `SafStorageProvider.transferCapabilities, OperationManager.enqueue, FilesScreen destination confirmation`

Issue key: `behavior; entry=Local-to-SAF destination eligibility; contract=finalization capability is proven before route advertisement and destructive Move continuation; effect=operation is enqueued then reaches unsupported/ambiguous finalization after destination-side side effects`

Issue fingerprint: `ifp-sha256:f36f4be6f72529279d95d9402ff9c9b8d7baef82cfd2a2f019280ad6645412d8`

Expected basis: `kind:hard-invariant; strength:authoritative; evidence:VS04 finalization and destination-picker requirements; ADR-005`

Confidence: `high`

Origin: `Coordinator`

Coordinator verification: `SafStorageProvider adds FINALIZE whenever the persisted grant has write access; the destination UI gates only CREATE_CHILD and WRITE. The created partial's FLAG_SUPPORTS_RENAME is checked only after enqueue and partial creation in finalizeOperationPartial.`

Look here first:
- [`SafStorageProvider.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/storage/SafStorageProvider.kt#L36)
- [`FilesScreen.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/ui/files/FilesScreen.kt#L82)

Failure mode:
- Expected: `A route is advertised only when the provider's create/write/finalize chain is proven for the selected destination semantics, or the route remains Unsupported before destructive stages.`
- Current: `Write grant plus parent create/write flags enable destination confirmation; a provider that creates a partial without rename support is discovered only after partial creation.`

Evidence:
- `Controlled provider now exposes configurable rename support, but the instrumentation was not executed because adb reported no devices.`
- `The implementation correctly returns FinalizationResult.Unsupported and never deletes the source in that case; the remaining issue is preflight/user-facing capability truth.`

Assumptions and limits:
- `A provider-specific runtime capability probe may be sufficient if it is non-destructive and persisted only as current-session evidence; otherwise this route must remain Unsupported for that provider.`

Reviewer action:
`request proof or keep the route disabled until finalization capability is established`

## Minor

None.

## Questions

None.

## Test Gaps

| ID | Severity | Surface | Missing coverage | Risk | Origin | Evidence | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `T1` | `Major` | Local→SAF provider/runtime chain | `No controlled DocumentsProvider instrumentation or real Pixel SAF flow has executed; adb devices is empty. Missing evidence includes non-seekable write, partial creation, final URI change, conflict, cancellation, restart, and Move source-delete ordering.` | `A compile pass cannot prove ContentResolver pipe closure, DocumentsContract rename identity, provider flags, or durable source-delete ordering.` | `Coordinator` | `:app:testDebugUnitTest passed; :app:compileDebugAndroidTestKotlin passed; adb devices returned no attached devices.` | `test-gap; entry=Local-to-SAF durable transfer; contract=controlled-provider and real-Pixel runtime evidence proves copy/move safety; gap=runtime matrix has not executed` | `ifp-sha256:1583219d9bb65d468590a22a3713b689e642d590a83a1e9ffc280085c064b944` | `kind:requirement; strength:authoritative; evidence:VS04 Phase A acceptance and final Pixel gate` |

## Review Coverage Ledger

| Area ID | Area / path | Touched files or entry points | Owner | Depth | Status | Result | Evidence / next step |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `A1` | Durable SAF locator/tree containment | `SafDurableLocatorCodec.kt`, `SafStorageProvider.kt` | `R1` | `contract trace` | `Reviewed - no issue found` | `Versioned saf-document-v1 payload, provider-scoped ID, authority/tree containment, and returned locator persistence are present.` | `Run instrumentation with changed document identity.` |
| `A2` | Persisted grant acquisition/restoration | `SafTreeGrantStore.kt`, `MainActivity.kt` | `R3` | `contract trace` | `Reviewed - no issue found` | `Only read/write bits are persisted and tree URIs are filtered; destination picker now requests write only when needed.` | `Run grant revocation/restart tests on device.` |
| `A3` | SAF partial create/write/verify/finalize | `SafStorageProvider.kt`, `TransferEngine.kt` | `R1` | `dependency trace` | `Finding F1` | `Bounded sequential transfer, operation-ID partial ownership, parent proof, and conservative finalization are implemented.` | `Resolve preflight finalization truth and run provider matrix.` |
| `A4` | Local→SAF Move ordering | `OperationManager.kt`, `OperationStateMachine.kt` | `R1` | `contract trace` | `Reviewed - no issue found` | `Destination completion is recorded before SOURCE_DELETE_PENDING and Local source deletion; final destination is revalidated before deletion.` | `Run controlled and Pixel Move ordering test.` |
| `A5` | Controlled provider harness | `TestDocumentsProvider.java` | `R2` | `diff-only` | `Reviewed - no issue found` | `Create/open pipe I/O, content, flags, unknown size, identity change, and deterministic faults are implemented.` | `Execute on Pixel; inspect asynchronous pipe commit.` |
| `A6` | Destination picker/action gating | `FilesViewModel.kt`, `FilesScreen.kt` | `R3` | `dependency trace` | `Finding F1` | `Self-cancel bug fixed; current UI gates create/write but not finalization proof.` | `Keep disabled or add a safe finalization capability proof.` |
| `A7` | Existing Local regression | `LocalStorageProvider.kt`, `OperationManagerTest.kt` | `R1` | `runtime verified` | `Reviewed - no issue found` | `61 host tests pass, including existing Local Copy/Move cases.` | `Run final full instrumentation when device is available.` |
| `A8` | Real SAF DocumentsUI/provider | `MainActivity.kt`, physical flows | `R3` | `not covered` | `Not covered` | `No device is attached.` | `Perform real Pixel tree selection and Local→SAF Copy/Move before support claim.` |

## Subagent Candidate Adjudication

| Candidate ID | Proposed by | Decision | Final ID | Coordinator evidence | Reason |
| --- | --- | --- | --- | --- | --- |
| `R1-C1` | `R1` | `accepted` | `F1` | `transferCapabilities and destinationCanAccept trace` | `Capability truth remains unresolved until finalization is proven for the created document.` |
| `R1-C2` | `R1` | `represented by T1` | `T1` | `adb devices and compile/test results` | `Runtime provider behavior cannot be inferred from host compilation.` |
| `R2-C1` | `R2` | `accepted` | `T1` | `instrumentation source compiles but no device execution` | `The matrix is implemented but unexecuted.` |
| `R3-C1` | `R3` | `dismissed` | `None` | `FilesViewModel root loader now lists directly in the same job` | `The prior self-cancellation concern was repaired before this report.` |

## Evidence Appendix

### Diff Inventory

| File or area | Classification | Semantic review area considered |
| --- | --- | --- |
| `app/src/main/java/com/omnifile/storage/SafStorageProvider.kt` | `surface` | `API, auth, data integrity, finalization` |
| `app/src/main/java/com/omnifile/operations/TransferEngine.kt` | `surface` | `ordering, retry, partial ownership, I/O errors` |
| `app/src/main/java/com/omnifile/operations/OperationManager.kt` | `surface` | `persistence, state machine, Move ordering` |
| `app/src/main/java/com/omnifile/storage/SafTreeGrantStore.kt` | `surface` | `auth, permission persistence` |
| `app/src/androidTest/java/com/omnifile/storage/TestDocumentsProvider.java` | `test-only` | `provider protocol and fault coverage` |
| `app/src/androidTest/java/com/omnifile/storage/SafStorageProviderInstrumentedTest.kt` | `test-only` | `controlled provider assertions` |
| `app/src/androidTest/java/com/omnifile/operations/SafTransferRuntimeInstrumentedTest.kt` | `test-only` | `route integration assertions` |
| `app/src/main/java/com/omnifile/MainActivity.kt` | `surface` | `picker flags and returned URI trust boundary` |
| `app/src/main/java/com/omnifile/files/FilesViewModel.kt` | `surface` | `destination picker lifecycle` |
| `app/src/main/java/com/omnifile/ui/files/FilesScreen.kt` | `surface` | `action gating and destination confirmation` |

### Verification Commands

- `:app:testDebugUnitTest --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process --dependency-verification=strict` -> `BUILD SUCCESSFUL; 61 tests`
- `:app:compileDebugAndroidTestKotlin --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process --dependency-verification=strict` -> `BUILD SUCCESSFUL`
- `adb devices -l` -> `no devices attached`
- `git diff --check` -> `clean`

### Supporting Code Links

| ID | Role | Link | Why it matters |
| --- | --- | --- | --- |
| `F1` | `entry` | [`FilesScreen.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/ui/files/FilesScreen.kt#L82) | `Destination confirmation is gated by parent create/write only.` |
| `F1` | `risk` | [`SafStorageProvider.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/storage/SafStorageProvider.kt#L36) | `FINALIZE is advertised from grant mode.` |
| `T1` | `test` | [`SafTransferRuntimeInstrumentedTest.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/androidTest/java/com/omnifile/operations/SafTransferRuntimeInstrumentedTest.kt#L1) | `Required route matrix exists but was not run.` |

### Dismissed Coordinator Candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| `SAF locator lacks versioning` | `dismissed` | `SafDurableLocatorCodec uses explicit saf-document-v1 and host codec tests pass.` |
| `Local Move ordering regression` | `dismissed` | `Existing OperationManager host tests pass after explicit Local directory WRITE capability.` |

### Blind Spots

| Area ID | Blind spot | Decision risk | What would resolve it |
| --- | --- | --- | --- |
| `A8` | `No connected device or real DocumentsUI/provider` | `Cannot claim any SAF route supported.` | `Run controlled and real Pixel tests with persisted grants.` |

## Prior Resolution Reconciliation

None - initial review generation.

## Receiving Handoff

- Handoff status: `Ready for receiving-code-review`
- Automatic receiving permitted: `No`
- Source report ID: `cr-20260920-7f4c91b2`
- Scope fingerprint to recheck: `sha256:e35d350de6d5f7e33b427b7ecfbc44b69d803b5cc4f151bd81b723d4c67a0fe1`
- Actionable finding IDs: `F1`
- Deferred finding IDs: `None`
- Actionable test-gap IDs: `T1`
- Deferred test-gap IDs: `None`
- Open question IDs: `None`
- Open coverage area IDs: `A8`
- Highest-risk verification to repeat: `Run controlled-provider and real Pixel Local→SAF Copy/Move with a changed final URI and non-seekable descriptors.`
- Suggested implementation boundaries: `Keep finalization capability proof and runtime gate isolated from the already-proven Local provider path.`
- Re-review note: `Treat every finding as a claim to verify. Challenges require a counterclaim, argument, evidence, limits, and settlement criterion.`
- Chain rule: `Generation 1 is terminal. Do not automatically invoke receiving-code-review; return remaining findings to the user or product owner.`

## Report Self-Check

- `yes` Actual assessment mode and rationale are recorded.
- `yes` Every changed review-relevant or unknown-impact area appears once in Review Coverage Ledger.
- `yes` Every final finding appears once in the index and once as a matching card.
- `yes` Every Finding F# area references an existing finding.
- `yes` Every standalone test gap has a stable ID and severity.
- `yes` Every F# and T# has a unique semantic issue fingerprint and authoritative expected basis.
- `yes` Generation, trigger, parent review, scope mode, and receiving handoff are recorded.
- `yes` Review A architecture findings were independently considered; this is a separate generation-0 checkpoint.
- `yes` Every non-Question finding and test gap appears exactly once in actionable/deferred handoff IDs.
- `yes` Every meaningful subagent candidate has an adjudication.
- `yes` Every Not covered area has a reason and next step.
- `yes` Recommendation follows the skill mapping.
- `pending` Validator execution is the next report-freeze step.
- `yes` Git state was not mutated during review.
