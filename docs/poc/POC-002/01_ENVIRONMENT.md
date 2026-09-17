# POC-002 — Environment

Status: COMPLETE

## Authority

- repository: `/Users/yuta/Desktop/File Manager`;
- branch: `poc/durable-operations-v1`;
- starting SHA: `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`;
- host harness: `poc/POC-002-durable-operations/engine.py` + `campaign.py`;
- Android harness: `poc/POC-002-durable-operations/android/`.

## Host reference

Exact Python/platform details are retained in `results/campaign.json`. Host evidence includes fault recovery, checkpoint cadence at 1/8/64 MiB and a 2 GiB transfer.

## Physical Android primary

Pixel 7a (`lynx`):
- Android 16 / API 36;
- fingerprint `google/lynx/lynx:16/BP4A.260205.001/14624666:user/release-keys`;
- ABI `arm64-v8a`;
- page size 4096 bytes;
- about 24 GiB free on `/data` and emulated storage at measurement time.

Raw device environment is retained under `results/android-pixel7a/`.

## Android execution model

The disposable Java harness was compiled with `javac --release 17`, converted with D8 `--min-api 31`, pushed to `/data/local/tmp/filemanager-poc002`, and invoked via `app_process`. `app_process` is only an executor for the PoC; durable truth lives in state files plus partial/final files. No Activity, Service, WorkManager, UIDT, FGS, production manifest, applicationId or module architecture was selected.

## Test sizes

Host: 4 MiB baseline, 64 MiB fault cases, 256 MiB checkpoint benchmark, 2 GiB large case.

Pixel 7a: 8 MiB baseline, 64/128/256 MiB fault cases, and a 2,147,483,648-byte real streamed destination. No tens/100 GiB test was performed.
