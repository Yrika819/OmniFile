# POC-001 — Results

Status: COMPLETE

Architecture authority: `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`

## Runtime environments

| Environment | Android | API | Device | Scope |
|---|---:|---:|---|---|
| AVD `poc_filemanager_api31` | 12 | 31 | Google APIs x86_64 | direct shared storage + synthetic pipe |
| AVD `poc_filemanager_api36` | 16 | 36 | Google APIs x86_64 | direct shared storage + synthetic pipe |
| Physical | 16 | 36 | Pixel 7a (`lynx`) | direct + synthetic pipe + real local SAF tree |

Raw evidence is under `poc/POC-001-storage-capabilities/results/`.

## Capability matrix

Legend: YES / NO / PROVIDER_DEPENDENT / NOT_TESTED.

| Surface | Sequential read | Seek/random read | Write/append/truncate | Rename/move | Identity behavior | FD | Notes |
|---|---|---|---|---|---|---|---|
| API31 direct shared | YES | YES | YES | YES | tested `fileKey` stable | REGULAR, seekable | emulator |
| API36 direct shared | YES | YES | YES | YES | tested `fileKey` stable | REGULAR, seekable | emulator |
| Pixel 7a direct shared | YES | YES | YES | YES | tested `fileKey` stable | REGULAR, seekable | physical Android 16 |
| Synthetic provider pipe | YES | NO | NO in fixture | NOT_TESTED | provider-defined | FIFO, `lseek` -> `ESPIPE` | API31/API36 + Pixel 7a |
| Pixel 7a local SAF Documents tree | YES | YES on tested provider | YES | YES | URI/documentId changed on rename and move | REGULAR and seekable on tested provider | provider-dependent result |
| SD / USB removable | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | no removable hardware |
| Cloud DocumentsProvider | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | no provider attached |

## Pixel 7a direct-storage measured facts

- 64 MiB sequential read: 17.548217 ms, about 3646.16 MiB/s; benchmark value is cache/device specific and not a production target.
- Random seek/read at 33,554,432 bytes: PASS.
- `ParcelFileDescriptor`: regular file, `lseek` PASS.
- Same-parent rename and cross-directory move: PASS; observed `fileKey` stayed `(dev=a1,ino=575375)`.
- 4,831,838,208-byte sparse logical length: PASS.
- Cooperative cancellation stopped after 851,968 / 67,108,864 bytes.
- Synthetic FIFO descriptor: sequential read PASS; seek failed with `ESPIPE`.

## Pixel 7a SAF measured facts

Real `ACTION_OPEN_DOCUMENT_TREE` selection was used for:
`content://com.android.externalstorage.documents/tree/primary%3ADocuments`.

Inside the dedicated `FileManagerPoc001Saf` test directory:
- mkdir/list/stat-like metadata: YES;
- sequential read: YES;
- `ParcelFileDescriptor`: YES;
- `AssetFileDescriptor`: YES;
- `lseek`: YES on this local provider;
- random `FileChannel.position` read: YES;
- append/truncate/mtime update: YES;
- same-parent rename: YES; URI and `documentId` both changed;
- cross-directory move: YES; URI and `documentId` both changed;
- >4 GiB sparse logical size: YES;
- cancellation: YES;
- delete: YES.

This confirms that the tested local provider is seekable, but does not generalize seekability to all DocumentsProviders.

## Filename behavior

Direct shared storage on API31, API36 emulator, and Pixel 7a rejected the requested double-quote and newline names with `EPERM`, while Unicode, emoji, spaces and tested shell metacharacters succeeded.

The Pixel local SAF provider did not reject those requests; it sanitized them instead:
- requested `quotes-'".txt` -> provider returned `quotes-'_.txt`;
- requested newline name -> provider returned `line_break.txt`.

Therefore successful create does not imply exact-name preservation.

## Persisted grant

The persisted local SAF grant survived process restart and the corrected `saf_probe` succeeded. Explicit grant revocation remains `NOT_TESTED`.

## Disconnect / revocation

- removable SD/USB disconnect: NOT_TESTED;
- cloud-provider disconnect: NOT_TESTED;
- explicit persisted-SAF grant revocation: NOT_TESTED;
- direct MANAGE_EXTERNAL_STORAGE revoke-during-I/O: NOT_TESTED.

No NOT_TESTED item is promoted to PASS.
