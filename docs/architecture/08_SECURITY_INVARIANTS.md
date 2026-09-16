# Architecture V1 — Security Invariants

Status: MANDATORY PRINCIPLES FROZEN

Source authority: `docs/research/11_SECURITY.md` plus related storage/root/archive/network/cloud research at SHA `b03a2ea99f24206f847f513fa4106e90268f3fc4`.

These invariants are architecture requirements. Exact Android manifest rules, APIs, credential-store classes, parser libraries, and implementation details remain unfrozen.

## 1. Names, paths, URIs, and provider metadata are untrusted — `ACCEPTED`

Filenames, display paths, SAF URIs/document metadata, cloud metadata, NAS responses, archive entry names, intent inputs, and external URLs are data from trust boundaries.

They must not be treated as executable command fragments, assumed-safe paths, or stable destructive-operation identity without provider-appropriate validation.

## 2. Archive extraction verifies containment — `ACCEPTED`

Every extracted output remains within the explicitly selected destination according to the semantics of that destination/provider.

For real filesystem paths this requires normalized/canonical containment checks before creation. Absolute paths, traversal escapes, and equivalent malformed names are rejected.

For provider/URI destinations, implementation must use provider-native safe child creation rather than reconstructing filesystem paths from display names.

## 3. Archive expansion has explicit limits — `ACCEPTED`

Extraction/browsing must have architecture support for bounded:

- total expanded bytes;
- entry count;
- nesting depth;
- per-entry handling where needed;
- temporary-space use;
- memory use.

Malformed/truncated archives and decompression-bomb patterns are expected hostile inputs.

Exact default limits are `DEFERRED` until product/security testing.

## 4. Root input is not concatenated into arbitrary shell commands — `ACCEPTED`

Arbitrary filenames may contain shell metacharacters, quotes, newlines, leading hyphens, and Unicode. Structured privileged file APIs/services are preferred.

Shell-string construction as the normal root file-transport architecture is `REJECTED`.

Any later unavoidable shell surface requires explicit argument framing/escaping review plus adversarial filename tests.

## 5. Tokens, passwords, private keys, and sensitive signed URLs do not enter logs — `ACCEPTED`

Credential material is a security asset. Diagnostic systems must redact it by construction.

Sensitive provider configuration should persist credential references, not plaintext secrets in ordinary diagnostic/state fields.

## 6. TLS validation is never disabled globally — `ACCEPTED`

Global trust-all certificate behavior is `REJECTED`.

If private/self-hosted CAs are supported later, trust configuration must be explicit, narrow, and scoped rather than disabling platform validation.

## 7. SSH host-key validation never defaults to trust-all — `ACCEPTED`

SFTP authentication without server host-key verification is insufficient. Trust-on-first-use or other UX, if later supported, must remain explicit and auditable.

Exact host-key UX/persistence — `DEFERRED` until SFTP implementation.

## 8. Move source deletion follows successful destination finalization — `ACCEPTED`

A cross-provider move does not delete the source until destination transfer/process, verification, and finalization have completed successfully.

This invariant survives process death, cancellation, network loss, storage-full conditions, and ambiguous non-atomic finalization.

## 9. Recursive operations have an explicit symlink policy — `ACCEPTED`

Destructive recursive copy/delete/extract operations must not silently cross symlink boundaries.

The provider must distinguish symlink identity/target when available. Exact user-facing defaults may vary by operation but cannot be implicit accident.

## 10. Malformed inputs are expected — `ACCEPTED`

Archives, media, images, metadata, network responses, cloud metadata, filenames, and intents may be malformed or malicious.

Parsers/providers must fail safely, keep resource use bounded, and receive malformed/adversarial regression fixtures alongside feature work.

## 11. Exported Android surfaces follow least privilege — `ACCEPTED`

Future activities/services/receivers/providers are internal by default unless a product requirement requires exposure.

Any external OPEN/VIEW/share surface must validate untrusted inputs and grant only narrowly required temporary access.

Exact manifest/exported rules are `DEFERRED` because no AndroidManifest exists in this phase.

## 12. Secure deletion on flash is not falsely guaranteed — `ACCEPTED`

Ordinary app-level overwrite/delete cannot honestly guarantee forensic erasure on modern flash/storage stacks because of wear leveling, remapping, copy-on-write/snapshots, provider behavior, and remote retention.

Marketing ordinary delete as guaranteed “secure erase” is `REJECTED`.

Provider-native crypto-erase/retention mechanisms, if ever offered, require separate backend-specific evidence.

## 13. Credentials use a protected storage boundary — `ACCEPTED`

Persisted OAuth refresh tokens, NAS passwords, and SSH private-key material require a reviewed protected design, expected to use Android Keystore-backed cryptographic protection or an equivalent platform-appropriate mechanism.

Exact key hierarchy/library/schema — `DEFERRED`.

## 14. OAuth uses least privilege — `ACCEPTED`

The application requests only scopes required for the intended UX. Broad/restricted scopes require evidence and distribution-policy review.

Exact Drive/OneDrive/Dropbox scopes are `POC_REQUIRED` or `DEFERRED`, not frozen here.

## 15. Mobile apps do not assume embedded confidential client secrets are secret — `ACCEPTED`

Public-client OAuth flows are required. APK-embedded values must not be treated as confidential secrets.

## 16. Root elevation is explicit and additive — `ACCEPTED`

No hidden automatic elevation. Root denial/revocation cannot break normal mode. Protected/system-path destructive actions require stronger UX safeguards.

## 17. Operation recovery data is not generic cache — `ACCEPTED`

A generic cache-clear action must not delete operation checkpoints/partials needed for safe reconciliation or user-requested offline files.

## 18. Search/index/cache data is not destructive authority — `ACCEPTED`

Before mutation, the actual provider object/capability/version is revalidated as appropriate. Stale index rows or thumbnails cannot authorize delete/move/overwrite.

## 19. Provider redirects, URLs, and remote trust metadata are untrusted — `ACCEPTED`

Network providers must handle redirects, host changes, certificates, OAuth endpoints, signed URLs, and server metadata without bypassing trust boundaries or leaking credentials.

Exact HTTP client/network-security configuration — `DEFERRED`.

## 20. Temporary files and caches remain private by default — `ACCEPTED`

Temporary conversion/edit/transfer data, media cache, thumbnails, and metadata caches should not be unnecessarily exposed to other apps or backups. Exact backup exclusions and storage locations are implementation decisions.

## 21. External file opening is user-directed, not arbitrary execution — `ACCEPTED`

Opening/share/install flows use Android's explicit user-mediated mechanisms. The File Manager must not silently execute arbitrary files or bypass package-installer consent.

## 22. Security tests ship with features — `ACCEPTED`

Each provider/parser/operation feature gains relevant tests for hostile names, permission denial, trust failure, malformed input, process death, provider disconnect/conflict, symlink/path containment, and log redaction.

Security validation is continuous architecture work, not a pre-release-only pass.
