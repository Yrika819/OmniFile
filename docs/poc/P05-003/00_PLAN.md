# P05-003 Provider-Neutral Media3 Playback Analysis Plan

**Goal:** Produce a read-only-grounded P05-003 evidence package and disposable contract/runtime harness for provider-neutral Media3 playback without introducing production Android code or claiming untested playback behavior.

**Scope:** The eight documents in `docs/poc/P05-003/` plus a disposable Python 3.9 standard-library contract harness and Android Media3 runtime PoC under `tools/p05_003_harness/`.

**Architecture:** The host harness models the provider boundary and the Android PoC exercises the next runtime boundary with actual AndroidX Media3/ExoPlayer. The PoC has an app-private local WAV fixture, an `ACTION_OPEN_DOCUMENT_TREE` source resolved to a child document, and a custom sequential/non-seekable `DataSource`; a redacted JSONL recorder captures lifecycle and failure evidence.

**Verification policy:** Tests must be written before the host harness implementation and observed failing. Host tests and Android compilation may be marked PASS only from fresh command output. Android runtime, SAF playback, device, and ADB runs remain `NOT_TESTED`; no ADB action is permitted for this request.

## Global constraints

- Work only in `/Users/yuta/Desktop/File Manager/.worktrees/p05-media3` on `poc/core-readiness-media3-v1`.
- Inspect `/Users/yuta/Desktop/FLACtify` read-only; do not modify it.
- Do not touch ADB or any other worktree.
- Do not clean, reset, stash, rebase, amend, force-push, or delete user data; the disposable PoC build is explicitly in scope.
- Pin Media3 only inside the disposable PoC module; do not freeze a production Media3 version or add production dependencies.
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
9. `tools/p05_003_harness/p05_003_contract.py` — disposable pure-Python contract model and runtime event/source contract.
10. `tools/p05_003_harness/test_p05_003_contract.py` — stdlib `unittest` coverage for the model.
11. `tools/p05_003_harness/media3-poc/` — disposable Android app using actual Media3/ExoPlayer, local and SAF sources, sequential `DataSource`, and JSONL evidence.

## Interfaces

The harness will expose:

```text
ReadCapabilities(sequential, seekable, random_range, native_descriptor, descriptor_readable)
MediaIdentity(provider_id, object_id, source_version, locator)
PlaybackRequest(requires_seek, prefers_random_range)
SourceMode = DIRECT_DESCRIPTOR | SEEKABLE_SOURCE | RANDOM_RANGE_SOURCE |
             SEQUENTIAL_CACHED_SOURCE | UNSUPPORTED
resolve_source(identity, capabilities, request) -> Resolution
cache_key(identity) -> str
```

The `locator` is intentionally transport-only. `cache_key` must not include it, and must include `provider_id`, `object_id`, and `source_version`.

## Execution sequence

- [x] Write the failing contract/event/source tests and observe the expected missing-import RED with `PYTHONPATH=tools/p05_003_harness python3 -m unittest tools/p05_003_harness/test_p05_003_contract.py -v`.
- [x] Implement the minimum model required by those tests.
- [x] Re-run the focused suite and then the complete harness suite: 14 tests pass.
- [x] Build the Android PoC with the locally available Gradle/SDK/AGP/Media3 inputs: `:app:assembleDebug` succeeds.
- [x] Write the eight evidence documents from the observed output and current checkout facts.
- [x] Inspect the final diff and status; confirm only scoped files changed.
- [x] Commit with one normal focused continuation commit after final diff/self-review; record the resulting SHA in the handoff.
