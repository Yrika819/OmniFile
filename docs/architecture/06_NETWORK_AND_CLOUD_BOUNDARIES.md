# Architecture V1 — Network and Cloud Boundaries

Status: PRINCIPLES FROZEN / LIBRARIES, AUTH SCOPES, AND PROTOCOL DETAILS UNFROZEN

Source authority: `07_NAS_NETWORK_STORAGE.md`, `08_CLOUD_STORAGE.md`, `11_SECURITY.md`, `14_LICENSES_DISTRIBUTION.md`, and research synthesis SHA `b03a2ea99f24206f847f513fa4106e90268f3fc4`.

## Architecture direction: first-class provider APIs + SAF fallback — `ACCEPTED`

SMB, SFTP, WebDAV, Google Drive, OneDrive, and Dropbox are modeled as first-class provider boundaries when their advanced capabilities are part of the product.

SAF remains a generic interoperability path and may expose some cloud/document providers without first-class integration.

The architecture must not collapse first-class provider objects and SAF objects merely because display names or visible paths match.

## Network provider principles

### SMB — `ACCEPTED` as a provider family

SMB may expose filesystem-like list/read/write, offset I/O, locking, signing/encryption, and server-native operations, but guarantees vary by server/library/dialect.

The provider advertises only verified capabilities.

Exact SMB library — `POC_REQUIRED`.

Research candidate SMBJ is not selected by this architecture phase. Interoperability must be measured against Windows, Samba, and representative consumer NAS devices before freeze.

### SFTP — `ACCEPTED` as a provider family

SFTP is an SSH-secured remote-file provider with provider/server-dependent extensions and metadata.

Strict SSH host-key validation is mandatory.

Atomic/POSIX rename behavior must not be assumed universally.

Exact SFTP library — `POC_REQUIRED`.

Research candidate SSHJ is not selected by this architecture phase.

### WebDAV — `ACCEPTED` as a provider family

WebDAV may expose PROPFIND/MOVE/COPY and HTTP range behavior, but interoperability, redirects, range support, ETag behavior, and server semantics vary.

Exact WebDAV library — `POC_REQUIRED`.

Research candidate dav4jvm is not selected here.

## Network sessions and reconnect — `ACCEPTED`

Connections, sockets, sessions, and negotiated state are ephemeral.

Durable operation state persists provider identity/object identity/version/checkpoint data, then reopens/revalidates after interruption.

A reconnect must not assume that a reused hostname/path refers to the unchanged file.

## Timeouts — `ACCEPTED`

Long transfers require activity/idle timeout and cancellation semantics appropriate to the protocol/workload. A single simplistic total timeout is not the architectural model for a 100 GB operation.

Exact timeout values — `DEFERRED` until protocol/device benchmarking.

## Discovery — `DEFERRED`

mDNS/NSD discovery may be a convenience feature, but manual host/IP configuration must remain possible if discovery is unavailable or permission-restricted.

Exact discovery UX and Android 16/17 local-network permission handling reopen during provider implementation.

## Cloud first-class providers

### Google Drive — `ACCEPTED` as an architectural provider option

Architecture must preserve provider object IDs, pagination, search, version/change metadata, resumable transfer, and provider-native operations where available.

Exact OAuth scope — `POC_REQUIRED` because the narrowest scope compatible with intended file-manager UX must be proven before requesting broader/restricted access.

SDK vs REST — `DEFERRED`.

### OneDrive — `ACCEPTED` as an architectural provider option

Architecture must preserve drive/item identity, pagination, version/conflict metadata, resumable upload/session semantics, and the fact that cross-drive moves may require copy+verify+delete rather than a simple atomic primitive.

Exact scopes/account support and SDK-vs-REST choices — `DEFERRED` / `POC_REQUIRED`.

### Dropbox — `ACCEPTED` as an architectural provider option

Architecture must preserve object/path semantics as documented by Dropbox, cursor/pagination/version information, upload sessions, and range/download behavior only when proven.

Exact SDK-vs-REST choice and version — `DEFERRED`.

Remote range/seek behavior — `POC_REQUIRED`.

## Cloud resumable transfer — `ACCEPTED` conceptually

Operation Manager has a generic resumable-transfer concept, while provider implementations retain opaque provider session/token state.

100 GB interruption/restart behavior across first-class cloud providers — `POC_REQUIRED`.

Provider session expiration is expected and must trigger reconciliation/restart rather than unsafe continuation.

## Rate limiting and quota — `ACCEPTED`

Provider rate-limit/quota responses are structured failures. Retry/backoff follows provider semantics and server guidance; fixed blind sleeps are `REJECTED`.

## Provider-native search/change feeds — `ACCEPTED` as optional capabilities

First-class cloud providers may expose native search and change/delta feeds. These can accelerate UX/indexing, but do not become universal storage semantics or destructive-operation authority.

## Credential and trust boundary — `ACCEPTED`

Credentials, refresh tokens, passwords, private keys, signed URLs, and OAuth grants are security assets.

Mandatory principles:

- standard OAuth/public-client flows;
- least-privilege OAuth scopes;
- no embedded confidential client secret assumption in a mobile APK;
- protected local credential storage using a reviewed Keystore-backed design or equivalent;
- no tokens/passwords/private keys in logs;
- strict TLS certificate validation;
- no global trust-all certificate mode;
- strict SSH host-key validation;
- private/self-hosted CA support, if added, is explicit and narrowly scoped rather than disabling validation globally.

Exact credential-store implementation — `DEFERRED`.

## Network/cloud identity — `ACCEPTED`

Display paths and temporary URLs are not universal identity. Provider-scoped IDs/version tokens are retained where available.

Cloud object change during transfer/resume must be detected using the strongest available token and surfaced as a conflict/reconciliation state rather than silently continuing from an old offset.

## Server-side move/copy — `ACCEPTED` as optional capability

The provider may use server-native operations only when supported and their guarantees are understood. Cross-provider operations use Operation Manager transfer/finalization semantics.

Universal atomic remote move — `REJECTED`.

## Remote media and archive access

Architecture must allow ranged/random provider reads for media/archive workloads — `ACCEPTED`.

Product-quality seek/reconnect performance over SMB/SFTP/WebDAV/cloud — `POC_REQUIRED` before first-class remote playback claims.

## Exact network/cloud libraries — `POC_REQUIRED` / `DEFERRED`

No production dependency is selected in this phase.

Selection must follow interoperability, security patch cadence, Android compatibility, range/resume semantics, footprint/license review, and fault-injection evidence rather than API aesthetics alone.
