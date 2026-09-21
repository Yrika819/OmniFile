# OmniFile CORE_V1 — Vertical Slice 06
## Permanent Adaptive App Shell V1

- Published VS05 base: `35dcf37ccadc7f86182ca1b038b59015a5962bba`
- VS06 branch: `development/core-v1-app-shell-v1`
- Worktree: `/Users/yuta/Desktop/File Manager-worktrees/omnifile-app-shell-v1`
- Status: implementation and physical verification complete; final commit/push metadata is recorded at closure

## Product navigation model

OmniFile now has four permanent top-level destinations:

1. **Home**
2. **Search**
3. **Music**
4. **Settings**

`Files` is a detail surface, not a fifth permanent destination. It is opened from a Home storage source, from a Search result, or through the existing storage flows. Contextual Search is also a detail surface owned by Files and uses the same Search engine/ViewModel contract as top-level Search.

Navigation is implemented with explicit shell state rather than Navigation Compose. The current dependency baseline did not include Navigation Compose, and `AppNavigationState` plus retained Activity ViewModels provide the required top-level/detail ownership without creating duplicate browser or Search stacks.

## Detail surfaces and Back policy

- Home → Files: nested directory Back moves to the parent; provider-root Back returns to Home.
- Top-level Search → result → Files: nested Back follows the Files path; provider-root Back returns to Search, preserving the Search ViewModel state while the process remains alive.
- Files → contextual Search: Back returns to the originating Files detail surface.
- Contextual Search result opening preserves the live Files stack; Back follows that stack rather than treating the scoped directory as a provider root.
- Contextual Search result Files routes carry an explicit contextual origin, so the Files boundary returns to contextual Search before contextual Search returns to Files.
- Search top-level, Music, Settings, and Home at their root use the normal Activity/system Back policy. Bottom navigation does not create a cyclic Back history.
- Selection mode and destination-picker Back handling remain owned by `FilesViewModel` before the shell consumes a Files boundary Back.

## State and restoration

The Activity retains application-scoped storage, SAF provider, Room operation, and operation-reconciliation infrastructure through `OmniFileApplication`/`AppContainer`. Activity-scoped ViewModels retain Home, Files, Search, and Operations state across ordinary configuration changes.

Saved instance state restores:

- selected top-level destination;
- detail surface and Files origin when the retained Files path is still available;
- Search query for top-level Search.

Search results are not claimed to survive process death. After process recreation, a restored Search query is rerun against current provider state. An in-memory Files detail path is not fabricated after process death; if no retained Files path exists, the detail route is cleared safely.

## Compact and adaptive navigation

- Compact widths use Material3 `NavigationBar` in the authority order Home, Search, Music, Settings.
- Widths at or above 600 dp use a Material3 `NavigationRail` with always-visible labels.
- Selected destinations expose both the Material selected state and visible labels; selection is not conveyed by color alone.
- The shell is always present on top-level surfaces and is hidden for Files/detail surfaces.
- Auto-hide-on-scroll was intentionally deferred because it is not required for this slice and would add unnecessary state/accessibility risk.

## Home V1 and source registry

Home is a useful source overview backed by the same provider contracts as Files:

- Local app-private OmniFile storage;
- currently valid, readable persisted SAF tree roots;
- Choose SAF folder action using the existing `ACTION_OPEN_DOCUMENT_TREE` flow;
- unavailable/revoked remembered sources are reported as unavailable and are not presented as usable roots.

No cloud, network, root filesystem, capacity numbers, or fake providers are shown. The root registry resolves SAF candidates on every snapshot, so a newly granted folder becomes visible without requiring process restart. Home refreshes that snapshot when the Activity resumes, so external grant/provider changes are reflected when returning to the app.

## Top-level Search and ThisDevice

Top-level Search defaults to `SearchScope.ThisDevice`. Files contextual Search defaults to `SearchScope.CurrentFolder` for the active provider/directory. Both use the existing bounded, cancellable, progressive Search engine and one Search ViewModel contract.

`ThisDevice` means only roots OmniFile can currently browse:

1. the Local OmniFile root;
2. currently persisted readable SAF trees whose provider/root call succeeds.

It does not include arbitrary Android filesystem paths, `MANAGE_EXTERNAL_STORAGE`, root, cloud, or network storage.

### Supported-root registry

`SupportedRootRegistry` keeps generic Search provider-neutral. Each root has a stable root ID, provider ID, display label, source type, and provider-neutral `StorageEntry`. Resolution also returns per-root failures. A root failure does not erase successful roots from another provider.

Remembered SAF URIs are revalidated against current persisted readable grants. A remembered URI without a live read grant is not an aggregation root. Invalid/revoked and provider-unavailable cases remain distinguishable through the failure classification exposed to Home/Search.

### Overlapping SAF roots

- Exact URI duplicates are scanned once.
- A descendant SAF tree is removed from the aggregation scan only when containment is proven by the provider-aware `DocumentsContract.isChildDocument` check.
- If containment cannot be proven, both roots remain searchable rather than guessing and risking omission.
- Result identity remains provider/document identity based, so equal names in Local, two SAF roots, or separate directories remain distinct.

### Multi-root results and failures

Search preserves root identity through provider-aware `EntryRef` identities and displays the registry's human-readable root label plus relative path context rather than raw URI/provider strings. Equal filenames under two roots therefore remain distinguishable even when folder names match.

