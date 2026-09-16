# Storage Provider Architecture Research

Status: COMPLETE — conceptual architecture only  
Last verified: 2026-09-17

## Classification legend

- **FACT** — supported by cited platform/protocol documentation.
- **INFERENCE** — architecture consequence of those facts.
- **RECOMMENDATION** — proposed direction; not frozen.
- **UNRESOLVED** — requires prototype evidence.

## Core conclusion

**RECOMMENDATION:** A single “filesystem-looking” abstraction can provide common discovery and streaming primitives, but it must be **capability-based**. It must not promise that every backend behaves like `java.io.File`.

The future source families have fundamentally different semantics:

- Local/direct: real Linux paths, descriptors and filesystem behavior where Android allows access.
- SAF: opaque `content://` identities and provider-advertised operations.
- Root: real filesystem semantics plus elevation, but still constrained by kernel/SELinux/mount policy.
- SMB: remote filesystem protocol with handles, offsets, locking and server semantics.
- SFTP: remote file operations over SSH; extension support varies by server.
- WebDAV: HTTP/WebDAV resource model; method and range support vary.
- Google Drive / OneDrive / Dropbox: object/document APIs with IDs, metadata, pagination, resumable upload and provider-specific server operations.
- Archive: a virtual read-mostly tree whose entries may be sequentially compressed and not independently seekable.

Sources:
- SAF: https://developer.android.com/guide/topics/providers/document-provider
- DocumentsContract flags: https://developer.android.com/reference/android/provider/DocumentsContract.Document
- SMB protocol overview/spec links: https://github.com/hierynomus/smbj
- Microsoft Graph DriveItem: https://learn.microsoft.com/en-us/graph/api/resources/driveitem
- Google Drive files: https://developers.google.com/workspace/drive/api/reference/rest/v3/files
- Dropbox file access: https://developers.dropbox.com/dbx-file-access-guide

## Why a `File` clone is unsafe

### Real paths are not universal

**FACT:** SAF identities are document URIs/IDs, while cloud APIs use provider object IDs. Display names are metadata, not stable paths.

**INFERENCE:** A universal `path: String` would either be fake or accidentally become an unstable display path for non-local providers.

**RECOMMENDATION:** Use an opaque provider-scoped entry identifier. A human-readable path/breadcrumb may exist as presentation metadata, never as the universal identity.

### Move/rename semantics differ

**FACT:** SAF exposes rename/move support through flags and operations; not every provider supports them. Google Drive, OneDrive and Dropbox expose their own metadata/move operations. SFTP/SMB/WebDAV have protocol-specific semantics.

**INFERENCE:** A method named `rename()` cannot imply POSIX atomic rename across providers.

**RECOMMENDATION:** Separate *operation availability* from *operation guarantees*. A capability may state `rename = supported` while `atomicRename = false/unknown`.

### Random access differs

**FACT:** A direct file supports seek. SAF can expose a descriptor that may or may not be a regular seekable file. HTTP/WebDAV/cloud can expose byte-range download only where supported. Compressed archive entries may be intrinsically sequential.

**RECOMMENDATION:** Do not put `seek()` on the minimum read contract. Expose a seekable/random-access handle only after capability negotiation.

### Server-side copy differs

**FACT:** Some cloud/DAV/SMB systems can perform server-side copy/move. A generic client-side stream copy works more broadly but can be vastly slower and consume data twice.

**RECOMMENDATION:** Operation planning should choose an optimized server-side primitive when source/destination share a provider and the provider reports it, otherwise fall back to streaming copy.

## Proposed conceptual vocabulary

Names below are deliberately illustrative and are **not implementation authority**.

### Entry identity and metadata

A conceptual `StorageEntry` should represent:

- provider/source identity
- opaque entry identity
- display name
- kind: file / directory / symlink / virtual / other
- size when known
- modification time when known
- MIME/media hints when known
- optional Unix metadata facet
- optional provider metadata facet
- advertised capabilities for the entry

**RECOMMENDATION:** Unknown metadata must be representable as unknown, not synthesized (for example, cloud creation time must not become a fake local inode timestamp).

