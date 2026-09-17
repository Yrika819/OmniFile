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
| Physical Pixel 7a API36 | Persisted local `ACTION_OPEN_DOCUMENT_TREE` grant and tree re-query | PASS — capability only | Fresh P0-001 probe; `primary:Documents` |
| Physical Pixel 7a API36 | Descriptor/stream/append/truncate/cancellation and sparse length | PASS — provider-specific capability only | Fresh P0-001 probe |
| Physical Pixel 7a API36 | Local→SAF, SAF→Local, SAF→SAF durable transfers | NOT_TESTED | No P05 transfer/recovery APK |
| Android API31 | ACTION_OPEN_DOCUMENT_TREE, persisted grant, and provider mutation | NOT_TESTED | No final API31 SAF run |
| Provider fault | Rename/documentId mutation and filename sanitization | PASS — one provider only | Fresh probe observed replacement URI/document ID and sanitized names |
| Lifecycle | Process death before verify/finalize | NOT_TESTED | Host model only |
| Security | Real destination containment and provider enforcement | NOT_TESTED | Application security boundary not implemented |

The eight host `PASS` rows are policy-model results only. The two Pixel
capability rows are real but provider-specific and do not establish physical
device transfer recovery, source-deletion safety, or API31 compatibility.
