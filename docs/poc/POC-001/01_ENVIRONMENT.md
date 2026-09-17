# POC-001 — Environment

Status: IN PROGRESS

## Host

- Host: MacBook Pro (Intel/x86_64 host confirmed by `uname -m`).
- Repository: `/Users/yuta/Desktop/File Manager`
- Architecture starting SHA: `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`
- Android SDK: `$HOME/Library/Android/sdk`
- Android Platform Tools: installed, ADB available.
- Android Build Tools used by PoC harness: 36.0.0.
- Java runtime observed: Java 26.0.1.
- Android Emulator installed for PoC: 37.1.11.

## Android virtual devices

Created specifically for this campaign:
- `poc_filemanager_api31` — `system-images;android-31;google_apis;x86_64` revision 14.
- `poc_filemanager_api36` — `system-images;android-36;google_apis;x86_64` revision 7.

The exact runtime build fingerprints and Android release/API values are captured by the harness and will be copied into `03_RESULTS.md` after execution.

## Physical / external providers

At campaign start `adb devices -l` reported no attached physical device.
Therefore physical Pixel/OEM behavior, SD removal, USB OTG, and physical cloud-provider behavior are not to be inferred from emulator evidence.

## Harness

Path: `poc/POC-001-storage-capabilities/`

Temporary package: `dev.poc.filemanager.storagecap`

This identifier is explicitly **POC-ONLY — NOT PRODUCTION AUTHORITY** and does not freeze the future applicationId or namespace.
