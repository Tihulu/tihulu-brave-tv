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
| Launcher | Starts from Google TV home after cold process kill without startup ANR/crash |
| Web focus | D-pad reaches links/buttons/forms and the focused element has a clear high-contrast TV outline |
| Dynamic top bar | Hold Up or Menu opens it; selected action is unmistakable; Down/Back closes it |
| Navigation mode | Use the explicit Mode button in the top bar to switch D-pad/Cursor |
| Scrolling | Focus navigation can progress through long pages without repeat-event stalls |
| Keyboard | Omnibox and page input open TV IME |
| Cursor | D-pad moves pointer, repeat accelerates smoothly, pointer remains in bounds |
| Click | Links, buttons and native browser controls receive click |
| Browser UI | Top bar, Tabs and TV Controls all show obvious selected-item feedback |
| Video | HTML5 playback, TV-clean fullscreen entry/exit, remote controls, Back exit |
| Lifecycle | Home -> resume, screen sleep -> resume, process recreation |
| Inputs | Remote, USB/Bluetooth keyboard, USB/Bluetooth mouse |
| Resources | Multi-tab memory pressure does not create ANR/crash loops |
| Security | No sandbox/security flags disabled by TV patches |

### Remote-only navigation contract

The minimum supported remote is six keys: four D-pad directions, OK and Back. Extra Menu/Info/Guide keys are optional accelerators, never requirements.

1. In **D-pad mode**, arrows and short OK use Chromium spatial/page navigation.
2. The web-focused element must have the Tihulu TV UA focus ring: a high-contrast 4 px outline with 3 px offset. This is a renderer stylesheet rule, not JavaScript injected on every key press.
3. Android key-repeat is intentionally thinned before reaching Chromium spatial navigation on low-end hardware. The first press and key-up still pass through; repeated focus calculations must not flood the renderer.
4. Hold **Up**, then release, to open the top browser bar. The bar is created after the repeat storm rather than while the key is still repeating.
5. If the remote has **Menu**, **Info** or **Guide**, that key toggles the top bar directly.
6. The top bar lives in a separate Dialog window, not inside Chromium's `DecorView`. Opening it must not detach or rebuild Chrome toolbar/content views.
7. The focused top-bar button uses a strong red background plus explicit `▶ ... ◀` text markers. No scale animation is used on low-end hardware.
8. **Left/Right** moves focus across the bar. **OK** activates. **Down** or **Back** closes the bar and returns control to page content.
9. Use the bar's **Mode: D-pad / Mode: Cursor** button to change page navigation mode. Do not overload long-OK: short and held OK must remain predictable for page controls and video players.
10. In **Cursor mode**, D-pad moves the pointer and holding a direction accelerates gradually. Movement updates only the visual pointer; it must not emit a synthetic `HOVER_MOVE` for every repeat event.
11. Short **OK** in Cursor mode emits the actual pointer click.
12. Browser-bar actions close the bar before navigating/opening another dialog. The Mode button is the exception: it updates in place so the current mode is immediately visible.
13. Tabs and TV Controls must use the same strong red selected-item feedback rather than Android's subtle default focus treatment.
14. Cold-start the browser and do not touch the remote for at least 20 seconds. No Tihulu persistent view, cursor, layout listener or fullscreen observer should be created on the startup path.

### Startup and input ANR regression

On low-memory Android TV, collect a cold-start trace after `am force-stop`. A failure like:

```text
Input event dispatching timed out ... TvBraveActivity ... Waited 5001ms for FocusEvent(hasFocus=true)
```

is an ANR even if there is no `FATAL EXCEPTION`. Treat it as a release blocker.

The TV integration deliberately keeps `performPostInflationStartup()` inert apart from caching TV mode and retaining the already-created `DecorView` reference. It must not add a browser bar, cursor, layout listener, Dialog or fullscreen-manager observer there. TV UI is created only after an explicit remote action.

After cold start, stress D-pad navigation for at least 60 seconds on a simple page and a complex page. Hold each direction long enough to generate Android repeat events. Confirm there is no multi-second freeze, `Input event dispatching timed out`, `ANR`, `FATAL EXCEPTION`, `SIGSEGV`, `SIGABRT`, or Chromium toolbar assertion.

Repeat the stress test in Cursor mode. Pointer movement should remain responsive and should not create synthetic hover-event storms. Test the same behavior in the first-run onboarding cursor.

### TV-clean HTML5 fullscreen

Run this on YouTube and at least one other HTML5-video site. Test once from D-pad mode and once from Cursor mode:

1. Enter video fullscreen from the page.
2. If the dynamic Tihulu top bar was open, confirm it is dismissed immediately.
3. If Cursor mode was active before fullscreen, confirm the Tihulu cursor overlay disappears.
4. Confirm D-pad and OK now go directly to the Chromium/page player rather than moving the hidden virtual cursor or opening Tihulu controls.
5. Confirm Android status/navigation bars follow Chromium's fullscreen behavior; Tihulu must not add a second system-UI controller that fights Chromium.
6. Press Back/remote exit and confirm Chromium leaves HTML fullscreen normally.
7. Confirm the top bar stays hidden after fullscreen exit until the user explicitly asks for it again.
8. Confirm the pre-fullscreen navigation mode is preserved. If Cursor mode was active, its pointer should return after exit at the previous logical position.
9. Repeat enter/exit at least ten times and check for overlay duplication, stuck hidden UI, flicker, focus loss, or a growing memory footprint.
10. Repeat after Home -> resume and screen sleep -> resume while video is fullscreen.

A fullscreen failure should be captured with `adb logcat`; do not add legacy `SYSTEM_UI_FLAG_*` or a separate `WindowInsetsController` workaround unless Chromium's own fullscreen state is proven not to handle the device.

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
3. Cold-launch in D-pad mode and confirm normal browsing works without first entering Cursor mode or opening Tihulu UI.
4. Leave the app untouched for 20 seconds after cold launch and verify no startup ANR occurs.
5. On several sites, verify D-pad selection is clearly marked by the 4 px high-contrast web focus ring.
6. Hold directions repeatedly for at least 60 seconds and verify repeat throttling prevents severe stalls or ANRs.
7. Hold Up and release to open the dynamic top bar; move focus left/right, verify the red `▶ ... ◀` selection, then close it with Down and Back.
8. Use the Mode button to switch to Cursor; verify D-pad movement/acceleration and OK click without hover-triggered jank.
9. Open Tabs and TV Controls and verify the selected button is obvious in both dialogs.
10. Open one normal site, then progressively open several tabs while watching for renderer reloads, ANRs or crash loops.
11. Play a 1080p HTML5 video, enter/leave fullscreen and then switch to another tab.
12. Send the app Home -> resume, then repeat after screen sleep.
13. Close unused tabs after deliberate memory pressure and confirm the browser recovers instead of remaining in a crash/reload loop.
14. Capture `adb logcat` around any `OutOfMemoryError`, linker failure, renderer crash, LMKD kill, ANR or repeated tab reload.
15. Do not solve an ARM32 failure by adding `--single-process`, `--process-per-site`, disabling sandbox/Site Isolation, or forcing software rendering.

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
