# Tihulu TV Browser

<p align="center">
  <img src="assets/branding/tihulu_tv_banner.png" alt="Tihulu TV Browser — Based on Brave & Chromium" width="960">
</p>

A TV-first Google TV / Android TV browser derivative based on Brave and Chromium.

The goal is to keep Brave's browser engine, Shields, tab model and Chromium compatibility while adding a proper 10-foot TV input experience:

- **D-pad mode** using Chromium/Blink's native spatial navigation.
- **Cursor mode** where the remote D-pad moves a virtual mouse pointer and OK clicks.
- **TV-first browser bar** with large Back, Forward, Reload, Search/Address, Tabs and TV Controls actions.
- **TV controls panel** for changing navigation mode, opening the address bar/keyboard and re-centering the pointer.
- **Tihulu branding** with a dedicated launcher icon, Android TV banner and About panel.
- **Android TV launcher support** through `LEANBACK_LAUNCHER`.
- TV-friendly hardware declarations so a touchscreen is not required.

> [!IMPORTANT]
> This is an **unofficial community project**. It is not affiliated with, endorsed by, or distributed by Brave Software. The app name used by this project is **Tihulu TV Browser**. Brave and Chromium trademarks belong to their respective owners.

## Project status

**Early MVP / development preview.** The overlay and patch tooling are implemented, but a full Brave Android build still needs to be compiled and smoke-tested on real Google TV hardware before any release should be treated as stable.

### MVP scope

- [x] Android TV launcher entry
- [x] Tihulu launcher icon and TV banner
- [x] Branded About panel
- [x] Native Chromium spatial navigation switch on TV
- [x] D-pad / cursor navigation modes
- [x] Virtual pointer overlay
- [x] Remote OK-to-click in cursor mode
- [x] Always-visible TV browser bar with large focus targets
- [x] TV tab-control panel
- [x] TV controls dialog
- [x] Address-bar / TV keyboard shortcut
- [x] Pointer/mouse capable TV metadata
- [x] Overlay verification and regression tests
- [ ] Real-device Google TV smoke test
- [ ] Fullscreen-video polish
- [ ] Per-site preferred navigation mode
- [x] TV tab switcher controls (previous/next/new/close)
- [ ] Rich tab cards with live titles/thumbnails
- [ ] TV-optimized downloads UI
- [ ] Release signing / Play TV packaging

## Why use native spatial navigation?

Chromium already contains a spatial-navigation mode intended for devices without a normal mouse or touchscreen, including TV-style controllers. Tihulu TV Browser enables that path on Android TV instead of injecting JavaScript into every page. This reduces page breakage and keeps focus behavior inside Blink.

## Architecture

This repository is intentionally a **small overlay**, not a permanent copy of the entire Chromium/Brave source tree.

```text
Tihulu TV Browser repo
        |
        |  scripts/bootstrap.sh
        v
Brave Core checkout in .work/brave-browser/src/brave
        |
        |  pnpm run init --target_os=android
        v
Brave + Chromium source checkout
        |
        |  scripts/apply_overlay.py
        v
TV Java layer + manifest/build-file integration
        |
        |  pnpm run build Debug --target_os=android ...
        v
Android TV APK
```

Keeping the TV code in a separate overlay makes Brave/Chromium updates easier to review and avoids pretending that upstream source code is licensed by this project.

## One-line APK build (Ubuntu / Pop!_OS / Debian)

For a normal 64-bit Google TV target (`arm64`), the supported one-line host setup + build path is:

```bash
sudo apt-get update && sudo apt-get install -y curl && curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | bash
```

This installs the required host packages, ensures Git 2.41+, installs a checksum-verified compatible Node.js 24 toolchain and pnpm >=11.9.0 when needed, initializes Brave/Chromium, runs Chromium's Android dependency installer, applies/verifies the TV overlay and builds a Debug APK.

Useful variants:

```bash
# x86_64 Android target
curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | ARCH=x64 bash

# Build and install to a connected adb TV
curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | INSTALL_TO_TV=1 bash
```

> [!NOTE]
> The one-line command automates setup; it does not make Chromium small. Brave initialization downloads a very large source/dependency tree and the full compile can take a long time.

## ADB + Google TV installation

ADB is only required when you want the computer to install the generated APK directly on a Google TV / Android TV device. Root is not required.

The one-line installer installs the `adb` package automatically on Ubuntu, Pop!_OS and Debian. To check it manually:

```bash
adb version
```

### 1. Enable developer options on the TV

On most Google TV / Android TV devices:

1. Open **Settings**.
2. Open **System** -> **About**.
3. Highlight **Android TV OS build** / **Build**.
4. Press **OK** repeatedly until the TV reports that developer mode is enabled.
5. Return to **System** -> **Developer options**.

Menu names can vary by manufacturer and Android TV version.

### 2. Choose USB or wireless ADB

For USB debugging, enable **USB debugging**, connect the TV/device to the computer if the hardware supports an ADB-capable USB connection, then accept the authorization prompt shown on the TV.

For wireless debugging, put the computer and TV on the same network, enable **Wireless debugging**, then use the pairing address and pairing code shown by the TV:

```bash
adb pair TV_IP:PAIRING_PORT
```

Enter the pairing code from the TV when prompted. Then connect using the TV's ADB connection address/port:

```bash
adb connect TV_IP:ADB_PORT
```

The pairing port and normal ADB connection port may be different; use the values shown on the TV.

### 3. Verify the TV is ready

```bash
adb devices
```

A working connection should show the TV with the state `device`. If it shows `unauthorized`, unlock/check the TV screen and accept the computer authorization prompt. If it shows `offline`, reconnect or restart ADB:

```bash
adb kill-server
adb start-server
adb devices
```

### 4. Build and install automatically

Once `adb devices` shows the TV as `device`, run:

```bash
curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | INSTALL_TO_TV=1 bash
```

The build script finds the newest generated Brave APK for the requested architecture and installs it with `adb install -r`.

If the APK is already built, install it without rebuilding:

```bash
cd ~/tihulu-brave-tv
./scripts/install-apk.sh arm64
```

The TV launcher entry is **Tihulu TV Browser**.

For troubleshooting, pairing examples, multiple-device handling and manual APK installation, see [`docs/ADB_INSTALL.md`](docs/ADB_INSTALL.md).

## Manual build (Ubuntu / Pop!_OS / Debian)

A Brave/Chromium build is large. Brave documents that initialization pulls many repositories and tens of gigabytes of source code. Make sure you have substantial free disk space before starting.

### 1. Install the host prerequisites

Brave's current Android tooling requires Git 2.41+, Python 3, Node.js >=24.16.0 and <25, and pnpm >=11.9.0. You can let this repository manage those requirements with:

```bash
./scripts/install-host-deps.sh
```

For a fully manual setup:

```bash
sudo apt update
sudo apt install -y \
  build-essential \
  git \
  python3 \
  python-is-python3 \
  python3-setuptools \
  adb

node --version   # should be >=24.16.0 and <25
git --version    # should be 2.41 or newer
python3 --version
```

On newer Ubuntu-family releases `python3-distutils` may no longer exist as a separate package. Do not force-install an obsolete package if APT does not provide it.

### 2. Clone this project

```bash
git clone https://github.com/Tihulu/tihulu-brave-tv.git
cd tihulu-brave-tv
```

### 3. Bootstrap Brave for Android

For a modern Google TV device, `arm64` is the usual target:

```bash
./scripts/bootstrap.sh arm64
```

This creates the Brave checkout under `.work/brave-browser/src/brave`, runs Brave's Android initialization and then applies the TV overlay.

If you already have an initialized Brave checkout, skip the download and point the overlay script at its project root:

```bash
python3 scripts/apply_overlay.py /path/to/brave-browser
```

The project root is the directory that contains `src/brave` and `src/chrome` after Brave initialization.

### 4. Install Chromium/Android build dependencies

After Brave initialization completes:

```bash
cd .work/brave-browser
./src/build/install-build-deps.sh --android
cd -
```

If Brave/Chromium reports that your distribution is unsupported, Brave documents `--unsupported` as an alternative:

```bash
.work/brave-browser/src/build/install-build-deps.sh --android --unsupported
```

### 5. Build a debug APK

```bash
./scripts/build-debug.sh arm64
```

The wrapper reapplies/verifies the TV overlay first, then invokes Brave's current Android build command with APK output enabled.

### 6. Connect a Google TV / Android TV device

Enable **Developer options** and **USB debugging** or **Wireless debugging** on the TV, then follow the full [`ADB installation guide`](docs/ADB_INSTALL.md).

Check the connection:

```bash
adb devices
```

### 7. Install the APK

Because Brave output filenames can change between architectures/versions, the install helper discovers the newest Brave APK:

```bash
./scripts/install-apk.sh arm64
```

Or install a known APK directly:

```bash
adb install -r /path/to/Brave*.apk
```

The TV launcher entry is **Tihulu TV Browser**.

## Remote controls

### D-pad mode

