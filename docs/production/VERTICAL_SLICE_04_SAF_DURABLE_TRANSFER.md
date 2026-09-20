# OmniFile CORE_V1 VS04 — SAF Durable Transfer

## Status

`OMNIFILE_CORE_V1_VS04_INCOMPLETE — SAF DESTINATION FINALIZATION AND SAF MOVE REMAIN UNSUPPORTED; REVOCATION/DESTRUCTIVE-MOVE EVIDENCE DEFERRED`

This document records the final verified state of the current VS04 worktree. SAF destination finalization is intentionally not globally trusted. The only SAF route claimed supported in production is sequential SAF source to Local destination Copy, using a currently persisted grant and the provider semantics verified below.

## Base, branch, and schema

- Published VS03 base SHA: `a95b3e28f47954b879b389ce6d5a5f17d4704407`
- VS04 branch: `development/core-v1-saf-transfer-v1`
- VS04 worktree: `/Users/yuta/Desktop/File Manager-worktrees/omnifile-saf-transfer-v1`
- Runtime-verified pre-commit HEAD: `d6a82896b074cfbf2a5f958c086e462906157d0`
- Room schema: version `1`; unchanged; no migration required
- Android runtime: Pixel 7a, Android 16/API 36
- Stable device serial: `adb-35241JEHN08768-sNRKBY._adb-tls-connect._tcp`

## Final route matrix

| Route | Production claim | Evidence and boundary |
|---|---|---|
| Local → Local Copy/Move | Supported | VS03 production route; final full instrumentation remained green. |
| SAF → Local Copy | Supported, sequential Copy only | Controlled provider runtime `18/18`; final APK real Pixel Copy `41 B`, source retained, Local output and terminal `COMPLETE` verified. This does not imply universal SAF provider support beyond the grant/provider capability checks. |
| SAF → Local Move | Unsupported | `sourceVersionProven=false` for production SAF. Move is not advertised/enqueued without a provider-scoped source version proof. The earlier pre-gate real Move is evidence only, not a final support claim. |
| Local → SAF Copy | Unsupported | `finalizationProven=false`; destination picker `Use this folder` remained disabled for SAF. No source deletion or direct final-name write is allowed. |
| Local → SAF Move | Unsupported | Same destination finalization gate, plus destructive source deletion requires a completed SAF destination. |
| SAF → SAF Copy | Unsupported | SAF destination finalization is not proven; no route is advertised. |
| SAF → SAF Move | Unsupported | SAF destination finalization and SAF source mutation/delete proof are not sufficient. |

Unsupported routes are capability-gated in the existing Files/destination-picker UI; no separate SAF operation UI or generic finalization trust was added.

## Durable SAF locator

Encoding: `saf-document-v1`.

The versioned payload contains URL-safe, unpadded Base64 tokens for:

1. the selected tree URI string;
2. the current provider document ID.

The provider ID is derived from SHA-256 of the selected tree URI. The locator is not a path, display name, `DocumentFile`, `ContentResolver`, descriptor, or stream. Decode and re-resolution validate the provider ID, exact selected-tree URI, authority, and provider child containment. Provider-returned document identity is used where finalization is supported by a provider; stale partial identity is never promoted to final truth.

Finalization records use the escaped/versioned `finalization-v2` encoding with legacy `finalization-v1` decoding retained. This prevents delimiter-bearing locator fields from corrupting restart truth.

## Persisted permission policy and security evidence

- Production constructs `ACTION_OPEN_DOCUMENT_TREE`.
- Source browsing requests read plus persistable permission.
- Destination picker adds write permission only when destination selection is requested.
- Only read/write bits actually present in the returned Intent flags are passed to `takePersistableUriPermission` and stored.
- Returned data must be `RESULT_OK`, a tree URI, content scheme, and non-blank authority.
- On restart, grants are rebuilt from `ContentResolver.persistedUriPermissions`; stored preferences do not override platform truth.
- Readable grants are restored with the shallowest tree document depth so a persisted child destination does not replace the selected broad root.
- Missing/revoked grants are classified as permission loss at the SAF adapter boundary; they do not prove source deletion and do not authorize broad storage permission.
- Authority/tree/document containment is checked before accepting a locator, returned identity, rename result, or cleanup target.

Real Pixel persisted grant evidence:

- Provider package/class: `com.android.externalstorage/.ExternalStorageProvider`
- Authority: `com.android.externalstorage.documents`
- Broad tree: `content://com.android.externalstorage.documents/tree/primary%3AOmniFile-SAF-Test`
- Destination tree: `content://com.android.externalstorage.documents/tree/primary%3AOmniFile-SAF-Test%2Fdestination`
- `dumpsys activity permissions`: both grants showed `mode=0x3`, `persistable=0x3`, `persisted=0x3`, `[prefix]`
- The final APK restored `OmniFile-SAF-Test` rather than the child `destination` tree.

