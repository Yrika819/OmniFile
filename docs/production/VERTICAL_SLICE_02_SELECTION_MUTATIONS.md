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

Host unit tests, lint, and debug assembly passed. The connected Pixel 7a / Android 16 suite passed with:

```text
tests=12 failures=0 errors=0 skipped=0
```

Final debug APK observed at `app/build/outputs/apk/debug/app-debug.apk`:

```text
SHA-256 4734aa95b77207b04671d2530a5d8c4725a6d65a46cb69730c4a1ff9a22140be
```

The connected test XML is `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel 7a - 16.xml`.

## Limits and closure state

- Process-death restoration of active selection/mutation is not implemented; persisted SAF grant restoration remains separate from selection state.
- No Copy/Move/Share, archive, media, network, cloud, or durable-operation work was added.
- `origin/HEAD` was observed as `793d151f9608a684c1d0d4be2e58e5b49d26f823`.
- The feature branch is not present on `origin`; remote equality for `development/core-v1-selection-mutations-v1` is therefore `NOT_VERIFIED`. No push was performed.
- No new production dependency or native `.so` was introduced.

## Final state

The isolated branch is ready for review/hand-off. It has not been pushed or merged.
