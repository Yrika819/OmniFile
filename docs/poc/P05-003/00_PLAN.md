# P05-003 Provider-Neutral Media3 Playback Analysis Plan

**Goal:** Produce a read-only-grounded P05-003 evidence package and disposable contract harness for provider-neutral Media3 playback without introducing production Android code or claiming untested playback behavior.

**Scope:** The eight documents in `docs/poc/P05-003/` plus a disposable Python 3.9 standard-library harness under `tools/p05_003_harness/`.

**Architecture:** The harness models the boundary before Media3: a provider-scoped identity advertises explicit sequential, seekable, random/range, and native-descriptor capabilities; a resolver selects the weakest source mode sufficient for a requested playback operation; cache identity is derived from provider identity and source version, never a tokenized locator. It does not import AndroidX, ExoPlayer, Media3, ADB, or device APIs.

**Verification policy:** Tests must be written before the harness implementation and observed failing. Host tests may be marked PASS only from fresh command output. Android/Media3/device/ADB runs are `NOT_TESTED` because this worktree has no Android project/toolchain and the user prohibited ADB.

## Global constraints

- Work only in `/Users/yuta/Desktop/File Manager/.worktrees/p05-media3` on `poc/core-readiness-media3-v1`.
- Inspect `/Users/yuta/Desktop/FLACtify` read-only; do not modify it.
- Do not touch ADB or any other worktree.
- Do not build, clean, reset, stash, rebase, amend, force-push, or delete user data.
- Do not freeze a Media3 version or add production dependencies.
- Keep playback claims limited to fresh harness evidence; device and Media3 execution remain `NOT_TESTED`.

## Deliverables

1. `00_PLAN.md` — scope, constraints, interfaces, and verification sequence.
2. `01_ENVIRONMENT.md` — checkout/toolchain/baseline evidence and blocked-run rationale.
3. `02_HARNESS_DESIGN.md` — disposable model, source modes, cache identity, and non-goals.
4. `03_TEST_MATRIX.md` — unit/component/instrumentation/device evidence matrix with status vocabulary.
5. `04_RESULTS.md` — fresh host test results and explicit untested rows.
6. `05_FAILURES_AND_ANOMALIES.md` — observed failures, limitations, and pre-existing dirty state outside scope.
7. `06_CONCLUSIONS.md` — bounded conclusions and no-go claims.
8. `07_ARCHITECTURE_IMPACT.md` — implications for the future File Manager design and next authorized POC.
9. `tools/p05_003_harness/p05_003_contract.py` — disposable pure-Python contract model.
10. `tools/p05_003_harness/test_p05_003_contract.py` — stdlib `unittest` coverage for the model.

## Interfaces

The harness will expose:

```text
ReadCapabilities(sequential, seekable, random_range, native_descriptor)
MediaIdentity(provider_id, object_id, source_version, locator)
PlaybackRequest(requires_seek, prefers_random_range)
SourceMode = DIRECT_DESCRIPTOR | SEEKABLE_SOURCE | RANDOM_RANGE_SOURCE |
             SEQUENTIAL_CACHED_SOURCE | UNSUPPORTED
resolve_source(identity, capabilities, request) -> Resolution
cache_key(identity) -> str
```

The `locator` is intentionally transport-only. `cache_key` must not include it, and must include `provider_id`, `object_id`, and `source_version`.

## Execution sequence

- [x] Write the failing contract tests and run them with `PYTHONPATH=tools/p05_003_harness python3 -m unittest discover -s tools/p05_003_harness -p 'test_*.py' -v`.
- [x] Implement the minimum model required by those tests.
- [x] Re-run the focused suite and then the complete harness suite.
- [x] Write the eight evidence documents from the observed output and current checkout facts.
- [x] Inspect the final diff and status; confirm only scoped files changed.
- [x] Commit with one normal commit; record the resulting SHA in the handoff.
