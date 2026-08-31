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
- **32-bit memory protection** using Chromium's supported low-end device mode without weakening Site Isolation or the renderer sandbox/process model.

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
- [x] Virtual pointer overlay, lazy-loaded when Cursor mode is used
- [x] Remote OK-to-click in cursor mode
- [x] Always-visible TV browser bar with large focus targets
- [x] TV tab-control panel
- [x] TV controls dialog
- [x] Address-bar / TV keyboard shortcut
- [x] Pointer/mouse capable TV metadata
- [x] Overlay verification and regression tests
- [x] Separate ARM32 and ARM64 setup guidance
- [x] Chromium low-end runtime profile for 32-bit / Android low-RAM TV processes
- [ ] Real-device Google TV smoke test
- [ ] Fullscreen-video polish
- [ ] Per-site preferred navigation mode
- [x] TV tab switcher controls (previous/next/new/close)
- [ ] Rich tab cards with live titles/thumbnails
- [ ] TV-optimized downloads UI
- [ ] Release signing / Play TV packaging

## Choose the correct Android TV architecture first

Connect the TV with ADB and check its Android ABI list:

```bash
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.product.cpu.abilist
```

Use the architecture-specific guide:

- **32-bit ARM only** — for example `armeabi-v7a,armeabi`: [`docs/SETUP_32BIT_ARM.md`](docs/SETUP_32BIT_ARM.md)
- **64-bit ARM** — `arm64-v8a` is present: [`docs/SETUP_64BIT_ARM.md`](docs/SETUP_64BIT_ARM.md)

If a device exposes only `armeabi-v7a`, an `arm64` APK cannot run on it. The same large Chromium source checkout can build both targets; do not duplicate or delete the source tree just to switch between ARM32 and ARM64.

## Why use native spatial navigation?

Chromium already contains a spatial-navigation mode intended for devices without a normal mouse or touchscreen, including TV-style controllers. Tihulu TV Browser enables that path on Android TV instead of injecting JavaScript into every page. This reduces page breakage and keeps focus behavior inside Blink.

## 32-bit / low-RAM policy

A 32-bit browser process has a much tighter virtual address space than a 64-bit browser process. Tihulu therefore enables Chromium's own `enable-low-end-device-mode` when the TV browser process is 32-bit. A 64-bit process also gets the profile when Android marks the device as low-RAM.

This is intentionally conservative. Tihulu does **not** use `--single-process`, `--process-per-site`, or an artificial renderer-process limit just to reduce RAM; those shortcuts can hurt isolation, stability, or compatibility. The virtual cursor is also created only when Cursor mode is selected.

The About panel reports the active runtime class, for example:

```text
Runtime: 32-bit · low-memory profile
```

See the [32-bit setup guide](docs/SETUP_32BIT_ARM.md) for low-memory operating recommendations and troubleshooting.

## Architecture

This repository is intentionally a **small overlay**, not a permanent copy of the entire Chromium/Brave source tree.

```text
Tihulu TV Browser repo
        |
        |  scripts/bootstrap.sh
        v
Pinned Brave Core checkout in .work/brave-browser/src/brave
        |
        |  pnpm run init/sync --target_os=android
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

### ARM64 Google TV / Android TV

```bash
sudo apt-get update && sudo apt-get install -y curl && \
  curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | \
  ARCH=arm64 BRAVE_GCLIENT_RECOVERY_JOBS=4 bash
```

Build and install to a connected ARM64 TV:

```bash
curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | \
  ARCH=arm64 BRAVE_GCLIENT_RECOVERY_JOBS=4 INSTALL_TO_TV=1 bash
```

### 32-bit ARM TV / TV box

```bash
sudo apt-get update && sudo apt-get install -y curl && \
  curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | \
  ARCH=arm BRAVE_GCLIENT_RECOVERY_JOBS=4 bash
```

Build and install to a connected 32-bit ARM TV:

```bash
curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | \
  ARCH=arm BRAVE_GCLIENT_RECOVERY_JOBS=4 INSTALL_TO_TV=1 bash
```

The host setup installs the required packages, ensures Git 2.46+, installs a checksum-verified compatible Node.js 24 toolchain and pnpm >=11.9.0 when needed, initializes the pinned Brave/Chromium source tree, runs Chromium's Android dependency installer, applies/verifies the TV overlay and builds a Debug APK.

Other supported architecture variants include:

```bash
# x86_64 Android target
curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | ARCH=x64 bash

# 32-bit x86 Android target
curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | ARCH=x86 bash
```

> [!NOTE]
> The one-line command automates setup; it does not make Chromium small. Brave initialization downloads a very large source/dependency tree and the full compile can take a long time. Preserve `.work/brave-browser` after partial downloads or build errors unless a specific diagnosis proves the checkout itself is unrecoverable.

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

### 3. Verify the TV and target ABI

```bash
adb devices
adb shell getprop ro.product.cpu.abilist
```

A working connection should show the TV with the state `device`. If it shows `unauthorized`, unlock/check the TV screen and accept the computer authorization prompt. If it shows `offline`, reconnect or restart ADB:

```bash
adb kill-server
adb start-server
adb devices
```

### 4. Build and install automatically

Use the architecture-specific command above. For an existing checkout:

```bash
# ARM64
cd ~/tihulu-brave-tv && git pull --ff-only && \
BRAVE_GCLIENT_RECOVERY_JOBS=4 INSTALL_TO_TV=1 ./scripts/build-apk-one-line.sh arm64

