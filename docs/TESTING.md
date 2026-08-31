# Testing

## Local static tests

```bash
./scripts/check.sh
```

The local suite is intentionally fast and does not require the multi-gigabyte Chromium checkout.

## Overlay fixture tests

`tests/test_apply_overlay.py` builds a miniature fake Brave/Chromium tree containing only the patch anchors. It verifies:

- overlay files are copied to the correct location,
- Java source list entries are added once,
- the TV manifest declarations are added once,
- spatial-navigation startup code is added once,
- applying the overlay twice is idempotent,
- missing/drifted anchors cause a hard failure.

## Cursor model test

`TvCursorState` contains no Android dependencies. `tests/java/TvCursorStateTest.java` checks movement, clamping, resize behavior and centering with the host JDK.

## Real-device matrix

Before tagging a release, test at minimum:

| Area | Test |
| --- | --- |
| Launcher | Starts from Google TV home after cold process kill |
| Focus | D-pad reaches links/buttons/forms on several sites |
| Scrolling | Focus navigation can progress through long pages |
| Keyboard | Omnibox and page input open TV IME |
| Cursor | Pointer moves at repeat speed and remains in bounds |
| Click | Links, buttons and native browser controls receive click |
| Browser UI | Back, forward, tab switcher and menus still work |
| Video | HTML5 playback, fullscreen entry/exit, back behavior |
| Lifecycle | Home -> resume, screen sleep -> resume, process recreation |
| Inputs | Remote, USB/Bluetooth keyboard, USB/Bluetooth mouse |
| Resources | Multi-tab memory pressure does not create crash loops |
| Security | No sandbox/security flags disabled by TV patches |

## Debugging a TV APK

Capture logs:

```bash
adb logcat -c
adb logcat | tee tv-browser.log
```

Find the package/activity:

```bash
adb shell dumpsys package | grep -i brave -n
adb shell dumpsys activity activities | grep -i tihulu -n
```

Inspect crashes and ANRs before declaring a build usable.

## Commit rule

A bug-fix commit should include:

1. a reproducible failure or clear regression,
2. the smallest practical fix,
3. a regression test when the failure can be modeled locally,
4. `./scripts/check.sh` passing,
5. packaged runtime testing when the change affects Android/Chromium behavior.

Do not create speculative “fix” commits solely to keep the repository active.