### Source/provider contract

A conceptual `StorageProvider` should cover only broadly meaningful operations:

- resolve/list children
- obtain metadata
- open a sequential read handle
- create/replace a destination where supported
- delete/create directory where supported
- expose capabilities

Higher-order operations should be optional/capability-driven:

- rename
- move
- server-side copy
- seekable read
- random write
- append
- native file descriptor
- POSIX metadata changes
- symlink operations
- watch/change feed
- resumable upload/download

### Read handles

Conceptually separate:

1. **ReadHandle** — sequential bounded streaming.
2. **SeekableReadHandle** — can query position and seek.
3. **RandomReadHandle** — offset reads without mutating shared cursor, useful for media/archive parsers.
4. **NativeFdFacet** — OS descriptor available and valid for a documented lifetime.

**RECOMMENDATION:** Media playback and archive browsing should request the weakest handle sufficient for the task. A player may prefer seekable/random access but can fall back to cached sequential input where sensible.

### Write handles

Conceptually separate:

- create-new
- replace/truncate
- append
- resumable/chunked session
- random write
- durable flush/commit where meaningful

Do not define one universal `openOutputStream(append=true)` and assume it maps cleanly to cloud/WebDAV/SAF.

### Transfer endpoints

A conceptual transfer layer can expose `TransferSource` and `TransferDestination` characteristics such as:

- known length
- seek/range support
- resume token support
- stable version/etag
- server-side-copy eligibility
- integrity/hash metadata
- temporary/partial destination support
- atomic-finalization support

This allows Operation Manager to plan recovery without exposing provider details to UI.

## Capability model

A capability should answer both **can** and **with what guarantee**.

Suggested conceptual categories:

| Capability | Example values |
|---|---|
| Read | none / sequential / ranged / random |
| Write | none / create / replace / append / random |
| Rename | unsupported / supported-nonatomic / atomic-guaranteed |
| Move | unsupported / client-stream / provider-native / atomic-same-container |
| Copy | client-stream / provider-native / server-side |
| Identity stability | session / persistent ID / path-dependent |
| Native descriptor | none / pipe-like / regular-file candidate |
| Unix metadata | none / readonly / mutable |
| Watch | none / polling / protocol events / change feed |
| Resume | none / offset / upload-session / provider token |
| Versioning | none / mtime / etag / generation/revision ID |

**RECOMMENDATION:** Keep capabilities typed rather than a large bag of booleans when semantics contain parameters. For example, `RandomRead(granularity, maxRange?)` is more useful than `supportsSeek = true` for remote providers.

## Provider-specific considerations

### Local/direct

Strengths:
- real paths
- strong seek/random access
- descriptors and mmap where valid
- efficient same-filesystem rename
- optional rich POSIX metadata

Constraints:
- Android permissions/scoped storage
- volume/file-system behavior varies
- removable media can disappear

### SAF

Strengths:
- user-authorized generic provider access
- can represent local/removable/cloud providers
- persisted URI permissions

Constraints:
- operations depend on document flags/provider
- opaque identity
- descriptor may be non-seekable
- no universal watcher/POSIX semantics

### Root

Strengths:
- access to paths unavailable to normal app, subject to actual root environment
- POSIX metadata and symlink operations where kernel/mount/SELinux allow

Constraints:
- optional and user-deniable
- elevated security boundary
- root implementation differs (Magisk/KernelSU/APatch)
- root does not nullify SELinux/mount/read-only constraints

### SMB

Strengths:
- filesystem-like protocol
- offset I/O, locking, server-side semantics available depending on server/library
- SMB 2/3 signing/encryption capabilities

Constraints:
- connection/session state
- dialect/server differences
- Android network transitions

### SFTP

Strengths:
- authenticated encrypted transport via SSH
- offset-based file transfers are broadly practical
- Unix-ish metadata may be available

Constraints:
- extensions such as POSIX rename vary
- host-key trust is a mandatory security concern

### WebDAV

