# Code Review Report

## Report Contract

- Report type: `code-review`
- Report ID: `cr-20260919-vs03reviewa7f31`
- Review chain ID: `rc-20260919-vs03reviewa7f31`
- Review generation: `0`
- Review trigger: `initial`
- Parent review report ID: `None`
- Parent review report path: `None`
- Parent resolution ID: `None`
- Parent resolution path: `None`
- Generated at: `2026-09-19T19:17:36Z`
- Report path: `~/Desktop/File Manager-worktrees/omnifile-durable-copy-move-v1/tmp/reviews/2026-09-19-code-review-review-a.md`
- Source skill: `code-review`
- Status: `Review complete`
- Git mutation during review: `None`
- Scope fingerprint: `sha256:f560fc22a82465d54e6c8d7af1da3464202576db9d9a9e2a574f955a01ebd40f`

## Scope

- Review date: `2026-09-19`
- Scope kind: `working tree`
- Scope description: `Review A: VS03 durable operation domain/state machine, Room v1 schema/store, generated schema, and required Gradle/KSP/Room dependency wiring from published VS02 base.`
- Scope mode: `full frozen scope`
- Baseline: `14732fd8f120b93380e624143e534cceff6d09c4 (published VS02)`
- Target: `development/core-v1-durable-copy-move-v1 working tree`
- Changed paths: `16`
- Diff size: `893 tracked additions / 70 tracked deletions, plus new durable domain, persistence, schema, and tests`
- Completion: `Complete within reviewed scope`
- Requirements consulted: `campaign durable invariants; ADR-004 durable operation state; ADR-005 safe Move finalization; Room schema/migration requirements; current StorageModel.kt`
- Prior resolution consulted: `None`
- Assumptions: `This is an initial milestone review; transfer providers, executor, reconciliation, and UI are intentionally outside Review A except where the state/store contract affects them.`
- Excluded as unrelated: `Protected root worktree, published VS02 branch history, provider transfer implementation, destination picker, Operations UI, and final VS02→VS03 regression behavior.`

## Review Orchestration

- Assessment subagent: `Coordinator assessment - cohesive state/persistence milestone with shared contracts; independent specialist partition would duplicate the same small surface`
- Orchestration decision: `Single reviewer`
- Decision confidence: `high`
- Decision rationale: `The changed behavior is cohesive and reviewable through one durable-state trace; the main risk is a single cross-cutting ordering invariant, while provider/UI surfaces are not yet changed.`
- Coordinator override: `None`
- Context or tool limits: `Physical adb unavailable; Room runtime instrumentation was not yet present at this milestone.`

### Risk Dimensions

- `Durable state and recovery ordering: an invalid transition can cause Move source deletion before destination truth.`
- `Schema/serialization/migration compatibility: Room rows must preserve active operation truth and Long counters across restart and future schema versions.`
- `Build/dependency integrity: KSP and Room must remain strict-verification and lockfile compatible with the frozen toolchain.`

### Reviewer Assignments

| Reviewer | Angle | Owned surfaces | Mandatory cross-checks | Status |
| --- | --- | --- | --- | --- |
| `R1` | `Coordinator correctness, persistence, migration, and test coverage` | `OperationModels.kt, OperationStateMachine.kt, OperationEntity.kt, OperationDao.kt, OperationDatabase.kt, OperationStore.kt, generated schema, Gradle Room/KSP wiring, focused tests` | `Move ordering, CAS behavior, Long columns, terminal discovery, strict verification, migration coverage` | `Complete` |

### Synthesis Statement

`The coordinator independently traced every state transition into OperationStore and the generated Room schema, checked the hard Move invariant against the campaign/ADR requirements, and separated the verified state-model defect from the unimplemented provider/recovery surfaces. The physical database reopen/migration path remains an explicit test gap rather than being inferred from compilation.`

## Review Snapshot

- Recommendation: `Changes requested`
- Completion: `Complete within reviewed scope`
- Why now: `The durable state contract must be safe before provider and executor code are allowed to depend on it.`
- Must-review now:
  1. `F1` `Require destination-completion evidence before promotion`
  2. `T1` `Add Room close/reopen and migration instrumentation`
- Findings count: `Blocker 0 | Major 1 | Minor 0 | Question 0`
- Standalone test gaps: `Blocker 0 | Major 1 | Minor 0`
- Coverage confidence: `high`
- Biggest blind spot: `Room runtime migration/reopen behavior is not executable in the current host-only verification.`

