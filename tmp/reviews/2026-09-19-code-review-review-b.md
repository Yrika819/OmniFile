# Code Review Report

## Report Contract

- Report type: `code-review`
- Report ID: `cr-20260919-vs03reviewb8c42`
- Review chain ID: `rc-20260919-vs03reviewb8c42`
- Review generation: `0`
- Review trigger: `initial`
- Parent review report ID: `None`
- Parent review report path: `None`
- Parent resolution ID: `None`
- Parent resolution path: `None`
- Generated at: `2026-09-19T19:30:00Z`
- Report path: `~/Desktop/File Manager-worktrees/omnifile-durable-copy-move-v1/tmp/reviews/2026-09-19-code-review-review-b.md`
- Source skill: `code-review`
- Status: `Review incomplete`
- Git mutation during review: `None`
- Scope fingerprint: `sha256:e87eca4d691625ca05ebb6a68e018cdf81d962fc6001eef4671db1e7f2af5e1f`

## Scope

- Review date: `2026-09-19`
- Scope kind: `working tree`
- Scope description: `Review B: provider-neutral transfer boundary and Local provider implementation added after Review A.`
- Scope mode: `full frozen scope`
- Baseline: `14732fd8f120b93380e624143e534cceff6d09c4 (published VS02)`
- Target: `development/core-v1-durable-copy-move-v1 working tree`
- Changed paths: `40 relevant source/config/schema/test paths`
- Diff size: `working tree includes Room/KSP wiring, durable operations, transfer boundary, Local implementation, generated schema, and focused tests`
- Completion: `Incomplete - static review completed, but the focused Local transfer test Gradle task timed out in KSP before producing a test result.`
- Requirements consulted: `campaign provider transfer boundary, Local containment/no-symlink invariants, partial/finalization rules, conflict policy, ADR-005, Review A report`
- Prior resolution consulted: `None`
- Assumptions: `The transfer engine has not yet been added; this review evaluates the boundary and Local provider directly.`
- Excluded as unrelated: `SAF implementation, transfer engine/reconciliation, destination picker, Operations UI, final regression review, protected root worktree.`

## Review Orchestration

- Assessment subagent: `Coordinator assessment - boundary and Local provider form one cohesive security-sensitive surface; one trace is more useful than duplicated provider passes before SAF exists`
- Orchestration decision: `Single reviewer`
- Decision confidence: `high`
- Decision rationale: `The changed API and its only implementation can be traced together through locator resolution, write, finalization, and deletion; independent partitions would not add evidence while the runtime test task is unavailable.`
- Coordinator override: `None`
- Context or tool limits: `adb unavailable; Gradle test tasks intermittently time out during KSP/resource processing under the only installed JDK 26.`

### Risk Dimensions

- `Write authority: a provider handle must not permit truncating unrelated final/source files.`
- `Filesystem trust boundary: path normalization alone must not allow symlink replacement races to escape the Local root.`
- `Finalization/conflict truth: partial output must remain operation-owned and final names must never be silently overwritten.`

### Reviewer Assignments

| Reviewer | Angle | Owned surfaces | Mandatory cross-checks | Status |
| --- | --- | --- | --- | --- |
| `R1` | `Coordinator provider contract, Local security, and test adequacy` | `StorageModel.kt transfer interfaces; LocalStorageProvider transfer methods/helpers; Local transfer tests; existing Local containment tests` | `locator scope, partial ownership, no overwrite, bounded handles, source-delete ordering boundary, error mapping` | `Partial` |

### Synthesis Statement

`The coordinator independently traced every transfer method and its Local path helper. F1 is a direct write-authority defect. F2 is a root-containment race in direct path operations. Runtime execution of the new transfer tests remains unverified because Gradle timed out before test execution; this is retained as an explicit test gap rather than inferred from source inspection.`

## Review Snapshot

- Recommendation: `Changes requested`
- Completion: `Incomplete - runtime transfer tests did not complete`
- Why now: `The provider boundary controls destructive and durable filesystem effects and must be hardened before the generic engine is built on it.`
- Must-review now:
  1. `F1` `Restrict Local write handles to operation-owned partials`
  2. `F2` `Use descriptor-relative Local partial/finalization operations`
  3. `T1` `Complete focused transfer and race coverage`
- Findings count: `Blocker 0 | Major 2 | Minor 0 | Question 0`
- Standalone test gaps: `Blocker 0 | Major 0 | Minor 1`
- Coverage confidence: `medium`
- Biggest blind spot: `No completed runtime result for the new Local transfer tests.`

## Complete Findings Index

