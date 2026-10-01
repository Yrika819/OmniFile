# OmniFile Core V1 — VS10 PDF Preview V1

## Closure status and authority

- VS10 status: **`CLOSED_GREEN_WITH_LIMITATIONS`**.
- Acceptance classification: **`GREEN_WITH_LIMITATIONS`**. Physical limitations and open Minor/Nit findings below remain part of this result; they are not acceptance failures.
- Authoritative `main` at VS10 start: `a51f4de9a66b69206cb333eb467c7d66aa4d5ea6`.
- Final VS10 implementation SHA: `59d1799549d5dc4218bb49719a5209bb9d8a6d49` on `development/vs10-pdf-preview-v1`.
- Scope: bounded PDF Preview for supported local files and SAF sources, using Android's platform `PdfRenderer` in an isolated renderer process.
- Out of scope: ZIP-contained PDF, sharing, conversion, cloud/network sources, root access, persistent PDF cache, external PDF ingress, and third-party PDF parsers.
- The local checkout's historical `4016dea...` value is not an authority for `main`; GitHub `origin/main` is authoritative.

## Automated authority

The final implementation SHA was validated independently from this documentation-only closure commit:

- Cloud Host CI run **36790292472**: PASS.
- API 31–36 Emulator CI run **36790673558**: all six APIs PASS.
- Unchanged-SHA API 31–36 repeat **36791451310**: all six APIs PASS.
- Host suite: **266 tests, 0 failures, 0 errors, 0 skips**. Lint, `assembleDebug`, `assembleDebugAndroidTest`, and strict dependency verification passed.
- Final unchanged-SHA matrix counts:

| API | Total | Passed | Failed | Authorized skip |
|---:|---:|---:|---:|---:|
| 31 | 106 | 105 | 0 | 1 |
| 32 | 106 | 105 | 0 | 1 |
| 33 | 106 | 105 | 0 | 1 |
| 34 | 106 | 105 | 0 | 1 |
| 35 | 103 | 102 | 0 | 1 |
| 36 | 106 | 105 | 0 | 1 |

All **19 named mandatory VS10 cases passed on each API**. The one authorized skip per API is the established SAF-device case. Existing audio exclusions are separate and unchanged. No PDF test exclusion was added. Earlier intermediate CI runs exposed defects that were repaired; they are not the final result.

The final delta contains the PDF Preview implementation; M1 bounded acquisition/deadline remediation; M2 explicit SharedMemory and IPC response ownership; M3 ViewModel/controller ownership remediation; corresponding host and instrumentation tests; and CI checks that require named mandatory cases and reject missing, malformed, duplicate, skipped, or absent evidence. No unrelated product feature was added.

## Physical authority

Physical acceptance was pinned to implementation SHA `59d1799549d5dc4218bb49719a5209bb9d8a6d49` on a Pixel 7a running Android 17 / API 37 beta. Physical instrumentation was **17/17 PASS**.

The exercised behavior included real Android DocumentsUI tree selection and a persisted SAF grant across force-stop/relaunch; provider/source disappearance; platform `PdfRenderer`; sustained page navigation; 50 open/render/close cycles; replacement races; Back during render; worker-death/Retry through existing safe instrumentation; force-stop/orphan reconciliation; the exact 16 MiB boundary and rejection above it; acceptance at 100 pages and rejection at 101; malformed/truncated and encrypted-PDF rejection; rotation/recreation; large font; Files/Search Back; text/image and ZIP text/image Preview regressions; and physical MediaSession/playback regression evidence.

No OmniFile crash, ANR, OOM, LMK, `SecurityException`, or `TransactionTooLargeException` was observed. Main-process FD use stayed flat through 60 page-navigation actions and 50 open/render/close cycles. Worker PSS rose during warmup then flattened, with no monotonic growth attributable to PDF Preview. No staging artifact accumulation was observed, and the worker exited after session closure. Worker FD use was not directly observed where SELinux prevented access. These observations do not establish a universal numerical native-PSS ceiling.

## Limitations

These are limits on physical evidence, not failures:

1. **Active native render worker death:** external shell kill was not permitted because the isolated renderer has a separate UID. Worker-death/Retry is covered by instrumentation, including accepted but untransferred responses. Deterministic physical death during native render is not claimed.
2. **Real per-tree SAF revocation:** Android 17 exposed no safe per-tree revoke UI. Real grant revocation is not claimed. Physical source disappearance was exercised and reported truthfully.
3. **Real unknown-size SAF:** no real DocumentsUI/provider path exposed a usable unknown-size source. Controlled SAF automation remains authoritative for that case.
4. **Audible output:** human-ear confirmation was not performed. Physical MediaSession evidence established an active session, position progression/completion, media usage/content type, and a foreground MediaStyle notification. Audible output is not claimed as human-confirmed.
5. **TalkBack:** not exercised. Large-font behavior and accessible control labels were verified. Because PDF Preview is rasterized, no accessibility claim is made for PDF document contents.
6. **Deep-sleep deadline:** the optional physical case was not exercised. Wall-clock timeout semantics across deep sleep are not claimed.

## Open Minor findings

All three findings remain open and were not fixed in this closure:

- **MIN-1 — PDF signature precedence:** PDF signature detection can override stronger image classification. Physically confirmed with a valid PNG containing `%PDF-1.7` in early metadata; it routed to PDF Preview and reported “corrupt or malformed.”
- **MIN-2 — CI aggregate XML counting:** duplicate or anonymous XML evidence can inflate nonmandatory aggregate case totals. The 19 named mandatory VS10 cases remain independently protected.
- **MIN-3 — acquisition deadline classification:** a late exception can win failure classification over an elapsed acquisition deadline when timer dispatch is delayed. Late successful acquisition remains prevented.

## Open Nits

Both Nits remain open and were not fixed in this closure:

- **NIT-1:** define the PDF signature scanning boundary precisely.
- **NIT-2:** clarify that stress iteration terminology distinguishes repeated iterations from distinct interleavings.

## Closure rule

VS10 is closed with limitations when the implementation and M1/M2/M3 ownership remediation are present; final implementation host and API 31–36 evidence is green with all 19 named mandatory cases present on every API; pinned physical acceptance is accurately reported with its limitations; and no Blocker or Major finding remains. Closure does not imply that the listed physical limitations or open Minor/Nit findings have been resolved.
