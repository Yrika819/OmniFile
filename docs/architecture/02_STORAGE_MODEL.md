# Architecture V1 — Conceptual Storage Model

Status: PRINCIPLE FROZEN / IMPLEMENTATION UNFROZEN
Source authority: research SHA `b03a2ea99f24206f847f513fa4106e90268f3fc4`

## Architecture decision

Capability-based, provider-scoped storage modeling is `ACCEPTED`.

A universal local-path or `java.io.File` model is `REJECTED` because SAF, root, archives, SMB/SFTP/WebDAV, and first-class cloud APIs do not share one honest identity/access/atomicity model.

This document defines concepts only. It does not define Kotlin interface names, modules, concrete classes, dependency choices, or persistence schemas.

## Provider identity

A **provider/source** identifies one independently addressable storage domain or account/connection/volume. Examples may eventually include Local, SAF, Root, Archive, SMB, SFTP, WebDAV, Google Drive, OneDrive, and Dropbox.

Provider identity must be distinguishable from an entry identity. A provider can become unavailable, be disconnected/ejected, have authorization revoked, or reconnect with a changed session while previously persisted operation state remains.

Provider lifecycle/disconnect as a first-class concept — `ACCEPTED`.

Exact provider-object lifetime and API shape — `DEFERRED` until implementation design.

## Entry identity

Each entry has a provider-scoped opaque identity — `ACCEPTED`.

Identity is conceptually separate from:

- display name;
- breadcrumb/presentation path;
- URI text shown to the user;
- transient download URL;
- cache key derived only from a name/path.

A display path may be useful and visible, particularly for power users, but it is not universal destructive-operation authority.

The following are `REJECTED`:

- path string as universal identity;
- deriving a real filesystem path from a SAF `content://` display name;
- assuming cloud paths are durable object identity;
- assuming rename preserves a particular textual URI/path representation.

## Entry metadata

Common display metadata may include, when known:

- display name;
- entry kind (file, directory, symlink, virtual, other);
- size;
- modified/created time;
- MIME/media hints;
- display breadcrumb/path context.

Optional/provider-specific facets may include:

- Unix owner/group/mode;
- symlink target;
- inode/device-like identity;
- ETag/generation/revision/version token;
- provider-specific IDs and sharing metadata;
- compressed/packed size for archive entries;
- server-specific metadata.

Unknown metadata must remain unknown — `ACCEPTED`.

## Parent/child relationships

A provider exposes discoverable relationships according to its own semantics. Hierarchies may be real filesystem directories, SAF document relationships, archive virtual trees, server resources, or cloud folder/parent relationships.

Architecture must allow that:

- an entry can have provider-defined parent relationships;
- a cloud object may not behave like a unique POSIX path;
- archive ancestry includes the outer archive identity;
- removable/provider disappearance can invalidate an entire source;
- recursive traversal needs explicit loop/symlink policy.

A single canonical POSIX-like parent path for every provider — `REJECTED`.

## Capability model

Capability-based behavior — `ACCEPTED`.

A capability answers both **whether** an action is available and, when relevant, **what guarantee** it provides.

Conceptual capability categories include:

|Area|Conceptual capability/guarantee|
|---|---|
|Read|none / sequential / ranged / seekable / random-offset|
|Write|none / create / replace / append / random-write|
|Rename|unsupported / supported without atomic guarantee / atomic when explicitly guaranteed|
|Move|unsupported / client-stream plan / provider-native / atomic same-container when guaranteed|
|Copy|client-stream / provider-native / server-side|
|Identity stability|session-scoped / persistent object ID / path-dependent / provider-defined|
|Native descriptor|none / pipe-like / regular-file candidate|
|Unix metadata|none / read-only / mutable|
|Watch/change|none / polling / events / change feed|
|Resume|none / byte offset / upload session / provider token|
|Versioning|none / mtime-like / ETag / generation/revision ID|
|Finalization|non-atomic / provider commit / atomic rename when guaranteed|

Exact types, names, and parameter structures — `DEFERRED`.

## Read semantics

### Sequential read — `ACCEPTED`

Bounded sequential streaming is the minimum read semantic for readable entries.

### Seekable read — `ACCEPTED` as a distinct capability

Seeking is available only when proven by the provider/handle. A file descriptor existing is not sufficient to claim it is a regular seekable file.

### Random-offset/ranged read — `ACCEPTED` as a distinct capability

Random reads may be useful for Media3, metadata extraction, archive indexes, resumable transfers, and remote sources. They must not be inferred from sequential-read support.

### Native descriptor / mmap

Native descriptor availability is an optional facet — `ACCEPTED`.

Universal mmap compatibility — `REJECTED`.

SAF/provider descriptor behavior and mmap value — `POC_REQUIRED`.

## Write semantics

