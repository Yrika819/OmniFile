# ADR-007 — Provider-Neutral Playback

## Status
`ACCEPTED`

## Context
The File Manager must play media from local, SAF, and potentially remote/cloud providers without requiring every source to expose a local filesystem path.

## Decision
Playback resolves provider-scoped media identity into a Media3-readable source through storage capabilities.

## Consequences
Remote seek quality becomes a separate PoC question, caches are provider/version aware, and FLACtify behavior can be reused without importing path-coupled architecture.

## Alternatives rejected
Requiring a local path for all playback and transplanting FLACtify's monolithic `PlayerViewModel` are `REJECTED`.

## Evidence references
- `docs/research/02_STORAGE_PROVIDER_ARCHITECTURE.md`
- `docs/research/05_MEDIA_PLAYBACK_FLACTIFY.md`
- `docs/research/15_ARCHITECTURE_RECOMMENDATIONS.md`