## Complete Findings Index

| ID | Severity | Surface | Review risk | Confidence | Origin | Verification | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `F1` | `Major` | `OperationStateMachine.requireTransition` | `Recovery can authorize Move source deletion without destination reality evidence` | `high` | `Coordinator` | `static transition trace plus focused state-machine tests` | `behavior; entry=operation recovery promotion; contract=destination completion must be durably established before Move source deletion; effect=source deletion can follow an unverified destination` | `ifp-sha256:f23eaf19b968735a30cccca8208d197e438974475b11c4578aa0eebe36517494` | `kind:hard-invariant; strength:authoritative; evidence:ADR-005 and campaign frozen durable invariant 3/4/5` |

## Blocker

None.

## Major

### F1 Major - Require destination-completion evidence before promotion

Impact: `A faulty or overly optimistic recovery caller can mark an interrupted Move as DESTINATION_COMPLETE and then legally enter SOURCE_DELETE_PENDING, exposing the original source to deletion before the final destination has been proven.`

Review reason: `The state machine is the central guard for an irreversible destructive stage; accepting a transition based only on the prior enum state is not sufficient for restart reconciliation.`

Surface: `OperationStateMachine.requireTransition and OperationStore.transition`

Issue key: `behavior; entry=operation recovery promotion; contract=destination completion must be durably established before Move source deletion; effect=source deletion can follow an unverified destination`

Issue fingerprint: `ifp-sha256:f23eaf19b968735a30cccca8208d197e438974475b11c4578aa0eebe36517494`

Expected basis: `kind:hard-invariant; strength:authoritative; evidence:ADR-005 and campaign frozen durable invariant 3/4/5`

Confidence: `high`

Origin: `Coordinator`

Coordinator verification: `The legal transition table explicitly permits INTERRUPTED -> DESTINATION_COMPLETE and RETRYABLE_FAILURE -> DESTINATION_COMPLETE. requireTransition receives no destination inspection/finalization proof. OperationStore.transition passes only current state, next state, stage, and source-delete state. A subsequent DESTINATION_COMPLETE -> SOURCE_DELETE_PENDING transition is accepted, so the missing proof is on the path to source deletion.`

