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
| Host contract | Isolated package, real SAF calls, directions, faults, durable identity fields, bounded event observability | PASS | `harness/test_android_contract.py` — 9/9 contract tests |
| Host build | Raw SDK compile, link, dex, sign, and APK verification | PASS | `android/build.sh`; signed APK metadata |
| Parent Android runtime | Persisted `ACTION_OPEN_DOCUMENT_TREE` grant and tree re-query | PASS | Pixel 7a API 36; shared writable tree grant re-queried after restart |
| Parent Android runtime | Local→SAF, SAF→Local, SAF→SAF durable transfers | PASS | Pixel evidence; 5,767,168-byte local and 5,242,880-byte SAF fixtures; VERIFY/PASS and COMPLETE |
| Parent Android runtime | SAF→SAF move-shaped copy/finalize and source-delete boundary | PASS | Pixel evidence; COMPLETE with `deleteSource=false` and `EXPLICIT_PARENT_STEP_ONLY` |
| Parent Android runtime | Restart/reconciliation, finalization, and URI identity | PASS | Controlled force-stop/relaunch; terminal record re-observed as `TERMINAL_RECORD` |
| Parent Android runtime | Interruption, cancellation, conflict, mutation, and provider fault | NOT_CLOSED | Fast transfer completed before interrupt/cancel taps were processed; no device PASS claimed |
| Parent Android runtime | API31/API36/provider-specific behavior and lifecycle faults | PARTIAL | API 36 Pixel 7a only; no API 31 or provider-disconnect/storage-full evidence |
| Lifecycle | Process death before verify/finalize | NOT_TESTED | Only post-terminal controlled force-stop/relaunch was run |
| Security | Real destination containment and provider enforcement | NOT_TESTED | Application security boundary not implemented |

The host `PASS` rows are policy/build-contract results; the 9/9 Android
contract rows are static source/build-contract checks, not dynamic UI or
provider behavior. Device rows are parent-observed and limited to the exact
Pixel 7a API 36 run documented in `04_RESULTS.md`; raw logcat/JSONL and the
installed APK are not retained in this branch, so those rows are not
independently reproducible from a clean checkout. They do not establish
production SAF behavior or closure of the remaining fault and lifecycle gates.
