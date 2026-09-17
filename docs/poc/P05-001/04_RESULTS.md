# P05-001 — Results

**Label:** `POC-ONLY — NOT PRODUCTION AUTHORITY`

## Environment audit

The audit was read-only and did not invoke ADB, an emulator, or a physical
device:

- Java/Javac: present (`java 26.0.1` / `javac 26.0.1`), not required by this host harness.
- Android SDK: present at `/Users/yuta/Library/Android/sdk` with API 31, 36,
  and 37 platform jars and build-tools 35/36/37.
- Gradle: absent.
- Worktree Gradle wrapper: absent.
- Kotlin compiler: absent.
- ADB availability is not required and no ADB command is used by this POC.

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
