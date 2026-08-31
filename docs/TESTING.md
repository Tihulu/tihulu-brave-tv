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
- the TV memory profile is wired through the generated Java source list,
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

## Architecture-specific runtime checks

Do not treat an ARM64 smoke test as proof that an ARM32 APK is safe, or vice versa. Native-code layout, address-space pressure and device firmware behavior differ.

### 32-bit ARM / `armeabi-v7a`

First confirm the installed target really is 32-bit:

```bash
adb shell getprop ro.product.cpu.abilist
```

A 32-bit-only box commonly reports:

```text
armeabi-v7a,armeabi
```

Then verify:

1. Install the `arm` APK; an `arm64` APK must not be substituted by the updater.
2. Open **About Tihulu TV Browser** and confirm `Runtime: 32-bit · low-memory profile`.
3. Cold-launch in D-pad mode and confirm normal browsing works without first entering Cursor mode.
4. Switch to Cursor mode and verify the lazy-created pointer behaves normally.
5. Open one normal site, then progressively open several tabs while watching for renderer reloads, ANRs or crash loops.
6. Play a 1080p HTML5 video, enter/leave fullscreen and then switch to another tab.
7. Send the app Home -> resume, then repeat after screen sleep.
8. Close unused tabs after deliberate memory pressure and confirm the browser recovers instead of remaining in a crash/reload loop.
9. Capture `adb logcat` around any `OutOfMemoryError`, linker failure, renderer crash, LMKD kill or repeated tab reload.
10. Do not solve an ARM32 failure by adding `--single-process`, `--process-per-site`, disabling sandbox/Site Isolation, or forcing software rendering.

On a particularly small 1–2 GB TV box, repeat the test after reboot so other apps do not distort the baseline.

Useful diagnostics:

```bash
adb shell cat /proc/meminfo | head -n 5
adb shell getprop ro.config.low_ram
adb shell dumpsys meminfo | head -n 40
```

### 64-bit ARM / `arm64-v8a`

Confirm `arm64-v8a` is present in `ro.product.cpu.abilist`, install the `arm64` APK and verify About reports either:

```text
Runtime: 64-bit · standard profile
```

or, when Android identifies the device as low-RAM:

```text
Runtime: 64-bit · low-RAM profile
```

Repeat the normal launcher, navigation, cursor, video, lifecycle and multi-tab tests. A device that supports ARM64 should normally use the ARM64 APK rather than intentionally running the 32-bit browser process.

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
