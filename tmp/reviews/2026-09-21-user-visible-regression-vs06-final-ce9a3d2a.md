# User-Visible Regression Audit

## Scope

- Review date: `2026-09-21`
- Requested outcome: `post-fix review`
- Continuation: `return post-fix findings`
- Scope reviewed: `commit range`
- Baseline: `35dcf37ccadc7f86182ca1b038b59015a5962bba`
- Target: `2a077dc2d422c67fb0d2d8e31da176d24187dee3`
- Completion: `Complete within reviewed scope`
- Assumptions: `VS06 brief and saved UI authority intentionally supersede the historical Files fifth-destination model; Pixel 7a API 36 is the primary physical target.`

## Gate Snapshot

- Recommendation: `Pass`
- Completion: `Complete within reviewed scope`
- Why now: `The final VS05→VS06 user journeys were traced and exercised through focused host tests, the existing 37-test device suite, and direct compact Pixel shell checks with no unresolved regression finding.`
- Must-review now: `None`
- Findings count: `Block 0 | Discuss 0 | Watch 0 | Intentional 4`
- Coverage confidence: `high`
- Behavior graph coverage: `built for 8 surfaces`
- Biggest blind spot: `Expanded-width physical rail runtime and process-death ActivityScenario coverage`

## Complete Findings Index

No user-visible regression findings identified in the reviewed scope.

## Block

None.

## Discuss

None.

## Watch

None.

## Intentional Changes