| ID | Severity | Surface | Review risk | Confidence | Origin | Verification | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `F1` | `Major` | `LocalStorageProvider.openSequentialWrite` | `A durable locator for a normal source/final file can be opened with TRUNCATE_EXISTING` | `high` | `Coordinator` | `static call trace` | `behavior; entry=provider write handle; contract=only operation-owned partial destinations may be written; effect=arbitrary durable destination can be truncated by transfer code` | `ifp-sha256:9d85061a469dc1013bffb869057985ba1f787cf9e092f1cfee19448dcf8d3eb1` | `kind:hard-invariant; strength:authoritative; evidence:campaign operation-owned partial and no unrelated destination mutation requirements` |
| `F2` | `Major` | `LocalStorageProvider.createOperationPartial` and `finalizeOperationPartial` | `Path-based create/move occurs after a check and can be redirected if an ancestor is replaced` | `medium` | `Coordinator` | `static filesystem/security trace; existing secure mutation design` | `behavior; entry=Local partial creation/finalization; contract=Local transfer remains root-contained and symlink-safe under path replacement; effect=operation can create or finalize outside the configured root` | `ifp-sha256:a1253f24467dfdd9a87e9b5cdd855632c3f15f1b89c4ae5c389742994cbdcf36` | `kind:hard-invariant; strength:authoritative; evidence:campaign Local root containment/no-symlink requirement and existing descriptor-relative mutation contract` |

## Blocker

None.

## Major

### F1 Major - Restrict Local write handles to operation-owned partials

Impact: `A future engine can pass a valid root-relative locator for a source or final file to openSequentialWrite and truncate it, violating no-overwrite and operation-owned partial invariants.`

Review reason: `The boundary names its parameter partial, but the implementation enforces only regular-file type; a type-level/API naming assumption cannot protect durable data.`

Surface: `LocalStorageProvider.openSequentialWrite`

Issue key: `behavior; entry=provider write handle; contract=only operation-owned partial destinations may be written; effect=arbitrary durable destination can be truncated by transfer code`

Issue fingerprint: `ifp-sha256:9d85061a469dc1013bffb869057985ba1f787cf9e092f1cfee19448dcf8d3eb1`

Expected basis: `kind:hard-invariant; strength:authoritative; evidence:campaign operation-owned partial and no unrelated destination mutation requirements`

Confidence: `high`

Origin: `Coordinator`

Coordinator verification: `The method resolves any locator, reads attributes, then opens it with WRITE and TRUNCATE_EXISTING. isOperationPartial is enforced in finalizeOperationPartial and deleteOperationPartial, but not in openSequentialWrite.`