## Capability and support rules

SAF capabilities are derived from current provider flags and current persisted grant mode, not object type alone:

- source: sequential read;
- destination parent: create child and write only when destination finalization is proven for that provider;
- partial/final destination: provider write/rename/delete capabilities are separately checked;
- Move source: delete plus a provider-scoped source version proof.

Provider flags are advertised capabilities, not proof that an operation succeeded. Runtime failures remain typed and durable. Production defaults remain:

```text
finalizationProven = false
sourceVersionProven = false
```

Controlled tests opt into deterministic proofs only inside the controlled provider fixture. No authority-name-only global trust decision was introduced.

## Supported SAF → Local Copy lifecycle

The supported one-sided route is:

```text
resolve persisted SAF locator
  -> validate current grant and selected-tree containment
  -> sequential read, including pipe-like descriptor support
  -> create operation-owned Local partial
  -> bounded transfer and verification
  -> Local finalization
  -> durably record destination complete
  -> retain SAF source for Copy
```

The Local destination uses the already-proven VS03 durable finalization semantics. SAF source deletion is not part of this supported route.

## Destination partial/finalization policy

The generic SAF destination lifecycle remains implemented behind a conservative gate:

```text
create operation-owned SAF partial
  -> bounded sequential transfer
  -> verify observed bytes
  -> provider rename/finalization
  -> verify returned identity and parent containment
  -> persist returned final locator
  -> only then allow Move source deletion
```

Partial names are collision-resistant and operation-bound: `.omnifile-<sanitized-operation-id>.partial`. Cleanup requires exact durable operation ownership; operation ID is required for partial access and cleanup; name resemblance alone never authorizes deletion. Intended final-name conflicts are detected without overwriting existing user data.

Controlled provider tests prove both same-URI and changed-URI rename, sanitized returned names, conflict preservation, ambiguous acknowledgement, and owned-partial cleanup. The real Pixel provider was not allowed to reach create/rename because generic SAF finalization is intentionally unsupported. Therefore no real-provider final URI/documentId change is claimed, and `finalizationProven` remains false.

## Transfer and metadata behavior

- Buffer: bounded `256 KiB`.
- Advisory checkpoint: `8 MiB`.
- SAF sequential descriptors may be pipe-like/non-seekable; true offset resume is not advertised.
- Unknown size remains unknown. Transfer proceeds to EOF and compares observed bytes against durable transfer facts where available; missing proof fails closed.
- Progress does not invent `0/0`; unknown-size operations use indeterminate/size-unknown presentation and keep verification/finalization stages visible.

## Cancellation, conflict, and restart

- Cancellation does not finalize incomplete output or delete a Move source.
- Only exact operation-owned partials may be cleaned.
- Existing destination data is not overwritten; conflict remains durable and the source remains.
- Restart does not blindly replay destructive stages.
- Persisted finalization records are reconciled before replay; repeated ambiguous finalization does not issue an illegal self-transition.
- Unknown-size destination verification compares durable observed bytes before destructive completion.
- Final destination locators are revalidated before source deletion in any future provider-scoped Move.

## Controlled DocumentsProvider evidence

The existing controlled `TestDocumentsProvider` was extended rather than replaced. The final direct AndroidJUnitRunner execution was:

```text
Tests run: 18
Failures: 0
Skipped: 0
Result: OK
```

Target classes:

- `com.omnifile.storage.SafStorageProviderInstrumentedTest`
- `com.omnifile.operations.SafTransferRuntimeInstrumentedTest`

Runtime coverage includes:

- sequential SAF read/write;
- pipe/non-seekable descriptors;
- unknown size;
- operation-owned partial creation and ownership;
- create/read/write/rename/delete failures;
- conflict preservation;
- same-identity and changed-identity rename;
- provider-sanitized final name;
- stale tree reference and read-only grant gating;
- provider disappearance/interruption and ambiguous finalization behavior represented by the harness;
- SAF→Local, Local→SAF, and SAF→SAF state/order fixtures;
- source-delete ordering and durable reconciliation cases covered by the controlled fixture.

The final full direct runner execution was:

```text
Tests run: 29
Failures: 0
Skipped: 0
Result: OK
```

This included scaffold/startup, Local transfer, Room, controlled SAF, and Compose UI tests.

## Real Pixel evidence

Dedicated disposable tree:

```text
OmniFile-SAF-Test/
  source/
    vs04-source.txt (41 B)
  destination/
```

Provider: Android system `ExternalStorageProvider` described above.

Final APK flow:

- Normal DocumentsUI tree selection and Android confirmation were used; no grant database, root, or fabricated permission was used.
- Files restored the selected SAF source tree and displayed `vs04-source.txt` at `41 B`.
- SAF source selection showed Copy enabled and Move disabled because `MOVE_SOURCE` requires `sourceVersionProven`.
- Destination picker showed SAF folder option, but SAF `Use this folder` was disabled because `finalizationProven=false`.
- SAF→Local Copy completed through the production UI as `COMPLETE · 41 B / 41 B`.
- Source remained visible after Copy.
- Local `files` showed the copied `vs04-source.txt` at `41 B`.
- An initial repeated Copy against the existing Local destination correctly became `CONFLICTED · 0 B / 41 B`; the existing destination was not overwritten. After deleting only that disposable Local output through the app, a fresh final-APK Copy completed.
- No real Local→SAF or SAF→SAF destination transfer was attempted because the production gate correctly prevented it.
- No final production SAF Move is claimed. The earlier pre-source-version-gate real Move is retained only as historical evidence and is not acceptance evidence for this tree.

The platform shell could read the disposable SAF source and observed SHA-256 `f2b879a7cf52d51ebcd3b69afd6ae4fee06520bfbf91de88aeca97b5b2aab766`; Android 16 disallowed `run-as` inspection of the app-private destination, so destination content equality is established by the production transfer verification plus the exact UI byte count, not by bypassing app sandbox policy.

## Room/schema impact

No schema change. Existing generic locator fields and finalization description carry versioned Local/SAF payloads. Existing VS03 active and terminal operations remain on Room schema version 1.

## Verification counts and artifacts

Environment: Android Studio JBR `25.0.3`, SDK `/Users/yuta/Library/Android/sdk`, serialized Gradle policy:

```text
--no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process --dependency-verification=strict
```

Fresh final host verification on the final runtime-fix tree:

- `:app:testDebugUnitTest`: `63 passed, 0 failed, 0 errors, 0 skipped`
- `:app:lintDebug`: PASS, `0 errors`, `22 warnings`
- `:app:assembleDebug`: PASS
- `:app:assembleDebugAndroidTest`: PASS
- strict dependency verification: PASS for every invocation above

APK SHA-256:

```text
app-debug.apk
3bec4009382cd59310ff437f8b9136baf54b549f128f3a74812aef6ed7c3243d

app-debug-androidTest.apk
058b2cc83270360f591866a0cd7a1e9b747edbe332f7c5b62677ded7023382ec
```

## Reviews and security closure

- Architecture Review A: historical report retained; conservative destination/source gates remain in force.
- Post-runtime Review B/C generation 0: `tmp/reviews/2026-09-20-code-review-post-runtime-bc.md`; findings were addressed or made irrelevant to the supported route by the current code and gate.
- Post-runtime Review E generation 0: `tmp/reviews/2026-09-20-code-review-post-runtime-e.md`; destination retry and permission classification fixes are present in the current tree; remaining revocation/device-error rendering evidence is documented as a gap.
- Generation-1 post-runtime reports: `tmp/reviews/2026-09-21-code-review-postruntimebc-gen1.md` and `tmp/reviews/2026-09-21-code-review-postruntimee-gen1.md`.
- Final whole-diff code review: `tmp/reviews/2026-09-21-code-review-final-vs04.md` — `Changes requested` for provider-unavailable classification and equal-depth grant selection.
- Final user-visible regression review: `tmp/reviews/2026-09-21-user-visible-regression-final.md` — `Discuss` for the same two interruption/restoration gaps.
- `android-intent-security`: picker result validation, read/write masking, persistable grant handling, restoration, and tree containment reviewed.
- `debug`: used for Gradle KSP stall and SAF provider/finalization/restart ambiguity; exact daemon PID `21500` was terminated only after inspection; no temporary debug probes remain.

## Explicit remaining gates and deferred work

- Generic SAF destination finalization remains Unsupported until a provider-scoped proof mechanism exists and real provider create/write/rename/returned-identity semantics are proven.
- SAF→Local Move remains Unsupported until a provider-scoped source version proof rules out source mutation before deletion.
- SAF grant revocation during an active operation and after process recreation remains a controlled/real evidence follow-up; provider-unavailable classification and equal-depth selected-tree restoration are final review blockers for closure. No destructive SAF route is enabled by these gaps.
- SAF→SAF remains Unsupported because it depends on destination finalization and source-delete proof.
- No directory, archive, background, WorkManager, UIDT, FGS, cloud, root, or non-SAF work was started.

## Next production frontier

The next safe frontier is provider-scoped SAF source version proof and deterministic persisted-grant revocation/restart evidence. Destination finalization must remain opt-in and provider-semantic-aware; route count must not be increased by weakening durable safety invariants.