| Input | Action |
| --- | --- |
| Up / Down / Left / Right | Chromium native spatial navigation |
| OK / Enter | Activate the focused page/UI element |
| Long-press OK | Focus the TV browser bar |
| Back | Browser back / normal Android back behavior |
| Menu, Info or Guide | Open TV Controls |

When a focused HTML text field is activated, Android's normal TV IME (for example Gboard on Google TV) is expected to open.

### Cursor mode

| Input | Action |
| --- | --- |
| Up / Down / Left / Right | Move virtual cursor |
| OK / Enter | Mouse click at cursor position |
| Long-press OK | Focus the TV browser bar |
| Back | Normal browser back behavior |
| Menu, Info or Guide | Open TV Controls |

The always-visible TV browser bar contains large focusable actions for **Back**, **Forward**, **Reload**, **Search / Address**, **Tabs** and **TV Controls**. It deliberately uses stable Android/Chromium input paths rather than depending on private Brave toolbar APIs.

The TV Controls dialog includes:

- Switch between **D-pad** and **Cursor** mode.
- **Address / Keyboard**, which sends Chrome's `Ctrl+L` shortcut so the omnibox receives focus and Android can display its keyboard.
- **Tabs**, with previous/next/new/close-current actions.
- **Check for updates**, which checks packaged APKs on GitHub Releases.
- **About Tihulu TV Browser**, with the project logo and the **Based on Brave & Chromium** attribution.
- **Center cursor**.

External USB/Bluetooth keyboards and mice continue to use Android/Chromium's normal input paths.

## Branding

The application-facing brand is **Tihulu TV Browser**. Brave and Chromium are credited as the underlying browser projects, but Brave logos and official Brave artwork are not used as the Tihulu application identity.

The project branding assets are:

- `assets/branding/tihulu_tv_icon.png` — launcher / app icon.
- `assets/branding/tihulu_tv_banner.png` — Android TV / Google TV banner and README/release artwork.

The Android overlay copies both files into Chromium's packaged drawable resources. The TV launcher activity uses the Tihulu icon and banner directly, and the in-app About panel uses the same icon for consistent branding.

See [`docs/BRANDING.md`](docs/BRANDING.md) for asset and release usage.

## Updating Brave

The actual Brave/Chromium checkout is ignored by this repository. To update it:

```bash
cd .work/brave-browser/src/brave
git pull
pnpm run sync --target_os=android
cd ../../../..
python3 scripts/apply_overlay.py .work/brave-browser
./scripts/check.sh
```

Always re-run the checks after an upstream update. The patcher deliberately fails rather than guessing if important upstream anchors have moved.

## Testing

Run the lightweight validation suite:

```bash
./scripts/check.sh
```

It checks:

- Python overlay tests.
- Pure-Java cursor-state tests.
- Android Java surface compilation against minimal CI stubs to catch syntax/type regressions before a full Chromium build.
- Shell syntax.
- License/header expectations.
- Required Android TV manifest markers in fixture application tests.
- Idempotence: applying the overlay twice must not duplicate entries.

A lightweight CI workflow runs these checks on every push and pull request. A separate scheduled workflow checks whether the Brave/Chromium files we patch have drifted upstream.

### Real-device smoke test before release

Do **not** publish a release only because CI is green. Test at least:

1. Cold launch from the Google TV home screen.
2. D-pad navigation on several structurally different sites.
3. Text input and on-screen keyboard.
4. Cursor movement and click routing.
5. Back navigation and tab behavior.
6. Fullscreen HTML5 video and exit from fullscreen.
7. A USB/Bluetooth mouse and keyboard.
8. App suspend/resume and process restart.
9. Memory pressure after multiple tabs.
10. A TV with only the minimal Google TV remote buttons.

See [`docs/TESTING.md`](docs/TESTING.md).

## Licensing

Original code in this repository is licensed under **GNU AGPL-3.0-only** unless a file says otherwise.

That does **not** relicense Brave or Chromium:

- Brave Core is primarily **MPL-2.0**.
- Chromium contains BSD-style and many other third-party licenses.
- Files modified inside an initialized upstream checkout retain the license notices and obligations of those upstream files/components.

See [`docs/LICENSING.md`](docs/LICENSING.md) and [`NOTICE.md`](NOTICE.md).

## Trademark note

Do not ship Brave logos, Brave store artwork, or imply that an unofficial build is an official Brave product. Use independent Tihulu branding for distributed builds unless you have the necessary permission.

## Security

Browser forks inherit an unusually large attack surface. Keep Brave/Chromium current, do not disable sandboxing, Site Isolation, Safe Browsing/Brave security mechanisms merely to make a TV feature work, and treat renderer/browser-process crashes as release blockers.

See [`SECURITY.md`](SECURITY.md).
