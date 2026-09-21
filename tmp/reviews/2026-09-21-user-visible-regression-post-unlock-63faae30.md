# User-Visible Regression Review

## Scope

- Review date: `2026-09-21`
- Requested outcome: `final post-unlock regression gate`
- Continuation: `return final gate recommendation; no implementation finding remains`
- Scope reviewed: `published VS03 a95b3e28f47954b879b389ce6d5a5f17d4704407 through final VS04 b3fd23078bc26857e80cbc4018ba5019d19dc04c, including final normally unlocked Pixel UI evidence`
- Baseline: `a95b3e28f47954b879b389ce6d5a5f17d4704407`
- Target: `b3fd23078bc26857e80cbc4018ba5019d19dc04c`
- Completion: `Complete within reviewed scope`
- Assumptions: `SAF to Local Copy is the only supported SAF route; SAF Move, Local to SAF, and SAF to SAF remain intentionally Unsupported; finalizationProven=false remains unchanged.`
- Excluded as unrelated: `VS05 and all out-of-scope storage providers or background features`

## Gate Snapshot

- Recommendation: `Pass`
- Completion: `Complete within reviewed scope`
- Why now: `The prior locked-device Discuss item is resolved: the focused Compose class passes 4/4, the complete final direct instrumentation suite passes 31/31 on the normally unlocked Pixel 7a, and the disposable SAF UI smoke confirms the conservative route matrix.`
- Must-review now: `None`
- Findings count: `Block 0 | Discuss 0 | Watch 0 | Intentional 3`
- Coverage confidence: `high`
- Behavior graph coverage: `Directly traced and runtime-verified for Local browsing, SAF source restoration, DocumentsUI grant flow, selection, Copy/Move capability gates, destination picker, conflicts, Operations, Back, and unsupported routes.`
- Biggest blind spot: `No unresolved blind spot within this VS04 gate; real user-provider revocation experiments remain intentionally deferred because controlled-provider evidence is authoritative for destructive failure semantics.`

## Complete Findings Index

No user-visible regressions identified in the reviewed scope.

## Block

None.

## Discuss

None.

## Watch

None.

## Intentional Changes

### I1 - SAF source restoration preserves explicit user intent

- A source tree selected through normal DocumentsUI is remembered only after current persisted readable grant validation.
- After Activity recreation, the selected disposable `OmniFile-SAF-Test` tree restored and listed `source` and `destination` without arbitrary equal-depth grant ordering.

### I2 - Provider outage is presented as retryable truth

- Provider disappearance remains distinct from true missing, permission loss, and stale identity.
- The durable path does not infer source absence, `COMPLETE`, or source deletion from provider outage.

### I3 - Unsupported SAF destination and Move routes remain conservative

- SAF source Copy is available for the verified sequential/persisted-grant path.
- SAF source Move is disabled.
- Local destination selection is available for Copy.
- SAF destination confirmation remains disabled while `finalizationProven=false`; Local to SAF and SAF to SAF are not claimed.

## Coverage Ledger

