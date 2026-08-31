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

The first MVP recognizes these remote keys:

- Menu
- Info
- Guide

A hardware keyboard can also use `Ctrl+Shift+M`.

Remote layouts differ between TV manufacturers. A later milestone will integrate a visible TV Controls entry directly into the browser toolbar/menu so minimal remotes do not depend on optional hardware keys.

## Keyboard

Select **Address / Keyboard** in TV Controls to focus the omnibox. The project emits Chrome's standard `Ctrl+L` shortcut rather than depending on a private ToolbarManager method whose Java signature can change between Chromium revisions.

On-page text fields are handled by Chromium normally. If the TV has Gboard or another TV IME installed, activating an editable field should bring it up.
