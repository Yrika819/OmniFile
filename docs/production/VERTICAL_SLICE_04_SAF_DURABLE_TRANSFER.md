# OmniFile CORE_V1 VS04 — SAF Durable Transfer

## Status

`OMNIFILE_CORE_V1_VS04_COMPLETE — SAF DURABLE TRANSFER SLICE CLOSED`

VS04 hardening is implemented and the two prior final-review code findings are closed. The final supported production matrix remains conservative: sequential SAF source → Local Copy is supported for the verified grant/provider path; SAF destination finalization, SAF Move, and SAF → SAF remain Unsupported.

The final physical Compose/UI gate is closed on a normally unlocked Pixel 7a. The focused Compose class passed 4/4, the complete direct instrumentation suite passed 31/31, and the bounded disposable-tree UI smoke confirmed source restoration and conservative capability gating. No route was enabled from this evidence.

## Base, branch, and current revision

- Published VS03 base: `a95b3e28f47954b879b389ce6d5a5f17d4704407`
- VS04 branch: `development/core-v1-saf-transfer-v1`
- Current VS04 HEAD: `b3fd23078bc26857e80cbc4018ba5019d19dc04c`
- VS04 worktree: `/Users/yuta/Desktop/File Manager-worktrees/omnifile-saf-transfer-v1`
- Hardening commit: `19b73f7 fix(storage): close saf interruption and tree restoration blockers`
- Room schema: version `1`; unchanged; no migration required
- Device: Pixel 7a, Android 16/API 36
- Stable serial: `adb-35241JEHN08768-sNRKBY._adb-tls-connect._tcp`

## Final production route matrix

| Route | Production claim | Evidence and boundary |
|---|---|---|
| Local → Local Copy/Move | Supported | VS03 durable route retained; host and instrumentation regression paths remain green. |
| SAF → Local Copy | Supported, sequential Copy only | Controlled provider and durable SAF runtime pass; prior real Pixel Copy completed `41 B`, source retained, Local destination reached `COMPLETE`. Claim is bounded to current persisted readable grant and provider capability path. |
| SAF → Local Move | Unsupported | Production `sourceVersionProven=false`; `MOVE_SOURCE` is not advertised/enqueued without provider-scoped mutation/version proof. |
| Local → SAF Copy | Unsupported | `finalizationProven=false`; SAF destination confirmation remains disabled. No direct write to intended final name is allowed. |
| Local → SAF Move | Unsupported | SAF destination finalization is unsupported, and destructive source deletion requires durable destination completion. |
| SAF → SAF Copy | Unsupported | SAF destination finalization is not proven. |
| SAF → SAF Move | Unsupported | Destination finalization plus SAF source mutation/delete proof are not sufficient. |

No route was enabled merely because a `ContentResolver` operation could be opened. Unsupported routes remain capability-gated in Files and the destination picker.

## Durable SAF locator and identity

Encoding: `saf-document-v1`.

The locator contains the exact selected tree URI string and provider document ID in a versioned, URL-safe payload. The provider ID is derived from the selected tree URI. A locator is not a path, display name, `DocumentFile`, `ContentResolver`, descriptor, or stream.

Re-resolution validates provider ID, exact tree URI, authority, locator encoding, current persisted grant, and selected-tree containment. Provider-returned document identity is used after rename/finalization where that route is enabled. A stale partial locator is never promoted to final destination truth.

Finalization records use escaped/versioned `finalization-v2`; legacy `finalization-v1` decoding remains for compatibility.

## Provider interruption classification

The prior final-review Major finding was that provider disappearance during containment could be converted to `StaleReference` and then `NOT_FOUND`. That path is closed.

`StorageError.ProviderUnavailable` is now a distinct storage result. It is emitted when provider containment/query cannot answer, rather than when the provider positively answers that a document is outside the selected tree.

Classification contract:

- provider unavailable / provider transport failure → `ProviderUnavailable`; retryable durable operation state;
- revoked/missing grant → `PermissionDenied`; source deletion is never inferred;
- query returns no in-tree document → `NotFound`; this is the only positive missing-document result in the controlled contract;
- valid document exists outside the selected tree or locator identity is mismatched → `StaleReference`;
- ordinary provider I/O failure → `IoFailure`.

`ProviderUnavailable` maps to `PROVIDER_UNAVAILABLE` and a retryable operation state. It never maps to `NOT_FOUND`, `COMPLETE`, source absence, or source deletion.

When destination completion has already been recorded, a provider observation outage enters finalization reconciliation rather than returning to `TRANSFERRING`. When the outage occurs during `SOURCE_DELETE_PENDING`, the operation remains retryable with source deletion pending. Recovery does not recopy or create a second destination.

The Files error path renders a stable retry-oriented message instead of exposing the raw enum name.

## Persisted grant and selected-tree policy

Production uses `ACTION_OPEN_DOCUMENT_TREE`.