- all roots succeed: `Results.complete == true` when traversal has no subtree failure/truncation;
- at least one root succeeds and another fails: successful matches remain visible with an incomplete/partial warning;
- all roots fail: Search reports an explicit root error rather than claiming an empty complete result;
- a subtree failure remains a subtree failure and does not discard other root results;
- no available roots is explicit and truthful.

Search generation and active-scope checks remain authoritative. Query replacement, scope replacement, and cancellation invalidate prior publications, including late publications from a multi-root scan.

## Files integration

The existing Files browser, provider navigation, selection, Rename/Delete, Local transfer operations, destination picker, Operations UI, and contextual Search remain on the proven VS01–VS05 surfaces. Search result opening now activates the result's provider when it differs from the currently selected Files provider, and stale/invalid results fail without closing the Search surface.

## Music and Settings

Music is a real top-level destination with a truthful empty state: no fake songs, player, playback controls, or Media3 behavior are included.

Settings is a read-only truthful surface showing current app identity/version, Android API, supported source count, and ThisDevice completeness status. It does not contain inert toggles.

## Edge-to-edge, IME, and accessibility

- `MainActivity` calls `enableEdgeToEdge()` and declares `adjustResize`.
- Material3 TopAppBar/NavigationBar/NavigationRail own their system-bar areas.
- Files and Search consume Scaffold padding once; Operations applies navigation-bar padding only when it is outside the shell's primary navigation area.
- Search applies `imePadding()` after Scaffold padding consumption so the query field/results remain usable with the keyboard.
- Navigation destinations expose visible labels and selected semantics; Home source cards are clickable with useful text; Search results expose filename, root/path context, and metadata.
- Android navigation-bar contrast enforcement is disabled for the edge-to-edge shell to avoid an unwanted system scrim.

## Host evidence

Commands used the required strict flags:

```text
--no-daemon --max-workers=1
-Dkotlin.compiler.execution.strategy=in-process
--dependency-verification=strict
```

Environment: Android SDK `/Users/yuta/Library/Android/sdk`; JDK 21 fallback at `/Users/yuta/.gradle/jdks/eclipse_adoptium-21-x86_64-os_x.2/jdk-21.0.7+6/Contents/Home` because JBR 25.0.3 was not installed. JDK 26 was not used for final verification.

- `:app:compileDebugKotlin`: PASS
- focused VS06 host tests: PASS; 18 tests across navigation, contextual Files origin, root registry, ThisDevice Search, root labels, and Files provider switching
- `:app:testDebugUnitTest`: 95 tests executed; 84 passed and 11 pre-existing LocalStorage secure-mutation/transfer tests fail under this macOS/JDK 21 runtime because `SecureDirectoryStream` is reported unavailable. The failures are unchanged VS05 LocalStorage/Operation paths and are not caused by VS06 shell/root code.
- `:app:lintDebug`: BUILD SUCCESSFUL; existing advisory/dependency/resource warnings remain, with no new error-level issue
- `:app:assembleDebug`: PASS
- `:app:assembleDebugAndroidTest`: PASS
- strict dependency verification: PASS for all successful Gradle commands

## Controlled provider and Pixel evidence

The existing controlled `DocumentsProvider` harness remained the only SAF fake system. Existing provider/search/operation instrumentation ran successfully.

Final direct runner:

- device: Pixel 7a
- API: 36
- runner: `androidx.test.runner.AndroidJUnitRunner`
- package: `com.omnifile.test`
- result: **37/37 PASS, 0 fail, 0 skip**

Direct Pixel checks observed:

- Home selected at launch with Local and persisted disposable OmniFile SAF source entries;
- compact bottom navigation exposes Home/Search/Music/Settings;
- top-level Search shows `This device` scope;
- Music shows the truthful no-library/no-playback state;
- Settings shows real app/storage/platform information;
- Home → Local Files → Android Back returns to Home;
- Files/Search and existing VS05 Compose/provider flows remain green.

Physical evidence is compact-width only. The expanded NavigationRail branch is statically reviewed through the width-based shell branch and remains a future medium/expanded runtime evidence opportunity.

## Review evidence

- Review A — shell/navigation architecture: `tmp/reviews/2026-09-21-code-review-vs06-review-a-1d4f8c2a.md`, validated Pass before substantial UI work.
- Review B — state restoration, Back, ThisDevice aggregation: `tmp/reviews/2026-09-21-code-review-vs06-review-b-7eb71a63.md`, validated Pass, 0 findings, 8 areas.
- Review C — UI/adaptive/accessibility/insets: `tmp/reviews/2026-09-21-code-review-vs06-review-c-535255d8.md`, validated Pass, 0 findings, 8 areas.
- Final whole-diff review and final VS05→VS06 regression review are performed at closure against the published VS05 SHA and recorded in the final review artifacts.

## Deferred capabilities

Media3 playback, music indexing, full Settings, root storage, network/cloud providers, archives, Preview, directory Copy/Move, SAF Move, SAF destination finalization, persistent indexing, full-text/OCR/semantic search, duplicate detection, WorkManager indexing, and VS07 work remain deferred.

## Recommended VS07 frontier

Use this shell as the stable attachment point for the next provider/media slice: add a real media capability/index contract behind the existing Music destination, with lifecycle/state tests and provider-specific result identity, without reopening the four-destination shell or Files ownership model.
