# Code Review Report

## Report Contract

- Report type: `code-review`
- Report ID: `cr-20260919-vs03reviewc9d53`
- Review chain ID: `rc-20260919-vs03reviewc9d53`
- Review generation: `0`
- Review trigger: `initial`
- Parent review report ID: `None`
- Parent review report path: `None`
- Parent resolution ID: `None`
- Parent resolution path: `None`
- Generated at: `2026-09-19T19:45:00Z`
- Report path: `/Users/yuta/Desktop/File Manager-worktrees/omnifile-durable-copy-move-v1/tmp/reviews/2026-09-19-code-review-review-c.md`
- Source skill: `code-review`
- Status: `Review incomplete`
- Git mutation during review: `None`
- Scope fingerprint: `sha256:8c3e06a64a82738974eb49f7f55518435a81bdfc9a5c7c37be571a2d50902d14`

## Scope

- Review date: `2026-09-19`
- Scope kind: `working tree`
- Scope description: `Review C: bounded transfer engine, fault boundaries, Operation Manager execution, restart reconciliation, cancellation, and Move ordering.`
- Scope mode: `full frozen scope`
- Baseline: `14732fd8f120b93380e624143e534cceff6d09c4 (published VS02)`
- Target: `development/core-v1-durable-copy-move-v1 working tree`
- Changed paths: `engine/coordinator plus prior durable/provider paths`
- Diff size: `working tree includes Room/KSP wiring, durable operations, provider boundary, Local route, transfer engine, and coordinator`
- Completion: `Incomplete - static engine review complete; engine/reconciliation/fault tests have not produced a completed Gradle result.`
- Requirements consulted: `campaign durable lifecycle, restart reconciliation, fault-injection matrix, capability gating, cancellation, Move ordering, ADR-004, ADR-005, Reviews A/B`
- Prior resolution consulted: `None`
- Assumptions: `Destination picker/UI and SAF are not yet integrated; this review evaluates the coordinator contract they must use.`
- Excluded as unrelated: `Destination picker, Operations UI, SAF implementation, final regression review, protected root worktree.`

## Review Orchestration

- Assessment subagent: `Coordinator assessment - engine and coordinator are one execution chain with shared durable-state effects`
- Orchestration decision: `Single reviewer`
- Decision confidence: `high`
- Decision rationale: `The key risks are cross-stage ordering and restart behavior; splitting engine and manager would obscure the state transition trace.`
- Coordinator override: `None`
- Context or tool limits: `Gradle test tasks time out at KSP debugUnitTest processing under Java 26; no adb runtime evidence.`

### Risk Dimensions

- `Restart/reconciliation: finalized destination reality must outrank stale checkpoint/state without causing false conflict.`
- `Durable user-visible truth: errors, conflicts, cancellation, and progress must be persisted, not only returned in memory.`
- `Capability and destructive ordering: unsupported routes must be rejected before durable execution; Move source deletion must remain last.`

### Reviewer Assignments

| Reviewer | Angle | Owned surfaces | Mandatory cross-checks | Status |
| --- | --- | --- | --- | --- |
| `R1` | `Coordinator execution, recovery, durability, and test adequacy` | `TransferEngine.kt, OperationManager.kt, OperationRepository.kt, OperationStore.kt, state transitions and fault boundaries` | `finalized destination, source delete, error facts, capability route, cancellation, Long/checkpoint behavior` | `Partial` |

### Synthesis Statement

`The coordinator traced each engine result through OperationManager and the repository transition API. The Move source-delete ordering is proof-gated in the normal happy path, but recovery/error/capability paths are not yet production-safe. Runtime fault/recovery tests remain an explicit blind spot.`

## Review Snapshot

- Recommendation: `Changes requested`
- Completion: `Incomplete - runtime fault/recovery tests unavailable`
- Why now: `The coordinator is the first component able to turn provider effects into user-visible durable operation truth.`
- Must-review now:
  1. `F1` `Reconcile finalized destination reality`
  2. `F2` `Persist error classification`
  3. `F3` `Gate unsupported routes before enqueue`
