# Code Review Report

## Report Contract

- Report type: `code-review`
- Report ID: `cr-20260920-postruntimebc`
- Review chain ID: `rc-20260920-postruntimebc`
- Review generation: `0`
- Review trigger: `initial`
- Parent review report ID: `None`
- Parent review report path: `None`
- Parent resolution ID: `None`
- Parent resolution path: `None`
- Generated at: `2026-09-20T23:20:00+09:00`
- Report path: `~/Desktop/File Manager-worktrees/omnifile-saf-transfer-v1/tmp/reviews/2026-09-20-code-review-post-runtime-bc.md`
- Source skill: `code-review`
- Status: `Review complete`
- Git mutation during review: `None`
- Scope fingerprint: `Unavailable - runtime evidence was supplied in-thread and the target worktree is outside the registered file-tool root`

## Scope

- Review date: `2026-09-20`
- Scope kind: `commit range plus working tree`
- Scope description: `Post-runtime Review B/C of durable SAF transfer paths and affected engine/recovery chains.`
- Scope mode: `full frozen scope`
- Baseline: `a95b3e28f47954b879b389ce6d5a5f17d4704407`
- Target: `d6a82896b074cfbf2a5f958c086e462906157d0 plus uncommitted TestDocumentsProvider.java fix`
- Changed paths: `durable operation engine, SAF provider, controlled provider, relevant tests`
- Diff size: `reviewed by targeted trace`
- Completion: `Complete within reviewed scope; real SAF destination routes remain intentionally unsupported`
- Requirements consulted: `VS04 brief, frozen architecture/ADR documents, runtime evidence, controlled 18-test result, Pixel evidence`
- Prior resolution consulted: `None`
- Assumptions: `ExternalStorageProvider evidence is provider-scoped and does not authorize generic SAF finalization.`
- Excluded as unrelated: `directories, background execution, cloud providers, and VS05 scope`

## Review Orchestration

- Assessment subagent: `Coordinator assessment - durable operation and provider semantics are high-risk and were independently checked by two read-only specialists`
- Orchestration decision: `Parallel specialists`
- Decision confidence: `high`
- Decision rationale: `Operation/recovery and provider/source-mutation paths have distinct failure contracts.`
- Coordinator override: `None`
- Context or tool limits: `No final rebuilt APK after the runtime harness fix was available during this review; supplied runtime evidence was nevertheless mechanically captured.`

### Risk Dimensions

- `Finalization ambiguity and duplicate-destination recovery.`
- `Unknown-size verification before destructive Move source deletion.`
- `SAF source mutation/version strength between enqueue, restart, and delete.`
- `Provider grant loss and durable error classification.`

### Reviewer Assignments

| Reviewer | Angle | Owned surfaces | Mandatory cross-checks | Status |
| --- | --- | --- | --- | --- |
| `R1` | `durable engine/state/recovery` | `OperationManager.kt, TransferEngine.kt, OperationStateMachine.kt, OperationModels.kt` | `restart, finalization record, Move ordering` | `Complete` |
| `R2` | `SAF source/provider semantics` | `SafStorageProvider.kt, FilesScreen.kt, controlled tests` | `grant, source mutation, non-seekable/unknown size` | `Complete` |

### Synthesis Statement

`The coordinator independently re-verified each accepted candidate against the current code and the supplied runtime evidence. The happy paths pass, but the findings below remain actionable before claiming a supported destructive SAF Move or finalization route.`

## Review Snapshot

- Recommendation: `Changes requested`
- Completion: `Complete within reviewed scope`
- Why now: `Runtime evidence proves the controlled harness and real SAF→Local happy paths, while review exposed restart and destructive-safety gaps not covered by those happy paths.`
- Must-review now:
  1. `F1` Unknown-size destination verification before source deletion
  2. `F2` Persisted finalization reconciliation on retry/restart
  3. `F3` SAF source mutation proof for destructive Move
- Findings count: `Blocker 0 | Major 4 | Minor 1 | Question 0`
- Standalone test gaps: `Blocker 0 | Major 5 | Minor 0`
- Coverage confidence: `medium`
- Biggest blind spot: `No final-APK runtime execution of revocation, adversarial same-metadata mutation, or ambiguous-finalization restart cases`

## Complete Findings Index

