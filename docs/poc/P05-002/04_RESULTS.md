# P05-002 Results

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## Host test execution

Command:

```text
PYTHONPATH=docs/poc/P05-002/harness python3 -m unittest discover -s docs/poc/P05-002/harness -p 'test_*.py' -v
```

Fresh post-remediation result on 2026-09-17:

```text
Ran 16 tests
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

## Fresh real SAF capability probe

On 2026-09-17, the existing disposable P0-001 raw APK was run on the connected
Pixel 7a (API 36, build `BP4A.251205.006`) using the already persisted local
DocumentsProvider grant `primary:Documents`. The probe recorded:

- persisted-grant re-query: `PASS`;
- tree identity and metadata: `PASS`;
- create/list/read and 64 MiB direct SAF fixture: `PASS`;
- regular descriptor, sequential read, random read, append, truncate: `PASS`;
- rename and cross-directory move: `PASS`, with a replacement URI and changed
  document ID observed rather than assumed stable;
- >4 GiB sparse document length: `PASS` (`4,831,838,208` bytes reported);
- cooperative `CancellationSignal`: `PASS` before full transfer;
- delete after the probe: `PASS`.

This is real provider capability evidence, not P05 transfer/recovery closure.
The probe did not implement or measure Local→SAF, SAF→Local, SAF→SAF durable
operation phases, process-death reconciliation, source mutation, destination
conflict, grant revocation, or provider disconnect. Those rows remain open.

## TDD evidence

The remediation tests were first run against the pre-remediation model and
failed with missing-field and incorrect-policy failures. After the minimal model
changes, the same command passed 16/16.
