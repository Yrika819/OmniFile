# OmniFile VS09 Code Review — Generation 1

## Review contract

- Report type: `code-review`
- Report ID: `cr-20260923-4ddf952e`
- Review chain: `rc-20260923-vs09-preview`
- Generation: `1`
- Parent report: `cr-20260923-6b72d4a1` (`docs/review/VS09_CODE_REVIEW_V1_6b72d4a1.md`)
- Parent receiving record: `docs/review/VS09_CODE_REVIEW_RECEIVING_6b72d4a1.md`
- Scope: complete VS09 working-tree implementation and affected execution paths
- Base: `9b97149c2c921a8fe4523be51624ebaad4fe1d18` (VS08)
- Target: `development/core-v1-preview-v1`
- Source fingerprint (`app/src`): `sha256:cfa44e5e99c26c889c750e528bac2196e6a45e79d7e6a4375de323208ec11eb7`
- Git mutation during review: none
- Recommendation: `Pass within reviewed scope`

## Review orchestration

- Reviewers: `Coordinator only`
- Decision: `Single reviewer was proportionate to this bounded slice; all new source and affected callers were traced against tests and the diff.`
- Requirements consulted: VS09 campaign, VS08 source/identity authority, provider capability boundaries, performance/security invariants, Preview V1 bounds, and Preview-specific host/instrumentation evidence.
- Device blind spot: no Android device or emulator was attached. Device runtime assertions are therefore not claimed; connected tests require a follow-up device run.

## Synthesis

Generation 0 found that repeated zero-progress provider reads could leave a request pending. The repair adds a finite no-progress limit and a deterministic test. During the final classifier sweep, a printable PDF signature was also found to pass the generic text heuristic; the signature regression was reproduced, then closed by classifying known PDF/ZIP signatures as unsupported. Generation 1 rechecked both paths and their callers. No new Blocker, Major, Minor, Question, or actionable test gap was found in the reviewed source delta.

## Findings

| ID | Parent item | Disposition | Repair and evidence |
|---|---|---|---|
| `F1` | Generation-0 Minor: repeated zero-progress reads can leave Preview loading | Closed | Eight consecutive empty reads maximum; exhaustion maps to `IoFailure`. `PreviewEngineTest.repeatedZeroProgressReadsBecomeTypedIoFailure` passes. Stream adapters reject zero progress for positive-length reads. |

No generation-1 findings remain.

## Review coverage ledger

| Area | Review result | Evidence |
|---|---|---|
| Provider-neutral identity | Pass | Shared model uses `EntryRef`; provider locator/URI remains inside Local/SAF adapters. Request and capability tests check identity agreement. |
| Capability boundaries | Pass | Local and SAF source adapters require regular files and sequential read capability; SAF remains sequential and grant-scoped. Provider-backed tests cover absent read capability. |
| Text classification/decoding | Pass | Signature-first image recognition; known PDF/ZIP signatures are unsupported; printable text uses an 8 KiB classification sample; BOM, malformed UTF-8, incomplete boundary, binary-looking data, empty text, and 256 KiB/64 Ki character caps have host coverage. |
| Image decode/resource control | Pass in source review; runtime pending device | PNG/JPEG/BMP only; 32 MiB encoded input limit, dimension check, sampled decode to 1600×1600 target and 8 MP ceiling, OOM/error mapping. Local/SAF instrumentation fixtures include valid image bytes and spoofed metadata. |
| Archive entry source | Pass | Supported listed file, same archive identity, single ordinal, safe path, and methods 0/8 are checked. Reopen verifies the indexed raw name and compression method against the current local header; changed method returns StaleReference. Preceding records are streamed under the metadata byte limit; no extraction or path-based identity is introduced. Duplicate-name and changed-method host tests cover ordinal identity and stale-source rejection. |
| Async ownership/cancellation | Pass in host evidence | Generation check prevents a non-cooperative late A result replacing B; closing clears state and cancels the request; the engine checks cancellation between reads. |
| Lifecycle/navigation | Pass in source and route tests | Preview is nested in existing detail/top-level Search state; Files/Search/Archive ViewModels remain alive; Back restores their exact active owner; process-death restore dismisses a source that cannot be reconstructed. Physical recreation remains untested. |
| UI, accessibility, and insets | Pass in source review and Android-test compilation | Material3 detail Scaffold, Back action, text selection/scroll, image aspect-fit and content description, truncation, loading, unsupported and retryable error semantics. Uses Scaffold padding and existing edge-to-edge shell. |
| Existing behavior | Pass | Files selection remains first, directories and ZIPs keep their routes, audio keeps explicit Play, Search scope/query stay in the existing Search ViewModel, and Archive selection/extraction remains intact. |
| External intent/security surface | Not applicable to this delta | No manifest, exported component, ACTION_VIEW/MIME filter, content URI ingress, FileProvider, or PendingIntent change. No new permission or grant is created. |

## Diagnostics and verification

- No editor/LSP diagnostics interface was exposed. Gradle Kotlin compilation and instrumentation Kotlin compilation were the available authoritative compiler checks; both passed.
- `:app:testDebugUnitTest`: `150 tests; 0 failures; 0 errors; 0 skipped`.
- Focused preview, archive ordinal/stale-method, navigation-origin, no-progress, and PDF signature tests: passed.
- `:app:compileDebugAndroidTestKotlin`: passed.
- `:app:lintDebug`: passed, `0 errors`, `17 warnings`. The warnings are repository/toolchain baseline items (version advisories, existing exported Media3 service notice, existing SDK checks/resources/KTX suggestions, and missing application icon); no new preview warning remained.
- `:app:assembleDebug`: passed.
- `:app:assembleDebugAndroidTest`: passed.
- `git diff --check`: passed.
- Connected instrumentation: `NOT RUN`; `adb devices -l` listed no device or emulator.

## Blind spots

| Area | Current limit | Resolving evidence |
|---|---|---|
| Device behavior | Compose/provider instrumentation compiled but did not execute; SAF and decoded image runtime claims remain controlled-source claims only. | Run the final Android-test APK on an awake, unlocked Pixel with `com.omnifile` foreground; execute Preview screen, Local/SAF provider, Archive storage, and existing regression suites. |
| Activity recreation | ViewModel ownership and restore routing were reviewed and covered at host state level; no device rotation/process-death run. | Focused physical rotation and process-death acceptance. |

## Final snapshot

- Findings: `Blocker 0 | Major 0 | Minor 0 | Question 0`
- Standalone code test gaps: `0`
- Coverage confidence: `high for source/host; limited for device runtime`
- Highest-risk follow-up: `physical execution of the compiled instrumentation APK and recreation path`
- Handoff: `terminal generation-1 review; return remaining physical acceptance to task owner`
