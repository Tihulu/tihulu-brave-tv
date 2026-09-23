# TV Controls

## D-pad mode

D-pad is the first-run default. Arrows use Chromium/Blink spatial navigation, OK activates the focused target and Back keeps the normal browser/Android behavior.

## Cursor mode

Arrows move the pointer and OK clicks at its position. Place the pointer over a nested scroll area before switching to Scroll mode. Pointer position is clamped to the window and is re-centered after a large layout change.

## Scroll mode

Arrows emit native mouse-wheel events at the pointer position: Up/Down scroll vertically; Left/Right scroll horizontally when the page supports it. Repeated events are limited to one every 80 ms. OK opens the toolbar; it does not click the page in this mode. Switch back to Cursor to reposition or click, or to D-pad to select links.

The Mode button cycles D-pad → Cursor → Scroll → D-pad. The selected mode is saved across activity recreation and app restarts. Unknown stored values fall back to D-pad. This is a global preference, not a per-site preference.

## Open browser controls

In D-pad or Cursor mode, hold Up and release. In Scroll mode, press OK. Menu, Info, Guide and Ctrl+Shift+M also open the toolbar when available. Back or the Close button dismisses it. Down moves between toolbar rows.

The toolbar is on demand, not permanently attached to Chromium's content view. Native text editing and fullscreen player controls retain their own key handling.

## Tabs

Tabs opens a text overview of the active regular or private tab model. The current tab is marked. Earlier tabs / More tabs page through groups of eight without thumbnail allocation. New tab, Close current tab, Previous tab and Next tab remain available.

Selection resolves a tab's stable ID at click time, so reordered/closed tabs cannot select an unrelated old index. A changed regular/private model cancels stale selection.

## Keyboard and Shields

Search / Address focuses Chromium's omnibox through Ctrl+L. Native text inputs keep arrows and OK; Web page inputs continue to use Chromium's IME handling.

Shields offers per-site Standard, Aggressive, Off and global defaults through Brave's native settings. The affected page reloads after a change. These controls do not guarantee blocking every video advertisement.

## Updates

Check for Tihulu updates queries this repository's latest GitHub Release. If an ABI-compatible APK exists, Android Download Manager downloads it and the package installer requests confirmation. Updates require the same package identity and signing key; see [UPDATES.md](UPDATES.md).
