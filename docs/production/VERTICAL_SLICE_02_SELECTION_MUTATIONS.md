# OmniFile Vertical Slice 02 — Selection and Mutations

Status: `IMPLEMENTED / DEVICE-VERIFIED / NOT PUSHED`

## Scope and provenance

- Base SHA: `97a63de6c7546b4dfec9439df328fba593a4cc0c`
- Branch: `development/core-v1-selection-mutations-v1`
- Worktree: `/Users/yuta/Desktop/File Manager-worktrees/omnifile-selection-mutations-v1`
- Root worktree originals were not edited.
- The 11 UI-authority PNGs are unchanged copies under `docs/ui-authority/v1/`; their SHA-256 values are recorded in that directory's README.
- Files normal-screen imagery was absent; VS01 production Files UI remains the normal-mode baseline. The single- and multiple-selection images are the VS02 authority.

## Shipped behavior

- Long-press enters selection mode; tap toggles entries while selection mode is active.
- Selection identity is `EntryRef` plus provider and current location boundary.
- Back first clears selection, then navigates; provider changes, navigation, and empty selection clear it.
- Rename is single-selection only and appears only when the selected entry exposes `RENAME`.
- Delete requires confirmation and is enabled only when every selected entry exposes `DELETE`.
- Copy, Move, and Share are deferred and have no production placeholder actions.
- Rename/Delete run through the existing `StorageProvider` boundary only. The ViewModel refreshes provider truth after the mutation.
- A provider-returned Rename ref is treated as a new identity; the old ref is not retained.
- Full Delete clears selection. Partial Delete retains failed entries that still exist after refresh and reports typed partial failure.
- Mutation submissions are serialized and stale completions cannot overwrite newer navigation.

## Provider semantics and safety

### Local

- Root/session-root mutation is unavailable.
- Symlinks are never followed.
- Delete is descriptor-relative and exposed only when secure directory operations are available.
- Regular files, symlinks, and empty directories are supported; non-empty recursive directory delete is intentionally unsupported.
- Local Rename remains conservatively unsupported because an atomic no-replace primitive was not proven.

### SAF

- Rename uses `DocumentsContract.renameDocument`; Delete uses `DocumentsContract.deleteDocument`.
- Calls are confined to the registered provider/tree boundary.
- Root mutation is unavailable even if provider flags claim Rename/Delete.
- Missing capability flags are mapped to `Unsupported` without a provider call.
- Returned Rename URI/document ID changes are normal and become a new `SafEntryRef`.
- Provider-sanitized names are accepted; requested and returned display names are not assumed equal.

## Verification evidence

All Gradle commands used strict dependency verification:

```text
./gradlew testDebugUnitTest --dependency-verification=strict
./gradlew testDebugUnitTest lintDebug assembleDebug --dependency-verification=strict
./gradlew connectedDebugAndroidTest --dependency-verification=strict
```

Host unit tests, lint, and debug assembly passed:

```text
host unit tests: 43 passed, 0 failed
lintDebug: passed
assembleDebug / assembleDebugAndroidTest: passed
connectedDebugAndroidTest on Pixel 7a / Android 16: 16 passed, 0 failed
  ProductionScaffoldInstrumentedTest: 2
  SafStorageProviderInstrumentedTest: 10
  FilesScreenComposeInstrumentedTest: 4
```

The focused Compose suite exercises real selection entry/toggle/close, capability gating, rename/delete confirmation boundaries, and colliding-reference rendering on the production `FilesScreen`. It does not claim manual system-back handling or a physical Local-provider file mutation flow. A separate Activity probe reached the real Local stack on this Pixel but received typed `PermissionDenied` while opening the app-private root; the provider's security checks were not weakened, and that unsupported/not-exercised path is excluded from the passing count.

Final debug APK observed at `app/build/outputs/apk/debug/app-debug.apk`:

```text
SHA-256 53c8cc35e6c3d4ead8469469a667a839ed4175d3646848127f3309b67761b85c
```

The final connected result XML is `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel 7a - 16.xml`.

## Limits and closure state

- Process-death restoration of active selection/mutation is not implemented; persisted SAF grant restoration remains separate from selection state.
- No Copy/Move/Share, archive, media, network, cloud, or durable-operation work was added.
- `origin/HEAD` was observed as `793d151f9608a684c1d0d4be2e58e5b49d26f823`.
- The feature branch is not present on `origin`; remote equality for `development/core-v1-selection-mutations-v1` is therefore `NOT_VERIFIED`. No push was performed.
- The debug-only test runtime adds Compose UI-test support, `kotlinx-coroutines-test:1.9.0` as `debugRuntimeOnly` for the Android test ServiceLoader, and Espresso `3.7.0`; no release dependency was added. The final APK's native inventory contains only the pre-existing Compose dependency library `libandroidx.graphics.path.so` for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

## Final state

The isolated branch contains the bounded fixes, passes host/static verification, and has final 16-test Pixel evidence. It has not been pushed or merged.