Strengths:
- HTTP ecosystem, PROPFIND/MOVE/COPY where server supports WebDAV
- can exploit Range requests for remote reading when server supports ranges

Constraints:
- server interoperability varies substantially
- HTTP redirect/TLS/auth behavior must be treated carefully

### First-class cloud APIs

Strengths:
- stable object IDs
- pagination/search
- provider-native metadata
- resumable uploads
- provider-native copy/move where available
- change/delta feeds and shared-drive/team semantics

Constraints:
- OAuth scopes/review
- quotas/rate limits
- provider-specific conflict/version semantics
- no Unix path/permission model

## Error model

**RECOMMENDATION:** Do not surface every backend error as a generic `IOException`. Preserve structured categories while retaining original provider details for diagnostics:

- NotFound
- PermissionDenied / GrantRevoked
- ReadOnly
- CapabilityUnsupported
- Conflict / AlreadyExists
- SourceChanged / VersionConflict
- StorageFull / QuotaExceeded
- NetworkUnavailable / Timeout
- AuthenticationExpired
- HostKeyOrCertificateFailure
- ProviderRateLimited
- SourceDisconnected
- CorruptData
- Cancelled
- UnknownProviderFailure

This error vocabulary should remain implementation-neutral and map to provider-native errors.

## Cross-provider copy/move

**RECOMMENDATION:** Define move conceptually as a transaction-like plan, not one primitive:

1. determine whether provider-native move is available;
2. otherwise stream copy to a partial destination;
3. verify completion/integrity where possible;
4. finalize destination;
5. only then delete source;
6. persist recovery state around every irreversible boundary.

A cross-provider “move” must not delete the source merely because a write stream ended successfully.

## Archive as a virtual provider

**RECOMMENDATION:** Present archive contents through the same entry/list/read concepts where helpful, but capability flags should accurately show typical limitations: read-only browsing, no path identity outside archive, no atomic rename, and potentially expensive random access for solid archives.

Nested archives should be explicitly bounded to prevent recursion/decompression abuse.

## Playback integration

**RECOMMENDATION:** Playback should depend on a media-readable source contract, not on local paths. A bridge to Media3 can adapt:

- direct file/FD
- `content://`
- HTTP/WebDAV range
- SMB/SFTP provider reads
- cloud ranged reads
- a block cache when the origin is high-latency

This boundary is important because FLACtify currently couples scanning/metadata/playback more closely than the new file manager should.

## Persistence

Persist provider-neutral operation state separately from ephemeral handles. Durable state may include:

- source provider + opaque entry ID
- destination provider + parent ID + desired name
- source version/etag/mtime snapshot
- bytes/chunks completed
- resumable session token where safe
- temp destination identity
- operation state/retry metadata

Do not persist open FDs, sockets, shell processes or in-memory provider objects as recovery state.

## Security boundaries

- Providers must not receive raw credentials from UI state.
- Credentials/tokens should be referenced through secure credential storage, not embedded in entry IDs.
- Names are data, never shell fragments.
- Provider URLs and redirects are untrusted network input.
- Archive virtual entries require traversal and symlink defenses.
- Root provider must remain isolated from non-root baseline APIs so accidental elevation is difficult.

## Prototype-required questions

1. Can the chosen Media3 integration perform smooth seeking over custom SMB/SFTP/cloud random-read adapters?
2. How do representative SAF providers behave when rename changes URI identity?
3. Which providers expose regular FDs versus pipes?
4. Can a shared block-cache design serve playback, archive metadata and copy without excessive duplication?
5. What transfer checkpoint granularity gives reliable resume without unacceptable database/write amplification?
6. Which server-side copy/move primitives are worth exposing in V1 vs later?

## Recommended direction

**Strong recommendation:** capability-based provider abstraction with opaque entry identity and separate sequential/seekable/random-access handles.  
**Strong recommendation:** Operation Manager owns cross-provider transfer semantics and recovery; providers expose primitives and capabilities.  
**Plausible:** archives can implement a virtual read provider rather than a separate UI-only tree.  
**Postpone:** exact Kotlin interface names/module boundaries, dependency choices, cache implementation and database choice.
