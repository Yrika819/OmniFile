# ADR-008 — Stable-First UI

## Status
`ACCEPTED`

## Context
The product wants a Material 3 Expressive identity, while research shows the stable Material 3 / Adaptive surface already covers the baseline phone, tablet, adaptive, edge-to-edge, and modern navigation needs. Experimental APIs can change rapidly.

## Decision
Use stable Android UI APIs as the baseline. Preserve the expressive product policy, but require explicit justification before depending on experimental/alpha APIs.

## Consequences
Dense file navigation stays stable and restrained, while Home/player signature motion can later evaluate newer APIs behind narrow boundaries.

## Alternatives rejected
Making an alpha dependency mandatory solely for visual branding is `REJECTED`.

## Evidence references
- `docs/research/10_UI_M3_EXPRESSIVE_ADAPTIVE.md`
- `UI_DESIGN_V1.md`
- `docs/research/15_ARCHITECTURE_RECOMMENDATIONS.md`
