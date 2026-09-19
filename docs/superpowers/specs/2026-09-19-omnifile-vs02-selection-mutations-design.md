# OmniFile Core V1 VS02 Selection and Capability-Aware Mutations

## Status

Approved design for `development/core-v1-selection-mutations-v1`.

Base: `97a63de6c7546b4dfec9439df328fba593a4cc0c`.

## Scope

VS02 adds selection mode, provider-scoped Rename and Delete capabilities, Local and SAF mutations, provider-truth refresh/reconciliation, and the Files selection UI. Copy, Move, Share, durable operation management, archive/media/network/cloud work, and navigation-shell redesign remain deferred.

The existing `StorageProvider` boundary remains authoritative. Only `rename` and `delete` are added to it. `Path` stays inside `LocalStorageProvider`; `Uri` and `DocumentsContract` stay inside `SafStorageProvider`.

## Selection model

Selection is represented by provider-scoped `EntryRef` values and the current browse-location boundary. A row is selectable only while the current `FilesUiState` is content or empty content. Long-press enters selection mode with one entry; taps in selection mode toggle entries. A tap on a directory in normal mode retains VS01 navigation behavior.

Selection clears when the user changes directory, changes provider/tree grant, presses Android Back or the top close affordance, deselects the final item, or completes a successful mutation. Rename reconciliation always ends selection after the refreshed listing is published. A full-success delete ends selection. A partial delete retains failed surviving entries when the refreshed provider listing can identify them; it never reports total success when any deletion failed.

The old identity is never retained after a successful rename that returns a new `EntryRef`. Selection never uses row index, display name, absolute path, or unscoped document ID as its key.

## Provider contract

`StorageCapability.RENAME` and `StorageCapability.DELETE` are exposed only for entries for which the provider can truthfully attempt the operation. The browse root is never exposed with either capability, even if a provider reports a mutation flag for it.

The provider-neutral error model adds only the VS02 cases: invalid name, name conflict, and a structured per-item delete outcome. Framework exceptions are translated inside providers.

`FilesRepository` delegates mutations by `entry.ref.providerId`. `FilesViewModel` serializes mutation submissions, rejects stale selection/location completions with a request token, refreshes the current parent listing after each mutation batch, and does not add a durable process-resume manager.

## Local semantics

Local Rename validates a non-empty single path component, rejects NUL, `/`, `\\`, `.`, `..`, and normalized traversal. It resolves only within the current entry's parent and rejects an existing target explicitly. The root/session root has no mutation capability and is rejected independently at execution time.

Local Delete supports regular files and symbolic links by deleting the link itself. Directory deletion is exposed only if the implementation can safely walk below the provider root without following symlinks. The implementation uses `Files.walkFileTree` without `FOLLOW_LINKS`, checks lexical containment for every visited path, refuses the configured root, and reports partial failures without claiming success. No shell command is constructed.

## SAF semantics

SAF Rename calls `DocumentsContract.renameDocument` and accepts the provider-returned URI as the authoritative new opaque reference. It re-queries the returned document and then refreshes the current parent. The requested name is never treated as authoritative because a provider may sanitize it.

SAF Delete calls `DocumentsContract.deleteDocument`; directory semantics remain provider-native. SAF Rename/Delete actions follow the document flags mapped by the existing provider. A valid file descriptor or write capability does not imply either mutation capability.

## UI

The current VS01 normal Files screen remains the baseline because no Files normal authority image exists. The user-provided Files single-selection and multiple-selection images are the primary VS02 authority. The selection top bar exposes close, selected count, and only real current actions. Rename is single-selection only. Delete is available for eligible selection sets and always requires confirmation. Copy, Move, and Share are omitted rather than rendered as dead placeholders.

Rows expose selected semantics through Compose accessibility state, not color alone. Icon-only controls have content descriptions. The contextual selection action area remains visible while the list scrolls.

## Verification

Host tests cover selection transitions, identity boundaries, Local validation and containment, symlink behavior, stale references, capability mapping, mutation races, refresh truth, and partial outcomes. Controlled DocumentsProvider instrumentation covers capability flags, `renameDocument` URI/document ID changes, provider-sanitized names, delete semantics, and missing flags. Focused Compose instrumentation covers long-press entry, toggles, Back/close, Rename availability, dialogs, confirmation, accessibility semantics, and refresh.

Real Pixel validation uses only a disposable `OmniFile-VS02-Test/` tree. Every destructive physical-device action is preceded by a provider-reference/tree-boundary check. Root worktree originals remain unchanged. Remote equality is recorded only after network access succeeds; current DNS failure is not treated as equality.