- Findings count: `Blocker 0 | Major 3 | Minor 0 | Question 0`
- Standalone test gaps: `Blocker 0 | Major 1 | Minor 0`
- Coverage confidence: `medium`
- Biggest blind spot: `No completed deterministic engine/restart/fault-matrix test result.`

## Complete Findings Index

| ID | Severity | Surface | Review risk | Confidence | Origin | Verification | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `F1` | `Major` | `OperationManager.reconcileNonTerminal` | `Finalized output with missing durable completion becomes a false conflict` | `high` | `Coordinator` | `static restart trace` | `behavior; entry=restart reconciliation; contract=an already-finalized destination with missing durable completion is reconciled as destination-complete before Move source deletion; effect=restart classifies a valid finalized output as conflict and leaves durable operation unresolved` | `ifp-sha256:99256258d86ab38027f2246a50a961ddef820319423f7628417266bd6b217179` | `kind:hard-invariant; strength:authoritative; evidence:campaign restart reconciliation and durable lifecycle` |
| `F2` | `Major` | `OperationManager.fail` and `OperationRepository` | `State persists but error/conflict facts are discarded` | `high` | `Coordinator` | `static persistence trace` | `behavior; entry=operation failure handling; contract=error and conflict classification is durable and user-readable; effect=state changes persist without error facts so Operations UI cannot explain the terminal outcome` | `ifp-sha256:bd46d805fa77aad3eaa96c73b7d652c9fc0986acf8f9ec025566df1653ecf3da` | `kind:requirement; strength:authoritative; evidence:campaign error classification and Operations UI requirements` |
| `F3` | `Major` | `OperationManager.enqueue` | `Unsupported provider route can be durably enqueued` | `high` | `Coordinator` | `static capability trace` | `behavior; entry=operation enqueue; contract=Copy/Move is exposed only when the complete provider capability route is supported; effect=unsupported route is durably enqueued and execution fails after user-visible intent` | `ifp-sha256:93404dfa7bfe828678fee0636475cc5ae48399d56c7ed3a1cbc254452daf9a3c` | `kind:hard-invariant; strength:authoritative; evidence:campaign capability model and UI gating requirements` |

## Blocker

None.

## Major

### F1 Major - Reconcile finalized destination reality

Impact: `A process loss after provider finalization but before DESTINATION_COMPLETE persistence leaves the final file present. Current reconciliation converts FINALIZING to INTERRUPTED, then restarts transfer; the engine sees the final name and marks CONFLICTED instead of adopting the already-finalized destination.`

Review reason: `The campaign explicitly requires recovery to handle an already-finalized destination and distinguish it from a true destination conflict.`

Surface: `OperationManager.reconcileNonTerminal -> execute -> TransferEngine.execute`

Issue key: `behavior; entry=restart reconciliation; contract=an already-finalized destination with missing durable completion is reconciled as destination-complete before Move source deletion; effect=restart classifies a valid finalized output as conflict and leaves durable operation unresolved`

Issue fingerprint: `ifp-sha256:99256258d86ab38027f2246a50a961ddef820319423f7628417266bd6b217179`

Expected basis: `kind:hard-invariant; strength:authoritative; evidence:campaign restart reconciliation and durable lifecycle`

Confidence: `high`

Origin: `Coordinator`

Coordinator verification: `FINALIZING/VERIFYING are first converted to INTERRUPTED; execute then invokes TransferEngine, whose existing-final check returns NameConflict. No reconciliation branch inspects the existing final candidate against expected size/source facts and no branch establishes destination completion from that evidence.`

