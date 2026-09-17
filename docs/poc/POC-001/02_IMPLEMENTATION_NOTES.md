# POC-001 — Implementation Notes

Status: PARTIAL EVIDENCE COMPLETE

All code under `poc/POC-001-storage-capabilities/` is **POC-ONLY — NOT PRODUCTION AUTHORITY**.

## Harness shape

The harness is a minimal Java Android application built directly with Android SDK command-line tools rather than a production Gradle application. This avoids freezing production module structure, applicationId, namespace, dependency versions, or release configuration.

Temporary package/authority:
- package: `dev.poc.filemanager.storagecap`
- DocumentsProvider authority: `dev.poc.filemanager.storagecap.documents`

The harness contains:
- direct shared-storage probes under `/storage/emulated/0/Documents/FileManagerPoc001Direct`;
- a synthetic `DocumentsProvider` that returns a pipe-backed `ParcelFileDescriptor`;
- optional user-selected SAF tree probes, which require a functioning system DocumentsUI picker;
- JSONL result persistence under app-private storage;
- host scripts to build, install, run, and capture results.

## Direct-storage probes

Measured operations include fixture creation, nested mkdir, list/stat, sequential read, `RandomAccessFile.seek`, regular-file `ParcelFileDescriptor` + `Os.lseek`, append, truncate, same-parent rename, cross-directory move, mtime change, cooperative cancellation, >4 GiB sparse sizing, and delete.

`Files.readAttributes(...).fileKey()` was recorded around rename/move to observe identity continuity on the emulator filesystem. This is evidence for the tested filesystem only; it is not a universal Android identity contract.

## Descriptor probe

`SyntheticDocumentsProvider` exposes document `pipe` using `ParcelFileDescriptor.createPipe()`. The client calls `fstat` to classify descriptor type and then calls `Os.lseek` rather than inferring seekability from descriptor availability.

This directly tests the Architecture V1 requirement that native descriptor presence and seekability remain separate capabilities.

## SAF implementation path

The harness includes normal `ACTION_OPEN_DOCUMENT_TREE` selection with persistable read/write grants and subsequent `DocumentsContract` probes for:
- list/query metadata and flags;
- sequential read;
- `ParcelFileDescriptor` and `AssetFileDescriptor`;
- `lseek`/random read;
- append/truncate;
- rename and move identity/URI behavior;
- >4 GiB sparse candidate where supported;
- cancellation;
- delete;
- persisted grant probe.

The API 36 headless emulator repeatedly produced System UI / launcher ANR dialogs when entering the picker. No permission injection or hidden bypass was used because that would not be equivalent to a real user-selected persisted SAF grant. Those SAF operations are therefore `NOT_TESTED` in this run.

## Build details

Build Tools: 36.0.0.
Minimum API encoded in DEX/APK: 31.
Target API encoded for the disposable harness: 36.
Signing: local Android debug keystore only.

None of the above is production configuration authority.

## Artifact classification

- Harness source/scripts: `DISPOSABLE`.
- Raw JSONL/getprop/package capture: `REFERENCE_ONLY`.
- Measured conclusions and architecture-impact documents: `REFERENCE_ONLY`.
- No artifact is classified production-ready.