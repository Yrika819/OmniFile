# POC-003 — Environment

Status: COMPLETE

## Host reference

- macOS 15.7.7 build 24G720, Darwin 24.6.0, x86_64.
- Java 26.0.1; harness compiled with `javac --release 17`.
- 7-Zip 26.03 was used only to generate interoperability fixtures.
- Zstandard CLI 1.5.7 was used to generate the TAR.ZST fixture.

## Android primary

Physical device:

- Pixel 7a (`lynx`).
- Android 16 / API 36.
- Build fingerprint: `google/lynx/lynx:16/BP4A.260205.001/14624666:user/release-keys`.
- ABI: `arm64-v8a`.
- observed page size: 4096 bytes.
- `/data` and emulated storage had about 24 GiB free during the run.

The Android harness was executed as a disposable DEX through `app_process`; no production Activity, manifest, applicationId or package architecture was introduced.

## Candidate versions measured

- Apache Commons Compress 1.28.0.
- Zip4j 2.11.6.
- Junrar 8.1.1.
- XZ for Java 1.10.
- zstd-jni 1.5.7-16; Android AAR arm64-v8a native library used for TAR.ZST.

Exact raw host/device environment captures are stored under `poc/POC-003-archive-engines/results/`.

All versions are **POC-ONLY — NOT PRODUCTION AUTHORITY**.