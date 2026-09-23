# VS08→VS09 User-Visible Regression Review

## Scope and recommendation

- Review date: `2026-09-23`
- Baseline: `9b97149c2c921a8fe4523be51624ebaad4fe1d18` (VS08)
- Target: VS09 implementation commit `64a28e5` and its affected callers.
- Review mode: changed surface and behavior graph, including unchanged neighboring Files/Search/Archive/Media/operation paths.
- Recommendation: `Pass within available evidence`
- Findings: `Block 0 | Discuss 0 | Watch 0 | Intentional 3`
- Host confidence: `high`
- Device confidence: `not established`; no Android device or emulator was available.

## Behavior graph

| Surface | Previous path | VS09 path | Review result |
|---|---|---|---|
| Files regular text/image | Tap normal file did not open content preview | Tap supported non-audio file opens nested Preview | Intentional; selection path still intercepts first. |
| Files directories/ZIP/audio | Directory browse, ZIP→Archive, explicit audio Play | Same route/affordance | No regression in source or host coverage. |
| Search result | Normal file opened its containing Files location; ZIP→Archive; audio had existing behavior | Normal non-audio regular file→Preview; ZIP/audio/directory retain prior branches | Intentional Preview behavior; same Search ViewModel, query, scope and results remain underneath and restore on Back. |
| Archive entry | Supported file had no content surface | Supported regular file→shared Preview; unsafe/unsupported entries do not expose Preview | Intentional; existing archive selection/extraction state remains underneath. |
| Preview Back | Not present | Returns to exact Files, contextual Search, top-level Search, or Archive owner | Explicit origin state and host navigation tests cover the owner restoration. |
| Media | Explicit Files/Search Play routes into existing playback coordinator/service | Audio remains excluded from generic Preview routing | No Preview path enters the media service. |
| Selection/mutation/operations | Existing selection, copy/move, rename/delete, and operation panels | Same paths; selection still wins over normal row click | Full host suite passes; no new mutation API or state was added. |
| SAF/external access | Existing persisted tree-grant/provider boundary | Preview reopens via provider-owned sequential read | No seek inference, extra permission, `ACTION_VIEW`, URI ingress, or manifest change. |

## Intentional changes

- `I1` Files non-audio regular-file row click now opens Preview.
- `I2` Search non-audio file result click now opens Preview while preserving the existing Search state beneath it.
- `I3` Supported Archive regular-file rows now open the common Preview surface by validated archive ordinal; no permanent extraction is involved.

## Regressions checked

- Directory navigation still routes through `FilesViewModel.openDirectory`.
- ZIP container routing retains existing Archive recognition and origin handling.
- Audio still has explicit Play and does not fall through to Preview.
- Long-press selection and selected-row toggles remain the first Files/Archive click branch; mutation state and destination picker are untouched.
- Search rows still pass the original `SearchHit` identity and preserve ancestors/root context.
- Archive source preview does not alter virtual paths or bypass safe-path/ordinal checks.
- Preview handles/resources are canceled and state cleared on Back or top-level navigation; A/B generation ownership blocks late publication.
- No top-level destination, service, permission, exported component, or external intent was added.

## Evidence

- `:app:testDebugUnitTest`: `150/150` passed, including Files/Search/Archive existing behavior, Preview limits/classification, Archive duplicate ordinal/stale method, and nested navigation-origin tests.
- `:app:compileDebugAndroidTestKotlin`: passed. Compose instrumentation sources cover Preview states, Files and Archive Preview callbacks, existing Search callback identity, and Local/SAF/ZIP provider cases.
- `:app:lintDebug`: passed with 0 errors; 17 baseline/toolchain warnings.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: passed.
- Connected tests: `NOT RUN`; `adb devices -l` returned no device or emulator. Therefore this report does not claim physical interaction, SAF persistence runtime, Activity recreation, or whole-device regression pass.

## Final review

No unintended source-level or host-test regression was identified in the reviewed scope. Physical device and Activity recreation remain explicit acceptance gaps. The changed click behavior is limited to the three intentional Preview entry paths above.