Write behavior must distinguish conceptually:

- create-new;
- replace/truncate;
- append;
- resumable/chunked session;
- random write;
- provider commit/finalization.

Universal `openOutputStream(append=true)` semantics — `REJECTED`.

Provider-specific append/random-write support is discovered, not invented.

## Native/provider-side operations

Provider-native or server-side copy/move may be used when capability and guarantees are known — `ACCEPTED`.

If unavailable, Operation Manager may plan a client-side bounded transfer.

A method named “move” is never sufficient proof of atomicity — `ACCEPTED`.

## Change/version tokens

The strongest available source/destination version token should be preserved for reconciliation/resume/conflict detection — `ACCEPTED`.

Depending on provider, this may be an ETag, revision/generation ID, modified time, object ID plus metadata, or no meaningful version token.

Synthesizing a fake universal version scheme — `REJECTED`.

## Provider lifecycle and reconnect semantics

A provider/session may become unavailable because of:

- SD/USB eject;
- revoked SAF grant;
- network loss/change;
- NAS session failure;
- cloud auth expiry;
- root denial/revocation/service crash;
- archive source mutation/deletion.

The architecture treats these as expected states, not impossible programmer errors — `ACCEPTED`.

After reconnect/reopen, durable operations re-resolve persisted identities and validate source/version/partial-destination reality before continuing. Open FDs, sockets, root shells, in-memory provider objects, and transient signed URLs are not durable recovery state.

## Conceptual provider matrix

Legend: `Y` normally meaningful, `P` provider/filesystem/server/entry dependent, `N` no portable semantic. This table is conceptual, not a promise that all eventual implementations ship in V1.

|Capability|Local|SAF|Root|Archive|SMB|SFTP|WebDAV|Drive|OneDrive|Dropbox|
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
|List/basic discovery|Y|Y|Y|Y|Y|Y|Y|Y|Y|Y|
|Sequential read|Y|Y|Y|Y|Y|Y|Y|Y|Y|Y|
|Seek/random read|Y|P|Y|P|P|P|P|P|P|P|
|Create/replace|Y|P|Y|P/N|Y|Y|P|Y|Y|Y|
|Append/random write|Y|P|Y|N/P|P|P|P|N/P|N/P|N/P|
|Rename|Y|P|Y|N/P|P|P|P|P|P|P|
|Atomic rename|P|N/P|P|N|P|P|P|N/P|N/P|N/P|
|Provider/server copy|P|P|P|N/P|P|P|P|P|P|P|
|Provider/server move|P|P|P|N/P|P|P|P|P|P|P|
|POSIX metadata|P|N/P|P|N/P|N/P|P|N|N|N|N|
|Symlink semantics|P|N/P|P|archive-format dependent|P|P|N/P|N|N|N|
|Resume token/session|P|P|P|N/P|P|P|P|P|P|P|
|Version/change token|P|P|P|outer-source/version dependent|P|P|P|Y/P|Y/P|Y/P|
|Watch/change feed|P|N|P|N|P|P|P|Y/P|Y/P|Y/P|
|Can disconnect/revoke|Y/P|Y|Y|source dependent|Y|Y|Y|Y|Y|Y|

The matrix deliberately uses `P` rather than converting uncertainty into a fake universal “yes”.

## Provider-family architecture positions

### Local/direct

Richer real-filesystem semantics where Android access permits — `ACCEPTED`.

Exact all-files permission strategy — `DEFERRED` pending product/distribution review and device evidence.

### SAF

First-class interoperability mechanism with opaque identity/capability flags — `ACCEPTED`.

Representative seek/write/rename/removable behavior — `POC_REQUIRED`.

### Root

Optional isolated provider/capability boundary — `ACCEPTED`.

Exact root transport/library — `POC_REQUIRED` / implementation choice unfrozen.

### Archive

Browsable virtual storage model where format/engine permits — `ACCEPTED`.

Random-access cost and writable/creation semantics remain format/engine-specific.

### SMB / SFTP / WebDAV

First-class provider boundaries with protocol-specific semantics — `ACCEPTED`.

Exact libraries and reconnection/range/server-operation guarantees — `POC_REQUIRED`.

### Google Drive / OneDrive / Dropbox

First-class provider APIs where advanced capabilities justify them, with SAF fallback for generic interoperability — `ACCEPTED` as an architecture direction.

Exact SDK-vs-REST choices and OAuth scopes — `DEFERRED` or `POC_REQUIRED` as specified in the PoC/deferred documents.

## Destructive-operation authority

Caches, breadcrumbs, thumbnails, search indexes, and stale listing snapshots are never sufficient authority for destructive operations — `ACCEPTED`.

Before destructive mutation, the architecture revalidates the real provider object/version/capability as appropriate to the provider.
