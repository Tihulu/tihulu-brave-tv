# Architecture

## Design goals

1. Keep Brave/Chromium security boundaries intact.
2. Minimize the size of the long-lived fork.
3. Prefer Chromium functionality over webpage JavaScript hacks.
4. Isolate TV-specific input so normal Android mouse/keyboard behavior is unaffected.
5. Fail closed on upstream patch drift.

## Overlay model

The repository contains only the TV-specific source and patching logic. `scripts/apply_overlay.py` modifies an initialized Brave tree in place.

Patched targets:

- `src/brave/android/brave_java_sources.gni`
  - Adds the TV Java classes to Brave's Android Java source list.
- `src/brave/android/java/org/chromium/chrome/browser/BraveApplicationImplBase.java`
  - On actual television UI mode, appends Chromium's `enable-spatial-navigation` command-line switch before browser activities create page WebContents.
- `src/chrome/android/java/AndroidManifest.xml`
  - Declares Android TV/Leanback support and adds the TV launcher activity.
- `src/chrome/android/java/res/drawable-nodpi/tihulu_tv_banner.png`
  - Independent project banner used by the TV launcher entry.

Overlay source copied into Brave:

- `TvBraveActivity`
- `TvNavigationMode`
- `TvCursorState`
- `TvCursorOverlay`
- `TvMouseDispatcher`
- `TvControlPanel`

## D-pad mode

D-pad arrow events are deliberately not translated into DOM JavaScript. They continue through Chrome's normal input dispatch. The project enables Blink spatial navigation on TV, allowing Chromium to choose focus candidates.

This avoids common JavaScript polyfill problems:

- page CSP conflicts,
- shadow-DOM blind spots,
- cross-origin iframe access restrictions,
- custom elements with unusual click semantics,
- sites replacing injected state,
- unnecessary renderer-side scripting.

## Cursor mode

Cursor mode intercepts only D-pad directional keys and OK/Enter. It maintains a pointer position in `TvCursorState`, renders a non-interactive overlay and dispatches Android mouse-style hover/click `MotionEvent`s to the browser window.

The implementation is intentionally isolated behind `TvMouseDispatcher`, because OEM Android TV input stacks and future Chromium changes may require routing adjustments.

## TV Controls

The controls dialog is implemented with standard Android focusable widgets. It allows switching modes, focusing the address bar through Chrome's standard `Ctrl+L` keyboard shortcut, and re-centering the cursor.

The dialog can be opened with common TV `MENU`, `INFO`, or `GUIDE` keys. A future TV-toolbar integration should provide a guaranteed visual entry point on minimal remotes that expose none of those keys.

## Why a subclass of ChromeTabbedActivity?

Brave transforms Chromium's `ChromeTabbedActivity` during its Android build so the tabbed activity uses Brave's activity behavior. The TV activity subclasses the tabbed activity rather than copying the full browser activity. This keeps the new code small and lets upstream Brave continue owning normal browser lifecycle, tabs, Shields and browser UI.

## Release stability rule

Passing static CI is necessary but insufficient. Changes that touch input routing, browser lifecycle or Chromium patch anchors require a packaged APK and real-TV test before release.
