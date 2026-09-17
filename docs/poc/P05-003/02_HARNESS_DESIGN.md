# P05-003 Harness Design

## Purpose

`tools/p05_003_harness/` contains a disposable host contract harness and a disposable Android runtime harness. The Android target is deliberately not a production app, service, provider adapter, or product dependency decision.

## Model

`p05_003_contract.py` defines:

- `MediaIdentity(provider_id, object_id, source_version, locator)` — durable identity fields plus a transport-only locator;
- `ReadCapabilities(sequential, seekable, random_range, native_descriptor, descriptor_readable)` — explicit read guarantees; descriptor presence alone is insufficient;
- `PlaybackRequest(requires_seek, prefers_random_range)` — the minimum playback requirement;
- `SourceMode` — direct descriptor, seekable source, random/range source, sequential cached source, or unsupported;
- `Resolution` — selected source mode, whether caching is required, and a bounded reason;
- `resolve_source(...)` — capability-driven source selection;
- `cache_key(...)` — version-aware cache identity that excludes the locator.
- `EventKind`, `RuntimeEvent`, and `EventRecorder` — deterministic lifecycle event schema with query-secret redaction;
- `SequentialReadSource` — finite host model that returns fixed chunks, EOF, and an explicit unsupported-seek failure.

The Android PoC under `media3-poc/` defines:

- an isolated `org.omnifile.poc.media3` application using actual Media3 ExoPlayer;
- an app-private generated PCM16 WAV source;
- `ACTION_OPEN_DOCUMENT_TREE` plus a `DocumentsContract` child-document query;
- direct URI playback and `SequentialDataSource`, whose `open` rejects any nonzero `DataSpec.position` and whose `read` returns Media3 EOF;
- `RuntimeEventRecorder`, which appends redacted JSONL and mirrors events to the `P05-003` log tag.

## Selection rules exercised

1. A seekable, explicitly readable native descriptor can satisfy a seek request as `DIRECT_DESCRIPTOR`.
2. A requested random/range capability is selected when available.
3. A seekable source is selected when random/range access is unavailable.
4. Sequential-only non-seek playback is represented as `SEQUENTIAL_CACHED_SOURCE` with `cache_required=True`.
5. Sequential-only playback cannot claim seek support.
6. Empty capabilities are explicitly unsupported.
7. Cache identity includes provider, object, and source version and excludes tokenized or otherwise transient locators.

## Non-goals

The host harness does not prove Media3 API compatibility. The Android build proves source compilation and packaging only; neither host nor build evidence proves ExoPlayer extractor/decoder support at runtime, descriptor seekability, SAF provider playback, network latency, reconnect behavior, cache I/O, audio fidelity, background playback, notification behavior, or process-death recovery.

## Disposable status

The harness is labeled P05-003 and should be deleted or replaced when the actual Android/provider implementation is authorized. Its tests are contract examples, not production acceptance tests.
