# P05-003 Harness Design

## Purpose

`tools/p05_003_harness/` is disposable host-side evidence code. It tests the provider-to-playback boundary before an Android Media3 integration exists. It is deliberately not a player, datasource, filesystem adapter, network client, or Android test fixture.

## Model

`p05_003_contract.py` defines:

- `MediaIdentity(provider_id, object_id, source_version, locator)` — durable identity fields plus a transport-only locator;
- `ReadCapabilities(sequential, seekable, random_range, native_descriptor)` — explicit read guarantees;
- `PlaybackRequest(requires_seek, prefers_random_range)` — the minimum playback requirement;
- `SourceMode` — direct descriptor, seekable source, random/range source, sequential cached source, or unsupported;
- `Resolution` — selected source mode, whether caching is required, and a bounded reason;
- `resolve_source(...)` — capability-driven source selection;
- `cache_key(...)` — version-aware cache identity that excludes the locator.

## Selection rules exercised

1. A seekable native descriptor can satisfy a seek request as `DIRECT_DESCRIPTOR`.
2. A requested random/range capability is selected when available.
3. A seekable source is selected when random/range access is unavailable.
4. Sequential-only non-seek playback is represented as `SEQUENTIAL_CACHED_SOURCE` with `cache_required=True`.
5. Sequential-only playback cannot claim seek support.
6. Empty capabilities are explicitly unsupported.
7. Cache identity includes provider, object, and source version and excludes tokenized or otherwise transient locators.

## Non-goals

The harness does not prove Media3 API compatibility, ExoPlayer extractor/decoder support, descriptor seekability, SAF provider behavior, network latency, reconnect behavior, cache I/O, audio fidelity, background playback, notification behavior, or process-death recovery.

## Disposable status

The harness is labeled P05-003 and should be deleted or replaced when the actual Android/provider implementation is authorized. Its tests are contract examples, not production acceptance tests.
