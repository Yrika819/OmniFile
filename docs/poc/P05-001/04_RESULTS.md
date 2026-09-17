# P05-001 — Results

**Label:** `POC-ONLY — NOT PRODUCTION AUTHORITY`

## Environment audit

The host audit and the disposable runtime campaign were kept separate.

- Java/Javac: present (`java 26.0.1` / `javac 26.0.1`), not required by this host harness.
- Android SDK: present at `/Users/yuta/Library/Android/sdk` with API 31, 36,
  and 37 platform jars and build-tools 35/36/37.
- Gradle: absent.
- Worktree Gradle wrapper: absent.
- Kotlin compiler: absent.
- ADB was used only by the disposable runtime harness; no production package or
  application was installed.

Runtime authorities:

| Target | Identity | Result |
| --- | --- | --- |
| Physical device | Pixel 7a, API 36, build `BP4A.251205.006`, `arm64-v8a`, 4096-byte page size | foreground app, dataSync FGS, JobScheduler, UIDT, restart/re-discovery complete |
| Emulator | API 31 `sdk_gphone64_x86_64`, build `SE1A.220826.008`, `x86_64` | foreground app, dataSync FGS, JobScheduler complete; UIDT `NOT_APPLICABLE`; restart row was `ABSENT` rather than recovered; final-artifact refresh later blocked by AVD exit |

Pixel evidence is in `runtime/results/`; API31 evidence is isolated in
`runtime/results/api31/`. Each row includes device identity, PID, lifecycle,
checkpoint events, and durable state. The runtime APK is explicitly POC-only.
`runtime/results/api31/final-artifact-refresh.txt` records the later AVD
blocker; it prevents claiming that the API31 rows are from the final APK hash.

The later executor-cancellation hardening was rebuilt and passed the host
contract suite and APK build, but the Pixel refresh was interrupted when its
wireless ADB session dropped. The checked-in device rows therefore remain the
earlier verified disposable capture; they are not presented as a fresh device
run of the latest hardening commit.

Observed Pixel lifecycle evidence includes `ACTIVITY_STOP` after Home,
`mWakefulness=Dozing` during screen-off and `Awake` after wake, durable
completion while backgrounded, and restart `REDISCOVER`. A Recents swipe was
attempted; Android 16 removed the task and killed the process, but the
foreground Activity has no `onTaskRemoved` callback, so that callback is not
claimed. The FGS callback remains implemented but was not used as a task-swipe
authority in this run. The screen-state captures are
`lifecycle-screen-off.txt` and `lifecycle-screen-on.txt`.

The rerunnable Pixel command requires explicit target selection:

```text
ADB_SERIAL=<verified Pixel serial> ./runtime/run-pixel7a.sh
ADB_SERIAL=<verified Pixel serial> ./runtime/run-lifecycle-pixel7a.sh
```

The runner stores a hashed serial and APK SHA-256 in `campaign-metadata.json`
and only the required build/API/model/ABI fields in `device-identity.txt`.

## Latest-hardening fresh sanity (2026-09-17)

The branch head before this continuation was
`81f886ed6c0df40a066ad49c5186e6659626c5b3`; the behavior hardening under test
is `c9fcf2d`. The parent rebuilt that exact head with `runtime/build.sh`,
producing the PoC APK SHA-256
`1daaedd0280b4841e26ae35959170acd3ccd9b234bd131f5d48a0c9633ace7c0`.
The source-to-artifact binding is retained in
`docs/poc/P05-001/08_ARTIFACT_PROVENANCE.md`.

The APK was freshly installed on the connected Pixel 7a and the focused
`runtime/run-pixel7a.sh` sanity completed successfully. Fresh device identity
was Pixel 7a / API 36 / `BP4A.251205.006` / `arm64-v8a`; the device reported a
4096-byte page size. Foreground app, `dataSync` FGS, JobScheduler, and UIDT
all emitted `START`, repeated `CHECKPOINT`, and `COMPLETE`. The controlled
restart emitted `REDISCOVER` from `RUNNING` durable state and then `COMPLETE`.
The install result was `Success`; the final persisted state was
`phase=COMPLETE`, `completedUnits=20`, `executor=FOREGROUND_APP`.

This fresh run validates the latest-hardened runtime source startup, routing initialization, durable
state discovery, and the Android 16 framework paths represented by this raw
harness. It does not replace the preserved lifecycle, screen-state, and API31
evidence described above, and it does not close WorkManager, media-processing
FGS, or API31 final-artifact equivalence.

WorkManager is `NOT_APPLICABLE` in this raw framework-only harness because no
AndroidX WorkManager dependency is present. Media-processing FGS is `NOT_TESTED`.

## TDD record

The non-generated Python classifier was developed in these cycles:

1. Wrote the partial-destination test first; the test was red because the
   harness module did not exist.
2. Added the minimum classifier; the test became green.
3. Wrote the ambiguous-finalization test; it was red because the classifier
   returned `RESTART_REQUIRED`.
4. Added the explicit `NEEDS_ATTENTION` branch; all tests became green.
5. Wrote the verified-final MOVE test; it was red because source deletion was
   incorrectly denied.
6. Restricted source deletion to verified-final MOVE fixtures with a present
   source; all tests became green.
7. Added the report-authority test; it was red because report helpers did not
   exist, then implemented the smallest report path and reran the full suite.
8. Added the executor-shape coverage assertion; it was red before the six-row
   fixture matrix was complete, then the full suite became green.

## Reproduction commands

From the worktree root:

```bash
PYTHONPATH=harness python3 -m unittest discover -s harness -p 'test_*.py' -v
PYTHONPATH=harness python3 harness/p05_001_executor_harness.py --json
```

The first command is the required host test. The second command emits the
fixture report and retains `PLANNING_FIXTURE_ONLY`,
`platform_runtime_observed: false`, and `selected_executor: null`.

## Safety protocol

The harness only constructs in-memory metadata and prints JSON. It has no file
mutation, network, Android, device, ADB, credential, or user-data path.
