# POC-001 — Failures and Edge Cases

Status: COMPLETE

## Direct filename restrictions

On API31/API36 emulator direct storage and Pixel 7a direct shared storage, requested names containing a double quote or newline failed with `EPERM`. Unicode, emoji, spaces and the tested shell metacharacters succeeded.

## SAF name sanitization

The Pixel local SAF provider accepted the same problematic requests but changed the display/document name. This is measured provider behavior and means callers must inspect the returned metadata rather than assume exact name preservation.

## SAF harness bugs found and corrected

The first physical-device implementation passed a tree URI directly to `DocumentsContract.createDocument`; this was invalid. It was corrected to derive a document URI with `getTreeDocumentId` + `buildDocumentUriUsingTree`. The persisted-grant probe needed the same correction. These are harness defects, not platform failures.

## Emulator instability

The API36 AVD repeatedly produced System UI/Launcher ANRs during the picker flow. No shell-grant bypass was used. Physical Pixel 7a testing later supplied the valid SAF evidence instead.

## API31 host load

The API31 emulator consumed roughly 100% host CPU and about 2.6 GiB RSS during the campaign. UI-heavy tests were not extended there. This is an emulator/host limitation, not Android 12 performance evidence.

## Removable/cloud disconnect

No SD card, USB OTG device, or cloud-backed provider was attached. Disconnect, reconnect, lazy descriptor failure and recovery remain NOT_TESTED.

## Persisted grant revocation

Grant persistence across process restart was measured. Explicit `releasePersistableUriPermission` followed by denied-access verification was not completed, so revocation remains NOT_TESTED.

## >4 GiB interpretation

The sparse-length tests prove 64-bit logical size handling on the tested direct/local-SAF surfaces without filling storage with 4+ GiB of physical payload. They do not establish behavior for FAT-like removable filesystems or cloud providers.

## Performance values

All throughput and latency values are single-run PoC observations only and are not production performance budgets.
