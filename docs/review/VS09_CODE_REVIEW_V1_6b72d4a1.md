# OmniFile VS09 Code Review — Generation 0

## Review contract

- Report ID: `cr-20260923-6b72d4a1`
- Review chain: `rc-20260923-vs09-preview`
- Generation: `0`
- Scope: uncommitted VS09 source and tests on `development/core-v1-preview-v1`
- Base: `9b97149c2c921a8fe4523be51624ebaad4fe1d18` (VS08)
- Source fingerprint (`app/src`): `sha256:1c056ad556d2562524c9d04119f61238641cec1d8060003fee846e28f868078f`
- Mode: implementation and affected execution-chain review
- Status: findings recorded; fixes required before generation 1
- Git mutation during review: none

## Review snapshot

- Recommendation: `Fix and re-review`
- Findings: `Blocker 0 | Major 0 | Minor 1 | Question 0`
- Coverage: preview domain, provider adapters, image/text decode, ZIP entry source, Files/Search/Archive routing, lifecycle ownership, UI bounds/insets, and unchanged external intent surface
- Device blind spot: no Android device/emulator was available; controlled instrumentation compiled but could not be executed

## Finding index

### F1 — Bound repeated zero-progress reads

- Severity: `Minor`
- Area: `PreviewEngine.readAtMost`, text continuation/skip, and stream adapters
- Contract: bounded preview reads must reach a result or a typed provider failure when a source repeatedly makes no forward progress.
- Evidence: several sequential-read loops accept `0` and retry forever (some with `yield()`), so a provider implementation that returns zero for a positive-length read can leave the Preview surface loading indefinitely.
- User effect: one malformed or stalled source can keep a preview request alive until the user navigates away; generation ownership prevents stale publication but does not complete the active request.
- Required repair: cap consecutive zero-progress reads, map exhaustion to an I/O failure, and make InputStream adapters reject zero progress for positive-length reads.
- Required regression test: synthetic handle repeatedly returns zero; preview reaches a typed error promptly.

## Coverage notes

- Provider-neutral identity remains `EntryRef` scoped; no path or raw URI entered the shared model.
- Local and SAF adapters reopen through their provider transfer APIs. SAF access continues to depend on the selected read grant.
- ZIP preview resolves a supported, listed node to its single archive ordinal and reopens the container sequentially; no extraction destination is used.
- Files keeps explicit audio Play behavior and existing archive routing. Search retains its ViewModel/scope while Preview is nested. Archive state remains owned by its existing ViewModel.
- No manifest, exported component, ACTION_VIEW, MIME filter, FileProvider, or external URI ingress changed; Android intent-security review is not triggered by this delta.
- Rotation retains the Preview ViewModel. A process-death restore dismisses an unrecoverable preview route instead of fabricating a source identity or handle.

## Evidence at generation 0

- Full `:app:testDebugUnitTest`: passed.
- Focused preview, archive ordinal, and navigation route tests: passed.
- `:app:compileDebugAndroidTestKotlin`: passed.
- `:app:lintDebug`: passed after removing a new unused BoxWithConstraints scope finding; 17 repository/tooling warnings remain.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: passed.
- Connected instrumentation: not run; `adb devices -l` returned no devices/emulators.

## Handoff

- Receiving record: `docs/review/VS09_CODE_REVIEW_RECEIVING_6b72d4a1.md`
- Required generation-1 scope: repair F1, add and run its host regression test, rerun full host/lint/assemble gates, and re-review the full changed surface.
- Highest-risk repeat: provider read progress, ZIP ordinal source, and Preview A/B cancellation ownership.
