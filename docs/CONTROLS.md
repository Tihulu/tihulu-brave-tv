# TV Controls

## D-pad mode

D-pad mode is the default.

- Arrow keys are handled by Chromium/Blink spatial navigation.
- OK/Enter activates the selected target.
- Back remains the normal browser/Android back action.
- Text controls use the normal Android input-method flow.

## Cursor mode

- Arrow keys move a virtual cursor.
- OK/Enter sends a primary-button click.
- Back remains browser/Android back.
- Cursor position is clamped to the current window bounds.
- Cursor is re-centered after large layout-size changes.

## Open TV Controls

TV Controls can be opened from the always-visible **TV Controls** action in the TV browser bar. The app also recognizes these remote keys when available:

- Menu
- Info
- Guide

A hardware keyboard can also use `Ctrl+Shift+M`. Long-pressing OK focuses the TV browser bar on minimal remotes.

## Keyboard

Select **Search / Address / Keyboard** in TV Controls to focus the omnibox. The project emits Chrome's standard `Ctrl+L` shortcut rather than depending on a private ToolbarManager method whose Java signature can change between Chromium revisions.

On-page text fields are handled by Chromium normally. If the TV has Gboard or another TV IME installed, activating an editable field should bring it up.

## Check for updates

Select **Check for updates** in TV Controls to query the latest GitHub Release for `Tihulu/tihulu-brave-tv`.

If a compatible APK asset is available, the app downloads it with Android Download Manager and opens the Android package installer. The updater does not silently install packages. Android still requires user confirmation and may ask the user to allow Tihulu TV Browser to install apps from this source.

An APK can update the installed app only when it uses the same application identity and signing key as the installed build. See [`UPDATES.md`](UPDATES.md) before publishing release APKs.
