# ADR-006 — Non-Root Baseline

## Status
`ACCEPTED`

## Context
Root managers are optional, authorization can be denied/revoked, and root remains subject to SELinux, mount, kernel, and read-only partition behavior.

## Decision
Normal operation is independently functional. Root is an additive, isolated provider/capability boundary rather than a global application mode.

## Consequences
Root failure cannot disable Local/SAF/network/cloud/archive behavior. Privileged actions remain explicit and protected/system mutations receive stronger safeguards.

## Alternatives rejected
Root as a startup prerequisite or hidden baseline dependency is `REJECTED`.

## Evidence references
- `docs/research/03_ROOT_ACCESS.md`
- `docs/research/11_SECURITY.md`
- `UI_DESIGN_V1.md`
