# TV refresh validation

## Changes

Native Android views provide a two-row toolbar and a shared dark/mint focus style without animation, blur, thumbnail caches or another browser engine. Focus does not rewrite labels. Down moves between rows; Back closes the toolbar. Hold Up on a page and release to open it.

Controls, tab actions, About and per-site Shields use scrollable panels sized to the display, leaving 24 dp margins. The tab panel still provides previous/next/new/close actions; it does not yet provide a visual tab overview.

Shields calls the pinned Brave 1.94.117 `BraveShieldsContentSettings` API. The TRACKERS setter updates both native cosmetic filtering and network ad controls. Standard and Aggressive explicitly enable Shields for the captured site. Off preserves the existing filtering level. Reset uses Brave's global defaults. The active tab profile is retained, including private profiles; a destroyed tab is ignored and a tab which navigated elsewhere is not reloaded. No JavaScript imitation or DNS-only blocker is added.

The memory profile uses physical total RAM, not available RAM. ARM32, vendor low-RAM devices, and devices reporting at most 2 GiB use Chromium's low-end mode. Sandbox and Site Isolation remain intact. This is a conservative configuration, not an enforced RAM cap or a performance guarantee.

## Automated validation

`./scripts/check.sh` runs Python patcher/source checks, Java cursor and updater tests, Android/Chromium stub compilation, memory/focus regression tests, Shields action tests, and shell/source checks. Stubs model API contracts; they do not prove APK compilation, Android layout, native filtering, or playback performance.

Upstream API references:

- https://github.com/brave/brave-core/blob/v1.94.117/android/java/org/chromium/chrome/browser/preferences/website/BraveShieldsContentSettings.java
- https://github.com/chromium/chromium/blob/152.0.7977.64/chrome/browser/tab/java/src/org/chromium/chrome/browser/tab/Tab.java

## Required packaged-device acceptance

Status: **NOT RUN**. No APK was built in the editing environment (about 30 GB free; the repository recommends around 200 GB for the full checkout/build). No Mi Box or TV emulator was connected. Actual layout screenshots, native Shields behavior and memory measurements are still required before release.

1. Build ARM32 and ARM64 from this branch using the existing scripts. Install the ABI supported by the target device; do not infer ABI from advertised CPU hardware.
2. On a 2 GB Mi Box, record OS, ABI, firmware, APK commit, Web site and display settings. Confirm About reports the low-memory profile.
3. At 720p and 1080p, use only the stock remote: search/address entry, both toolbar rows, mode toggle followed by focus away/back, all scrollable panel actions and Back. Repeat with enlarged system fonts. Check for clipping and unreachable buttons.
4. In Cursor mode, enter an address and edit it with arrows/OK. Verify IME operation; also test page text inputs, whose handling remains Chromium-owned.
5. Enter HTML video fullscreen before ever opening the TV toolbar. Confirm remote events go to the player. Repeat after opening Controls, Shields and Tabs. Test Home/resume and rotation/display changes.
6. On a controlled ad/tracker test page, compare resource requests under Shields Off, Standard and Aggressive. Verify persistence after reload/restart, site isolation of settings, global reset and private-profile separation. Test native Brave filter updates online and cached filters offline. Do not use one YouTube playback as proof of universal ad blocking.
7. Cold launch, browse several ordinary pages, play 1080p video, open/close 3–5 tabs and repeat for 20 minutes. Capture `adb shell dumpsys meminfo PACKAGE` and `adb logcat` for ANRs, renderer crashes, LMKD kills and jank. Repeat after reboot. No numeric RAM/performance claim is currently established.

## Remaining product work

Real-device UI review, a full tab overview, TV-specific downloads/history/bookmarks flows, reliable page scrolling in cursor mode, navigation preference persistence, release signing and full ARM32/ARM64 build validation remain. This refresh must not be presented as a finished stable Google TV release.
