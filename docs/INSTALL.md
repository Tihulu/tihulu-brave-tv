# Installation and Build Guide

This guide targets an Ubuntu-family Linux workstation and a Google TV / Android TV device.

## Requirements

- 64-bit Linux host.
- Git 2.41 or newer.
- Python 3.
- Node.js 24 or newer.
- `pnpm` as used by current Brave Core.
- Android Debug Bridge (`adb`).
- Large free disk space for Chromium/Brave source and build output.
- A Google TV / Android TV device with Developer options enabled.

Brave's build tooling downloads the Chromium tree and a large dependency graph. This is not an Android Studio-sized project.

## Host setup

```bash
sudo apt update
sudo apt install -y build-essential git python3 python-is-python3 python3-setuptools adb
```

Install Node.js 24+ using your preferred supported method. If you use `nvm`:

```bash
nvm install 24
nvm use 24
node --version
```

Enable the package manager supplied through Corepack when appropriate for your Node installation:

```bash
corepack enable
```

## Clone and initialize

```bash
git clone https://github.com/Tihulu/tihulu-brave-tv.git
cd tihulu-brave-tv
./scripts/bootstrap.sh arm64
```

What the bootstrap script does:

1. Creates `.work/brave-browser/src`.
2. Clones `brave/brave-core` to `.work/brave-browser/src/brave`.
3. Runs `pnpm install` inside Brave Core.
4. Runs Brave's Android initialization for the requested architecture.
5. Applies the Tihulu TV overlay.
6. Verifies that the overlay appears exactly once.

The architecture parameter maps common Android ABI names to Brave's architecture names:

| Input | Brave target |
| --- | --- |
| `arm64`, `arm64-v8a` | `arm64` |
| `arm`, `armeabi-v7a` | `arm` |
| `x64`, `x86_64` | `x64` |
| `x86` | `x86` |

For most physical Google TV devices, use `arm64`.

## Build dependencies after init

```bash
.work/brave-browser/src/build/install-build-deps.sh --android
```

If the Chromium dependency script rejects a derivative distribution, Brave documents the `--unsupported` switch as an alternative.

## Build

```bash
./scripts/build-debug.sh arm64
```

Equivalent Brave command from `src/brave`:

```bash
pnpm run build Debug \
  --target_os=android \
  --target_arch=arm64 \
  --target_android_output_format=apk
```

If Android lint runs out of heap, Brave's Android documentation recommends increasing `JAVA_OPTS`, for example:

```bash
export JAVA_OPTS='-Xmx10G -Xms1G'
```

## Device setup

On Google TV:

1. Open **Settings > System > About**.
2. Click the build entry repeatedly to enable Developer options if needed.
3. Open **Developer options**.
4. Enable USB debugging or Wireless debugging.
5. For wireless debugging, pair the host using the address/code shown by the TV.

Verify:

```bash
adb devices
```

## Install

```bash
./scripts/install-apk.sh arm64
```

The helper searches the corresponding Brave Android output directory and installs the newest `.apk` it finds.

For a specific APK:

```bash
adb install -r /absolute/path/to/file.apk
```

## Update an existing checkout

```bash
cd .work/brave-browser/src/brave
git pull
pnpm run sync --target_os=android
cd ../../../..
python3 scripts/apply_overlay.py .work/brave-browser
./scripts/check.sh
./scripts/build-debug.sh arm64
```

If `apply_overlay.py` reports upstream drift, do not bypass it with a blind search/replace. Review the changed Brave/Chromium source and update the patcher/tests first.

## Play / release builds

The MVP is designed first for sideloaded testing. Before a Play TV release, verify current Google Play TV quality, target SDK, signing, banner/icon and app-bundle requirements. Google Play requirements change over time; do not hard-code an old target SDK based on this document.

As of this repository's initial development date (2026-08-31), Android TV apps have a TV-specific target-API allowance compared with ordinary phone/tablet submissions, but this must be rechecked before each release.