Look here first:
- [`LocalStorageProvider.kt:154-170`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt#L154-L170)
- [`LocalStorageProvider.kt:218-224`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt#L218-L224)

Failure mode:
- Expected: `Only a locator created by createOperationPartial and still carrying the operation-owned partial identity can be opened for write.`
- Current: `Any regular file under the Local root can be opened and truncated when its locator is supplied.`

Evidence:
- `openSequentialWrite` has no operation-partial identity check.
- The interface is provider-neutral, so the future generic engine will rely on this boundary rather than know Local path naming itself.

Assumptions and limits:
- `The current engine is not yet present, but this is an exposed destructive API and the defect is independent of caller intent.`

Reviewer action:
`request fix`

### F2 Major - Make Local partial/finalization operations descriptor-relative

Impact: `An ancestor replacement race can make create or finalize operate on a path outside the configured root, undermining Local containment and potentially placing transfer output outside the app-private boundary.`

Review reason: `Existing Local mutation code intentionally uses SecureDirectoryStream because normalized path checks are not sufficient under replacement races; the new transfer methods regress to direct path operations.`

Surface: `LocalStorageProvider.createOperationPartial` and `finalizeOperationPartial`

Issue key: `behavior; entry=Local partial creation/finalization; contract=Local transfer remains root-contained and symlink-safe under path replacement; effect=operation can create or finalize outside the configured root`

Issue fingerprint: `ifp-sha256:a1253f24467dfdd9a87e9b5cdd855632c3f15f1b89c4ae5c389742994cbdcf36`

Expected basis: `kind:hard-invariant; strength:authoritative; evidence:campaign Local root containment/no-symlink requirement and existing descriptor-relative mutation contract`

Confidence: `medium`

Origin: `Coordinator`

Coordinator verification: `resolveDurablePath checks ancestors with NOFOLLOW_LINKS, but create uses Files.newByteChannel(partial) and finalize uses Files.move(partialPath, finalPath) after that check. The existing delete path uses withSecureParent/SecureDirectoryStream specifically to bind operations to a checked directory descriptor. The race is mechanically possible even though normal app-private use may not expose another actor.`

Look here first:
- [`LocalStorageProvider.kt:128-151`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt#L128-L151)
- [`LocalStorageProvider.kt:173-201`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt#L173-L201)

Failure mode:
- Expected: `Creation and finalization must use the same descriptor-relative, no-symlink containment boundary as destructive Local mutation.`
- Current: `Both operations resolve a Path and then perform the effect through path-based NIO calls.`

Evidence:
- Existing Local tests cover ancestor replacement for delete, demonstrating that this repository treats the race as a real security boundary.
- No transfer race test completed because the new Gradle test task timed out before execution.

Assumptions and limits:
- `The exact exploit requires an ancestor replacement during the small check/effect window; confidence is medium until a deterministic provider test demonstrates the race.`

Reviewer action:
`request fix`

## Minor

None.

## Questions

None.

## Test Gaps

| ID | Severity | Surface | Missing coverage | Risk | Origin | Evidence | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `T1` | `Minor` | `LocalStorageTransferTest and transfer boundary` | `The focused Local transfer test task timed out in KSP before producing a result, and no deterministic write-scope or ancestor-replacement regression assertion exists yet.` | `F1/F2 fixes could regress without a completed positive, conflict, owned-write, and containment test signal.` | `Coordinator` | `Gradle task :app:testDebugUnitTest --tests com.omnifile.storage.LocalStorageTransferTest timed out at :app:kspDebugUnitTestKotlin; existing Local tests do not exercise transfer methods.` | `test-gap; entry=Local transfer boundary; contract=owned-write, no-replace finalization, and symlink containment remain mechanically verified; gap=no completed focused transfer test result and no regression assertion for write-scope/race boundaries` | `ifp-sha256:2e2cca6b3bcb0f55b8fe12522257b2fa824f7d75263a6a1ba53c86c60c0848eb` | `kind:requirement; strength:authoritative; evidence:campaign provider/fault and Local security test requirements` |

## Review Coverage Ledger

| Area ID | Area / path | Touched files or entry points | Owner | Depth | Status | Result | Evidence / next step |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `A1` | `Provider-neutral transfer contract` | `StorageModel.kt:75-139` | `R1` | `contract trace` | `Reviewed - no issue found` | `No Path/Uri/provider session types cross the boundary; handles are explicitly non-durable.` | `Retain capability-specific semantics when SAF is added.` |
| `A2` | `Local durable locator encoding/resolution` | `LocalStorageProvider.kt:226-246` | `R1` | `security trace` | `Reviewed - no issue found` | `Provider ID, encoding, normalization, root containment, and NOFOLLOW ancestor checks are present.` | `Add deterministic malformed/ancestor replacement transfer tests.` |
| `A3` | `Local partial creation` | `LocalStorageProvider.kt:128-152` | `R1` | `security trace` | `Finding F2` | `Effect is path-based after a check; operation-owned naming is collision-resistant but not descriptor-bound.` | `Use secure parent descriptor for CREATE_NEW.` |
| `A4` | `Local write handle` | `LocalStorageProvider.kt:154-171` | `R1` | `contract trace` | `Finding F1` | `Bounded sequential handle exists, but any regular locator can be truncated.` | `Require isOperationPartial before opening write.` |
| `A5` | `Local finalization` | `LocalStorageProvider.kt:173-202` | `R1` | `security trace` | `Finding F2` | `No-replace precheck plus ATOMIC_MOVE is explicit, but effect is path-based and race-sensitive.` | `Use secure parent descriptor or prove equivalent atomic/no-replace behavior.` |
| `A6` | `Local source/partial deletion` | `LocalStorageProvider.kt:204-224` | `R1` | `contract trace` | `Reviewed - no issue found` | `Source delete uses existing secure mutation boundary; partial delete enforces operation-owned naming.` | `Add ordering tests in engine slice.` |
| `A7` | `Focused transfer tests` | `LocalStorageTransferTest.kt` | `R1` | `runtime verified` | `Not covered` | `Test source exists, but Gradle timed out before execution.` | `Run focused tests after KSP/resource pipeline stabilizes.` |
| `A8` | `SAF transfer route` | `SafStorageProvider.kt` unchanged | `R1` | `diff-only` | `Not review-relevant` | `Deferred to SAF boundary work and controlled provider changes.` | `Review before advertising SAF Copy/Move.` |

## Subagent Candidate Adjudication

`No subagent candidates; coordinator used a single-reviewer plan and independently re-traced the boundary.`

## Evidence Appendix

### Diff Inventory

| File or area | Classification | Semantic review area considered |
| --- | --- | --- |
| `app/src/main/java/com/omnifile/storage/StorageModel.kt` | `surface` | `provider-neutral transfer API and non-durable handle boundary` |
| `app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt` | `surface` | `Local locator, partial, write, finalization, deletion, containment` |
| `app/src/test/java/com/omnifile/storage/LocalStorageTransferTest.kt` | `test-only` | `happy path, conflict, containment` |
| Existing Local provider tests | `supporting context` | `descriptor-relative mutation/security contract` |
| Room/KSP/config/schema paths | `supporting context` | `build/dependency compatibility only; not reopened from Review A` |

### Verification Commands

- `:app:compileDebugKotlin --dependency-verification=strict --no-daemon` -> `BUILD SUCCESSFUL` after transfer boundary implementation.
- `:app:testDebugUnitTest --tests com.omnifile.storage.LocalStorageTransferTest --dependency-verification=strict --no-daemon` -> `timed out at :app:kspDebugUnitTestKotlin; no test result claimed`.
- Existing Local security tests were inspected for `SecureDirectoryStream` ancestor replacement expectations.
- `git status --short` -> review remained read-only; no Git mutation.

### Supporting Code Links

| ID | Role | Link | Why it matters |
| --- | --- | --- | --- |
| `F1` | `entry` | [`LocalStorageProvider.kt:154-170`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt#L154-L170) | `Any regular locator is opened with TRUNCATE_EXISTING.` |
| `F2` | `create` | [`LocalStorageProvider.kt:142-150`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt#L142-L150) | `Partial creation uses a path effect after a check.` |
| `F2` | `finalize` | [`LocalStorageProvider.kt:190-199`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt#L190-L199) | `Finalization uses path-based move after a no-replace precheck.` |
| `T1` | `test` | [`LocalStorageTransferTest.kt:1-103`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/test/java/com/omnifile/storage/LocalStorageTransferTest.kt#L1-L103) | `Test source exists but did not yield runtime evidence.` |

### Dismissed Coordinator Candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| `Provider-neutral handles expose live streams` | `dismissed` | `Handles are explicitly runtime-only and no handle is present in DurableLocator/OperationSnapshot/Room entities.` |
| `Local true resume must be implemented now` | `dismissed` | `The approved scope permits SAFE RESTART where resume cannot be proven; Local advertises no RESUME_WRITE and rejects append.` |
| `FinalizationResult.Unsupported for AtomicMoveNotSupported is too conservative` | `dismissed` | `The campaign explicitly requires Unsupported where safe finalization semantics are not proven.` |

### Blind Spots

| Area ID | Blind spot | Decision risk | What would resolve it |
| --- | --- | --- | --- |
| `A5` | `Concurrent ancestor replacement during create/finalize` | `Static race is clear, but deterministic reproduction and secure move API behavior remain unverified.` | `Descriptor-relative implementation plus deterministic symlink replacement tests.` |
| `A7` | `Focused transfer runtime result` | `The new path could still have test-only API or filesystem behavior defects.` | `Run the focused test task to completion or isolate test compilation from the KSP/resource timeout.` |

## Prior Resolution Reconciliation

None - initial review generation for Review B.

## Receiving Handoff

- Handoff status: `Ready for receiving-code-review`
- Automatic receiving permitted: `Yes`
- Source report ID: `cr-20260919-vs03reviewb8c42`
- Scope fingerprint to recheck: `sha256:e87eca4d691625ca05ebb6a68e018cdf81d962fc6001eef4671db1e7f2af5e1f`
- Actionable finding IDs: `F1, F2`
- Deferred finding IDs: `None`
- Actionable test-gap IDs: `None`
- Deferred test-gap IDs: `T1`
- Open question IDs: `None`
- Open coverage area IDs: `A7`
- Highest-risk verification to repeat: `Owned-write rejection, descriptor-relative create/finalize, and Local transfer tests.`
- Suggested implementation boundaries: `Local operation-partial guard; secure parent create/finalize; focused provider tests.`
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
- `yes` Every non-Question finding and standalone test gap appears exactly once in actionable or deferred handoff IDs; open areas A5/A7 are listed.
- `yes` Every meaningful subagent candidate has an adjudication or is explicitly absent.
- `yes` Every Not covered area has a reason and next step.
- `yes` Recommendation follows the skill mapping.
- `pending` Validator execution is performed immediately after report creation.
- `yes` Git state was not mutated during review.