| Surface / path | Touched files or entry points | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| Local browse | `LocalStorageProvider.kt`, `FilesViewModel.kt`, `FilesScreen.kt` | `Reviewed - no user-visible regression found` | `Pass` | `Full final instrumentation 31/31; existing VS03 path retained.` |
| Local selection, Rename, Delete | `FilesScreen.kt`, `FilesViewModel.kt`, repository/provider mutation paths | `Reviewed - no user-visible regression found` | `Pass` | `Focused Compose class 4/4 includes selection/dialog coverage; full suite green.` |
| Local to Local Copy/Move | `OperationManager.kt`, local provider, Operations UI | `Reviewed - no user-visible regression found` | `Pass` | `Full final instrumentation 31/31 and host durable ordering evidence.` |
| SAF DocumentsUI tree grant flow | `MainActivity.kt`, `SafTreeGrantStore.kt` | `Reviewed - no user-visible regression found` | `Pass` | `Normal DocumentsUI flow on Pixel; disposable tree confirmation completed; no grant database manipulation.` |
| SAF source restoration and browsing | `SafTreeGrantStore.kt`, `SafStorageProvider.kt`, `FilesViewModel.kt` | `Reviewed - no user-visible regression found` | `Pass` | `Activity recreation restored OmniFile-SAF-Test; source and destination folders listed.` |
| SAF source selection and SAF to Local Copy | `FilesScreen.kt`, `FilesViewModel.kt`, `OperationManager.kt` | `Reviewed - no user-visible regression found` | `Pass` | `41 B disposable source visible; Copy enabled; prior real Pixel Copy COMPLETE and source retained; controlled/full runtime green.` |
| SAF Move | capability derivation and Files UI | `Intentional I3` | `Unsupported safely` | `Move parent was disabled for selected SAF source; no source deletion attempted.` |
| Local to SAF and SAF to SAF | destination picker and SAF provider capability gates | `Intentional I3` | `Unsupported safely` | `SAF final confirmation parent was disabled; finalization remains unproven.` |
| destination picker and Back | `FilesScreen.kt`, `FilesViewModel.kt`, `MainActivity.kt` | `Reviewed - no user-visible regression found` | `Pass` | `Copy entered destination picker; Local option was enabled; Back returned without transfer.` |
| conflicts | provider/operation conflict handling and Operations UI | `Reviewed - no user-visible regression found` | `Pass` | `Prior real Pixel conflict evidence and final 31/31 suite; no overwrite claim changed.` |
| cancellation and restart | `OperationManager.kt`, `OperationStateMachine.kt`, grant restoration | `Reviewed - no user-visible regression found` | `Pass` | `Controlled recovery coverage; Activity recreation source restore; no duplicate route introduced.` |
| Operations UI | `OperationsPanel.kt`, operation state mapping | `Reviewed - no user-visible regression found` | `Pass` | `Full final instrumentation includes Operations paths; no unsupported terminal claim added.` |
| provider switching and permission errors | `SafStorageProvider.kt`, `FilesScreen.kt` | `Reviewed - no user-visible regression found` | `Pass` | `Controlled provider 17/17 and full 31/31; typed errors remain truthful.` |
| Android Back and process recreation | `MainActivity.kt`, `FilesViewModel.kt` | `Reviewed - no user-visible regression found` | `Pass` | `Full final instrumentation and explicit force-stop/relaunch smoke.` |

## Evidence Appendix

### Behavior Graph Deltas

| ID | Surface | Baseline path | Final path | User-visible delta | Result |
| --- | --- | --- | --- | --- | --- |
| `B1` | Provider outage | SAF access failure could collapse into stale/not-found | typed `ProviderUnavailable` -> retryable durable/UI path | users are not told a missing-file conclusion without evidence | reviewed, no regression |
| `B2` | Tree restoration | persisted grants selected by depth/platform order | explicit selected marker + current grant validation -> safe fallback | selected source intent survives equal-depth grants and restart | reviewed, no regression |
| `B3` | Copy/Move gating | route capabilities derived from provider truth | SAF Copy source path enabled; SAF Move and unproven destination remain disabled | unsupported destructive/destination actions are not advertised as usable | reviewed, intentional |
| `B4` | Destination picker | durable destination selection path | Local selectable; SAF final confirmation disabled without finalization proof | no unsafe SAF destination operation can be confirmed | reviewed, intentional |
| `B5` | Compose/UI runtime | four Compose checks were blocked by lockscreen | focused 4/4 and full 31/31 after normal unlock | prior physical Discuss gate is resolved | reviewed, no regression |

### Runtime Evidence

