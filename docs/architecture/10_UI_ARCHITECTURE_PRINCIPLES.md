# Architecture V1 — UI Architecture Principles

Status: PRODUCT/UI PRINCIPLES FROZEN / COMPOSE AND MATERIAL VERSIONS UNFROZEN

Source authority: `UI_DESIGN_V1.md`, `docs/research/10_UI_M3_EXPRESSIVE_ADAPTIVE.md`, and research synthesis SHA `b03a2ea99f24206f847f513fa4106e90268f3fc4`.

## Material 3 baseline — `ACCEPTED`

Material 3 is the product UI baseline.

This freezes a design-system direction, not an exact Compose/Material artifact/version.

## Stable-first Android UI APIs — `ACCEPTED`

The baseline implementation must be viable on stable AndroidX/Material APIs available at implementation time.

Requiring an alpha dependency merely to call the UI “Expressive” is `REJECTED`.

Newer expressive APIs may later be evaluated when they provide material product value.

Exact Material3/Adaptive/Navigation versions — `DEFERRED`.

## Expressive intensity by surface — `ACCEPTED`

The product keeps the distinction already established by `UI_DESIGN_V1.md`:

- Home, player, major transitions, dialogs, empty/completion states: stronger expressive treatment;
- dense file lists, root browsing, multi-select, search results, repeated navigation: restrained and information-dense.

Expressive styling must not reduce visible file density or make repeated file-management actions feel slower.

## Adaptive navigation — `ACCEPTED`

Navigation adapts to available window size rather than hard-coded device names.

Conceptual direction:

- phone portrait: bottom navigation / single-pane browsing;
- wider layouts: navigation rail or equivalent adaptive navigation;
- large window: multi-pane arrangements when useful.

Exact scaffold/navigation implementation — `DEFERRED`.

## Future two-pane file browsing — `ACCEPTED`

Architecture must allow a large-screen file-manager layout with a left source/tree/folder-context pane and a right current-directory/content pane.

The exact split, pane scaffold, and selection/navigation behavior require usability validation.

Stable adaptive building blocks are sufficient as a baseline; a custom two-pane arrangement may later be justified.

## Edge-to-edge — `ACCEPTED`

Insets/status/navigation-bar handling is a baseline layout concern. The UI must not be designed around fixed legacy system-bar padding assumptions.

Exact implementation follows the target SDK/platform state selected later.

## Predictive-back-aware navigation — `ACCEPTED`

Back/navigation architecture must be compatible with modern Android predictive-back behavior from the first implementation phase.

Raw legacy back-key assumptions as the long-term navigation architecture are `REJECTED`.

This applies to folder navigation, archive virtual folders, player transitions, sheets/dialogs, and selection mode.

## Accessibility and large-font support — `ACCEPTED`

Architecture must preserve:

- accessible touch targets;
- screen-reader semantics/content descriptions;
- non-color-only destructive warnings;
- large-font layouts without hiding essential actions;
- sufficient contrast for dynamic/player/root warning surfaces;
- reduced-motion behavior where platform/user preferences permit.

Testing at high font scales is required; shrinking text to preserve fixed row height is not the default solution.

## Keyboard and mouse support — `ACCEPTED` as a tablet/desktop-class input requirement

Interaction architecture must not assume touch is the only input.

Future large-screen support should allow:

- keyboard focus/navigation;
- selection modifiers where platform APIs support them;
- common file-manager shortcuts where appropriate;
- mouse/trackpad context menus;
- scroll-wheel/trackpad navigation.

Exact shortcut map and implementation phase — `DEFERRED`.

## Drag and drop — `DEFERRED`

Drag-and-drop implementation is not required by this principle freeze, but future drag operations must map to the same capability-aware Operation Manager commands as button/menu actions rather than bypassing operation safety.

Reopen during large-screen interaction work.

## Capability-aware actions — `ACCEPTED`

The UI must not present unsupported storage actions as if they are universally available.

Examples:

- no Seek-dependent affordance when the source cannot support required playback behavior;
- no Rename/Move/Append/POSIX action when the provider does not expose it;
- root-only actions appear only in an authorized root context;
- provider-specific destructive guarantees/warnings remain honest.

Disabled-vs-hidden exact UX is `DEFERRED` per feature, but false universal affordances are prohibited.

## Paths and identity presentation — `ACCEPTED`

Breadcrumbs/paths remain visible and useful for power users as specified by `UI_DESIGN_V1.md`, but presentation paths are not treated as universal object identity.

The UI may show a URI/provider location when appropriate, while destructive operations continue to use provider-scoped identity.

## Root/system warnings — `ACCEPTED`

Protected/system-path destructive operations receive stronger warnings than ordinary user-storage operations. Root elevation remains explicit and opt-in.

Exact wording/color/confirmation thresholds — `DEFERRED`.

## Player theming — `ACCEPTED`

Album-art-derived colors may influence playback surfaces without continuously recoloring the entire file manager.

## Performance-sensitive UI — `ACCEPTED`

Directory rendering, metadata, thumbnails, search updates, and selection must support incremental/bounded work. Expressive motion is subordinate to interaction latency and file-density goals.

## Experimental/newer expressive APIs — `DEFERRED`

Reopen when:

- an API reaches suitable stability; or
- a specific signature Home/player transition cannot be achieved acceptably with stable primitives; and
- a focused UI PoC demonstrates value without unacceptable stability/accessibility/performance cost.

No alpha Material dependency is selected in this phase.

## `UI_DESIGN_V1.md` relationship

`UI_DESIGN_V1.md` remains preserved as product design authority. This document constrains its implementation interpretation:

- “Material 3 Expressive” means the product's visual/motion policy, not mandatory adoption of a particular experimental API;
- root remains additive;
- archive browsing remains virtual where possible;
- FLACtify behavior is integrated conceptually without transplanting its monolithic ViewModel;
- adaptive/two-pane intentions remain valid without freezing a specific Compose API.

No redesign of `UI_DESIGN_V1.md` is performed here.