- `I1` Permanent destinations are Home/Search/Music/Settings and Files is detail - explicitly required by the VS06 brief and saved authority - [`AppShell.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-app-shell-v1/app/src/main/java/com/omnifile/ui/shell/AppShell.kt#L25).
- `I2` Top-level Search defaults to This device while Files contextual Search uses CurrentFolder - required origin distinction using one Search contract - [`MainActivity.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-app-shell-v1/app/src/main/java/com/omnifile/MainActivity.kt#L101).
- `I3` Music is an honest empty state and Settings is read-only current information - VS06 explicitly defers playback/full settings and forbids inert controls - [`MusicScreen.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-app-shell-v1/app/src/main/java/com/omnifile/ui/music/MusicScreen.kt#L14).
- `I4` ThisDevice includes only live OmniFile Local/SAF roots and can show partial results - required truthful storage scope - [`SupportedRootRegistry.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-app-shell-v1/app/src/main/java/com/omnifile/storage/SupportedRootRegistry.kt#L47).

## Coverage Ledger

| Surface / path | Touched files or entry points | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| Four-destination shell | `AppShell.kt; MainActivity.kt` | `Intentional I1` | `No fifth Files item; selected semantics and labels present.` | `Pixel hierarchy; 37/37 instrumentation; source trace.` |
| Home source overview | `HomeScreen.kt; HomeViewModel.kt; SupportedRootRegistry.kt` | `Reviewed - no user-visible regression found` | `Real Local/SAF sources and Choose SAF folder remain available.` | `Pixel launch dump; root registry tests; resume refresh trace.` |
| Home → Files → Back | `MainActivity.kt; FilesViewModel.kt; FilesScreen.kt` | `Reviewed - no user-visible regression found` | `Local source opens Files and compact Pixel Back returns Home.` | `Direct Pixel interaction; existing Files instrumentation.` |
| Top-level Search | `MainActivity.kt; SearchScreen.kt; SearchViewModel.kt` | `Intentional I2` | `This device scope and existing Search surface remain available.` | `Pixel hierarchy; Search instrumentation; ThisDevice host tests.` |
| Search → Files → Search | `AppNavigation.kt; MainActivity.kt; FilesViewModel.kt` | `Reviewed - no user-visible regression found` | `Top-level origin and contextual origin are explicit; result/provider identity preserved.` | `AppNavigationStateTest; FilesSearchIntegrationTest; static path trace.` |
| Files contextual Search | `MainActivity.kt; SearchScreen.kt; FilesViewModel.kt` | `Reviewed - no user-visible regression found` | `CurrentFolder scope and preserved Files path are used.` | `Focused host tests; source trace.` |
| ThisDevice multi-root output | `SupportedRootRegistry.kt; SearchViewModel.kt; SearchScreen.kt` | `Intentional I4` | `Local/SAF identity, root labels, partial warnings, and failures are visible.` | `SupportedRootRegistryTest; ThisDeviceSearchTest; Pixel Search screen.` |
| Music | `MusicScreen.kt` | `Intentional I3` | `Truthful no-library/no-playback state; no fake controls.` | `Pixel UI hierarchy; source trace.` |
| Settings | `SettingsScreen.kt` | `Intentional I3` | `Real version/platform/source information; no inert toggles.` | `Pixel UI hierarchy; source trace.` |
| Compact navigation accessibility | `AppShell.kt` | `Reviewed - no user-visible regression found` | `Visible labels and selected destination semantics present.` | `Pixel UI hierarchy; instrumentation.` |
| Edge-to-edge/IME | `Manifest; MainActivity.kt; FilesScreen.kt; SearchScreen.kt; OperationsPanel.kt` | `Reviewed - no user-visible regression found` | `adjustResize and single-owner inset paths are present.` | `edge-to-edge audit; lint; instrumentation.` |
| VS05 Files mutations/transfers | `FilesViewModel.kt; OperationsPanel.kt; storage/operations dependencies` | `Reviewed - no user-visible regression found` | `Existing Android instrumentation remained green.` | `37/37 instrumentation; host failures isolated to unchanged JDK/macOS SecureDirectoryStream paths.` |

## Evidence Appendix

### Behavior Graph Deltas

| ID | Surface | Baseline path | After-change path | Delta | Ledger / finding link |
| --- | --- | --- | --- | --- | --- |
| `B1` | Top-level navigation | `Files-owned ingress -> Files/Search` | `Home/Search/Music/Settings -> detail surfaces` | `Intentional four-destination shell` | `I1` |
| `B2` | Home source opening | `Files source selection -> provider root` | `Home source registry -> Files provider root` | `Home becomes stable source entry; Files implementation reused` | `Reviewed` |
| `B3` | Top-level Search | `Files Search shortcut -> CurrentFolder` | `Search destination -> ThisDevice registry -> bounded Search` | `New top-level owner; engine remains shared` | `I2` |
| `B4` | Contextual Search | `Files -> Search -> Files` | `Files -> contextual Search -> preserved Files stack` | `Origin is explicit and stack-preserving` | `Reviewed` |
| `B5` | ThisDevice roots | `No executable multi-root scope` | `Local/valid SAF -> root resolution -> partial/results` | `New truthful aggregation path` | `I4` |
| `B6` | Music | `No permanent destination` | `Music -> truthful empty state` | `Intentional minimal surface` | `I3` |
| `B7` | Settings | `No permanent destination` | `Settings -> real current information` | `Intentional minimal surface` | `I3` |
| `B8` | System bars/IME | `Files/Search surfaces without permanent shell ownership` | `AppShell + Scaffold/conditional inset ownership` | `Edge-to-edge ownership added; existing surfaces retained` | `Reviewed` |

### Diff Inventory

| File or area | Classification | User-visible path considered |
| --- | --- | --- |
| `AppShell.kt; AppNavigation.kt; MainActivity.kt` | `surface` | `shell, destinations, Back, restoration` |
| `HomeScreen.kt; HomeViewModel.kt` | `surface` | `Home source overview` |
| `SearchModels.kt; SearchViewModel.kt; SearchScreen.kt` | `surface` | `Search scope/results/partial output` |
| `SupportedRootRegistry.kt; SafTreeGrantStore.kt` | `dependency` | `source availability and permissions` |
| `MusicScreen.kt; SettingsScreen.kt` | `surface` | `minimal top-level destinations` |
| `FilesViewModel.kt; FilesScreen.kt; OperationsPanel.kt` | `surface` | `Files detail and existing operations` |
| `app/src/test` VS06 tests | `test-only` | `regression/contract evidence` |
| production/review docs | `docs-only` | `evidence only` |

### Candidate Sweep Log

| Candidate | Decision | Reason |
| --- | --- | --- |
| `Files must remain a permanent bottom destination` | `intentional I1` | `Explicit VS06 product authority says Files is detail.` |
| `Contextual result Back loses Files origin` | `dismissed` | `Fixed in 687d8a1 and covered by focused Files/navigation tests.` |
| `Same-name roots cannot be distinguished` | `dismissed` | `Root labels now flow through SearchHit and are rendered in result context.` |
| `Home remains stale after external grant change` | `dismissed` | `Home refreshes registry on Activity resume.` |
| `JDK/macOS secure mutation test failures are VS06 regressions` | `dismissed` | `They are unchanged baseline LocalStorage/Operation tests; device suite is green.` |
| `Music needs fake player controls` | `intentional I3` | `Playback is explicitly out of scope.` |

### Verification Commands

- `:app:testDebugUnitTest` -> `97 tests; 86 pass, 11 unchanged baseline LocalStorage secure-mutation/transfer failures under JDK 21 macOS`
- focused VS06 host suite -> `18 pass`
- `:app:lintDebug` -> `BUILD SUCCESSFUL`
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest` -> `BUILD SUCCESSFUL`
- direct `adb shell am instrument -w -r com.omnifile.test/androidx.test.runner.AndroidJUnitRunner` -> `37/37 pass, Pixel 7a API 36`
- direct Pixel UI checks -> `Home selected; Local/SAF roots; four labels; This device; Music empty; Settings info; Home→Files→Home`
- `git diff --check 35dcf37..HEAD` -> `clean`

### Supporting Code Links

| ID | Role | Link | Why it matters |
| --- | --- | --- | --- |
| `I1` | `shell` | [`AppShell.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-app-shell-v1/app/src/main/java/com/omnifile/ui/shell/AppShell.kt#L25) | `Permanent destination output.` |
| `I2` | `search` | [`SearchViewModel.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-app-shell-v1/app/src/main/java/com/omnifile/search/SearchViewModel.kt#L99) | `Shared Search contract and generation gate.` |
| `I4` | `roots` | [`SupportedRootRegistry.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-app-shell-v1/app/src/main/java/com/omnifile/storage/SupportedRootRegistry.kt#L47) | `Truthful ThisDevice roots.` |

### Blind Spots

| Area | Risk introduced by the blind spot | What would resolve it |
| --- | --- | --- |
| `Expanded/medium physical rail` | `Rail-specific geometry/insets could differ from compact.` | `Run expanded window/device evidence before adaptive redesign.` |
| `Process-death ActivityScenario` | `Rare SavedState/ViewModel ordering could diverge from static trace.` | `Add lifecycle instrumentation for route/query restoration.` |
| `Real overlapping SAF trees on device` | `Provider-specific containment behavior is not physically exercised in this closure.` | `Run disposable controlled-provider overlapping-tree instrumentation.` |

### Report Self-Check

- `yes` Every touched user-visible or unknown-impact surface appears in Coverage Ledger.
- `yes` No regression findings appear in action sections or index.
- `yes` All visible behavior changes are represented as Intentional or reviewed.
- `yes` Blind spots have concrete next verification steps.
- `yes` Recommendation follows the regression-review skill mapping.