- Source browsing requests read plus persistable permission.
- Destination selection adds write only while a destination picker is active.
- Only read/write bits present in the returned Intent are passed to `takePersistableUriPermission`.
- Returned data must be `RESULT_OK`, a tree URI, content scheme, and nonblank authority.
- Platform `ContentResolver.persistedUriPermissions` remains the authority for current access; an app preference never grants access.
- A successful source picker result records `selected-read-tree-uri-v1` only after the current persisted grant is re-read and confirmed readable.
- A destination picker result persists the grant but does not replace the selected source marker.
- On restore, a valid exact marker wins regardless of platform permission enumeration order.
- If the marker is revoked or absent, a unique shallowest readable tree is used as the safe broad-root fallback.
- If unrelated readable grants tie at the shallowest depth, no tree is auto-selected; the user must select a source again. Platform list order is not treated as intent.

The prior final-review Minor finding about equal-depth restoration is closed by the marker and deterministic fallback policy. Host tests cover selecting A, later selecting B, reversed grant enumeration, revoked marker fallback, write-only grants, and unique broad-root fallback.

Real Pixel grant evidence from the disposable tree:

- Provider: `com.android.externalstorage/.ExternalStorageProvider`
- Authority: `com.android.externalstorage.documents`
- Broad tree: `content://com.android.externalstorage.documents/tree/primary%3AOmniFile-SAF-Test`
- Destination tree: `content://com.android.externalstorage.documents/tree/primary%3AOmniFile-SAF-Test%2Fdestination`
- Observed permission state: `mode=0x3`, `persistable=0x3`, `persisted=0x3`, `[prefix]`
- Prior production restart restored the broad `OmniFile-SAF-Test` tree rather than the child destination tree.

Real user-provider grant revocation remains intentionally deferred; deterministic controlled/host evidence is the primary evidence for interruption and destructive revocation semantics. The final physical source restoration check used the existing disposable tree after normal DocumentsUI selection and Activity recreation.

## Capability and finalization gates

SAF capabilities derive from current grant flags and provider document flags, not object type alone:

- source: sequential read;
- destination parent: create/write only when destination finalization is proven for that provider;
- partial/final destination: write, rename, inspect, and delete are independently checked;
- Move source: delete plus provider-scoped source-version proof.

Production defaults remain:

```text
finalizationProven = false
sourceVersionProven = false
```

Controlled tests may opt into deterministic proofs only inside the controlled provider fixture. No authority-name-only global trust decision was introduced.

The generic destination lifecycle remains implemented behind the disabled gate:

```text
create operation-owned SAF partial
  -> bounded sequential write
  -> verify observed bytes
  -> provider finalization
  -> verify returned identity and parent containment
  -> persist returned final locator
  -> only then allow source deletion for Move
```

Partial names are operation-bound: `.omnifile-<sanitized-operation-id>.partial`. Cleanup requires exact durable operation ownership and an operation ID; name resemblance alone never authorizes deletion. Conflicts do not overwrite existing user data.

## Supported SAF → Local Copy lifecycle

```text
resolve persisted SAF locator
  -> validate current grant and selected-tree containment
  -> sequential read, including pipe-like descriptor support
  -> create operation-owned Local partial
  -> bounded transfer and verification
  -> Local finalization
  -> durably record destination completion
  -> retain SAF source for Copy
```

SAF source deletion is not part of the supported route.

## Metadata, streaming, cancellation, and recovery

- Transfer buffer: bounded `256 KiB`.
- Advisory checkpoint cadence: `8 MiB`.
- Pipe-like/non-seekable SAF descriptors are supported for sequential reads/writes in the controlled provider.
- True offset resume is not advertised.
- Unknown size remains unknown; transfer proceeds sequentially and verification compares observed durable bytes.
- Progress uses indeterminate/unknown-size semantics rather than inventing `0 B` total.
- Cancellation does not finalize incomplete output or delete a Move source.
- Restart reconciles persisted finalization before replay and never blindly duplicates a destination.
- Ambiguous finalization remains interruption/reconciliation truth.
- Provider outage during source deletion leaves `SOURCE_DELETE_PENDING`/retryable truth; source deletion is attempted only after destination completion is established.

## Controlled DocumentsProvider runtime evidence

The existing `TestDocumentsProvider` was extended rather than replaced. Final direct AndroidJUnitRunner evidence on the Pixel:

```text
SafStorageProviderInstrumentedTest: 17/17 OK
SafStorageProviderInstrumentedTest + SafTransferRuntimeInstrumentedTest: 20/20 OK
```

Coverage includes:

- sequential read/write;
- pipe/non-seekable descriptors;
- unknown size;
- operation-owned partial creation and cleanup;
- provider unavailable before resolution and child listing;
- provider recovery;
- true in-tree NotFound;
- permission failure;
- stale identity/tree boundaries;
- create/read/write/rename/delete failures;
- conflict preservation;
- same-identity and changed-identity rename;
- provider-sanitized final name;
- ambiguous finalization;
- SAF→Local, controlled Local→SAF, and controlled SAF→SAF order fixtures;
- Move source-delete ordering and reconciliation.

The controlled harness’s `isChildDocument` treats containment as an identity-boundary answer, not an existence query, so an in-tree deleted ID reaches `queryDocument` and can produce truthful `NotFound` rather than framework-level permission denial.

