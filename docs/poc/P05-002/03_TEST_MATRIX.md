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
| Android API 31/36 | ACTION_OPEN_DOCUMENT_TREE and persisted grant | NOT_TESTED | No Android module/device run |
| Physical Pixel 7a | Local→SAF, SAF→Local, SAF→SAF | NOT_TESTED | ADB/device execution not performed |
| Provider fault | Rename/documentId mutation, provider sanitization | NOT_TESTED | Host model only |
| Lifecycle | Process death before verify/finalize | NOT_TESTED | Host model only |
| Security | Real destination containment and provider enforcement | NOT_TESTED | Application security boundary not implemented |

The eight host `PASS` rows are policy-model results only. They do not establish
Android SAF behavior or physical-device recovery.
