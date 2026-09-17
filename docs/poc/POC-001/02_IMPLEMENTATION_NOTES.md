# POC-001 — Implementation Notes

Status: COMPLETE

## Disposable harness

The PoC lives under `poc/POC-001-storage-capabilities/` and is intentionally independent from any production Android application structure.

It is built directly with `javac`, D8, `aapt2`, `zipalign` and `apksigner`. The temporary package is `dev.poc.filemanager.storagecap` and is **POC-ONLY — NOT PRODUCTION AUTHORITY**.

## Direct-storage test boundary

All direct destructive operations are restricted to:

`/storage/emulated/0/Documents/FileManagerPoc001Direct`

The harness deletes/recreates only that dedicated fixture root. It does not scan or modify unrelated user files.

## SAF test boundary

The accepted physical-device SAF evidence came from a real user-style `ACTION_OPEN_DOCUMENT_TREE` flow selecting the local `Documents` tree. The harness then creates and operates only inside:

`Documents/FileManagerPoc001Saf`

The tree URI is persisted using `takePersistableUriPermission`.

Two harness URI bugs were discovered and fixed during physical testing:

1. the first SAF implementation passed the tree URI directly to `DocumentsContract.createDocument`; Android correctly rejected it as an invalid document URI;
2. the persisted-grant probe initially queried the tree URI directly.

The corrected implementation converts the tree URI with `getTreeDocumentId` + `buildDocumentUriUsingTree` before document operations or metadata queries.

These were PoC harness bugs, not Android storage failures.

## Descriptor testing

A synthetic `DocumentsProvider` returns a pipe-backed `ParcelFileDescriptor`. Sequential reads are valid, while lower-level `Os.lseek` reports `ESPIPE`. This intentionally separates descriptor presence from seekability.

For the local SAF provider, the harness independently measures:
- `ParcelFileDescriptor`;
- `AssetFileDescriptor`;
- lower-level `lseek`;
- `FileChannel.position` random read.

## Identity testing

Direct storage records Java/NIO `fileKey` before and after same-filesystem rename and cross-directory move.

SAF records the returned URI and `documentId` before and after `DocumentsContract.renameDocument` and `moveDocument`. The harness does not assume stable identity merely because those calls succeed.

## Filename testing

The harness requests Unicode, emoji, spaces, quotes, shell metacharacters, newline, long names and nested directories.

The requested name and provider-returned metadata are both recorded, allowing provider sanitization to be distinguished from exact-name acceptance.

## Large-size and cancellation testing

A 4,831,838,208-byte logical sparse length is used to exercise 64-bit size handling without intentionally filling primary storage with 4+ GiB of real payload data.

Cancellation is cooperative and bounded. The test records bytes consumed before stop rather than treating cancellation as an unmeasured boolean.

## Evidence collection

Emulator evidence is captured directly from the app-private JSONL result file. On the physical Pixel 7a, SELinux blocked `run-as` despite the disposable debug harness, so final physical-device JSONL evidence was captured from the harness's structured `POC001` logcat records. The same JSON payload emitted by `record()` is preserved.

## Deliberately excluded

The PoC does not define production interfaces, storage adapters, dependency injection, module boundaries, SDK versions, worker/executor choices or release packaging.