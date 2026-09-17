# P05-002 Results

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Host test execution

Command:

```text
PYTHONPATH=docs/poc/P05-002/harness python3 -m unittest discover -s docs/poc/P05-002/harness -p 'test_*.py' -v
```

Fresh result on 2026-09-18:

```text
Ran 25 tests
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
passed 9/9. It checks the isolated package, required SAF calls, all evidence
labels/fields, raw SDK tools, bounded event observability, parent instructions,
and absence of production package/device automation.

Command:

```text
docs/poc/P05-002/android/build.sh
```

Fresh build result: exit 0. `aapt2 dump badging` identified package
`dev.poc.safoperations`, version `0.1-poc`, min SDK 31, target SDK 36, and
launcher activity `dev.poc.safoperations.MainActivity`. `apksigner verify`
reported one v3 signer. The build output is an ignored disposable APK and the
script regenerates its disposable signing key when the build directory is
removed, so its SHA-256 is run-specific and is not claimed as a stable branch
artifact. The exact parent-recorded runtime APK identity is the hash below;
`android/build/` is ignored and is not a commit artifact.

## Pixel 7a runtime evidence

Parent-controlled runtime was executed on the connected Pixel 7a using the
parent-recorded APK SHA-256
`6a06aaaaeef42a076588f3ddf0c416f61a572b79554d1c0771edd6acb9df7a01`.
Device identity was model Pixel 7a, API 36, build `BP4A.251205.006`, ABI
`arm64-v8a`, 4 KiB page size. The installed disposable package was
`dev.poc.safoperations`; no production package was installed.

The run selected a writable DocumentsUI tree and recorded a persisted-grant
re-query. It created a 5,767,168-byte local fixture and a 5,242,880-byte SAF
fixture. Real provider operations completed with `VERIFY/PASS` and
`COMPLETE` for Local→SAF (`p05-op-1`), SAF→Local (`p05-op-2`), SAF→SAF
(`p05-op-3`), and a SAF→SAF move-shaped copy (`p05-op-4`). The move-shaped
row retained `deleteSource=false` and
`deleteSourceEligibility=EXPLICIT_PARENT_STEP_ONLY`.

The parent then force-stopped and relaunched the disposable package. Startup
re-observed the persisted grant and reconciled the terminal operation as
`TERMINAL_RECORD`. The fast transfer completed before the interrupt and
cancel taps were processed, so interruption/cancellation are intentionally
not claimed as device passes. Raw event details were captured from the
`P05-SAF` logcat tag because the non-debuggable disposable APK denied
`run-as`; the durable JSONL remains app-private.

After that runtime capture, the parent applied the independent review fixes
for bounded tail reads, refresh coalescing, child-document grant matching, and
UTF-8-safe logcat truncation. The resulting build passed 25/25 host tests and
v3 APK verification; the runtime claims above remain bound to the earlier
parent-recorded hash. The post-review APK was not reinstalled on the device.

## TDD evidence

The Android contract test is static source/build-contract evidence, not a
behavioral execution of UI refresh, tail reads, logcat truncation, or persisted
grant matching. It was first run before Android artifacts existed and failed
with missing-file/expected-content failures. A second RED run covered
the bounded UI/logcat observability contract before its minimal fix. The same
full command passed 25/25 after the fixes.
