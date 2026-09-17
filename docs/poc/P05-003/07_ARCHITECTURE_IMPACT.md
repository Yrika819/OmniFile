# P05-003 Architecture Impact

## Required future boundaries

The future implementation should keep these responsibilities separate:

```text
Provider-scoped MediaIdentity
        |
Provider ReadCapability / ReadHandle
        |
MediaSourceResolver
        |
Media3 DataSource / MediaItem adapter
        |
MediaSessionService + playback state gateway
```

The resolver should consume provider identity and advertised guarantees, not infer capability from a URI scheme or file extension. It should return a source whose lifetime, cancellation, length, seek behavior, and version semantics are explicit.

## Cache impact

The stream/block cache must be distinct from metadata, artwork, offline/pinned content, and operation recovery data. Cache keys should include provider identity, object identity, and source version or equivalent conflict token. Source-version changes must invalidate or isolate stale media blocks.

## FLACtify reuse boundary

The reusable concepts are session ownership, controller reconnection, playlist commands, metadata/artwork behavior, and user-visible playback controls. The scanner, folder-oriented persistence, URI-only library identity, global cache factory, tag editor, recommendations, and monolithic `PlayerViewModel` should not be transplanted into the File Manager architecture.

## P05-003 runtime PoC boundary

The disposable Android target now exercises the proposed Media3 adapter seam at compile time and provides manual probes for local, SAF-tree, direct, and sequential sources. Its JSONL schema records prepare/start/playback/duration, seek, EOF, stop, reopen, player recreation, source re-resolution, and failure details. This makes the next runtime campaign measurable without turning the PoC into a production module.

## Next authorized evidence

The next runtime campaign should exercise the current disposable APK on API 31 and API 36, beginning with the local WAV and representative SAF sources, descriptor seekability, sequential-source failure/fallback behavior, and process/session lifecycle. Remote SMB/range playback should remain a separate campaign measuring startup, seek, bytes, cache, reconnect, source-version changes, and memory under realistic latency.

Until those runs exist, the product format list and product-quality remote seek promise remain open.
