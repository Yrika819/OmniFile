# POC-001 — Environment

Status: COMPLETE

## Authority

- Repository: `/Users/yuta/Desktop/File Manager`
- Branch: `poc/storage-capabilities-v1`
- Architecture starting SHA: `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`
- Research authority: `b03a2ea99f24206f847f513fa4106e90268f3fc4`

## Host

- Intel/x86_64 Mac host.
- Android SDK: `$HOME/Library/Android/sdk`.
- Android Build Tools used by the disposable harness: 36.0.0.
- Java runtime observed during the campaign: Java 26.0.1.
- Android Emulator: 37.1.11.

## Android 12 compatibility environment

AVD `poc_filemanager_api31`:
- Android 12 / API 31.
- `system-images;android-31;google_apis;x86_64` revision 14.
- runtime fingerprint captured in `results/api31-android12-direct-pipe.jsonl` and `results/api31-getprop.txt`.

The API 31 emulator was sufficient for direct-storage and descriptor evidence, but sustained approximately 100% host CPU and about 2.6 GB RSS during the campaign. UI-heavy SAF work was therefore not extended on this AVD.

## Android 16 emulator environment

AVD `poc_filemanager_api36`:
- Android 16 / API 36.
- `system-images;android-36;google_apis;x86_64` revision 7.
- runtime fingerprint captured in `results/api36-android16-direct-pipe.jsonl` and `results/api36-getprop.txt`.

The emulator provided direct-storage and descriptor evidence. Its DocumentsUI/System UI path produced repeated ANRs during early SAF-picker attempts, so those failed UI attempts were not promoted to storage evidence.

## Android 16 primary physical environment

Pixel 7a:
- device codename: `lynx`;
- Android 16 / API 36;
- build fingerprint: `google/lynx/lynx:16/BP4A.260205.001/14624666:user/release-keys`;
- ABI: `arm64-v8a`;
- observed page size during the related P0 campaign: 4096 bytes;
- about 24 GiB free on `/data` / emulated storage when the P0 physical-device work began.

POC-001 physical evidence is stored in:
- `results/pixel7a-api36-direct-pipe.jsonl`;
- `results/pixel7a-api36-saf.jsonl`;
- `results/pixel7a-api36-saf-probe.jsonl`;
- `results/pixel7a-api36-getprop.txt`;
- `results/pixel7a-api36-package.txt`.

## SAF provider actually measured

The physical test used Android's local ExternalStorage DocumentsProvider through a real `ACTION_OPEN_DOCUMENT_TREE` picker selection of the `Documents` tree.

Persisted tree URI:
`content://com.android.externalstorage.documents/tree/primary%3ADocuments`

No shell-injected URI grant was used for the accepted SAF evidence.

## NOT TESTED environments

- removable SD card behavior;
- USB OTG disconnect/reconnect;
- cloud-backed DocumentsProvider behavior;
- OEM DocumentsProvider variants other than the tested Pixel/Android local provider;
- explicit persisted-SAF-grant revocation.

## Harness authority boundary

Temporary package: `dev.poc.filemanager.storagecap`.

This package, its manifest, build scripts and source are **POC-ONLY — NOT PRODUCTION AUTHORITY**. They do not freeze the future applicationId, namespace, module architecture, SDK levels or release structure.