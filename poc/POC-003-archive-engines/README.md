# POC-003 Archive Engine Harness

POC-ONLY — NOT PRODUCTION AUTHORITY.

Disposable harness for the P0 archive-engine campaign.

Typical reproduction order:
1. `./fetch-dependencies.sh`
2. `./prepare-fixture-tools.sh`
3. `./generate_fixtures.py`
4. `./build-and-run-host.sh`
5. Set `ANDROID_SERIAL` and run `./build-and-run-android-api36.sh` on an Android 16 test device.

Downloaded libraries/tools, generated archives, compiled output and temporary work directories are intentionally ignored. Raw measurement results and environment captures are retained.