# ADR-001 — Capability-Based Storage

## Status
`ACCEPTED`

## Context
The researched storage backends do not share identical identity, seek, metadata, atomicity, write, resume, or server-operation semantics.

## Decision
Use a capability-based conceptual storage model. Common abstractions expose only genuinely common semantics; provider-specific guarantees remain explicit.

## Consequences
UI and operations become capability-aware. Providers may omit unsupported guarantees, and contract tests validate only what each provider advertises.

## Alternatives rejected
A universal local-filesystem semantic model is `REJECTED` because it would misrepresent SAF, archive, network, and cloud behavior.

## Evidence references
- `docs/research/01_ANDROID_PLATFORM_STORAGE.md`
- `docs/research/02_STORAGE_PROVIDER_ARCHITECTURE.md`
- `docs/research/15_ARCHITECTURE_RECOMMENDATIONS.md`
