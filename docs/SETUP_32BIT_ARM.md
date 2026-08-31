# 32-bit ARM Android TV setup

Use this guide when the TV / TV box Android userspace only exposes 32-bit ARM ABIs.

A typical 32-bit-only result is:

```text
armeabi-v7a,armeabi
```

Check with:

```bash
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.product.cpu.abilist
```

If `arm64-v8a` is **not** present and `armeabi-v7a` is present, build the `arm` target. An `arm64` APK will not install on that Android userspace.

## What Tihulu changes for 32-bit devices

32-bit Android has a much tighter per-process virtual address space than 64-bit Android. A device may therefore run out of usable browser address space even when its physical RAM is above Chromium's normal low-end threshold.

Tihulu TV Browser handles this conservatively:

- A 32-bit browser process automatically enables Chromium's supported `enable-low-end-device-mode` path.
- A 64-bit TV that Android itself marks as a low-RAM device also uses that profile.
- The virtual cursor overlay is allocated only when Cursor mode is actually used; normal D-pad startup does not create it.
- Tihulu does **not** use `--single-process`, `--process-per-site`, a renderer-process cap, or other shortcuts that weaken Chromium's process isolation or tend to create compatibility problems.
- The in-app updater keeps 32-bit ARM and ARM64 release assets separate.

You can confirm the active runtime profile later from **About Tihulu TV Browser**. A 32-bit build should report:

```text
Runtime: 32-bit · low-memory profile
```

## Host requirements

The build PC can still be 64-bit Linux. The target being 32-bit does not require a 32-bit build host.

Recommended host baseline:

- Ubuntu 24.04, Pop!_OS 24.04, or a supported Debian-family system
- 16 GB RAM minimum for the build host; 32 GB or more is preferable
- roughly 200 GB or more free disk space before a first Brave/Chromium checkout/build
- a stable internet connection for the initial dependency download

The large Chromium source checkout is shared between ARM32 and ARM64 builds. Switching from `arm64` to `arm` does **not** mean downloading Chromium from zero again.

## 1. Connect the TV with ADB

Verify that exactly one target is connected:

```bash
adb devices
```

Then confirm the ABI:

```bash
adb shell getprop ro.product.cpu.abilist
```

For this guide the expected form is similar to:

```text
armeabi-v7a,armeabi
```

You can also inspect the device RAM:

```bash
adb shell cat /proc/meminfo | head -n 3
adb shell getprop ro.config.low_ram
```

`ro.config.low_ram` may be empty on some vendor ROMs; that alone is not an error.

## 2. Fresh one-line setup + ARM32 build

```bash
sudo apt-get update && sudo apt-get install -y curl && \
  curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | \
  ARCH=arm BRAVE_GCLIENT_RECOVERY_JOBS=4 INSTALL_TO_TV=1 bash
```

This builds for 32-bit ARM / `armeabi-v7a` and, when the build succeeds, installs the generated APK to the connected ADB device.

To build without installing to the TV, omit `INSTALL_TO_TV=1`:

```bash
curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | \
  ARCH=arm BRAVE_GCLIENT_RECOVERY_JOBS=4 bash
```

## 3. Resume an existing checkout

If the Brave/Chromium checkout already exists, do **not** delete `.work/brave-browser`.

Use:

```bash
cd ~/tihulu-brave-tv && \
git pull --ff-only && \
BRAVE_GCLIENT_RECOVERY_JOBS=4 \
INSTALL_TO_TV=1 \
./scripts/build-apk-one-line.sh arm
```

The script reuses the existing Chromium source/dependency tree and creates a separate ARM32 build output.

## 4. Install an already-built ARM32 APK

```bash
cd ~/tihulu-brave-tv
./scripts/install-apk.sh arm
```

If more than one ADB device is connected, the installer intentionally refuses to guess. Select the device explicitly:

```bash
ADB_SERIAL=TV_SERIAL ./scripts/install-apk.sh arm
```

## Low-memory usage recommendations

The app automatically uses Chromium's low-end profile on a 32-bit process, but websites themselves can still consume large amounts of memory. For a small TV box:

- Prefer **D-pad mode** when you do not need the virtual cursor.
- Keep only a few actively used tabs open, especially on 1–2 GB boxes.
- If a heavy page or video tab is repeatedly reloaded by Android, close unused tabs before retrying.
- Prefer 1080p playback over multiple simultaneous heavy/4K pages on very small boxes.
- Do not disable GPU/hardware video acceleration as a generic memory fix; that can move more work to CPU/RAM and cause playback regressions.
- Avoid third-party "RAM cleaner" apps that repeatedly kill browser processes; Android already manages cached processes and Tihulu/Chromium must be allowed to recover normally.

The low-memory profile is deliberately limited to Chromium-supported behavior. It does not trade away site isolation simply to save memory.

## Common 32-bit problems

### `INSTALL_FAILED_NO_MATCHING_ABIS`

The wrong APK was used. Rebuild/install with `arm`, not `arm64`.

### Tabs reload or the renderer disappears under load

This is usually memory pressure. First close unused tabs and retry. If it is reproducible with one normal page, collect logs rather than adding unsafe Chromium flags:

```bash
adb logcat -c
adb logcat
```

Reproduce the problem and keep the section around the crash / low-memory event.

### Browser starts but a native library fails to load

Capture the exact `dlopen`, linker, or `UnsatisfiedLinkError` message from `adb logcat`. Do not install an ARM64 library into a 32-bit package as a workaround.

### Build succeeds for ARM64 but not ARM32

Do not remove the source checkout. ARM32 and ARM64 use the same source tree but different output directories, so an ARM-specific compile failure should be debugged from its first compiler/linker error.

## Updating later

A 32-bit TV should receive a release asset named for ARM32, for example:

```text
Tihulu-TV-Browser-arm.apk
```

The in-app updater must not substitute `arm64` for this device. Android package updates also require the new APK to use the same package identity and compatible signing certificate as the installed version.
