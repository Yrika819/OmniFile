# P05-002 Results

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Host test execution

Command:

```text
PYTHONPATH=docs/poc/P05-002/harness python3 -m unittest discover -s docs/poc/P05-002/harness -p 'test_*.py' -v
```

Fresh result on 2026-09-17:

```text
Ran 22 tests
OK
```

The host model covers partial/checkpoint reconciliation, provider and grant
loss, source mutation, wrong-final precedence, finalization ambiguity, malformed
terminal records, independent destination grants, final-copy completion without
source reopen, MOVE source revalidation before delete authorization, move source
preservation, and opaque locators.

## Evidence disposition

`HOST_POLICY_PASS` — the selected in-memory policy classified the tested fault
cases deterministically and never set `delete_source` true.

## Android contract and APK build

The new host contract command was included in the full command above and
passed 6/6. It checks the isolated package, required SAF calls, all evidence
labels/fields, raw SDK tools, parent instructions, and absence of production
package/device automation.

Command:

```text
docs/poc/P05-002/android/build.sh
```

Fresh build result: exit 0. `aapt2 dump badging` identified package
`dev.poc.safoperations`, version `0.1-poc`, min SDK 31, target SDK 36, and
launcher activity `dev.poc.safoperations.MainActivity`. `apksigner verify`
reported one v3 signer. The APK is
`docs/poc/P05-002/android/build/p05-saf-operations.apk`; `android/build/` is
ignored and is not a commit artifact.

## Runtime boundary

No Android runtime, emulator, physical device, provider account, or user file
was used for this continuation. The new APK is an evidence recorder for the
parent procedure in `android/README.md`; all runtime rows remain `NOT_TESTED`.

## TDD evidence

The Android contract test was first run before Android artifacts existed and
failed with missing-file/expected-content failures. After the minimal APK
artifacts were added, the same full command passed 22/22.
