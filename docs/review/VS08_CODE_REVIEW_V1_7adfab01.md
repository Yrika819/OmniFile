# Code Review Report

## Report Contract

- Report type: `code-review`
- Report ID: `cr-20260923-7adfab01`
- Review chain ID: `rc-20260923-7adfab01`
- Review generation: `0`
- Review trigger: `initial`
- Parent review report ID: `None`
- Parent review report path: `None`
- Parent resolution ID: `None`
- Parent resolution path: `None`
- Generated at: `2026-09-23T18:30:00Z`
- Report path: `docs/review/VS08_CODE_REVIEW_V1_7adfab01.md`
- Source skill: `code-review`
- Status: `Review complete`
- Git mutation during review: `None`
- Scope fingerprint: `sha256:67fbbe45aab301cc50b3d5c08d53b76f9ec2e8e3c80c722c577c5aed7fd919ea`

## Scope

- Review date: `2026-09-23`
- Scope kind: `working tree`
- Scope description: `38d2f936c2a65c96c61f1754994de19c3c76f89a..working tree` for VS08 ZIP archive browse/extract implementation, including untracked implementation, tests, and documentation.
- Scope mode: `full frozen scope`
- Baseline: `38d2f936c2a65c96c61f1754994de19c3c76f89a` (VS07 final)
- Target: `development/core-v1-archive-v1` working tree
- Changed paths: `16 implementation/test/document paths plus generated build outputs excluded from Git`
- Diff size: `reviewed through working-tree inventory; tracked diff 174 additions / 6 deletions plus untracked VS08 files`
- Completion: `Complete within reviewed scope`
- Requirements consulted: `VS08 task contract; docs/architecture/08_SECURITY_INVARIANTS.md; docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md; docs/research/04_ARCHIVE_FORMATS.md; docs/research/11_SECURITY.md; docs/research/12_PERFORMANCE_AND_LIMITS.md; docs/production/VERTICAL_SLICE_08_ARCHIVE_BROWSE_EXTRACT_V1.md`
- Prior resolution consulted: `None`
- Assumptions: `The current working tree is the intended VS08 implementation and untracked source files are in scope.`
- Excluded as unrelated: `pre-existing root-checkout untracked docs/ui-authority/; VS07 media implementation except affected shell/back behavior`

## Review Orchestration

- Assessment subagent: `Coordinator assessment - the changed surface is cohesive but spans archive parsing, hostile extraction, provider adapters, shell navigation, Compose UI, and Android tests; one coordinator can trace all affected chains without context handoff loss.`
- Orchestration decision: `Single reviewer`
- Decision confidence: `high`
- Decision rationale: `The archive implementation is a single vertical slice with no disjoint ownership boundary; independent specialists would duplicate the same parser/extractor and navigation reasoning.`
- Coordinator override: `None`
- Context or tool limits: `Physical device full-suite run exposed unrelated pre-existing Activity/Compose failures; focused VS08 instrumentation passed and is recorded as evidence, not treated as full-suite proof.`

### Risk Dimensions

- `Untrusted ZIP names and streaming payloads cross provider and filesystem trust boundaries.`
- `Multi-entry finalization, rollback, cancellation, and provider disappearance can create user-visible partial output.`
- `Files/Search detail navigation and Material UI changes can regress existing origin/back behavior.`
- `Sequential SAF behavior must not be generalized to seekability or universal SAF destination writing.`

### Reviewer Assignments

| Reviewer | Angle | Owned surfaces | Mandatory cross-checks | Status |
| --- | --- | --- | --- | --- |
| `Coordinator` | correctness, hostile input, provider capability, durability, UI integration, regression | all VS08 paths and affected callers | host tests, lint/build, focused Pixel instrumentation, existing shell/search/files paths | Complete |

### Synthesis Statement

The coordinator independently re-read every accepted candidate against the implementation and current architecture invariants. The two Major findings below are concrete repairs, not product-intent questions. The standalone UI journey test gap is retained until focused Files/Search callback coverage is added.

## Review Snapshot

