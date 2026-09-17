# POC-001 — Android Storage Capability Matrix — Plan

Status: COMPLETE

Architecture authority: `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`
Research authority: `b03a2ea99f24206f847f513fa4106e90268f3fc4`
Branch: `poc/storage-capabilities-v1`

## Scope

This PoC measures Android storage behavior; it does not define production package/module/API choices.
All harness identifiers are **POC-ONLY — NOT PRODUCTION AUTHORITY**.

Primary environments:
- Android 16 / API 36 emulator, x86_64 Google APIs image.
- Android 12 / API 31 emulator, x86_64 Google APIs image.

Storage surfaces planned:
- direct shared storage under a dedicated `Documents/FileManagerPoc001Direct` fixture root;
- user-selected SAF tree backed by the emulator ExternalStorage/DocumentsProvider;
- a synthetic PoC DocumentsProvider exposing a pipe-like descriptor to prove that FD availability does not imply seekability.

Unavailable surfaces are recorded as `NOT_TESTED`, never converted to PASS:
- physical SD removal;
- USB OTG unplug;
- physical OEM DocumentsProvider variation;
- real cloud-backed DocumentsProvider unless one is actually present.

## Measurements

For each available surface, measure as applicable:
- list and stat-like metadata;
- sequential read;
- seek and random read;
- write/truncate/append;
- same-parent rename;
- cross-directory move;
- delete/mkdir;
- ParcelFileDescriptor/AssetFileDescriptor availability;
- actual lower-level `lseek` result and descriptor file type;
- size and modification-time behavior;
- URI/document-ID stability after rename/move;
- provider flags;
- cancellation behavior;
- persisted SAF grant behavior across process restart and revocation;
- >4 GiB sparse-size behavior where the filesystem/provider permits it.

## Fixtures

Dedicated generated fixtures only:
- zero-byte;
- small text;
- 64 MiB deterministic binary fixture;
- >4 GiB sparse candidate;
- Unicode and emoji names;
- spaces, quotes, shell metacharacters, newline name where accepted;
- long filename;
- nested directories.

## Safety

No test is permitted outside generated PoC directories.
No unrelated user data is read, overwritten, moved, or deleted.
No production Android application is initialized.
