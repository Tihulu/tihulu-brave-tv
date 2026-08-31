# ADB installation guide for Google TV / Android TV

This guide explains how to connect an Ubuntu, Pop!_OS or Debian computer to a Google TV / Android TV device and install Tihulu TV Browser with ADB.

ADB means **Android Debug Bridge**. It lets the computer talk to the TV for development tasks such as installing an APK, reading logs and restarting an app. Root access is not required.

## 1. Install ADB on the computer

The Tihulu TV Browser one-line installer already installs ADB automatically. To install it manually:

```bash
sudo apt update
sudo apt install adb
```

Verify it:

```bash
adb version
```

## 2. Enable developer options on the TV

Menu names differ slightly between Google TV manufacturers, but the usual path is:

1. Open **Settings**.
2. Open **System**.
3. Open **About**.
4. Highlight **Android TV OS build** or **Build**.
5. Press **OK** repeatedly until the TV says developer mode is enabled.
6. Go back to **System** and open **Developer options**.

## 3. USB debugging

If the TV or Android TV box supports an ADB-capable USB connection:

1. Enable **USB debugging** in Developer options.
2. Connect the TV/device to the computer.
3. Run:

```bash
adb devices
```

4. The TV may ask whether to allow debugging from this computer. Accept it. Selecting **Always allow from this computer** is optional.

A working result looks similar to:

```text
List of devices attached
0123456789ABCDEF    device
```

If the state is `unauthorized`, check the TV for an authorization prompt.

## 4. Wireless debugging

Wireless ADB is usually the easiest option for Google TV because no USB cable is required.

The computer and TV should normally be on the same local network.

### Pair the computer

1. On the TV, enable **Wireless debugging**.
2. Open **Pair device with pairing code**.
3. The TV shows an IP address, pairing port and pairing code.
4. On the computer run:

```bash
adb pair TV_IP:PAIRING_PORT
```

Example:

```bash
adb pair 192.168.1.80:37123
```

5. Enter the pairing code shown on the TV.

A successful pairing reports a message similar to:

```text
Successfully paired to 192.168.1.80:37123
```

### Connect after pairing

The Wireless debugging screen also shows an IP/port used for the normal ADB connection. This port can be different from the pairing port.

Run:

```bash
adb connect TV_IP:ADB_PORT
```

Example:

```bash
adb connect 192.168.1.80:39877
```

Then verify:

```bash
adb devices
```

The device should appear with state `device`.

## 5. Build and automatically install Tihulu TV Browser

Once `adb devices` shows the TV as `device`, the one-line command can build the APK and install it automatically:

```bash
curl -fsSL https://raw.githubusercontent.com/Tihulu/tihulu-brave-tv/main/install.sh | INSTALL_TO_TV=1 bash
```

The installer:

1. installs the required Ubuntu/Pop!_OS/Debian host dependencies,
2. prepares Brave/Chromium,
3. applies the TV overlay,
4. builds the APK,
5. discovers the newest APK for the selected architecture,
6. installs it with `adb install -r`.

The default target is `arm64`, which is appropriate for most modern Google TV hardware.

## 6. Install an already-built APK

If the APK has already been built, there is no need to run the full build again:

```bash
cd ~/tihulu-brave-tv
./scripts/install-apk.sh arm64
```

The helper verifies that an ADB device is ready and selects the newest matching Brave APK from the build output.

You can also install a specific APK manually:

```bash
adb install -r /path/to/app.apk
```

`-r` means reinstall/update the existing app while keeping its application data when Android permits it.

## 7. Multiple ADB devices

If more than one Android device is connected, `adb devices` may show several entries. Standard ADB commands then need a serial/device selector.

List devices:

```bash
adb devices
```

Run a command against one device:

```bash
adb -s DEVICE_SERIAL shell
```

Or for a network device:

```bash
adb -s 192.168.1.80:39877 shell
```

The current `scripts/install-apk.sh` expects the normal single-ready-device workflow. Disconnect unrelated Android devices before automatic installation if ADB reports an ambiguous target.

## 8. Common connection problems

### `unauthorized`

Run:

```bash
adb devices
```

Then check the TV for the authorization dialog and approve the computer.

If no dialog appears, disable and re-enable debugging on the TV and reconnect.

### `offline`

Restart the ADB server:

```bash
adb kill-server
adb start-server
adb devices
```

For wireless ADB, reconnect afterward:

```bash
adb connect TV_IP:ADB_PORT
```

### `connection refused` or timeout

Check that:

- Wireless debugging is still enabled.
- The IP address has not changed.
- You are using the normal ADB connection port, not the pairing port.
- The computer and TV can reach each other on the local network.
- Guest Wi-Fi/client isolation is not blocking device-to-device traffic.

### TV IP or port changed

Wireless debugging ports may change after reboots or after toggling the feature. Reopen the TV's Wireless debugging screen and use the currently displayed values.

## 9. Useful verification commands

Show connected devices:

```bash
adb devices
```

Open an Android shell on the TV:

```bash
adb shell
```

Show basic device properties:

```bash
adb shell getprop ro.product.model
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.build.version.release
```

The CPU ABI is useful before choosing an APK architecture. Most recent Google TV devices report an ARM ABI, typically `arm64-v8a` or sometimes a 32-bit ARM userspace.

## 10. After installation

After a successful install, open the Google TV app launcher and look for **Tihulu TV Browser**.

Before treating a build as release-ready, test remote navigation, text input, cursor mode, tabs, fullscreen video, suspend/resume and Brave Shields on the physical TV. CI validation does not replace a real-device smoke test.
