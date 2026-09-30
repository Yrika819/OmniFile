# Code Review Report

## Report Contract

- Report type: `code-review`
- Report ID: `cr-20260921-63faae30`
- Review chain ID: `rc-20260921-63faae30`
- Review generation: `0`
- Review trigger: `initial`
- Parent review report ID: `None`
- Parent review report path: `None`
- Parent resolution ID: `None`
- Parent resolution path: `None`
- Generated at: `2026-09-21T00:50:09Z`
- Report path: `~/Desktop/File Manager-worktrees/omnifile-saf-transfer-v1/tmp/reviews/2026-09-21-code-review-post-unlock-63faae30.md`
- Source skill: `code-review`
- Status: `Review complete`
- Git mutation during review: `None`
- Scope fingerprint: `sha256:0c8f4cb2f61230a5ea3818a9fc27a030b101923d100e97ba7f862e66ef9d3c8c`

Treat this completed report as a fixed review input. It must not be rewritten during receiving or implementation.

## Scope

- Review date: `2026-09-21`
- Scope kind: `commit range plus physical runtime evidence`
- Scope description: `Final VS04 whole-diff review from the published VS03 base through b3fd230, reconciled with the normally unlocked Pixel 7a final Compose/full instrumentation run and bounded disposable-tree UI smoke.`
- Scope mode: `full frozen scope`
- Baseline: `a95b3e28f47954b879b389ce6d5a5f17d4704407`
- Target: `b3fd23078bc26857e80cbc4018ba5019d19dc04c`
- Changed paths: `43`
- Diff size: `6124 additions, 186 deletions`
- Completion: `Complete within reviewed scope`
- Requirements consulted: `VS04 runtime closure brief; SAF durability invariants; persisted-grant and provider identity requirements; existing frozen Review A-E and post-hardening reports`
- Prior resolution consulted: `None`
- Assumptions: `SAF to Local Copy is the only claimed production SAF route; SAF Move, Local to SAF, and SAF to SAF remain Unsupported; finalizationProven=false remains the default.`
- Excluded as unrelated: `VS05, directory/archive/background/cloud/root/media/search/Share work`

## Review Orchestration

- Assessment subagent: `Coordinator assessment - cohesive final VS04 diff with prior findings already isolated; current work adds physical evidence rather than a new implementation frontier`
- Orchestration decision: `Single reviewer`
- Decision confidence: `high`
- Decision rationale: `The final review must reconcile one bounded storage/UI implementation with its durable contracts and new physical evidence. A single coordinator can trace the full path without duplicating the frozen specialist reviews.`
- Coordinator override: `None`
- Context or tool limits: `No material limit remained for the requested scope; the Pixel was normally unlocked and the direct runner completed.`

### Risk Dimensions

- `Provider disappearance, permission loss, true NotFound, stale identity, and durable retry classification.`
- `Finalization identity, partial ownership, restart reconciliation, duplicate prevention, and Move source-delete ordering.`
- `Persisted SAF grant validation, explicit equal-depth tree intent, picker role separation, and authority containment.`
- `User-visible route gating, Compose hierarchy/tappability, Operations UI truth, and regression evidence.`

### Reviewer Assignments

| Reviewer | Angle | Owned surfaces | Mandatory cross-checks | Status |
| --- | --- | --- | --- | --- |
| `R1` | `Coordinator correctness/security/reliability/UI` | `All VS04 production, test, documentation, and review paths` | `Provider error taxonomy; grant restoration; finalization gates; Move ordering; full Pixel evidence; VS03 regression` | `Complete` |

### Synthesis Statement

`The coordinator independently re-read the changed storage, operation, picker, UI, test, and documentation paths, reconciled the prior provider-disappearance and equal-depth findings, and verified the former physical T1 against the current normally unlocked Pixel run. No unresolved code finding, standalone test gap, or coverage blind spot remains within the requested scope.`

## Review Snapshot

