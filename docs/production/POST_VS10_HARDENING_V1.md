# Post-VS10 hardening V1 closure

**Status: `POST_VS10_HARDENING_CLOSED_GREEN_WITH_LIMITATIONS`**

This is a separate post-VS10 hardening slice. It does not revise the historical
VS10 result: VS10 remains `CLOSED_GREEN_WITH_LIMITATIONS`. The VS10 closure record
(`VERTICAL_SLICE_10_PDF_PREVIEW_V1.md`) is unchanged. No VS11 work is included or
started by this closure.

## Pinned integration baseline and implementation

- Repository: `Yrika819/OmniFile`
- Main baseline: `048bcfd84bd7e8120822e938f4f6f5dd5edb1672`
- Hardening implementation: `ff98a5fb1e6f61306138ea48923ff77acf2d106d`
- Hardening branch: `hardening/post-vs10-findings-v1`

The implementation consists of the bounded classification precedence fix,
instrumentation evidence-integrity hardening, acquisition deadline exception
precedence, stress-terminology clarification, and the directly related tests and
CI support. It contains no dependency or SDK change and no VS11 feature.

## Finding disposition

| Finding | Disposition |
| --- | --- |
| MIN-1: supported image signatures versus embedded PDF evidence | **RESOLVED** by post-VS10 hardening. Byte-zero PNG/JPEG/BMP signatures win. |
| NIT-1: PDF scan-contract precision | **RESOLVED** with MIN-1. Complete `%PDF-` signature starts at offsets 0 through 1024 inclusive; all five bytes must be present. |
| MIN-2: instrumentation aggregate evidence integrity | **RESOLVED** by post-VS10 hardening. Counts derive from unique concrete testcase records; ambiguous, duplicate, or unexplained positive evidence fails closed. |
| MIN-3: exception/deadline precedence | **RESOLVED** by post-VS10 hardening. An exception observed while ACTIVE before the absolute deadline remains the original exception; at or after the deadline it resolves as `AcquisitionTimeout`. Existing terminal states remain unchanged. |
| NIT-2: stress terminology | **RESOLVED** by terminology clarification only. Repeated iterations/templates are distinguished from distinct schedule diversity; stress counts were not increased. |
| Independent re-audit Minor | **OPEN**. Mandatory-failure logs may lose sanitized failure-body diagnostics. Failure rejection remains correct; this is diagnostic-quality loss, not a gate-integrity bypass. It is intentionally not fixed in this closure. |

These dispositions are post-VS10 findings and must not be read as claims that the
findings were resolved at the time of the original VS10 closure.

## Automated authority

The accepted implementation SHA is
`ff98a5fb1e6f61306138ea48923ff77acf2d106d`.

Host gate on that implementation:

- Unit tests: **293 tests, 0 failures, 0 errors, 0 skips**
- Lint: **PASS**, 28 warnings, 0 errors
- `assembleDebug`: **PASS**
- `assembleDebugAndroidTest`: **PASS**
- Strict dependency verification: **PASS**

Two complete API31–36 matrices were accepted on the same unchanged implementation
SHA:

| API | Total | Passed | Failed | Authorized skips | Mandatory |
| --- | ---: | ---: | ---: | ---: | ---: |
| 31 | 109 | 108 | 0 | 1 | 22/22 passed |
| 32 | 109 | 108 | 0 | 1 | 22/22 passed |
| 33 | 109 | 108 | 0 | 1 | 22/22 passed |
| 34 | 109 | 108 | 0 | 1 | 22/22 passed |
| 35 | 106 | 105 | 0 | 1 | 22/22 passed |
| 36 | 109 | 108 | 0 | 1 | 22/22 passed |

Accepted full matrix run IDs: **36856271109** and **36857316506**. Both ran at
the same SHA with no intervening code, skip, exclusion, or timeout changes.

An earlier complete attempt, **36854536964**, had an unexplained API36 initial
Home-wait failure. It is retained as historical test/harness flake evidence; it
is not an accepted authoritative matrix. Two subsequent complete matrices passed
on unchanged code. The earlier failure is not erased or represented as though it
did not occur.

## Independent re-audit

An independent read-only GPT-6.1 Sol / High re-audit was completed against the
final implementation SHA. Its result was **0 Blocker, 0 Major, 1 Minor**. The
result was supplied externally and was not stored in the pinned repository tree
before this note. This is an evidence-location limitation, not evidence that the
audit did not occur. The remaining diagnostic Minor is listed above and remains
open.

## Targeted physical authority

Pinned physical SHA: `ff98a5fb1e6f61306138ea48923ff77acf2d106d`.

Result: `POST_VS10_HARDENING_PIXEL_GREEN_WITH_LIMITATIONS` on **Pixel 7a / Android
17 / API37**. Android 17 beta status was not established.

Physically passed: embedded-`%PDF-` PNG, JPEG, and BMP each opened as image
Preview; normal PNG/JPEG/BMP and normal PDF; Files and Search navigation to the
embedded-PDF PNG; PDF page navigation; recreation/page retention; and Back
behavior. No incorrect PDF worker launch was observed for embedded-image cases;
the normal PDF launched the PDF renderer worker as expected. No OmniFile crash,
ANR, `SecurityException`, or `PdfRenderer` crash was observed.

### Physical limitations

1. Leading-junk PDF was **not** exercised physically in this targeted campaign.
   The 0..1024 classifier contract is covered by host tests.
2. Device designation is **Android 17 / API37** only; beta status is not claimed.
3. This targeted campaign did **not** re-prove VS10 orphan reconciliation. A
   pre-existing stale `.candidate` from an earlier run was manually removed
   before the focused renderer test could pass its clean-workspace assertion.
   That cleanup was not a product fix. Separate orphan-reconciliation authority
   is in the full VS10 campaign.
4. The independent re-audit result was supplied externally and was not already
   committed into the pinned source tree; this document makes the summary durable.

No ADB serial, wireless debugging endpoint, raw SAF URI, private local path,
personal filename, or unique device fingerprint is recorded here.

## Closure boundary

This document records the post-VS10 hardening acceptance evidence and its
limitations. It does not alter VS10 history, close the remaining re-audit Minor,
claim physical leading-junk coverage or targeted orphan-reconciliation coverage,
or authorize VS11 work.
