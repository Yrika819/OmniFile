# P05-003 Conclusions

## Supported conclusions

1. Provider-neutral playback needs an explicit capability boundary between durable media identity and the transport source consumed by a future Media3 adapter.
2. Sequential, seekable, random/range, and native-descriptor access must be represented separately; sequential access cannot imply seek support.
3. Cache identity must be based on provider identity, object identity, and source version. A locator, signed URL, bearer token, or other transient transport value must not be durable identity.
4. A sequential-only origin can be represented as a cached fallback for non-seek playback, but the product must not claim seek support for it.
5. The disposable contract model is internally verified by 10 passing host tests.

## Claims explicitly not supported

This campaign does not claim that Media3 compiles in File Manager, that any codec plays, that SAF descriptors seek, that remote playback is responsive, that reconnect/version changes are handled, that cache I/O is correct, or that audio quality is acceptable.

## Decision

Keep Media3/MediaSession as the conceptual playback direction, retain the provider-scoped resolver boundary, and defer dependency/version, Android service structure, cache implementation, format promise, and remote seek promise until an authorized Android POC produces fresh evidence.