- Recommendation: `Pass`
- Completion: `Complete within reviewed scope`
- Why now: `The final APK's previously blocked Compose tests now pass 4/4, the complete direct instrumentation suite passes 31/31, and the disposable SAF UI smoke confirms conservative gating without enabling unsupported routes.`
- Must-review now: `None identified`
- Findings count: `Blocker 0 | Major 0 | Minor 0 | Question 0`
- Standalone test gaps: `Blocker 0 | Major 0 | Minor 0`
- Coverage confidence: `high`
- Biggest blind spot: `None identified within the requested VS04 scope; real user-provider revocation remains intentionally deferred and is covered by the controlled provider.`

## Complete Findings Index

No code-review findings identified in the reviewed scope.

## Blocker

None.

## Major

None.

## Minor

None.

## Questions

None.

## Test Gaps

None.

## Review Coverage Ledger

| Area ID | Area / path | Touched files or entry points | Owner | Depth | Status | Result | Evidence / next step |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `A1` | `SAF locator, containment, and provider error taxonomy` | `SafStorageProvider.kt, SafDurableLocatorCodec.kt, StorageModel.kt` | `R1` | `dependency trace + controlled runtime` | `Reviewed - no issue found` | `ProviderUnavailable, PermissionDenied, NotFound, StaleReference, and IoFailure remain distinct at the strongest proven boundary. | `Controlled provider and full Pixel suites passed; no generic finalization trust added.` |
| `A2` | `Durable transfer and finalization` | `TransferEngine.kt, OperationManager.kt, OperationStateMachine.kt, OperationModels.kt` | `R1` | `deep state trace + host/runtime` | `Reviewed - no issue found` | `Partial ownership, returned final identity, interruption reconciliation, no-recopy behavior, and terminal truth remain conservative. | `69/69 host; controlled SAF transfer runtime; SAF destination remains Unsupported.` |
| `A3` | `Move ordering and source deletion` | `OperationManager.kt, OperationStateMachine.kt, LocalStorageProvider.kt, SafStorageProvider.kt` | `R1` | `deep state trace + runtime` | `Reviewed - no issue found` | `SOURCE_DELETE_PENDING follows durable destination completion; unsupported SAF Move cannot delete a source. | `Host and Pixel suites pass; UI Move is disabled for the SAF source.` |
| `A4` | `Persisted grants and selected-tree restoration` | `SafTreeGrantStore.kt, MainActivity.kt` | `R1` | `security/contract trace + physical smoke` | `Reviewed - no issue found` | `Marker is validated against current persisted readable grants; source picker records intent; destination picker does not overwrite it; restart restored OmniFile-SAF-Test. | `Normal DocumentsUI flow, Activity relaunch, and 31/31 suite passed.` |
| `A5` | `Picker and capability gating` | `MainActivity.kt, FilesViewModel.kt, FilesScreen.kt` | `R1` | `UI trace + physical smoke` | `Reviewed - no issue found` | `SAF source Copy is available, SAF Move is disabled, Local destination is available, and SAF final destination confirmation is disabled while finalization is unproven. | `Pixel UI smoke on disposable tree; no destructive transfer performed.` |
| `A6` | `Operations and user-visible errors` | `OperationsPanel.kt, FilesScreen.kt, FilesViewModel.kt` | `R1` | `UI/error trace + Compose runtime` | `Reviewed - no issue found` | `Compose hierarchy, selection, dialogs, and capability-gating interactions are physically verified on the final APK. | `Focused Compose 4/4 and full suite 31/31 passed.` |
| `A7` | `Controlled provider and regression harness` | `TestDocumentsProvider.java, SafStorageProviderInstrumentedTest.kt, SafTransferRuntimeInstrumentedTest.kt, host tests` | `R1` | `test contract + runtime` | `Reviewed - no issue found` | `Outage, recovery, identity change, non-seekable I/O, unknown size, conflict, permission, mutation, and restart cases are represented and green. | `Controlled and complete Pixel direct runner passed; host 69/69 passed.` |
| `A8` | `Local/VS03 compatibility and route claims` | `LocalStorageProvider.kt, production document, prior VS03 paths` | `R1` | `whole-diff trace + runtime` | `Reviewed - no issue found` | `Local routes remain supported; only SAF to Local Copy is claimed among SAF routes; unsupported routes remain conservative. | `31/31 instrumentation and prior VS03 evidence; docs retain exact matrix.` |

