# 64-bit ARM Android TV setup

Use this guide when the TV / TV box Android userspace supports ARM64.

Check with:

```bash
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.product.cpu.abilist
```

A normal ARM64 result looks similar to:

```text
arm64-v8a,armeabi-v7a,armeabi
```

If `arm64-v8a` is present, `arm64` is the preferred Tihulu TV Browser target.

## Runtime memory behavior

64-bit is preferred when the device supports it because the browser has a much larger virtual address space and is less likely to hit 32-bit address-space exhaustion.

Tihulu keeps Chromium's normal 64-bit behavior unless Android explicitly identifies the device as low-RAM. In that case it enables Chromium's supported low-end device mode. It does not reduce site isolation or use a single-process browser model.

The virtual cursor is also lazy-loaded: D-pad-only users do not allocate the cursor overlay at browser startup.

You can see the selected mode from **About Tihulu TV Browser**:

```text
Runtime: 64-bit · standard profile
```

or, on an Android low-RAM device:

```text
Runtime: 64-bit · low-RAM profile
```

## Host requirements

Recommended build host baseline:

- Ubuntu 24.04, Pop!_OS 24.04, or a supported Debian-family system
- 16 GB RAM minimum; 32 GB or more is preferable
- roughly 200 GB or more free disk space before a first Brave/Chromium checkout/build
- a stable internet connection for the initial dependency download

The same source checkout can later build ARM32 as well. Do not keep separate 60+ GB Chromium source trees just for ARM32 and ARM64.

## 1. Connect the TV with ADB

```bash
adb devices
adb shell getprop ro.product.cpu.abilist
```

Make sure `arm64-v8a` is present.

## 2. Fresh one-line setup + ARM64 build

```bash
sudo apt-get update && sudo apt-get install -y curl && \
  curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | \
  ARCH=arm64 BRAVE_GCLIENT_RECOVERY_JOBS=4 INSTALL_TO_TV=1 bash
```

To build without installing to the TV:

```bash
curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | \
  ARCH=arm64 BRAVE_GCLIENT_RECOVERY_JOBS=4 bash
```

## 3. Resume an existing checkout

Do not delete `.work/brave-browser` just because a previous build or dependency download failed.

```bash
cd ~/tihulu-brave-tv && \
git pull --ff-only && \
BRAVE_GCLIENT_RECOVERY_JOBS=4 \
INSTALL_TO_TV=1 \
./scripts/build-apk-one-line.sh arm64
```

The bootstrap path preserves and resumes the existing Brave/Chromium checkout where possible.

## 4. Install an already-built ARM64 APK

```bash
cd ~/tihulu-brave-tv
./scripts/install-apk.sh arm64
```

If multiple ADB devices are connected:

```bash
ADB_SERIAL=TV_SERIAL ./scripts/install-apk.sh arm64
```

The installer intentionally refuses ambiguous device selection.

## Should an ARM64 TV use the ARM32 APK?

Normally, no. Even if the ABI list also contains `armeabi-v7a`, use `arm64` unless you are deliberately testing ARM32 compatibility. A 32-bit browser process has a smaller address space and Tihulu will therefore enable the low-memory runtime profile for it.

## Common ARM64 problems

### `INSTALL_FAILED_NO_MATCHING_ABIS`

Re-check the ABI list. If `arm64-v8a` is absent, use the [32-bit ARM guide](SETUP_32BIT_ARM.md).

### Build or dependency download failed

Keep the existing checkout. Retry from the repository:

```bash
cd ~/tihulu-brave-tv
git pull --ff-only
BRAVE_GCLIENT_RECOVERY_JOBS=4 ./scripts/build-apk-one-line.sh arm64
```

### Several devices are connected

Use `ADB_SERIAL` instead of allowing an install script to guess:

```bash
adb devices
ADB_SERIAL=YOUR_TV_SERIAL ./scripts/install-apk.sh arm64
```

## Updating later

A normal ARM64 release asset should be named similar to:

```text
Tihulu-TV-Browser-arm64.apk
```

The in-app updater keeps ARM64 and ARM32 assets separate. In-place Android updates require the new APK to use the same package identity and a compatible signing certificate as the installed APK.
