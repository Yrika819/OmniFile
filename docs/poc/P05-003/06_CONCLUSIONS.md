# P05-003 Conclusions

## Supported conclusions

1. Provider-neutral playback needs an explicit capability boundary between durable media identity and the transport source consumed by a future Media3 adapter.
2. Sequential, seekable, random/range, and native-descriptor access must be represented separately; sequential access cannot imply seek support.
3. Cache identity must be based on provider identity, object identity, and source version. A locator, signed URL, bearer token, or other transient transport value must not be durable identity.
4. A sequential-only origin can be represented as a cached fallback for non-seek playback, but the product must not claim seek support for it.
5. The disposable host contract model is internally verified by 14 passing host tests.
6. A disposable Android target using actual AndroidX Media3/ExoPlayer compiles and packages under the isolated `org.omnifile.poc.media3` id. Its source wiring and event recorder are present for an authorized runtime campaign.

## Claims explicitly not supported

This campaign does not claim that Media3 is integrated into File Manager, that any codec plays on Android, that SAF descriptors seek, that the sequential source plays successfully, that remote playback is responsive, that reconnect/version changes are handled, that cache I/O is correct, or that audio quality is acceptable. The APK build is a PoC compilation/package result, not a device result.

## Decision

Keep Media3/MediaSession as the conceptual playback direction, retain the provider-scoped resolver boundary, and treat the isolated Android target as disposable evidence code. Defer production dependency/version, Android service structure, cache implementation, format promise, and remote seek promise until an authorized runtime/device POC produces fresh evidence.
