# P05-003 Conclusions

## Supported conclusions

1. Provider-neutral playback needs an explicit capability boundary between durable media identity and the transport source consumed by a future Media3 adapter.
2. Sequential, seekable, random/range, and native-descriptor access must be represented separately; sequential access cannot imply seek support.
3. Cache identity must be based on provider identity, object identity, and source version. A locator, signed URL, bearer token, or other transient transport value must not be durable identity.
4. A sequential-only origin can be represented as a cached fallback for non-seek playback, but the product must not claim seek support for it.
5. The disposable host contract model is internally verified by 15 passing host tests.
6. A disposable Android target using actual AndroidX Media3/ExoPlayer compiled,
   packaged, and ran under the isolated `org.omnifile.poc.media3` id on one
   Pixel 7a API 36. Local WAV/FLAC and a SAF child played directly; the
   sequential source played until EOF and rejected the seek probe as expected.

## Claims explicitly not supported

This campaign does not claim that Media3 is integrated into File Manager, that
SAF pipe descriptors seek, that remote playback is responsive, that
reconnect/version changes are handled, that cache I/O is correct, or that
audio quality is acceptable. The APK result is a bounded API 36 PoC device
result, not production acceptance.

## Decision

Keep Media3/MediaSession as the conceptual playback direction, retain the provider-scoped resolver boundary, and treat the isolated Android target as disposable evidence code. Defer production dependency/version, Android service structure, cache implementation, format promise, and remote seek promise until an authorized runtime/device POC produces fresh evidence.