## Subagent Candidate Adjudication

| Candidate ID | Proposed by | Decision | Final ID | Coordinator evidence | Reason |
| --- | --- | --- | --- | --- | --- |
| `Prior-T1-locked-compose` | `previous review` | `resolved` | `None` | `Focused class returned OK (4 tests); complete runner returned OK (31 tests) with mDreamingLockscreen=false.` | `The only previous approval caveat was the physical locked-device gate.` |
| `Candidate-provider-outage-misclassification` | `Coordinator` | `dismissed` | `None` | `ProviderUnavailable remained distinct in controlled runtime and durable state mapping.` | `No code path converts a provider outage to NotFound, COMPLETE, or source absence.` |
| `Candidate-equal-depth-intent-loss` | `Coordinator` | `dismissed` | `None` | `Selected marker restoration tests and physical Activity relaunch restored the selected disposable tree.` | `Current persisted readable grant validation prevents stale marker access and arbitrary equal-depth selection.` |
| `Candidate-generic-SAF-finalization-trust` | `Coordinator` | `dismissed` | `None` | `finalizationProven=false; SAF destination confirmation parent disabled in UI smoke.` | `No provider-wide or authority-wide finalization trust was introduced.` |
| `Candidate-duplicate-on-retry` | `Coordinator` | `dismissed` | `None` | `Host/controlled recovery evidence and one-partial ownership invariant remain green.` | `Restart/finalization recovery does not recopy a durable destination.` |

## Evidence Appendix

### Diff Inventory

| File or area | Classification | Semantic review area considered |
| --- | --- | --- |
| `app/src/main/java/com/omnifile/storage/SafStorageProvider.kt` | `surface/dependency` | `SAF identity, grants, provider errors, partial/finalization capabilities` |
| `app/src/main/java/com/omnifile/storage/SafTreeGrantStore.kt` | `surface/security` | `persisted grant validation and selected-tree intent` |
| `app/src/main/java/com/omnifile/operations/*.kt` | `surface/dependency` | `durable state, retries, reconciliation, source-delete ordering` |
| `app/src/main/java/com/omnifile/MainActivity.kt` | `surface/security` | `ACTION_OPEN_DOCUMENT_TREE, flags, result roles, restoration` |
| `app/src/main/java/com/omnifile/files/*.kt` and `ui/files/*.kt` | `surface` | `browse, selection, capability gating, error and picker UI` |
| `app/src/main/java/com/omnifile/ui/operations/OperationsPanel.kt` | `surface` | `terminal/intermediate operation truth` |
| `app/src/androidTest/java/**` and `TestDocumentsProvider.java` | `test-only` | `controlled and physical runtime evidence` |
| `app/src/test/java/**` | `test-only` | `state, locator, grant, and transfer regression evidence` |
| `docs/production/VERTICAL_SLICE_04_SAF_DURABLE_TRANSFER.md` | `docs-only` | `support matrix and evidence claims` |
| `tmp/reviews/**` | `review-only` | `review lineage and prior finding resolution` |

### Verification Commands

