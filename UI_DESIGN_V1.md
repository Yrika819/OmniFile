# File Manager UI Design V1

Status: Draft / design authority candidate
Target: Android 12+ (primary: Android 16)
UI system: Jetpack Compose + Material 3 Expressive
Primary device class: phone
Secondary: tablet / landscape adaptive layout

## 1. Product UI principles

1. File operations come first. Visual expression must never reduce information density or slow repetitive navigation.
2. Material 3 Expressive is used strongly on Home, transitions, major actions, player surfaces, dialogs and empty states.
3. File lists, root filesystem views, multi-select and search results remain comparatively restrained and dense.
4. Standard mode is fully functional without root. Root is an additive capability, not a separate app mode.
5. Paths are visible. The app is intended to be comfortable for power users without becoming hostile to normal users.
6. Audio playback is a first-class feature. FLACtify is integrated as the internal player rather than launched as an external app.
7. Destructive operations in system/root paths receive stronger visual warning than ordinary user-storage operations.

## 2. Primary navigation

Phone bottom navigation:

- Home
- Files
- Music

Search is contextual and appears in top app bars rather than as a permanent fourth destination.
Settings opens from the Home top bar and overflow menus.

Tablet / landscape:

- Bottom navigation becomes NavigationRail.
- Large width may use two-pane browsing: folder tree / source pane on the left, contents on the right.

## 3. Home screen

Purpose: quick access to storage, categories, recent items and current playback.

Wireframe:

```text
┌──────────────────────────────────┐
│ Files                       🔍 ⚙ │
│                                  │
│ Storage                          │
│ ╭──────────────────────────────╮ │
│ │ Internal storage             │ │
│ │ 82.4 GB / 128 GB             │ │
│ │ █████████████░░░             │ │
│ │                       ›      │ │
│ ╰──────────────────────────────╯ │
│                                  │
│ ╭─────────────╮ ╭─────────────╮ │
│ │ 🎵 Music    │ │ 🖼 Images   │ │
│ │ 1,284       │ │ 3,921       │ │
│ ╰─────────────╯ ╰─────────────╯ │
│ ╭─────────────╮ ╭─────────────╮ │
│ │ 📦 Archives │ │ 📄 Docs     │ │
│ │ 31          │ │ 286         │ │
│ ╰─────────────╯ ╰─────────────╯ │
│                                  │
│ Recent                           │
│ album.flac                       │
│ backup.7z                        │
│ notes.pdf                        │
│                                  │
│ ╭──────────────────────────────╮ │
│ │ artwork  Track title    ▶  ⏭ │ │
│ │          Artist              │ │
│ ╰──────────────────────────────╯ │
│                                  │
│   Home         Files       Music │
└──────────────────────────────────┘
```

Behavior:

- Internal storage card is the primary hero card.
- SD card and USB appear as additional storage cards only when available.
- Root appears as a separate source card only when root support is enabled and authorized.
- Category cards: Music, Images, Videos, Documents, APK, Archives.
- Recent list should remain compact rather than using large cards.
- Mini player appears only when a media session has an active/current item.

Expressive level: HIGH.

## 4. Files screen

Purpose: fast, predictable filesystem navigation.

Wireframe:

```text
┌──────────────────────────────────┐
│ ‹ Download                  🔍 ⋮ │
│ Internal > Download > ROMs       │
├──────────────────────────────────┤
│ 📁 Firmware                  ⋮   │
│    18 items                      │
│                                  │
│ 📁 Pokémon                   ⋮   │
│    42 items                      │
│                                  │
│ 📦 backup.7z                 ⋮   │
│    2.41 GB · 7Z                  │
│                                  │
│ 🎵 music.flac                ⋮   │
│    86.4 MB · FLAC · 24/96        │
│                                  │
│ 📄 notes.txt                 ⋮   │
│    4.2 KB · TXT                  │
│                                  │
│                         ＋       │
│   Home         Files       Music │
└──────────────────────────────────┘
```

Top app bar:

- Back/up
- Current folder name
- Search
- Overflow

Breadcrumb:

- Visible by default.
- Horizontally scrollable when deep.
- Every segment is tappable.
- Examples:
  - Internal > Download > ROMs
  - SD card > Music > FLAC
  - Root > data > user > 0

List item metadata:

Folder:
- item count when available
- optional last modified date

File:
- size
- type/extension
- media-specific compact metadata where useful

View modes:

- Compact list (default)
- Comfortable list
- Grid

Sort:

- Name
- Date modified
- Size
- Type
- Ascending / descending
- Folders first toggle

FAB:

- New folder
- New text file (later phase if desired)
- Compress selected / import actions only when contextually relevant

Expressive level: LOW to MEDIUM.

## 5. Multi-select mode

Trigger:

- Long press a file/folder
- Selection checkboxes then become available

Wireframe:

```text
┌──────────────────────────────────┐
│ ×   3 selected       ☆  📤  🗑 ⋮│
├──────────────────────────────────┤
│ ✓ 📁 Firmware                    │
│   📁 Pokémon                     │
│ ✓ 📦 backup.7z                   │
│ ✓ 🎵 music.flac                  │
│   📄 notes.txt                   │
└──────────────────────────────────┘
```

Primary actions:

- Copy
- Move
- Share
- Delete
- Favorite
- More

More:

- Compress
- Properties
- Rename (single selection only)
- Open with
- Root-specific operations when applicable

The top bar morphs into selection mode using expressive container transition, but list rows themselves remain restrained.

Expressive level: MEDIUM.

## 6. Archive browser

Archives are browsed as virtual folders whenever the format allows listing.

Wireframe:

```text
┌──────────────────────────────────┐
│ ‹ backup.7z                 ⤓ ⋮ │
│ backup.7z > ROMs                 │
├──────────────────────────────────┤
│ 📁 BIOS                          │
│ 📁 Saves                         │
│ 📄 readme.txt                    │
│ 🎮 game.nds                      │
│                                  │
│ ╭──────────────────────────────╮ │
│ │ Extract                     │ │
│ ╰──────────────────────────────╯ │
└──────────────────────────────────┘
```

Actions:

- Browse without extracting whole archive
- Extract selected items
- Extract all
- Test archive where library support allows
- Password prompt for encrypted archive
- Show compression method, packed size, original size where available

Supported target families for V1/V1.x:

- ZIP / ZIP64 / encrypted ZIP
- 7z
- RAR / RAR5
- TAR
- TAR.GZ / GZ
- TAR.BZ2 / BZ2
- TAR.XZ / XZ
- TAR.ZST / ZSTD

Expressive level: LOW to MEDIUM.

## 7. FLACtify mini player

Appears above bottom navigation when there is a current item.

```text
╭────────────────────────────────╮
│ [art] Track title        ▶   ⏭ │
│       Artist                   │
╰────────────────────────────────╯
```

Behavior:

- Tap body: open full FLACtify player.
- Tap play/pause: immediate action.
- Tap next: next item.
- Swipe down/right may dismiss visual surface only if playback is stopped; playing sessions remain represented.
- Theme color may be derived from artwork but should affect the player surface, not recolor the entire file manager on every track change.

Expressive level: HIGH.

## 8. Full music player

The full player is visually allowed to diverge more strongly from the file manager shell while remaining within the same Material 3 theme family.

Reuse targets from FLACtify:

- PlaybackService
- PlaybackController
- metadata extraction
- library scanning logic
- PlayerScreen concepts
- playlists / favorites where appropriate

Do not directly transplant the monolithic PlayerViewModel. Refactor player state into a feature/module boundary first.

The transition Mini Player -> Full Player should be one of the signature expressive interactions of the app.

Expressive level: VERY HIGH.

## 9. Root UI

Root is additive and opt-in.

Settings:

```text
Advanced access

Root access                         OFF
Use superuser privileges to access
system and app-private filesystem
locations.
```

Activation flow:

1. Detect root capability.
2. User explicitly enables Root access.
3. Request `su` authorization.
4. Enable RootStorageProvider only after success.
5. Failure leaves StandardStorageProvider fully operational.

Root source:

```text
Root
/
├─ data
├─ system
├─ vendor
├─ product
└─ ...
```

System-path destructive action warning:

```text
System area

/data/user/0/...

Changing or deleting this item may prevent
an app or Android from working correctly.

[Cancel] [Continue]
```

Warnings should be stronger for protected/system paths but should not make normal user-storage operations annoying.

Root-only property additions:

- owner
- group
- Unix permissions
- symlink target
- SELinux context later if useful
- mount / filesystem information later

Root-only actions planned:

- chmod
- chown
- symlink operations
- elevated copy/move/delete

Expressive level: LOW for browsing, MEDIUM/HIGH for warnings.

## 10. Search

Search is scoped by entry point.

From Home:
- global/shared-storage search

From Files:
- current folder by default
- optional recursive search toggle

Filters:

- Type
- Size
- Modified date
- Storage/source

Results use the compact file-list style and display enough path context to distinguish identical names.

## 11. File properties sheet

Use a large bottom sheet on phones, side sheet/dialog on wider layouts.

Contents:

- icon / thumbnail
- filename
- full path / URI
- MIME/type
- size
- modified time
- created time when available
- checksum action (SHA-256 initially)
- permissions/root metadata when available
- media metadata for audio/video/images where useful

## 12. File operation queue

Long-running operations are represented globally.

Example:

```text
Copying 14 items
14.2 GB / 31.5 GB
██████████░░░░░ 45%
82 MB/s

[Pause] [Cancel]
```

Operations:

- Copy
- Move
- Delete when lengthy
- Extract
- Compress
- Network transfer later

The operation queue is accessible from a compact progress indicator in the app shell.

## 13. Motion rules

Use two motion personalities.

Standard / restrained:

- Folder-to-folder navigation
- Opening overflow menus
- Sorting
- Search result updates
- Repeated list interactions

Expressive:

- Home cards
- Mini Player -> Full Player
- Selection top-bar morph
- Important dialogs
- Empty-state transitions
- Completion states

Rule: repeated file-management actions must never feel slower because of animation.

## 14. Color and theming

Default:

- Material You dynamic color where supported.
- Light and dark modes.
- User override later if desired.

FLACtify surfaces:

- album-art-derived color is allowed inside player surfaces.
- file browser does not continuously recolor itself based on the active track.

Root/system areas:

- do not permanently tint the whole UI red.
- use warning colors only for destructive/system-sensitive actions.

## 15. Shape and density

Home:
- large expressive rounded containers
- generous spacing

File list:
- smaller shape radius
- dense rows
- no card around every ordinary file row

Archive list:
- same density language as file browser

Player:
- strongest use of large containers and expressive shapes

## 16. Adaptive layout

Phone portrait:
- bottom navigation
- single-pane browser

Phone landscape / small tablet:
- navigation rail optional
- content width constrained for readability

Tablet / large screen:
- NavigationRail
- optional two-pane file browser
- left: storage/source/folder context
- right: current folder contents
- player can use expanded two-column layout

## 17. Accessibility and ergonomics

- Minimum touch targets follow Material guidance.
- Icons are not the sole indicator for destructive actions.
- Long filenames support ellipsis and accessible full-name exposure.
- Dynamic type/font scaling must not break list actions.
- Motion reduction setting should be respected.
- Content descriptions for icon-only controls.

## 18. Proposed implementation order

UI Phase A — Shell
- Theme
- Navigation
- Home
- Files mock screen
- responsive scaffold

UI Phase B — Browser interaction
- breadcrumbs
- list/grid modes
- selection mode
- sorting
- contextual actions

UI Phase C — Archive
- virtual archive browser
- extraction flows
- password dialogs

UI Phase D — Player
- mini player
- FLACtify core refactor/import
- full player transition

UI Phase E — Root
- root source
- root-aware properties/actions
- system-path warnings

## 19. V1 visual identity decision

Target identity:

"Android 16 / Pixel-native Material 3 Expressive, with more information density than Files by Google and less visual clutter than a traditional power-user file manager. File browsing stays fast and restrained; Home and FLACtify playback carry the expressive personality."

This document should be treated as the starting design authority before implementation. Changes are expected, but major navigation, density and interaction changes should be made deliberately rather than accidentally during coding.