- Recommendation: `Changes requested`
- Completion: `Complete within reviewed scope`
- Why now: `The mandatory ZIP path is implemented and tested, but two security/reliability invariants and one high-risk integration coverage gap remain before approval.`
- Must-review now:
  1. `F1` Mid-stream provider loss is flattened into generic archive I/O
  2. `F2` Canonical Unicode output collisions are not rejected
  3. `T1` Files/Search archive launch and origin-return paths lack focused UI/integration coverage
- Findings count: `Blocker 0 | Major 2 | Minor 0 | Question 0`
- Standalone test gaps: `Blocker 0 | Major 0 | Minor 1`
- Coverage confidence: `high`
- Biggest blind spot: `Full Activity/Compose instrumentation baseline is unstable on the connected Pixel; focused VS08 storage instrumentation passed.`

## Complete Findings Index

| ID | Severity | Surface | Review risk | Confidence | Origin | Verification | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `F1` | Major | ZIP source read/parser and extractor | Provider disappearance during streaming is reported as generic I/O, losing the required failure classification | high | Coordinator | static trace; controlled-provider architecture; focused tests | `behavior; entry=archive source read; contract=provider disappearance remains a first-class archive failure; effect=mid-stream provider loss is reported as generic archive I/O` | `ifp-sha256:ce697c2e20e86b5ce73457b08f35f95087a9a5f3301fc21978aa2d932140112a` | `kind:hard-invariant; strength:authoritative; evidence:docs/architecture/08_SECURITY_INVARIANTS.md and VS08 task contract` |
| `F2` | Major | extraction target planning | Canonically equivalent Unicode names are treated as different output identities | high | Coordinator | static trace; hostile-name tests; extraction plan review | `behavior; entry=archive extraction destination planning; contract=canonically equivalent output names cannot target the same namespace; effect=duplicate normalized outputs can be written as distinct raw names` | `ifp-sha256:98dbd4d2ceb2ad571d6ffe43353733e93162532ea9c17ffbb3f2b5e429c79245` | `kind:hard-invariant; strength:authoritative; evidence:VS08 task contract normalization-collision requirement and docs/research/11_SECURITY.md` |

## Blocker

None.

## Major

### F1 Major - Mid-stream provider loss is flattened into generic archive I/O

Impact: A SAF archive whose provider disappears or its sequential descriptor fails after parsing begins reaches the UI as a generic archive read error rather than a provider-unavailable failure. Retry/recovery and durable truth cannot distinguish source outage from malformed ZIP data.

Review reason: The VS08 contract explicitly makes provider disappearance a first-class failure and requires provider-aware Local/SAF behavior.

Surface: `ZipArchiveReader.read` and `ArchiveExtractor.extract` source stream error paths.

Issue key: `behavior; entry=archive source read; contract=provider disappearance remains a first-class archive failure; effect=mid-stream provider loss is reported as generic archive I/O`

Issue fingerprint: `ifp-sha256:ce697c2e20e86b5ce73457b08f35f95087a9a5f3301fc21978aa2d932140112a`

Expected basis: `kind:hard-invariant; strength:authoritative; evidence:docs/architecture/08_SECURITY_INVARIANTS.md and VS08 task contract`

Confidence: `high`

Origin: `Coordinator`

Coordinator verification: `The source provider maps open-time failures to ArchiveError.Provider, but ZipArchiveReader catches subsequent IOException as ArchiveError.Io; ArchiveExtractor does the same for read/write-loop IOException. The UI maps ArchiveError.Io to a generic message.`