Look here first:
- [`OperationStateMachine.kt:48-67`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/OperationStateMachine.kt#L48-L67)
- [`OperationStore.kt:24-55`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/persistence/OperationStore.kt#L24-L55)

Failure mode:
- Expected: `A recovery promotion to DESTINATION_COMPLETE must carry an explicit fact established by provider inspection/finalization reconciliation; only then may Move enter SOURCE_DELETE_PENDING.`
- Current: `Any caller can request DESTINATION_COMPLETE from recovery states without supplying or persisting destination-completion evidence.`

Evidence:
- `OperationStateMachineTest.interruptedOperationCanBeReconciledWithoutBlindReplay` currently proves the unsafe transition without an evidence argument.
- `OperationStateMachineTest.moveCompletesOnlyAfterSourceDeletionIsDurable` proves only the later deletion guard; it cannot protect the earlier unverified promotion.

Assumptions and limits:
- `A future reconciler may inspect the destination correctly, but the current API does not require it, so that safety depends on every caller remaining correct.`

Reviewer action:
`request fix`

## Minor

None.

## Questions

None.

## Test Gaps

| ID | Severity | Surface | Missing coverage | Risk | Origin | Evidence | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `T1` | `Major` | `OperationDatabase`, `OperationStore`, generated schema | `No Android Room test currently creates, closes, reopens, queries non-terminal rows, exercises CAS updates, validates Long counters, or establishes the migration-test foundation.` | `A schema or converter/DAO defect could erase or fail to recover active Copy/Move truth while host state tests remain green.` | `Coordinator` | [`OperationDatabase.kt:5-11`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/persistence/OperationDatabase.kt#L5-L11); Android test inventory had no operations/persistence test. | `test-gap; entry=Room operation persistence; contract=active operation truth survives database close/reopen and schema migration; gap=no Room database and migration instrumentation exercises the durable schema` | `ifp-sha256:5492b11e9a096db8e39a2ccb3bf91337dbda7fafb3165615ba311098dc65ee59` | `kind:requirement; strength:authoritative; evidence:campaign Room/database and migration-test requirements` |

## Review Coverage Ledger

| Area ID | Area / path | Touched files or entry points | Owner | Depth | Status | Result | Evidence / next step |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `A1` | `State model and legal transitions` | `OperationModels.kt`, `OperationStateMachine.kt` | `R1` | `contract trace` | `Finding F1` | `Move completion is guarded, but recovery promotion lacks destination evidence.` | `Fix transition API and add a test that unverified promotion fails.` |
| `A2` | `Durable locator and snapshot shape` | `OperationModels.kt`, `OperationEntity.kt` | `R1` | `dependency trace` | `Reviewed - no issue found` | `Live Android/runtime objects are absent; provider ID, encoding, value, Long counters, source-delete facts, errors, and timestamps are represented.` | `Add malformed-row/reconciliation tests in the persistence slice.` |
| `A3` | `Room schema and versioning` | `OperationDatabase.kt`, generated `app/schemas/.../1.json` | `R1` | `contract trace` | `Not covered` | `Static schema shape was reviewed, but runtime close/reopen and migration behavior was not executable in this milestone.` | `T1: execute database reopen and future-migration tests.` |
| `A4` | `DAO/store durability and CAS` | `OperationDao.kt`, `OperationStore.kt` | `R1` | `dependency trace` | `Reviewed - no issue found` | `CAS state update and non-terminal query are present; source-delete completion is state-guarded.` | `T1: instrument actual SQLite behavior and concurrency/CAS result.` |
| `A5` | `Room/KSP build integration` | `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`, catalogs/locks/verification | `R1` | `runtime verified` | `Reviewed - no issue found` | `Room 2.8.4/KSP 2.2.10-2.0.2 compile under strict verification; exact hashes and locks are present.` | `Retain strict verification in all later tasks; address experimental built-in-Kotlin warning only if toolchain permits.` |
| `A6` | `State-model unit tests` | `OperationStateMachineTest.kt` | `R1` | `targeted test` | `Finding F1` | `Focused tests pass but encode the unsafe recovery transition and do not cover persistence.` | `Update test after F1 fix; add Room instrumentation for T1.` |
| `A7` | `Provider transfer/reconciliation/UI` | `Not yet changed` | `R1` | `diff-only` | `Not review-relevant` | `Deferred to Reviews B-E; no claims made about these surfaces.` | `Review at their approved milestones.` |

## Subagent Candidate Adjudication

`No subagent candidates; coordinator selected a single-reviewer plan because this milestone is cohesive and the review phase remained read-only.`

## Evidence Appendix

### Diff Inventory

| File or area | Classification | Semantic review area considered |
| --- | --- | --- |
| `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml` | `dependency/config` | `Room/KSP compatibility and build reproducibility` |
| `app/build.gradle.kts`, `app/gradle.lockfile`, `gradle/verification-metadata.xml` | `dependency/config` | `strict verification, lock state, processor classpaths` |
| `app/schemas/com.omnifile.operations.persistence.OperationDatabase/1.json` | `generated` | `schema shape, Long affinities, version identity` |
| `app/src/main/java/com/omnifile/operations/OperationModels.kt` | `surface` | `durable representation and serialization boundary` |
| `app/src/main/java/com/omnifile/operations/OperationStateMachine.kt` | `surface` | `transition legality and destructive ordering` |
| `app/src/main/java/com/omnifile/operations/persistence/OperationEntity.kt` | `persistence` | `Room row conversion and durable locators` |
| `app/src/main/java/com/omnifile/operations/persistence/OperationDao.kt` | `persistence` | `queries, CAS, cancellation, terminal filtering` |
| `app/src/main/java/com/omnifile/operations/persistence/OperationDatabase.kt` | `persistence` | `database version and migration boundary` |
| `app/src/main/java/com/omnifile/operations/persistence/OperationStore.kt` | `persistence` | `state/store API and CAS integration` |
| `app/src/test/java/com/omnifile/operations/OperationStateMachineTest.kt` | `test-only` | `positive/negative state-machine coverage` |

### Verification Commands

- `:app:compileDebugKotlin --dependency-verification=strict --no-daemon` -> `BUILD SUCCESSFUL`
- `:app:testDebugUnitTest --tests com.omnifile.operations.OperationStateMachineTest --dependency-verification=strict --no-daemon` -> `BUILD SUCCESSFUL`
- Generated Room schema inspected at `app/schemas/.../1.json` -> `version=1`, `bytesCompleted` and `expectedBytes` have INTEGER affinity.
- `git status --short` -> only intended Gradle/schema/operations files present; no Git mutation during review.

### Supporting Code Links

| ID | Role | Link | Why it matters |
| --- | --- | --- | --- |
| `F1` | `entry` | [`OperationStateMachine.kt:48-67`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/OperationStateMachine.kt#L48-L67) | `Recovery states can promote directly to destination complete.` |
| `F1` | `risk` | [`OperationStateMachine.kt:84-100`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/OperationStateMachine.kt#L84-L100) | `Only enum/source-delete facts are checked; destination evidence is absent.` |
| `F1` | `caller` | [`OperationStore.kt:24-55`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/persistence/OperationStore.kt#L24-L55) | `Store API has no destination-completion proof parameter.` |
| `T1` | `coverage gap` | [`OperationDatabase.kt:5-11`](file://~/Desktop/File%20Manager-worktrees/omnifile-durable-copy-move-v1/app/src/main/java/com/omnifile/operations/persistence/OperationDatabase.kt#L5-L11) | `The Room database exists, but no Android database/migration test exercises it.` |

### Dismissed Coordinator Candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| `One-row-per-file lacks a separate batch aggregate table` | `dismissed` | `The approved scope requires independent per-item truth and permits a batch/group ID with aggregate status derived later; no aggregate table is required for this milestone.` |
| `String enum columns are unsafe by themselves` | `dismissed` | `Explicit names plus future migrations are an acceptable frozen design; malformed/corrupt rows remain a later reconciliation/error-path test.` |
| `Experimental built-in-Kotlin compatibility flag is itself a release blocker` | `dismissed` | `It is a toolchain compatibility setting, compile passes under strict verification, and no safer compatible Room/KSP combination is established yet.` |

### Blind Spots

| Area ID | Blind spot | Decision risk | What would resolve it |
| --- | --- | --- | --- |
| `A3` | `Actual SQLite close/reopen and migration behavior` | `A schema can compile yet fail at runtime or lose active records.` | `T1 Android Room database and migration tests.` |
| `A5` | `Java 26/KSP execution is unusually slow for some Gradle tasks` | `Long-running verification may obscure failures or make CI behavior differ.` | `Repeat clean strict build/lint later; use a supported project JDK if the environment gains one.` |

## Prior Resolution Reconciliation

None - initial review generation.

## Receiving Handoff

- Handoff status: `Ready for receiving-code-review`
- Automatic receiving permitted: `Yes`
- Source report ID: `cr-20260919-vs03reviewa7f31`
- Scope fingerprint to recheck: `sha256:f560fc22a82465d54e6c8d7af1da3464202576db9d9a9e2a574f955a01ebd40f`
- Actionable finding IDs: `F1`
- Deferred finding IDs: `None`
- Actionable test-gap IDs: `T1`
- Deferred test-gap IDs: `None`
- Open question IDs: `None`
- Open coverage area IDs: `A3`
- Highest-risk verification to repeat: `Room instrumented reopen/migration test plus a negative unverified DESTINATION_COMPLETE transition test.`
- Suggested implementation boundaries: `OperationStateMachine proof parameter; OperationStore proof forwarding; OperationStateMachineTest; OperationDatabaseMigrationTest.`
- Re-review note: `Treat every finding as a claim to verify. Challenges require a counterclaim, argument, evidence, limits, and settlement criterion.`
- Chain rule: `Generation 1 is terminal. Do not automatically invoke receiving-code-review; return remaining findings to the user or product owner.`

## Report Self-Check

- `yes` Actual assessment mode and rationale are recorded: coordinator, delegated assessor, or unavailable fallback.
- `yes` Every changed review-relevant or unknown-impact area appears once in Review Coverage Ledger.
- `yes` Every final finding appears once in the index and once as a matching card.
- `yes` Every Finding F# area references an existing finding.
- `yes` Every standalone test gap has a stable ID and severity.
- `yes` Every F# and T# has a unique semantic issue fingerprint and an authoritative expected basis.
- `yes` Generation, trigger, parent resolution, scope mode, and receiving handoff satisfy the bounded chain contract.
- `yes` Generation 1 reconciliation is not applicable to generation 0.
- `yes` Every non-Question finding and standalone test gap appears exactly once in actionable or deferred handoff IDs; no questions/open coverage beyond A3 are omitted.
- `yes` Every meaningful subagent candidate has an adjudication or is explicitly absent.
- `yes` Every Not covered area has a reason and next step; A3 is explicitly listed in Open coverage area IDs.
- `yes` Recommendation follows the skill mapping.
- `pending` Validator execution is performed immediately after report creation.
- `yes` Git state was not mutated during review.
