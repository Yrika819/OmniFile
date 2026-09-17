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

## Next authorized evidence

The next implementation POC should be a small Android target with a current Media3 version selected at that time. It should first exercise direct/local and representative SAF sources on API 31 and API 36, including descriptor seekability and process/session lifecycle. Remote SMB/range playback should remain a separate campaign measuring startup, seek, bytes, cache, reconnect, source-version changes, and memory under realistic latency.

Until those runs exist, the product format list and product-quality remote seek promise remain open.