# ARM32
cd ~/tihulu-brave-tv && git pull --ff-only && \
BRAVE_GCLIENT_RECOVERY_JOBS=4 INSTALL_TO_TV=1 ./scripts/build-apk-one-line.sh arm
```

The build script finds the newest generated Brave APK for the requested architecture, checks that the APK archive is valid, and installs it with `adb install -r`.

If the APK is already built:

```bash
./scripts/install-apk.sh arm64  # ARM64
./scripts/install-apk.sh arm    # ARM32
```

If several ADB devices are connected, the installer refuses to guess; set `ADB_SERIAL=...` explicitly.

The TV launcher entry is **Tihulu TV Browser**.

For troubleshooting, pairing examples, multiple-device handling and manual APK installation, see [`docs/ADB_INSTALL.md`](docs/ADB_INSTALL.md).

## Manual build (Ubuntu / Pop!_OS / Debian)

A Brave/Chromium build is large. Make sure you have substantial free disk space before starting. Around 200 GB of free space is a safer starting point for the initial source/dependency tree plus build outputs.

### 1. Install the host prerequisites

The current wrapper requires Git 2.46+, Python 3, Node.js >=24.16.0 and <25, and pnpm >=11.9.0. Let the repository manage compatible local tools with:

```bash
./scripts/install-host-deps.sh
```

For a fully manual baseline:

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
git --version    # project wrapper requires >=2.46
python3 --version
```

On newer Ubuntu-family releases `python3-distutils` may no longer exist as a separate package. Do not force-install an obsolete package if APT does not provide it.

### 2. Clone this project

```bash
git clone https://github.com/Tihulu/tihulu-brave-tv.git
cd tihulu-brave-tv
```

### 3. Bootstrap Brave for Android

Choose the target after checking `ro.product.cpu.abilist`:

```bash
./scripts/bootstrap.sh arm64  # arm64-v8a present
./scripts/bootstrap.sh arm    # armeabi-v7a only
```

This creates the Brave checkout under `.work/brave-browser/src/brave`, pins Brave Core to the reviewed tag in `config/brave-core-ref`, initializes/synchronizes Chromium and then applies the TV overlay.

If you already have an initialized compatible Brave checkout, the wrapper reuses it. Do not delete the large checkout to switch between `arm` and `arm64`.

### 4. Install Chromium/Android build dependencies

After Brave initialization completes:

```bash
cd .work/brave-browser
./src/build/install-build-deps.sh --android
cd -
```

If Brave/Chromium reports that your distribution is unsupported:

```bash
.work/brave-browser/src/build/install-build-deps.sh --android --unsupported
```

### 5. Build a debug APK

```bash
./scripts/build-debug.sh arm64
# or
./scripts/build-debug.sh arm
```

The wrapper reapplies/verifies the TV overlay first, then invokes Brave's current Android build command with APK output enabled.

### 6. Connect and install

```bash
adb devices
./scripts/install-apk.sh arm64
# or
./scripts/install-apk.sh arm
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
- **Check for Tihulu updates**, which checks packaged APKs on GitHub Releases.
- **About Tihulu TV Browser**, with the project logo, engine versions and active runtime memory profile.
- **Center cursor**.

External USB/Bluetooth keyboards and mice continue to use Android/Chromium's normal input paths.

## Branding

The application-facing brand is **Tihulu TV Browser**. Brave and Chromium are credited as the underlying browser projects, but Brave logos and official Brave artwork are not used as the Tihulu application identity.

The project branding assets are:

- `assets/branding/tihulu_tv_icon.png` — launcher / app icon.
- `assets/branding/tihulu_tv_banner.png` — Android TV / Google TV banner and README/release artwork.

The Android overlay copies both files into Chromium's packaged drawable resources. The TV launcher activity uses the Tihulu icon and banner directly, and the in-app About panel uses the same icon for consistent branding.

See [`docs/BRANDING.md`](docs/BRANDING.md) for asset and release usage.

## Updating the Brave/Chromium baseline

The default build is intentionally pinned to the reviewed Brave tag stored in:

```text
config/brave-core-ref
```

Do **not** update the production baseline with an unreviewed `git pull` of Brave `master`. To move to a new stable Brave release:

1. Put the intended stable Brave tag in `config/brave-core-ref`.
2. Run the normal bootstrap/build path for the target architecture.
3. Let the bootstrap synchronize the matching Chromium revision and reapply the Tihulu overlay.
4. Run `./scripts/check.sh`.
5. Complete the full APK compile and real-TV smoke test before publishing the update.

`BRAVE_CORE_REF=...` remains available only for deliberate development/testing overrides. The bootstrap refuses to silently reset unknown local Brave changes when switching refs.

## Testing

Run the lightweight validation suite:

```bash
./scripts/check.sh
```

It checks:

- Python overlay tests.
- Pure-Java cursor-state tests.
- Android Java surface compilation against minimal CI stubs to catch syntax/type regressions before a full Chromium build.
- 32-bit/low-RAM profile wiring and unsafe memory-saving flag absence.
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
9. Memory pressure after multiple tabs, especially on ARM32 / 1–2 GB devices.
10. A TV with only the minimal Google TV remote buttons.
11. The correct runtime profile shown in About.

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

Browser forks inherit an unusually large attack surface. Keep Brave/Chromium current, do not disable sandboxing, Site Isolation, Safe Browsing/Brave security mechanisms merely to make a TV feature work, and treat renderer/browser-process crashes as release blockers. The 32-bit low-memory profile deliberately uses Chromium's supported low-end mode rather than weakening the browser process model.

See [`SECURITY.md`](SECURITY.md).