- `adb devices -l` -> `Pixel 7a` available through stable serial `<ANDROID_SERIAL>`; duplicate mDNS alias identified and not used.
- `adb -s <stable> shell getprop ro.build.version.sdk` -> `36`; release `16`; `mDreamingLockscreen=false`.
- `adb -s <stable> install -r app-debug.apk` -> `Success`; app APK SHA-256 `d7429bd8d7d68a2d38b92620aed740214bbb977dd0b2aadf149ec17aa80b5c4b`.
- `adb -s <stable> install -r app-debug-androidTest.apk` -> `Success`; test APK SHA-256 `6881e010dc1a0455a967c1e13885aa72a6ac79b209ede489e15a93976b216814`.
- `adb -s <stable> shell am instrument -w -r -e class com.omnifile.ui.files.FilesScreenComposeInstrumentedTest com.omnifile.test/androidx.test.runner.AndroidJUnitRunner` -> `OK (4 tests)`.
- `adb -s <stable> shell am instrument -w -r com.omnifile.test/androidx.test.runner.AndroidJUnitRunner` -> `OK (31 tests)`.
- `adb -s <stable> shell uiautomator dump` during disposable-tree smoke -> `OmniFile-SAF-Test`, `source`, `vs04-source.txt`, `41 B`, Copy enabled, Move disabled, Local destination enabled, SAF final confirmation parent disabled.
- `adb -s <stable> am force-stop com.omnifile; monkey -p com.omnifile 1` -> after marker creation, `OmniFile-SAF-Test` source tree restored and listed after Activity recreation.
- `:app:testDebugUnitTest` -> `69/69 PASS` from the current production build; no production code changed after that build.
- `:app:lintDebug` -> `PASS`; `:app:assembleDebug` -> `PASS`; `:app:assembleDebugAndroidTest` -> `PASS`; strict dependency verification -> `PASS` from current production build.

### Supporting Code Links

No finding links are required because the final report contains no findings. The coverage ledger names every reviewed production surface and its evidence.

### Dismissed Coordinator Candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| `Enable Local to SAF because ExternalStorageProvider was visible` | `dismissed` | `finalizationProven=false` and destination confirmation remained disabled; one provider observation cannot generalize SAF finalization. |
| `Enable SAF Move because delete capability exists` | `dismissed` | `sourceVersionProven=false`; production capability gate and UI keep Move disabled. |
| `Treat a restarted app with no prior marker as a failed restore` | `dismissed` | The app was freshly reinstalled before smoke; after selecting the disposable tree through normal DocumentsUI, Activity recreation restored it correctly. |

## Prior Resolution Reconciliation

- `Provider disappearance finding`: `resolved and retained. ProviderUnavailable remains a distinct retryable classification and does not imply NotFound, COMPLETE, or source absence.`
- `Equal-depth selected-tree finding`: `resolved and retained. selected-read-tree-uri-v1 records explicit source intent only after current readable grant validation; physical relaunch restored the selected disposable tree.`
- `Locked-device Compose T1`: `resolved by the normally unlocked Pixel focused 4/4 and complete 31/31 direct-run evidence.`
- `Conservative route matrix`: `unchanged. SAF to Local Copy remains the only claimed SAF route; all SAF destination and Move paths remain Unsupported.`

## Receiving Handoff

- Handoff status: `Ready for receiving-code-review`
- Automatic receiving permitted: `No`
- Source report ID: `cr-20260921-63faae30`
- Scope fingerprint to recheck: `sha256:0c8f4cb2f61230a5ea3818a9fc27a030b101923d100e97ba7f862e66ef9d3c8c`
- Actionable finding IDs: `None`
- Deferred finding IDs: `None`
- Actionable test-gap IDs: `None`
- Deferred test-gap IDs: `None`
- Open question IDs: `None`
- Open coverage area IDs: `None`
- Highest-risk verification to repeat: `No repeat required for current closure; preserve controlled provider and conservative SAF destination gates.`
- Suggested implementation boundaries: `Do not enable Local to SAF, SAF Move, or SAF to SAF without new provider-scoped proof.`
- Re-review note: `Any future production code change requires a new generation-0 review chain.`
- Chain rule: `Generation 0 may be consumed only by an explicit receiving-code-review resolution.`

## Report Self-Check

- `yes` Scope, baseline, target, diff size, and fingerprint are recorded.
- `yes` Coordinator assessment, orchestration decision, risk dimensions, and synthesis are recorded.
- `yes` All review-relevant areas are mapped in the coverage ledger.
- `yes` No unresolved finding or standalone test gap is omitted from the index/cards/ledger.
- `yes` Prior provider-disappearance, equal-depth, and locked-device findings are reconciled with current evidence.
- `yes` Recommendation follows the zero-finding, complete-coverage mapping: Pass.
- `yes` Review generated without Git mutation apart from the report artifact.