## Real Pixel evidence

Disposable tree used:

```text
OmniFile-SAF-Test/
  source/
    vs04-source.txt (41 B)
  destination/
```

Earlier final production APK evidence on the same Pixel/provider established:

- normal DocumentsUI tree grant acquisition;
- selected SAF source restoration;
- SAF source visible at `41 B`;
- Copy enabled and Move disabled;
- SAF destination `Use this folder` disabled;
- SAF→Local Copy reached `COMPLETE · 41 B / 41 B`;
- SAF source remained after Copy;
- repeated Copy against existing Local destination became `CONFLICTED · 0 B / 41 B` without overwrite;
- fresh Copy completed after deleting only the disposable Local output.

No real Local→SAF or SAF→SAF destination operation was attempted because production finalization is intentionally unsupported. No final production SAF Move is claimed.

Final physical UI closure on the normally unlocked Pixel 7a/API 36:

- Focused `FilesScreenComposeInstrumentedTest`: `4/4 PASS`.
- Complete direct AndroidJUnitRunner suite: `31/31 PASS`, including the four Compose tests.
- Existing disposable `OmniFile-SAF-Test` tree restored after Activity recreation; `source` and `destination` were listed and `vs04-source.txt` remained `41 B`.
- Selected SAF source: Copy enabled; Move disabled.
- Destination picker: Local selectable; SAF final `Use this folder` confirmation remained disabled while `finalizationProven=false`.
- No destructive transfer, real grant revocation, personal-file access, root, grant-database edit, or security bypass was used.

## Room/schema impact

No schema change. Room remains schema version `1`. Versioned locators and finalization descriptions fit existing generic fields.

## Verification counts, builds, and artifacts

Environment:

```text
Android Studio JBR 25.0.3
SDK /Users/yuta/Library/Android/sdk
--no-daemon --max-workers=1
-Dorg.gradle.java.home=/Applications/Android Studio.app/Contents/jbr/Contents/Home
-Dkotlin.compiler.execution.strategy=in-process
--dependency-verification=strict
```

Fresh current tree results:

- `:app:testDebugUnitTest`: `69 tests; 0 failures; 0 errors; 0 skipped`
- `:app:lintDebug`: PASS; `0 errors`
- `:app:assembleDebug`: PASS
- `:app:assembleDebugAndroidTest`: PASS
- strict dependency verification: PASS for all Gradle invocations

Final APK SHA-256:

```text
app-debug.apk
d7429bd8d7d68a2d38b92620aed740214bbb977dd0b2aadf149ec17aa80b5c4b

app-debug-androidTest.apk
6881e010dc1a0455a967c1e13885aa72a6ac79b209ede489e15a93976b216814
```

## Reviews and security closure

- Prior final whole-diff review: `tmp/reviews/2026-09-21-code-review-final-vs04.md` — old F1/F2 findings superseded by current hardening.
- Current post-hardening code review: `tmp/reviews/2026-09-21-code-review-post-hardening-7d2c9a41.md` — historical `Pass with caveat`; its locked-device UI gap is superseded by the post-unlock evidence below.
- Final post-unlock code review: `tmp/reviews/2026-09-21-code-review-post-unlock-63faae30.md` — `Pass`; 0 findings, 0 test gaps, 8 coverage areas.
- Current post-hardening regression review: `tmp/reviews/2026-09-21-user-visible-regression-post-hardening-7d2c9a41.md` — historical `Discuss` for the locked-device gate.
- Final post-unlock regression review: `tmp/reviews/2026-09-21-user-visible-regression-post-unlock-63faae30.md` — `Pass`; no user-visible regressions identified.
- `android-intent-security`: picker construction, returned URI/flags, read/write masking, persistable grant validation, restoration, and containment reviewed.
- `debug`: used for SAF provider/error ambiguity, controlled provider evidence, and Gradle/Kotlin daemon stalls. No temporary debug probes remain.
- `testing-setup`: existing JUnit4, Compose instrumentation, Room, and controlled DocumentsProvider harness were extended; no unnecessary dependency was added.
- `edge-to-edge`: no new layout/inset surface was introduced by the hardening delta.

## Explicit remaining gates and deferred work

- Final physical Compose/UI verification is closed: focused 4/4 and complete 31/31 on the normally unlocked Pixel.
- Real persisted-grant revocation through normal user interaction remains deferred; controlled provider/host evidence is green and no supported destructive SAF route depends on unproven user-provider behavior.
- Generic SAF destination finalization remains Unsupported until provider-scoped create/write/rename/returned-identity proof is established.
- SAF→Local Move remains Unsupported until provider-scoped source-version proof is established.
- SAF→SAF remains Unsupported because it depends on destination finalization and source-delete proof.
- No directory, archive, WorkManager, UIDT, FGS, cloud, root, media, search expansion, Share, or VS05 work was started.

## Next production frontier

The next safe frontier is provider-scoped SAF source-version proof plus normal-user persisted-grant revocation/restart evidence. Destination finalization must remain opt-in and provider-semantic-aware; route count must not be increased by weakening durable safety invariants.
