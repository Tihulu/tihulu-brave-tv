# Upstream Tracking

The TV layer deliberately touches a small set of upstream anchors. Chromium/Brave rebases can move or redesign them.

## Watched files

- `brave/android/brave_java_sources.gni`
- `brave/android/java/org/chromium/chrome/browser/BraveApplicationImplBase.java`
- `chrome/android/java/AndroidManifest.xml`
- Chromium spatial-navigation switch and Blink setting

## Safe update workflow

```bash
cd .work/brave-browser/src/brave
git pull
pnpm run sync --target_os=android
cd ../../../..
python3 scripts/apply_overlay.py .work/brave-browser
./scripts/check.sh
```

If the patcher fails, inspect upstream first. Do not loosen anchors until you understand why they changed.

## Things to re-check after major Chromium updates

- `ChromeTabbedActivity` remains subclassable and compatible with the TV activity.
- Brave's bytecode/activity inheritance modifications still apply to the tabbed activity.
- Chromium still exposes the `enable-spatial-navigation` switch and maps it to Blink web preferences.
- `MotionEvent` mouse routing still reaches page content and native browser controls.
- Chrome's `Ctrl+L` keyboard shortcut still focuses the omnibox on Android.
- Manifest Jinja structure still contains the same application/activity blocks.
