# P05-002 Results

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Host test execution

Command, run from `docs/poc/P05-002/harness`:

```text
python3 -m unittest -v
```

Result on 2026-09-17:

```text
Ran 10 tests

OK
```

The ten tests cover valid partial-prefix resume, provider disconnect, grant revocation, source-version conflict, mismatched partial output, valid move finalization, valid copy finalization, wrong final output, missing outputs, and opaque in-memory provider locators.

## Evidence disposition

`HOST_POLICY_PASS` — the tested in-memory policy classified all selected fault cases deterministically and never set `delete_source` true. This is a host-side result only. It is not Android SAF, API-level, provider, emulator, physical-device, process-death, or ADB evidence.

## TDD evidence

The initial focused test run failed during import because `saf_recovery.py` did not exist. The implementation was then added and the final focused run passed 10/10. The later test contract was preserved when the worktree contained the expanded host test set.
