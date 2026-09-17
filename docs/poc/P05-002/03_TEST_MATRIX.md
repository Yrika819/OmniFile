# P05-002 Test Matrix

`POC-ONLY — NOT PRODUCTION AUTHORITY`

| Layer | Scenario | Status | Evidence |
|---|---|---|---|
| Host unit | Checkpoint lag vs actual valid partial | PASS | `harness/test_saf_recovery.py` |
| Host unit | Provider disconnect and source-grant loss | PASS | Same |
| Host unit | Wrong final destination, including valid partial present | PASS | Same |
| Host unit | Finalization acknowledgement ambiguity | PASS | Same |
| Host unit | Malformed terminal durable record | PASS | Same |
| Host unit | Independent destination-grant loss | PASS | Same |
| Host unit | Verified final copy without source reopen | PASS | Same |
| Host unit | MOVE source revalidation before delete authorization | PASS | Same |
| Host contract | Isolated package, real SAF calls, directions, faults, durable identity fields | PASS | `harness/test_android_contract.py` |
| Host build | Raw SDK compile, link, dex, sign, and APK verification | PASS | `android/build.sh`; signed APK metadata |
| Parent Android runtime | Persisted `ACTION_OPEN_DOCUMENT_TREE` grant and tree re-query | NOT_TESTED | Parent-only run; no runtime run in this branch |
| Parent Android runtime | Local→SAF, SAF→Local, SAF→SAF durable transfers | NOT_TESTED | Parent-only run |
| Parent Android runtime | Interruption/restart/reconciliation, cancellation, conflict, mutation, finalize, identity | NOT_TESTED | Parent-only run |
| Parent Android runtime | API31/API36/provider-specific behavior and lifecycle faults | NOT_TESTED | Parent-only run |
| Lifecycle | Process death before verify/finalize | NOT_TESTED | Requires parent-controlled lifecycle action |
| Security | Real destination containment and provider enforcement | NOT_TESTED | Application security boundary not implemented |

The host `PASS` rows are policy/build-contract results only. Parent Android
runtime rows remain untested here; no device result is claimed.