| ID | Severity | Surface | Review risk | Confidence | Origin | Verification | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `F1` | `Major` | `OperationManager.verifyDurableDestination` | `Unknown-size Move can verify only existence/bytesCompleted state and delete source after a damaged destination` | `medium` | `R1` | `static trace` | `behavior; entry=unknown-size Move recovery; contract=observed destination bytes must be compared before source deletion; effect=source deletion after unproven destination integrity` | `ifp-sha256:d4cbb00f523d5bd020c33aff940bb73480dfd4148cc72558a6ac3e0567c5a317` | `kind:hard-invariant; strength:authoritative; evidence:VS04 unknown-size and Move ordering requirements` |
| `F2` | `Major` | `OperationManager finalization recovery` | `Retry can recopy/conflict after finalization record was persisted` | `high` | `R1` | `static trace` | `behavior; entry=post-finalization retry; contract=persisted final identity is reconciled before transfer replay; effect=false conflict or duplicate-destination path` | `ifp-sha256:9cc2ea2aaf477a1b96de3e98eab48775084975c47881f8c3bb25e64c10ca0c00` | `kind:hard-invariant; strength:authoritative; evidence:ambiguous-finalization requirements` |
| `F3` | `Major` | `OperationManager.reconcileNonTerminal` | `Repeated ambiguous finalization can attempt an illegal INTERRUPTED→INTERRUPTED transition and crash recovery` | `high` | `R1` | `static trace` | `behavior; entry=ambiguous-finalization restart; contract=ambiguous state remains reconcilable without crash; effect=restart exception` | `ifp-sha256:ee50832e8b575141789d9b71c1c56dbcfcebed6b89b2738d6a3df7dc9547b8bb` | `kind:hard-invariant; strength:authoritative; evidence:restart/reconciliation requirements` |
| `F4` | `Major` | `SafStorageProvider source version` | `Metadata token does not prove content identity for destructive SAF Move` | `medium` | `R2` | `static trace plus real unchanged happy path` | `behavior; entry=SAF Move source deletion; contract=source mutation is ruled out before delete; effect=mutated source deleted after copied destination` | `ifp-sha256:65e4c2990a19da6ee3b8a1f705b7bb24d51d387fcb5d8cc2582851dc440801bf` | `kind:hard-invariant; strength:authoritative; evidence:SAF source mutation requirements and ADR-002` |
| `F5` | `Minor` | `FinalizationRecord` | `Delimiter encoding cannot represent an otherwise valid single-component name containing U+001F` | `medium` | `R1` | `static trace` | `behavior; entry=finalization locator persistence; contract=valid provider locator/name round-trips; effect=restart treats finalized destination as ambiguous` | `ifp-sha256:0109e2813fbf1defeee2758fb3e1316103b5d731863ae495e9265ebbbe262cd2` | `kind:hard-invariant; strength:authoritative; evidence:durable locator persistence requirements` |

## Blocker

None.

## Major

### F1 Major - Unknown-size destination verification must use the durable observed byte count

Impact: `A destructive Move with unknown source size must not delete the source unless the final destination byte count is proven.`

Review reason: `The current verification compares only when expectedBytes is non-null; unknown-size operations need a conservative durable comparison before source deletion.`

Surface: `OperationManager.verifyDurableDestination`
Issue key: `behavior; entry=unknown-size Move recovery; contract=observed destination bytes must be compared before source deletion; effect=source deletion after unproven destination integrity`
Issue fingerprint: `ifp-sha256:d4cbb00f523d5bd020c33aff940bb73480dfd4148cc72558a6ac3e0567c5a317`
Expected basis: `kind:hard-invariant; strength:authoritative; evidence:VS04 unknown-size and Move ordering requirements`
Confidence: `medium`
Origin: `R1`
Coordinator verification: `Independent code-path trace and supplied runtime evidence.`

