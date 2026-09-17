# POC-003 — Architecture Impact

Status: COMPLETE

Architecture authority evaluated: `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`.

No Architecture V1 authority document was modified on this branch.

## CONFIRMED

- Archive browsing should remain modeled as virtual storage rather than pretending every archive is a normal random-access filesystem.
- Secure extraction requires an explicit policy layer independent from parser/codec libraries.
- RAR creation must not be assumed merely because RAR reading works.
- Native dependencies require a stronger acceptance gate than pure-Java libraries.
- Large archive metadata sets require bounded/paged treatment rather than assuming negligible whole-index cost.

## REFINED

- A measured multi-engine combination is viable: Commons Compress for broad ZIP/TAR/7z coverage, Zip4j for encrypted/split ZIP, and Junrar for RAR read/extract.
- TAR.ZST is technically viable on the tested Pixel 7a when the Android zstd-jni AAR/native library is supplied; therefore Zstandard support is not inherently blocked, but remains subject to native packaging/page-size review.
- Malformed-input signaling differs significantly by engine. The future archive abstraction should preserve engine diagnostics while normalizing the application-level failure category.
- Virtual browsing for tested 10k-entry archives is practical on the Pixel 7a, while 100k ZIP metadata memory use is large enough to justify paging/lazy indexing design work.

## CHALLENGED

- A single universal archive library is not supported by this evidence. Commons Compress did not read the encrypted ZIP fixtures, while Zip4j covered them; RAR required a separate engine.
- Treating successful extraction APIs as security guarantees is challenged by traversal/link edge cases and the need for explicit streaming expansion limits.

## CONTRADICTED

None of the frozen Architecture V1 principles were contradicted by the measured evidence.

## UNAFFECTED

- production applicationId/package/namespace;
- production module architecture;
- minSdk/targetSdk/compileSdk freeze;
- production executor architecture;
- root provider architecture;
- NAS/cloud provider selection;
- release/signing architecture.

## Unsafe to freeze from this PoC

- exact archive dependency versions;
- final archive abstraction interface;
- libarchive adoption or rejection;
- 16 KiB native compatibility;
- production thresholds for byte expansion, entry count, nesting depth, timeout, memory or cancellation;
- final RAR licensing decision.

## Safe architecture-review input

The next architecture review can safely treat the measured multi-engine approach as viable and keep explicit extraction safety as a non-negotiable boundary, while preserving the native/libarchive/licensing questions as unresolved gates.