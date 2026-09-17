# ADR-002 — Provider-Scoped Identity

## Status
`ACCEPTED`

## Context
SAF documents, cloud objects, remote resources, and archive entries cannot safely use a display path as universal durable identity.

## Decision
Entry identity is opaque and scoped to its provider/source. Display name, breadcrumb, path, URI text, and temporary transport URLs remain separate presentation/transport data.

## Consequences
Rename/path changes need not redefine conceptual identity, caches avoid path aliasing, and destructive actions revalidate provider objects rather than trusting display text.

## Alternatives rejected
Path string as universal identity is `REJECTED`.

## Evidence references
- `docs/research/01_ANDROID_PLATFORM_STORAGE.md`
- `docs/research/02_STORAGE_PROVIDER_ARCHITECTURE.md`
- `docs/research/08_CLOUD_STORAGE.md`
- `docs/research/12_PERFORMANCE_AND_LIMITS.md`

## P0 evidence update

POC-001 observed both SAF URI and `documentId` changes after rename and cross-directory move on the tested local provider (`poc/storage-capabilities-v1`, `docs/poc/POC-001/03_RESULTS.md`, raw `pixel7a-api36-saf.jsonl`). This supports the decision's separation of opaque identity from presentation/locator data, but does not make the tested behavior universal across SAF providers. A production adapter must re-resolve mutable locators and classify ambiguity.
