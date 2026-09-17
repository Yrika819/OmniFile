# P05-003 Failures and Anomalies

## TDD discovery failure

The first test run intentionally preceded implementation and failed during import with `ModuleNotFoundError: No module named 'p05_003_contract'`. This was the expected RED state. After adding the minimal contract model, the full host suite completed with 14 passing tests.

## Environment blockers

- The P05 worktree has no repository Gradle wrapper; the disposable runtime uses the explicitly documented local Gradle 9.6.0 distribution.
- The first build invocation omitted the SDK environment and failed with `SDK location not found`.
- The corrected invocation initially requested unavailable API 35 and failed with `Failed to find target with hash string 'android-35'`; the PoC was changed to installed API 36.
- The offline build then failed because transitive `androidx.core:core:1.8.0` was not cached. The online build resolved it and reached Java compilation.
- The first Java compilation exposed twelve small API/source issues (unavailable annotation dependency and Media3 1.3.1 API names); these were corrected, and the subsequent build succeeded.
- No Media3 ADB/device run, SAF playback run, network-provider run, or human audio acceptance campaign was completed in this branch.

## Boundary anomalies retained as findings

- FLACtify's observed service/cache/controller structure is useful reference behavior, but its URI-based `MediaItem` flow is not evidence of a provider-neutral resolver.
- FLACtify's historical Media3 `1.3.1` dependency was not copied or frozen.
- A source locator may contain credentials or expiration state; the harness deliberately excludes it from durable cache identity. A production implementation still needs explicit log redaction and provider-version semantics.
- The host tests do not establish that a native descriptor is actually seekable. That guarantee must come from the provider capability contract and Android instrumentation.
- The original seven-test TDD cycle did not cover the three review-regression cases; those were added and verified in remediation commit `73d9fd2ac271ee45c1a4197e6a496dbebb045aef`.

No production failure was diagnosed because no production playback implementation exists in this worktree. The Android PoC has build evidence, not runtime evidence.
