# Material 3 Expressive and Adaptive UI Research

Status: COMPLETE — UI technology research only; no Compose implementation  
Last verified: 2026-09-17  
Context: `UI_DESIGN_V1.md` was read as design context and was not modified

## Classification legend

**FACT** documented behavior; **INFERENCE** derived consequence; **RECOMMENDATION** proposed direction; **UNRESOLVED** requires prototype/accessibility testing.

## Current Compose/Material state — September 2026

**FACT:** AndroidX release pages on 2026-09-09 report:

- Compose UI/Foundation stable: **1.12.1**;
- Compose Material 3 stable: **1.4.0**;
- Material 3 alpha: **1.5.0-alpha28**;
- Compose Material3 Adaptive stable: **1.3.0**;
- Adaptive alpha: **1.4.0-alpha02**.

Sources:
- https://developer.android.com/jetpack/androidx/releases/compose
- https://developer.android.com/jetpack/androidx/releases/compose-material3
- https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive

**RECOMMENDATION:** Do not freeze these versions in this research phase. At implementation time, prefer the then-current stable release and isolate any experimental dependency behind a narrow UI boundary.

## Material 3 Expressive

`UI_DESIGN_V1.md` targets a Pixel-native Android 16 / Material 3 Expressive direction while keeping file lists restrained and dense.

### Stable capability

**FACT:** Stable Material 3 already provides the core Material theming/component system, dynamic color, standard components, predictive-back integration for multiple components, shapes, typography and animation primitives.

**INFERENCE:** Much of the desired expressive visual identity can be built on stable APIs through composition, shape, typography, container transforms and conventional animation without requiring the newest experimental Expressive API surface.

### Experimental/alpha capability

**FACT:** The active Material 3 1.5.0 line is alpha as of this research date. APIs introduced/changed only in that line are not stable implementation authority.

**RECOMMENDATION:** Do not adopt 1.5 alpha solely to obtain a particular Expressive motion API. Prototype it separately; the baseline UI should remain viable on stable Material3 1.4.x.

## Motion schemes

Material 3's newer motion-scheme APIs are evolving in alpha releases.

Reference:
- https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme
- https://developer.android.com/jetpack/androidx/releases/compose-material3

**RECOMMENDATION:** Preserve the design requirement (“restrained file navigation, expressive Home/player/major transitions”) as a product-level motion policy rather than binding it to an alpha API. A future implementation can map that policy to stable transitions first and opt into a stable MotionScheme when available.

## Dynamic Color

**FACT:** Material 3 includes Material You dynamic-color support on compatible Android versions.

Reference:
- https://m3.material.io/styles/color/dynamic-color/overview
- https://developer.android.com/develop/ui/compose/designsystems/material3

**RECOMMENDATION:** Dynamic color can be the stable default where supported, with light/dark themes. Album-art-derived color should remain scoped to playback surfaces as already specified by `UI_DESIGN_V1.md` rather than recoloring the file manager continuously.

## Android 16 edge-to-edge

**FACT:** For apps targeting Android 16/API 36 on Android 16, the old edge-to-edge opt-out is disabled. Edge-to-edge must be handled correctly.

Source:
- https://developer.android.com/about/versions/16/behavior-changes-16

**RECOMMENDATION:** Treat window insets/status/navigation bars as baseline layout inputs from the beginning. Do not design fixed top/bottom paddings that assume legacy system-bar layout.

## Predictive back

**FACT:** Predictive back/system back animations are enabled by default on newer Android versions, and targeting Android 16 changes legacy back handling: `onBackPressed`/raw back-key assumptions are no longer a suitable architecture. Navigation Compose/Material components have predictive-back support when using supported APIs.

Sources:
- https://developer.android.com/develop/ui/compose/system/predictive-back-setup
- https://developer.android.com/about/versions/16/behavior-changes-16

**RECOMMENDATION:** Navigation, archive virtual folders, full-player transitions, dialogs/sheets and selection mode should use modern back APIs from the first implementation phase. Do not bolt predictive back on after custom navigation has proliferated.

## Adaptive layout

### Stable Material3 Adaptive

**FACT:** Material3 Adaptive 1.3.0 is stable as of 2026-08-12 and includes stable adaptive layout/navigation functionality. Official guidance provides `ListDetailPaneScaffold`, supporting-pane patterns and navigation adaptation.

Sources:
- https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
- https://developer.android.com/develop/adaptive-apps/guides/list-detail
- https://developer.android.com/develop/adaptive-apps/guides/canonical-layouts

**RECOMMENDATION:** A stable adaptive foundation is available; no alpha dependency is inherently required to implement phone single-pane + tablet/two-pane file management.

### Navigation rail / navigation suite

Official adaptive guidance recommends changing navigation presentation according to available window space, including navigation bar/rail patterns.

Reference:
- https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation
- https://developer.android.com/develop/adaptive-apps/guides/adaptive-dos-and-donts

This aligns with the existing UI design: bottom navigation on phones and NavigationRail on larger/landscape layouts.

### Two-pane file management

**RECOMMENDATION:** The likely large-screen arrangement is not exactly mail-style list/detail: a file manager may use left source/tree/folder context plus right directory content. Stable adaptive pane scaffolds are still useful building blocks, but prototype whether a custom two-pane scaffold offers better keyboard/folder behavior than forcing the canonical list-detail semantics.

## Foldables/posture

Material3 Adaptive exposes window/posture information and pane layouts.