- Target: `Pixel 7a`, Android `16`, API `36`, stable serial `adb-35241JEHN08768-sNRKBY._adb-tls-connect._tcp`.
- Device state during final run: `device`, `mDreamingLockscreen=false`, OmniFile Activity foreground during smoke.
- Final app APK: `d7429bd8d7d68a2d38b92620aed740214bbb977dd0b2aadf149ec17aa80b5c4b`.
- Final test APK: `6881e010dc1a0455a967c1e13885aa72a6ac79b209ede489e15a93976b216814`.
- Focused class `com.omnifile.ui.files.FilesScreenComposeInstrumentedTest`: `4 tests, 4 passed, 0 failed, 0 skipped`.
- Complete direct runner: `31 tests, 31 passed, 0 failed, 0 skipped`.
- Disposable tree: `OmniFile-SAF-Test/source/vs04-source.txt`, `41 B`; source was retained; no destructive UI flow executed.
- UI smoke: source tree restored after Activity recreation; Copy enabled; Move disabled; Local destination selectable; SAF final `Use this folder` parent disabled; no raw error enum observed.

### Diff Inventory

| File or area | Classification | User-visible path considered |
| --- | --- | --- |
| `app/src/main/java/com/omnifile/storage/SafStorageProvider.kt`, `StorageModel.kt` | `user-visible dependency` | SAF browse, capability and provider error outcomes |
| `app/src/main/java/com/omnifile/storage/SafTreeGrantStore.kt`, `MainActivity.kt` | `user-visible dependency` | grant persistence, picker result, source restore |
| `app/src/main/java/com/omnifile/operations/*.kt` | `user-visible dependency` | transfer states, retries, source-delete ordering, Operations |
| `app/src/main/java/com/omnifile/files/*.kt`, `app/src/main/java/com/omnifile/ui/files/*.kt` | `user-visible surface` | browsing, selection, dialogs, route gating, destination picker |
| `app/src/main/java/com/omnifile/ui/operations/OperationsPanel.kt` | `user-visible surface` | progress and terminal truth |
| `app/src/androidTest/**`, `TestDocumentsProvider.java`, `app/src/test/**` | `test-only` | runtime and regression evidence |
| `docs/production/VERTICAL_SLICE_04_SAF_DURABLE_TRANSFER.md` | `docs-only` | support and evidence claims |

### Verification Commands

- `adb devices -l` -> stable Pixel 7a selected; duplicate mDNS alias not used for instrumentation.
- `adb -s <stable> shell getprop ro.build.version.sdk` -> `36`; `mDreamingLockscreen=false`.
- Focused direct AndroidJUnitRunner -> `OK (4 tests)`.
- Complete direct AndroidJUnitRunner -> `OK (31 tests)`.
- Normal DocumentsUI tree flow -> disposable `OmniFile-SAF-Test` selected; no root/security bypass.
- Force-stop/relaunch plus `uiautomator dump` -> `OmniFile-SAF-Test` source restored and listed.
- Host baseline retained: `69/69`; no production code changed after the built APK.

### Dismissed Candidates

| Candidate | Decision | Evidence |
| --- | --- | --- |
| `Locked-device Compose failures are a product regression` | `dismissed` | Failures disappeared after normal user unlock; focused and complete suites both passed. |
| `Provider outage is shown as missing file` | `dismissed` | Controlled provider/runtime classification and retry mapping remain distinct. |
| `Equal-depth persisted grants restore arbitrary tree` | `dismissed` | Explicit marker and Activity recreation restored the selected disposable tree. |
| `Unsupported SAF destination should be claimed because DocumentsUI opened` | `dismissed` | Destination confirmation remained disabled and finalization proof remains false. |

### Strongest Remaining Blind Spot

`Real user-provider revocation and disappearance experiments were not performed destructively. The controlled DocumentsProvider deterministically covers these semantics, and no supported route depends on generic SAF destination finalization or SAF Move.`

## Final Gate Statement

`The previously locked-device Discuss item is resolved by final physical evidence. No user-visible regression was identified in the published VS03 to final VS04 scope. The conservative SAF route matrix is preserved.`