Look here first:
- [`OperationManager.kt:55-65`](file:///Users/yuta/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/OperationManager.kt#L55-L65)
- [`TransferEngine.kt:44-54`](file:///Users/yuta/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/TransferEngine.kt#L44-L54)

Failure mode:
- Expected: `Reconciliation inspects the intended final candidate and, when verification facts match, durably promotes destination completion without replaying or falsely conflicting.`
- Current: `Any existing intended final name is treated as a new conflict before its provenance/reality is reconciled.`

Evidence:
- `TransferEngine` has no distinction between an operation-owned finalized candidate and an unrelated pre-existing conflict.
- The persisted operation currently does not retain finalization facts sufficient for this branch.

Assumptions and limits:
- `A provider may not expose provenance beyond name/size/version; reconciliation must remain conservative when facts do not prove ownership.`

Reviewer action:
`request fix`

### F2 Major - Persist error classification

Impact: `Operations UI and restart logic can observe only a state such as FAILED or CONFLICTED; the specific permission, source-changed, unsupported, provider I/O, cancellation, or ambiguous-finalization reason is lost.`

Review reason: `The approved schema includes error/conflict classification and the user-visible UI must show actionable error text.`

Surface: `OperationManager.fail, OperationRepository, OperationStore/OperationDao`

Issue key: `behavior; entry=operation failure handling; contract=error and conflict classification is durable and user-readable; effect=state changes persist without error facts so Operations UI cannot explain the terminal outcome`

Issue fingerprint: `ifp-sha256:bd46d805fa77aad3eaa96c73b7d652c9fc0986acf8f9ec025566df1653ecf3da`

Expected basis: `kind:requirement; strength:authoritative; evidence:campaign error classification and Operations UI requirements`

Confidence: `high`

Origin: `Coordinator`

Coordinator verification: `OperationSnapshot and Room columns contain errorCode/errorMessage, but OperationRepository has no failure-recording method and OperationManager.fail only transitions state.`

Look here first:
- [`OperationManager.kt:155-164`](file:///Users/yuta/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/OperationManager.kt#L155-L164)
- [`OperationStore.kt:25-64`](file:///Users/yuta/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/persistence/OperationStore.kt#L25-L64)

Failure mode:
- Expected: `Failure transitions atomically or durably record state plus OperationErrorCode and message.`
- Current: `Only state/stage/source-delete fields are updated; error fields remain null from enqueue.`

Evidence:
- `OperationEntity` has error columns, proving the persistence shape anticipates this data, but no DAO/store update path exists.

Assumptions and limits:
- `The eventual UI could synthesize generic text, but that would weaken the approved error-classification contract and lose provider detail across restart.`

Reviewer action:
`request fix`

### F3 Major - Gate unsupported routes before enqueue

Impact: `A user can lose the original selection into a durable operation that can never execute, or see a delayed generic failure rather than a truthful disabled/unsupported action.`

Review reason: `Capability gating is a product/security invariant, not only a UI concern; the durable coordinator must refuse routes it cannot complete safely.`

Surface: `OperationManager.enqueue and provider capability map`

Issue key: `behavior; entry=operation enqueue; contract=Copy/Move is exposed only when the complete provider capability route is supported; effect=unsupported route is durably enqueued and execution fails after user-visible intent`

Issue fingerprint: `ifp-sha256:93404dfa7bfe828678fee0636475cc5ae48399d56c7ed3a1cbc254452daf9a3c`

Expected basis: `kind:hard-invariant; strength:authoritative; evidence:campaign capability model and UI gating requirements`

Confidence: `high`

Origin: `Coordinator`

Coordinator verification: `enqueue accepts arbitrary locators and type without resolving provider IDs or checking READ_SEQUENTIAL/CREATE_CHILD/WRITE_SEQUENTIAL/FINALIZE, and MOVE does not require DELETE.`

Look here first:
- [`OperationManager.kt:14-52`](file:///Users/yuta/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/OperationManager.kt#L14-L52)
- [`StorageModel.kt:75-82`](file:///Users/yuta/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/storage/StorageModel.kt#L75-L82)

Failure mode:
- Expected: `Enqueue validates provider availability and the complete route, or returns Unsupported before durable operation creation.`
- Current: `Any request is persisted as PLANNED and fails later during execution.`

Evidence:
- `StorageTransferProvider.transferCapabilities` exists, but no manager path consumes it.

Assumptions and limits:
- `Destination picker may later perform the same check, but the coordinator remains the final durable boundary.`

Reviewer action:
`request fix`

## Minor

None.

## Questions

None.

## Test Gaps

| ID | Severity | Surface | Missing coverage | Risk | Origin | Evidence | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `T1` | `Major` | `TransferEngine, OperationManager, repository fakes` | `No completed deterministic tests cover checkpoint-behind/partial reality, cancellation, finalized-before-completion restart, source-delete ordering, fault boundaries, error persistence, or capability rejection.` | `The highest-risk durable and destructive paths can regress while only compile evidence remains.` | `Coordinator` | `No engine/recovery test result exists; focused Gradle test tasks time out during KSP debugUnitTest processing.` | `test-gap; entry=durable transfer engine; contract=partial, checkpoint, finalization, cancellation, restart, and Move ordering faults are mechanically covered; gap=no completed engine/reconciliation/fault-matrix test result` | `ifp-sha256:fe0d417d718008a1a691d47a5ed16095de1eb2fbcac8a180144e4abf512b8fcb` | `kind:requirement; strength:authoritative; evidence:campaign fault-injection matrix and required recovery tests` |

## Review Coverage Ledger

| Area ID | Area / path | Touched files or entry points | Owner | Depth | Status | Result | Evidence / next step |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `A1` | `Bounded transfer and checkpoints` | `TransferEngine.kt:132-179` | `R1` | `contract trace` | `Reviewed - no issue found` | `Fixed buffer and Long counters; checkpoint cadence is advisory and not used as storage truth.` | `Add large-file/fault tests.` |
| `A2` | `Verify/finalize boundary` | `TransferEngine.kt:93-129` | `R1` | `dependency trace` | `Reviewed - no issue found` | `Verify and finalization remain distinct; finalization result is explicit.` | `Test ambiguity and post-finalize crash.` |
| `A3` | `Restart reconciliation` | `OperationManager.kt:55-65,127-153` | `R1` | `contract trace` | `Finding F1` | `No final-candidate reconciliation before replay.` | `Implement inspect-and-adopt only with strong facts.` |
| `A4` | `Error/cancellation durability` | `OperationManager.kt:81-97,155-164`, `OperationRepository.kt` | `R1` | `dependency trace` | `Finding F2` | `Cancellation changes state, but error facts are not persisted.` | `Add durable failure update.` |
| `A5` | `Capability gating` | `OperationManager.kt:14-52`, `StorageModel.kt:75-139` | `R1` | `contract trace` | `Finding F3` | `Capabilities exist but enqueue does not enforce them.` | `Reject unsupported routes before create.` |
| `A6` | `Move source-delete ordering` | `OperationManager.kt:98-124` | `R1` | `contract trace` | `Reviewed - no issue found` | `Normal path requires proof-bearing DESTINATION_COMPLETE then SOURCE_DELETE_PENDING before delete.` | `Add fault/restart tests for every boundary.` |
| `A7` | `Fault/recovery test surface` | `No completed engine tests` | `R1` | `diff-only` | `Not covered` | `Gradle test execution is unavailable beyond compile evidence.` | `T1: isolate/fix KSP test pipeline and execute matrix.` |

## Subagent Candidate Adjudication

`No subagent candidates; coordinator performed one end-to-end execution trace.`

## Evidence Appendix

### Diff Inventory

| File or area | Classification | Semantic review area considered |
| --- | --- | --- |
| `app/src/main/java/com/omnifile/operations/TransferEngine.kt` | `surface` | `streaming, checkpoints, verify, finalize, cancellation, fault injection` |
| `app/src/main/java/com/omnifile/operations/OperationManager.kt` | `surface` | `durable transitions, reconciliation, capability, Move ordering` |
| `app/src/main/java/com/omnifile/operations/OperationRepository.kt` | `persistence` | `durable coordinator contract` |
| `app/src/main/java/com/omnifile/operations/persistence/OperationStore.kt` | `persistence` | `Room CAS/state persistence` |
| `app/src/main/java/com/omnifile/storage/StorageModel.kt` | `surface` | `error/capability types` |
| `app/src/test` and `app/src/androidTest` operations paths | `test-only` | `fault/restart/recovery evidence` |

### Verification Commands

- `:app:compileDebugKotlin --dependency-verification=strict --no-daemon` -> `BUILD SUCCESSFUL` after engine/coordinator compile fixes.
- `:app:testDebugUnitTest --tests com.omnifile.storage.LocalStorageTransferTest --dependency-verification=strict --no-daemon --max-workers=1` -> `timed out at :app:kspDebugUnitTestKotlin; no test result claimed`.
- Static trace of state callbacks -> normal Move path requires `DESTINATION_COMPLETE` proof before source delete.
- `git status --short` -> review remained read-only.

### Supporting Code Links

| ID | Role | Link | Why it matters |
| --- | --- | --- | --- |
| `F1` | `replay entry` | [`OperationManager.kt:55-65`](file:///Users/yuta/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/OperationManager.kt#L55-L65) | `Recovery replays instead of inspecting a possibly finalized candidate.` |
| `F1` | `conflict branch` | [`TransferEngine.kt:44-54`](file:///Users/yuta/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/TransferEngine.kt#L44-L54) | `Any existing final name becomes NameConflict.` |
| `F2` | `failure` | [`OperationManager.kt:155-164`](file:///Users/yuta/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/OperationManager.kt#L155-L164) | `State changes but error facts are not written.` |
| `F3` | `enqueue` | [`OperationManager.kt:14-52`](file:///Users/yuta/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/OperationManager.kt#L14-L52) | `No capability route validation precedes durable create.` |
| `T1` | `coverage` | [`TransferEngine.kt:195-209`](file:///Users/yuta/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/TransferEngine.kt#L195-L209) | `Fault hooks exist but are not exercised by a completed test matrix.` |

### Dismissed Coordinator Candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| `Checkpoint cadence of 8 MiB is too specific` | `dismissed` | `POC-002 is evidence input and the chosen cadence is documented as advisory; it is not a correctness defect.` |
| `Universal SHA-256 is missing` | `dismissed` | `Frozen verification policy explicitly does not require universal SHA-256.` |
| `Move source deletion occurs before Complete state` | `dismissed` | `SOURCE_DELETE_PENDING` is a distinct durable state and deletion is called only after proof-bearing DESTINATION_COMPLETE.` |

### Blind Spots

| Area ID | Blind spot | Decision risk | What would resolve it |
| --- | --- | --- | --- |
| `A7` | `No completed engine/recovery/fault runtime tests` | `Static ordering may hide fake-provider or Room integration errors.` | `Run deterministic host tests after resolving KSP test-variant timeout; then Room instrumentation.` |

## Prior Resolution Reconciliation

None - initial review generation for Review C.

## Receiving Handoff

- Handoff status: `Ready for receiving-code-review`
- Automatic receiving permitted: `Yes`
- Source report ID: `cr-20260919-vs03reviewc9d53`
- Scope fingerprint to recheck: `sha256:8c3e06a64a82738974eb49f7f55518435a81bdfc9a5c7c37be571a2d50902d14`
- Actionable finding IDs: `F1, F2, F3`
- Deferred finding IDs: `None`
- Actionable test-gap IDs: `T1`
- Deferred test-gap IDs: `None`
- Open question IDs: `None`
- Open coverage area IDs: `A7`
- Highest-risk verification to repeat: `Finalized-destination reconciliation, durable error update, capability rejection, and deterministic Move fault matrix.`
- Suggested implementation boundaries: `Add reconciliation inspection branch; repository failure update; route validator; fake repository/provider tests.`
- Re-review note: `Treat every finding as a claim to verify. Challenges require a counterclaim, argument, evidence, limits, and settlement criterion.`
- Chain rule: `Generation 1 is terminal. Do not automatically invoke receiving-code-review; return remaining findings to the user or product owner.`

## Report Self-Check

- `yes` Actual assessment mode and rationale are recorded.
- `yes` Every changed review-relevant or unknown-impact area appears once in Review Coverage Ledger.
- `yes` Every final finding appears once in the index and once as a matching card.
- `yes` Every Finding F# area references an existing finding.
- `yes` Every standalone test gap has a stable ID and severity.
- `yes` Every F# and T# has a unique semantic issue fingerprint and authoritative expected basis.
- `yes` Generation, trigger, parent resolution, scope mode, and receiving handoff are consistent.
- `yes` Every non-Question finding and test gap is assigned exactly once; A7 is the only open coverage area.
- `yes` Every meaningful subagent candidate has an adjudication or is explicitly absent.
- `yes` Every Not covered area has a reason and next step.
- `yes` Recommendation follows the skill mapping.
- `pending` Validator execution is performed immediately after report creation.
- `yes` Git state was not mutated during review.