**RECOMMENDATION:** Avoid device-name branching. Adapt by available window/posture. Hinge areas should not obscure selection controls, player transport or drag/drop targets.

## Landscape

Phone landscape may have a wide but short window. Do not automatically assume “wide = tablet two-pane”; consider height and minimum viable row/player layout.

**PROTOTYPE REQUIRED:** small phone landscape with IME/keyboard shown and with the mini player active.

## Accessibility

### Touch targets

**FACT:** Android/Compose guidance recommends at least a 48dp accessible interaction target for touch controls. Material components generally implement accessible defaults, but custom controls must preserve them.

Sources:
- https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- https://developer.android.com/guide/topics/ui/accessibility/apps

### Large font

**RECOMMENDATION:** Test high font scales rather than shrinking text. Dense file rows may need to grow vertically or wrap secondary metadata. Essential actions must remain reachable at 200%+ font scale where the platform configuration permits.

### Semantics / screen reader

- icon-only buttons require meaningful labels;
- selection state must be exposed semantically;
- destructive state cannot be communicated by color/icon alone;
- long file names need accessible full-name exposure even when visually ellipsized;
- storage progress must include textual/semantic values.

### High contrast

**RECOMMENDATION:** Do not rely on subtle tonal contrast for selection/root warnings. Validate Material dynamic schemes and custom album-art colors against contrast requirements.

### Reduced motion

**RECOMMENDATION:** Decorative expressive transitions should be suppressible/reduced according to platform/user accessibility preferences where available. Core navigation must never require motion to understand state.

## Keyboard support

A power-user file manager benefits unusually strongly from keyboard access:

- arrow traversal;
- Enter/open;
- Back/Escape/up behavior;
- Ctrl/Cmd-like shortcuts where Android conventions support them;
- Ctrl+A select all;
- copy/cut/paste shortcuts where semantically safe;
- Delete with confirmation policy;
- search focus;
- Tab traversal between panes/toolbars.

Reference:
- https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/commands
- https://developer.android.com/develop/ui/compose/touch-input/input-compatibility-on-large-screens

**RECOMMENDATION:** Keyboard navigation should be included in interaction architecture, not only accessibility cleanup.

## Mouse/trackpad support

Official large-screen guidance calls for mouse wheel/trackpad scrolling, hover, right-click/context behavior and precision input consideration.

Source:
- https://developer.android.com/develop/ui/compose/touch-input/input-compatibility-on-large-screens

**RECOMMENDATION:** Later desktop/tablet UX should support right-click context menus and multi-selection modifiers where Android input APIs allow, while preserving touch-first behavior.

## Drag and drop

Potential high-value future feature:
- drag file between panes/folders;
- external app drag/drop on large screens.

**RECOMMENDATION:** Postpone implementation, but do not make the provider/operation model depend exclusively on button-triggered copy/move. A drag should eventually create the same Operation Manager command as another UI action.

## Dense file lists vs expressive surfaces

Existing `UI_DESIGN_V1.md` is technically consistent with platform capability:

- Home/player can use larger expressive containers and stronger motion;
- Files/archive lists can use restrained Lazy layouts;
- selection mode can use a morphing top bar without animating every row heavily;
- adaptive pane layouts can expand density without duplicating screen-specific business logic.

**RECOMMENDATION:** Keep that separation; expressive design should not increase latency or reduce visible file density during repeated navigation.

## Stable vs experimental matrix

| Requirement | Stable path as of 2026-09 | Experimental needed? |
|---|---|---|
| Material3 components/theme | Yes, M3 1.4.0 stable | No |
| Dynamic Color | Yes | No |
| edge-to-edge | Platform/stable Compose support | No |
| predictive back | Stable modern navigation/material path | No for baseline |
| NavigationRail/adaptive navigation | Yes | No |
| list-detail/two-pane building blocks | Adaptive 1.3.0 stable | No |
| accessibility semantics/touch targets | Yes | No |
| keyboard/mouse | Yes | No |
| latest MotionScheme/brand-new Expressive APIs | evolving | **Potentially yes** |
| newest adaptive 1.4 features | alpha | Yes, only if specifically justified |

## UI performance requirements

- lazy lists/grids for directory content;
- stable entry keys from provider identity;
- avoid synchronous metadata/thumbnail I/O during composition;
- thumbnails loaded/cancelled according to viewport;
- sorting/index work off UI thread;
- operation progress updates rate-limited enough to avoid recomposition storms;
- animation must not compete with large copy/decompression CPU/I/O.

## Test requirements

- phone portrait/landscape;
- tablet and foldable postures;
- split screen/freeform resizing;
- edge-to-edge with gesture and 3-button navigation;
- predictive back through nested folder/archive/player states;
- large fonts and display scaling;
- TalkBack/switch access/manual semantics checks;
- keyboard, mouse and trackpad;
- 10k+ visible-directory model with active operation progress;
- reduced motion/high contrast/dark/light/dynamic color.

## Recommendation summary

**Strong:** stable Material3 + stable Material3 Adaptive can cover the baseline; alpha is not required merely to call the UI “Expressive.”  
**Strong:** edge-to-edge and predictive back are baseline Android 16 requirements.  
**Strong:** adaptive/window-based layouts and keyboard/mouse support belong in architecture from the start.  
**Plausible:** isolate a future stable/experimental expressive motion layer for signature Home/player transitions.  
**Postpone:** dependency versions, exact navigation library generation and alpha Expressive APIs until implementation begins.