Look here first:
- [`ZipArchiveReader.kt` source exception mapping](~/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/archive/ZipArchiveReader.kt#L105)
- [`ArchiveExtractor.kt` streaming exception mapping](~/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/archive/ArchiveExtractor.kt#L284)

Failure mode:
- Expected: `Provider loss remains distinguishable from corrupt/truncated archive input after the stream has opened.`
- Current: `Open-time StorageResult failures are provider-wrapped, but mid-stream IOException is returned as ArchiveError.Io / ArchiveExtractionError.Source(ArchiveError.Io(...)).`

Evidence:
- `SafStorageProvider.openSequentialRead` exposes a sequential descriptor whose later read can fail independently of open success.
- The parser and extractor both have generic IOException catches after the source handle is open.
- Existing controlled SAF tests already distinguish provider availability at provider boundaries, but no archive mid-stream failure assertion exists.

Assumptions and limits:
- `IOException` from an open source handle is treated as provider/source transport failure; ZIP structural failures continue to use ZipException/Corrupt classification.

Reviewer action:
`request fix and focused provider-disappearance regression test`

### F2 Major - Canonical Unicode output collisions are not rejected

Impact: Two archive entries that normalize to the same filesystem namespace can both pass preflight because target identity is built from raw path strings. Depending on provider/filesystem normalization, extraction can create an ambiguous result or cause one output to shadow another.

Review reason: The task explicitly requires normalization-collision defense and duplicate normalized output targets must fail before writes.

Surface: `ArchiveExtractor.plan` duplicate/type conflict and destination preflight.

Issue key: `behavior; entry=archive extraction destination planning; contract=canonically equivalent output names cannot target the same namespace; effect=duplicate normalized outputs can be written as distinct raw names`

Issue fingerprint: `ifp-sha256:98dbd4d2ceb2ad571d6ffe43353733e93162532ea9c17ffbb3f2b5e429c79245`

Expected basis: `kind:hard-invariant; strength:authoritative; evidence:VS08 task contract normalization-collision requirement and docs/research/11_SECURITY.md`

Confidence: `high`

Origin: `Coordinator`

Coordinator verification: `ArchivePath` preserves raw components and ArchiveExtractor.pathKey joins raw components. The existing duplicate fixture only covers byte-identical names; NFC/NFD-equivalent names are not compared canonically.`

Look here first:
- [`ArchiveExtractor.kt` raw target grouping](~/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/archive/ArchiveExtractor.kt#L357)
- [`ArchiveModels.kt` path validation](~/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/archive/ArchiveModels.kt#L211)

Failure mode:
- Expected: `Duplicate normalized output targets and file/directory normalization collisions fail before any output is created.`
- Current: `Only raw path equality and raw prefix comparisons are used.`

Evidence:
- `groupBy { pathKey(it.target) }`, `filePaths`, and `directoryPaths` all use raw strings.
- No `java.text.Normalizer` or provider namespace normalization is applied.
- Hostile-path tests cover traversal, absolute, drive, and mixed separators but not NFC/NFD collisions.

Assumptions and limits:
- VS08 uses Local destinations; the fix should remain conservative for provider namespaces without adding case-folding assumptions that would reject valid Linux names unnecessarily.

Reviewer action:
`request fix and deterministic NFC/NFD collision regression test`

## Minor

None.

## Questions

None.

## Test Gaps

| ID | Severity | Surface | Missing coverage | Risk | Origin | Evidence | Issue key | Issue fingerprint | Expected basis |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `T1` | Minor | Files/Search archive launch and origin-return journeys | No focused UI/integration test directly exercises archive callback wiring from Files and Search, nested detail back, and selection/extract action affordance | A shell/navigation wiring regression could leave the parser green while users cannot enter or leave the archive detail surface | Coordinator | Existing tests cover Files/Search and shell separately; new archive storage instrumentation passes but does not launch the archive UI | `test-gap; entry=Files and Search archive open callbacks; contract=archive entry launch and origin-return journeys are exercised on Android; gap=no focused UI/integration test covers those callbacks` | `ifp-sha256:e062904fff37d055a260595b0548e4b418b780577a9b6c829fe9aa9b89882cec` | `kind:requirement; strength:authoritative; evidence:VS08 controlled Android testing requirements` |

## Review Coverage Ledger

| Area ID | Area / path | Touched files or entry points | Owner | Depth | Status | Result | Evidence / next step |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `A1` | Archive identity and virtual hierarchy | `archive/ArchiveModels.kt`, `ArchiveRepository.kt` | Coordinator | contract trace + targeted host test | Reviewed - no issue found | Outer provider identity plus ordinal/virtual discriminator; nested, empty, duplicate, Unicode display tests pass | Host archive tests |
| `A2` | ZIP parser and bounded metadata | `archive/ZipArchiveReader.kt` | Coordinator | dependency + runtime verified | Finding F1 | Sequential-only bounded parser, EOCD proof, corrupt/truncated tests pass; mid-stream provider classification needs repair | F1 |
| `A3` | Hostile path and extraction planning | `archive/ArchiveExtractor.kt`, `ArchiveModels.kt` | Coordinator | security contract trace + targeted tests | Finding F2 | Traversal/absolute/drive/mixed separator/type/duplicate/conflict/resource-limit tests pass; canonical normalization missing | F2 |
| `A4` | Local durability and rollback | `LocalStorageProvider.kt`, `ArchiveExtractor.kt` | Coordinator | contract trace + targeted host test | Reviewed - no issue found | Partial/write/finalize, rollback and no-overwrite behavior exercised; restart-required documented | ArchiveExtractorTest |
| `A5` | SAF sequential source and capability gate | `FilesRepository.kt`, `SafStorageProvider.kt`, archive instrumentation | Coordinator | runtime verified | Reviewed - no issue found | Pixel focused 1/1 SAF browse + SAF→Local extraction passed; no seek claim or SAF destination claim | Pixel report |
| `A6` | Files/Search entry integration | `MainActivity.kt`, `FilesScreen.kt`, existing SearchScreen path | Coordinator | dependency trace | Not covered | Static wiring traced and shell navigation tested; focused archive UI callback coverage absent | T1 |
| `A7` | Archive detail UI/insets | `ui/archive/ArchiveScreen.kt`, `MainActivity.kt` | Coordinator | contract trace + compile/lint | Reviewed - no issue found | Detail surface only, Scaffold padding, weighted list/progress, existing edge-to-edge Activity; no new intent surface | lint/build |
| `A8` | External/security surface | `AndroidManifest.xml` unchanged | Coordinator | diff-only | Not review-relevant | No MIME filter, ACTION_VIEW, exported Activity, provider, or FileProvider change | No intent review required |
| `A9` | Existing Files/Search/shell regressions | affected callers and `AppNavigation.kt` | Coordinator | host + device evidence | Reviewed - no issue found | 127/127 host tests; existing full device suite has unrelated baseline failures, focused archive device test passes | regression review |

## Subagent Candidate Adjudication

No subagents were used; coordinator-only review was proportionate to the cohesive vertical slice.

## Evidence Appendix

### Diff Inventory

| File or area | Classification | Semantic review area considered |
| --- | --- | --- |
| `app/src/main/java/com/omnifile/archive/*` | surface/dependency | archive identity, parser, safety, extraction, provider failure |
| `app/src/main/java/com/omnifile/ui/archive/ArchiveScreen.kt` | surface | detail UI, selection, progress, insets |
| `app/src/main/java/com/omnifile/archive/ArchiveViewModel.kt` | surface | async state, cancellation, origin/back behavior |
| `app/src/main/java/com/omnifile/MainActivity.kt` | surface/dependency | Files/Search callbacks and shell routing |
| `app/src/main/java/com/omnifile/files/FilesRepository.kt` | dependency | provider-neutral sequential read seam |
| `app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt` | dependency | Local directory creation and secure output boundary |
| `app/src/main/java/com/omnifile/ui/files/FilesScreen.kt` | surface | archive recognition/open affordance |
| `app/src/main/java/com/omnifile/ui/shell/AppNavigation.kt` | surface | detail origin/back preservation |
| `app/src/test/java/com/omnifile/archive/*` | test-only | deterministic ZIP and extraction safety coverage |
| `app/src/androidTest/java/com/omnifile/archive/*` | test-only | controlled SAF and Pixel evidence |
| `docs/production/VERTICAL_SLICE_08_ARCHIVE_BROWSE_EXTRACT_V1.md` | docs-only | frozen architecture and capability claims |
| `app/src/test/java/com/omnifile/ui/shell/AppNavigationStateTest.kt` | test-only | origin/back contract |

### Verification Commands

- `:app:testDebugUnitTest` -> `127 tests, 0 failures, 0 errors, 0 skipped`.
- Focused archive/navigation host tests -> `19 tests, 0 failures`.
- `:app:assembleDebugAndroidTest` -> success.
- `:app:lintDebug` -> success; only baseline/tooling findings.
- `:app:assembleDebug` -> success.
- Full `:app:connectedDebugAndroidTest` -> `78 tests, 28 unrelated baseline Activity/Compose failures and one pre-existing physical SAF assumption failure`; VS08 archive test passed.
- Focused Pixel 7a/API 36 `ArchiveStorageInstrumentedTest` -> `1/1 passed`.

### Supporting Code Links

| ID | Role | Link | Why it matters |
| --- | --- | --- | --- |
| `F1` | entry | [`ArchiveRepository.kt`](~/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/archive/ArchiveRepository.kt#L11) | source handle is opened through provider-neutral repository seam |
| `F1` | risk | [`ZipArchiveReader.kt`](~/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/archive/ZipArchiveReader.kt#L105) | mid-stream IOException becomes generic Io |
| `F2` | entry | [`ArchiveExtractor.kt`](~/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/archive/ArchiveExtractor.kt#L357) | raw target grouping is the duplicate-conflict gate |
| `F2` | risk | [`ArchiveExtractorTest.kt`](~/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/test/java/com/omnifile/archive/ArchiveExtractorTest.kt#L42) | current duplicate coverage is byte-identical only |
| `T1` | coverage gap | [`MainActivity.kt`](~/Desktop/File%20Manager-worktrees/omnifile-archive-v1/app/src/main/java/com/omnifile/MainActivity.kt#L239) | Files callback wiring needs focused UI exercise |

### Dismissed Coordinator Candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| Seekable SAF archive requirement | dismissed | architecture and focused Pixel test prove sequential SAF source is sufficient; no random-access claim is made |
| SAF destination extraction | dismissed | capability matrix explicitly gates it off; UI offers only Local extraction |
| Symlink traversal through archive output | dismissed | platform parser does not create/follow links and output names are regular file/directory provider operations |
| External archive intents | dismissed | manifest unchanged; no new external ingress |
| Whole-archive RAM materialization | dismissed | parser and extractor use bounded fixed buffers and sequential handles |

### Blind Spots

| Area ID | Blind spot | Decision risk | What would resolve it |
| --- | --- | --- | --- |
| `A6` | Files/Search archive UI flow lacks focused callback/integration test | A shell wiring regression could survive storage tests | Add focused Files/Search archive open and back/selection tests |
| `A9` | Full Pixel baseline has unrelated Activity/Compose failures | Whole-app regression confidence is lower than focused archive evidence | Repair or isolate the existing device harness baseline, then rerun full suite |

## Prior Resolution Reconciliation

None - initial review generation.

## Receiving Handoff

- Handoff status: `Ready for receiving-code-review`
- Automatic receiving permitted: `Yes`
- Source report ID: `cr-20260923-7adfab01`
- Scope fingerprint to recheck: `sha256:67fbbe45aab301cc50b3d5c08d53b76f9ec2e8e3c80c722c577c5aed7fd919ea`
- Actionable finding IDs: `F1, F2`
- Deferred finding IDs: `None`
- Actionable test-gap IDs: `T1`
- Deferred test-gap IDs: `None`
- Open question IDs: `None`
- Open coverage area IDs: `A6`
- Highest-risk verification to repeat: `focused provider-loss and NFC/NFD collision tests, then host suite and archive Pixel test`
- Suggested implementation boundaries: `archive source error mapping; normalized target identity; focused Files/Search UI tests`
- Re-review note: `Treat every finding as a claim to verify. Challenges require a counterclaim, argument, evidence, limits, and settlement criterion.`
- Chain rule: `Generation 1 is terminal. Do not automatically invoke receiving-code-review; return remaining findings to the user or product owner.`

## Report Self-Check

- yes Actual assessment mode and rationale are recorded.
- yes Every changed review-relevant or unknown-impact area appears once in the Review Coverage Ledger.
- yes Every final finding appears once in the index and once as a matching card.
- yes Every Finding F# area references an existing finding.
- yes Every standalone test gap has a stable ID and severity.
- yes Every F# and T# has a unique semantic issue fingerprint and authoritative expected basis.
- yes Generation, trigger, parent resolution, scope mode, and receiving handoff satisfy the bounded chain contract.
- yes Every non-Question finding and standalone test gap appears in actionable handoff IDs.
- yes Every meaningful subagent candidate is accounted for by coordinator-only adjudication.
- yes Every Not covered area has a reason and next step.
- yes Recommendation follows the skill mapping.
- pending Validator must pass before review closure.
- yes Git state was not mutated by review.
