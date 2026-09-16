# ADR-003 — Separate Read Access Capabilities

## Status
`ACCEPTED`

## Context
Direct files are usually seekable, but SAF may yield pipe-like descriptors, remote providers may support only ranges, and compressed archive entries may be sequential or expensive to revisit.

## Decision
Sequential read, seekable read, random/ranged read, and native-descriptor availability are distinct capabilities.

## Consequences
Playback, archive, metadata, and transfer code request the weakest sufficient access mode. Providers do not need to fake seek support.

## Alternatives rejected
A minimum read contract that universally promises `seek()` is `REJECTED`.

## Evidence references
- `docs/research/01_ANDROID_PLATFORM_STORAGE.md`
- `docs/research/02_STORAGE_PROVIDER_ARCHITECTURE.md`
- `docs/research/04_ARCHIVE_FORMATS.md`
- `docs/research/05_MEDIA_PLAYBACK_FLACTIFY.md`
