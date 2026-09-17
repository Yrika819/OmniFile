# P05-003 Failures and Anomalies

## TDD discovery failure

The first test run intentionally preceded implementation and failed during import with `ModuleNotFoundError: No module named 'p05_003_contract'`. This was the expected RED state. After adding the minimal contract model, the same seven tests passed.

## Environment blockers

- The P05 worktree contains architecture/research documents but no Android application, Gradle wrapper, Media3 dependency graph, or Kotlin compiler.
- Python 3.9 and `unittest` were available, so only a pure host contract harness was feasible.
- ADB was not invoked because the user prohibited it.
- No device, emulator, Media3 runtime, SAF provider, network provider, or real audio fixture campaign was run.

## Boundary anomalies retained as findings

- FLACtify's observed service/cache/controller structure is useful reference behavior, but its URI-based `MediaItem` flow is not evidence of a provider-neutral resolver.
- FLACtify's historical Media3 `1.3.1` dependency was not copied or frozen.
- A source locator may contain credentials or expiration state; the harness deliberately excludes it from durable cache identity. A production implementation still needs explicit log redaction and provider-version semantics.
- The host tests do not establish that a native descriptor is actually seekable. That guarantee must come from the provider capability contract and Android instrumentation.
- The original seven-test TDD cycle did not cover the three review-regression cases; those were added and verified in remediation commit `73d9fd2ac271ee45c1a4197e6a496dbebb045aef`.

No production failure was diagnosed because no production playback implementation exists in this worktree.