Look here first:
- [`OperationManager.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/operations/OperationManager.kt#L331)
- [`VERTICAL_SLICE_04_SAF_DURABLE_TRANSFER.md`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/docs/production/VERTICAL_SLICE_04_SAF_DURABLE_TRANSFER.md#L89)

Failure mode:
- Expected: `Unknown-size transfer retains and compares actual observed bytes before destructive completion.`
- Current: `The final verification conditional skips the comparison when expectedBytes is null.`

Reviewer action: `request fix and focused unknown-size recovery test`

### F2 Major - Persisted finalization identity must be reconciled before replay

Impact: `A provider can have completed rename while the acknowledgement or post-finalize query failed; retry must inspect the persisted final identity instead of treating the intended name as a fresh conflict.`

Review reason: `The manager records finalizedDestination on a failed transfer, but generic retry enters TRANSFERRING and TransferEngine checks name conflict first.`

Surface: `OperationManager.execute / TransferEngine.execute`
Issue key: `behavior; entry=post-finalization retry; contract=persisted final identity is reconciled before transfer replay; effect=false conflict or duplicate-destination path`
Issue fingerprint: `ifp-sha256:9cc2ea2aaf477a1b96de3e98eab48775084975c47881f8c3bb25e64c10ca0c00`
Expected basis: `kind:hard-invariant; strength:authoritative; evidence:ambiguous-finalization requirements`
Confidence: `medium`
Origin: `R1`
Coordinator verification: `Independent code-path trace and supplied runtime evidence.`

Look here first:
- [`OperationManager.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/operations/OperationManager.kt#L120)
- [`TransferEngine.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/operations/TransferEngine.kt#L156)

Failure mode:
- Expected: `Reconcile a persisted final locator, then complete or retain conservative ambiguity.`
- Current: `Retry can re-enter transfer and observe the already-finalized name as a conflict.`

Reviewer action: `request fix and restart/retry test`

### F3 Major - Ambiguous finalization must not crash on repeated reconciliation

Impact: `Process recreation can throw instead of preserving an ambiguous operation for later reconciliation.`

Review reason: `The current failure path can ask the Room state machine to transition INTERRUPTED to itself.`

Surface: `OperationManager.reconcileNonTerminal / reconcileFinalizing`
Issue key: `behavior; entry=ambiguous-finalization restart; contract=ambiguous state remains reconcilable without crash; effect=restart exception`
Issue fingerprint: `ifp-sha256:ee50832e8b575141789d9b71c1c56dbcfcebed6b89b2738d6a3df7dc9547b8bb`
Expected basis: `kind:hard-invariant; strength:authoritative; evidence:restart/reconciliation requirements`
Confidence: `medium`
Origin: `R1`
Coordinator verification: `Independent code-path trace and supplied runtime evidence.`

Look here first:
- [`OperationManager.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/operations/OperationManager.kt#L208)
- [`OperationStateMachine.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/operations/OperationStateMachine.kt#L49)

Failure mode:
- Expected: `An ambiguous finalization remains interrupted and retryable/reconcilable.`
- Current: `Repeated reconciliation can record the same state as a transition and throw.`

Reviewer action: `request fix and repeated-reconciliation test`

### F4 Major - Destructive SAF Move needs a stronger source mutation proof

Impact: `A SAF source can change while retaining the current synthetic metadata token, allowing deletion of a source that was not the copied snapshot.`

Review reason: `Document ID + size + modified time + MIME type are strongest available facts in the current adapter but are not a content/version guarantee.`

Surface: `SafStorageProvider.inspectTransfer / OperationManager.attemptSourceDeletion`
Issue key: `behavior; entry=SAF Move source deletion; contract=source mutation is ruled out before delete; effect=mutated source deleted after copied destination`
Issue fingerprint: `ifp-sha256:65e4c2990a19da6ee3b8a1f705b7bb24d51d387fcb5d8cc2582851dc440801bf`
Expected basis: `kind:hard-invariant; strength:authoritative; evidence:SAF source mutation requirements and ADR-002`
Confidence: `medium`
Origin: `R1`
Coordinator verification: `Independent code-path trace and supplied runtime evidence.`

Look here first:
- [`SafStorageProvider.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/storage/SafStorageProvider.kt#L164)
- [`OperationManager.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/operations/OperationManager.kt#L272)

Failure mode:
- Expected: `Move is advertised only when the provider can establish a safe source version proof; otherwise Move remains Unsupported.`
- Current: `Any writable SAF file with DELETE flags can reach Move, even though its token is metadata-derived.`

Reviewer action: `request provider-scoped Move gate or stronger snapshot proof`

## Minor

### F5 Minor - FinalizationRecord needs an escaped/versioned field encoding

Impact: `A valid name containing the current delimiter can make persisted finalization truth undecodable after restart.`

Review reason: `Durable locator payloads require an evolution-safe encoding.`

Surface: `FinalizationRecord.encode/decode`
Issue key: `behavior; entry=finalization locator persistence; contract=valid provider locator/name round-trips; effect=restart treats finalized destination as ambiguous`
Issue fingerprint: `ifp-sha256:0109e2813fbf1defeee2758fb3e1316103b5d731863ae495e9265ebbbe262cd2`
Expected basis: `kind:hard-invariant; strength:authoritative; evidence:durable locator persistence requirements`
Confidence: `medium`
Origin: `R1`
Coordinator verification: `Independent code-path trace and supplied runtime evidence.`

Look here first:
- [`OperationModels.kt`](~/Desktop/File%20Manager-worktrees/omnifile-saf-transfer-v1/app/src/main/java/com/omnifile/operations/OperationModels.kt#L68)

Failure mode:
- Expected: `All valid provider locator fields round-trip, while legacy records remain readable.`
- Current: `U+001F is unescaped and the decoder requires exactly four split fields.`

Reviewer action: `add escaped v2 encoding with legacy decoder and round-trip test`

## Questions

None.

## Test Gaps

| ID | Severity | Surface | Missing coverage | Risk | Origin | Evidence | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `T1` | `Major` | `unknown-size Move` | `No restart test verifies destination bytes and source-delete gate when source size is unknown` | `Data loss can escape happy-path tests` | `R1` | `controlled suite 18/18 did not include this exact post-runtime scenario` | `test-gap; entry=unknown-size Move; contract=source deletion follows durable byte verification; gap=unknown-size restart assertion` | `ifp-sha256:29cb4bfc075b65d9e7d19c1691f524d9040de8b5aed412c7ac1f67e0f265b967` | `kind:requirement; strength:authoritative; evidence:VS04 unknown-size/Move requirements` |
| `T2` | `Major` | `finalization recovery` | `No test covers rename success followed by missing final acknowledgement and retry` | `Duplicate/conflict or source-deletion risk` | `R1` | `runtime happy paths only` | `test-gap; entry=finalization recovery; contract=returned identity is reconciled; gap=post-finalize retry` | `ifp-sha256:75424380798e8a6547df88f261bc40af048ef498ba77235940e5b826ad7f24b5` | `kind:requirement; strength:authoritative; evidence:ambiguous-finalization requirements` |
| `T3` | `Major` | `SAF source mutation` | `No adversarial test preserves ID/size/time/MIME while changing content` | `Move may delete a mutated source` | `R2` | `no such controlled fault in supplied run` | `test-gap; entry=SAF source mutation; contract=mutation cannot cause destructive false completion; gap=same-metadata mutation` | `ifp-sha256:0171ee1d90a3ffb4969f992c9e1c5d63846b675069293f10995fb1a65e4cd0b5` | `kind:requirement; strength:authoritative; evidence:SAF source mutation requirements` |
| `T4` | `Minor` | `finalization record` | `No round-trip test for delimiter-bearing valid names` | `Restart ambiguity for a narrow valid-name case` | `R1` | `static trace` | `test-gap; entry=finalization record; contract=valid names round-trip; gap=delimiter case` | `ifp-sha256:db22398f24f661ce2a683aac7dbe9ddb9fa75912d43a143f585e6aa786f97b9d` | `kind:hard-invariant; strength:authoritative; evidence:durable locator requirements` |
| `T5` | `Major` | `grant revocation` | `No process-recreation test covers persisted grant revocation and retry classification` | `Permission loss can become stale/terminal or crash` | `R2` | `Pixel grant acquisition only; no revocation run` | `test-gap; entry=SAF grant revocation; contract=truthful permission/retry state without source deletion; gap=revoked-after-restart` | `ifp-sha256:ce393ff94aecf6c2b6f4422ff0f3f84e4935d1703b7c2b8466911029b734effc` | `kind:requirement; strength:authoritative; evidence:grant revocation requirements` |

## Review Coverage Ledger

| Area ID | Area / path | Touched files or entry points | Owner | Depth | Status | Result | Evidence / next step |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `A1` | `SAF transfer engine verification` | `OperationManager.kt, TransferEngine.kt` | `R1` | `contract trace` | `Finding F1` | `Finalization and unknown-size recovery need fixes.` | `Add focused unit tests.` |
| `A2` | `SAF source mutation/delete` | `SafStorageProvider.kt, OperationManager.kt` | `R2` | `contract trace` | `Finding F4` | `Metadata token is not proof of content identity.` | `Gate Move or add stronger provider proof.` |
| `A3` | `Finalization persistence` | `OperationModels.kt, OperationStore.kt` | `R1` | `dependency trace` | `Finding F5` | `Delimiter encoding has a narrow round-trip hole.` | `Version/escape encoding.` |
| `A4` | `Controlled provider runtime` | `TestDocumentsProvider.java, Saf*InstrumentedTest.kt` | `R2` | `runtime verified` | `Reviewed - no issue found` | `18/18 direct runner tests passed.` | `Keep runtime evidence in final docs.` |
| `A5` | `Real SAF→Local` | `MainActivity.kt, FilesViewModel.kt, OperationsPanel.kt` | `R2` | `runtime verified` | `Reviewed - no issue found` | `Real 41 B Copy and 35 B Move happy paths completed; source absent after Move.` | `Re-run on final APK after fixes; Move may be gated unsupported.` |
| `A6` | `SAF destination` | `SafStorageProvider.kt, FilesScreen.kt` | `R1` | `runtime verified` | `Reviewed - no issue found` | `ExternalStorageProvider grant mode/persisted 0x3; destination confirm disabled under finalizationProven=false.` | `Do not enable generic SAF finalization.` |

## Subagent Candidate Adjudication

| Candidate ID | Proposed by | Decision | Final ID | Coordinator evidence | Reason |
| --- | --- | --- | --- | --- | --- |
| `R1-C1` | `R1` | `accepted` | `F2` | `execute` and `reconcileNonTerminal` trace | `FinalizationDescription is persisted but not prioritized on retry.` |
| `R1-C2` | `R1` | `accepted` | `F3` | `OperationStateMachine` transition trace | `Repeated ambiguous reconciliation can be illegal.` |
| `R2-C1` | `R2` | `accepted` | `F4` | `SafStorageProvider` token trace | `Writable/delete flags do not prove source content immutability.` |
| `R1-C3` | `R1` | `accepted` | `F1` | `verifyDurableDestination` conditional trace | `Unknown-size verification must compare durable observed bytes.` |
| `R1-C4` | `R1` | `accepted` | `F5` | `FinalizationRecord` delimiter trace | `Valid delimiter-bearing fields do not round-trip.` |
| `R2-C2` | `R2` | `accepted` | `T5` | `grant acquisition evidence versus missing revocation run` | `Required runtime gap remains.` |

## Evidence Appendix

### Diff Inventory

| File or area | Classification | Semantic review area considered |
| --- | --- | --- |
| `app/src/main/java/com/omnifile/operations/OperationManager.kt` | `surface` | `state, persistence, ordering, retry` |
| `app/src/main/java/com/omnifile/operations/TransferEngine.kt` | `surface` | `I/O, verification, finalization` |
| `app/src/main/java/com/omnifile/operations/OperationModels.kt` | `surface` | `durable serialization` |
| `app/src/main/java/com/omnifile/storage/SafStorageProvider.kt` | `surface` | `identity, grants, source mutation, provider flags` |
| `app/src/androidTest/java/com/omnifile/storage/TestDocumentsProvider.java` | `test-only` | `provider protocol and Android 16 document ID semantics` |

### Verification Commands

- `adb devices -l` -> `Pixel 7a API 36 device`
- `adb am instrument -e class SafStorageProviderInstrumentedTest,SafTransferRuntimeInstrumentedTest` -> `Tests run: 18, Failures: 0, Skipped: 0, Result: OK`
- `dumpsys activity permissions` -> `ExternalStorageProvider root and destination URI grants mode=0x3 persisted=0x3`
- real production UI -> `SAF→Local Copy 41 B COMPLETE; SAF→Local Move 35 B COMPLETE; Move source absent; SAF destination Use this folder disabled`

### Dismissed Coordinator Candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| `Generic SAF destination can be globally enabled from ExternalStorageProvider result` | `dismissed` | `finalizationProven=false remains default and destination confirm was disabled on Pixel` |
| `Controlled 18/18 proves universal SAF` | `dismissed` | `controlled provider is scoped evidence only` |

## Prior Resolution Reconciliation

None - this is the frozen post-runtime generation-0 checkpoint.

## Receiving Handoff

- Handoff status: `Ready for receiving-code-review`
- Automatic receiving permitted: `Yes`
- Source report ID: `cr-20260920-postruntimebc`
- Scope fingerprint to recheck: `Unavailable - runtime evidence was supplied in-thread and the target worktree is outside the registered file-tool root`
- Actionable finding IDs: `F1,F2,F3,F4,F5`
- Deferred finding IDs: `None`
- Actionable test-gap IDs: `T1,T2,T3,T4,T5`
- Deferred test-gap IDs: `None`
- Open question IDs: `None`
- Open coverage area IDs: `None`
- Highest-risk verification to repeat: `unknown-size/restart, ambiguous finalization, source mutation, grant revocation`
- Suggested implementation boundaries: `Keep Local provider and generic host semantics intact; gate SAF Move and fix recovery in OperationManager.`
- Re-review note: `After fixes, run generation-1 post-runtime review over affected execution chains.`
- Chain rule: `Generation 1 is terminal.`

## Report Self-Check

- `yes` Scope, risk, orchestration, findings, test gaps, coverage, evidence, and handoff are present.
- `yes` Findings are frozen before implementation.
- `yes` Git state was not mutated during review.
- `pending` Validator must be run before closure.
