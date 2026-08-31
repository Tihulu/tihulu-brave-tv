#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_ARCH="${1:-arm64}"
WORKSPACE="${BRAVE_TV_WORKSPACE:-$ROOT/.work/brave-browser}"

command -v adb >/dev/null || { echo "adb is not installed" >&2; exit 2; }
command -v unzip >/dev/null || { echo "unzip is required to validate the APK" >&2; exit 2; }

ADB=(adb)
if [[ -n "${ADB_SERIAL:-}" ]]; then
  ADB=(adb -s "$ADB_SERIAL")
  if [[ "$("${ADB[@]}" get-state 2>/dev/null || true)" != "device" ]]; then
    echo "ADB_SERIAL=$ADB_SERIAL is not a ready adb device." >&2
    adb devices >&2 || true
    exit 2
  fi
else
  mapfile -t READY_DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')
  if (( ${#READY_DEVICES[@]} == 0 )); then
    echo "No adb device is ready. Check 'adb devices'." >&2
    adb devices >&2 || true
    exit 2
  fi
  if (( ${#READY_DEVICES[@]} > 1 )); then
    echo "More than one adb device is ready; refusing to guess which one is the TV." >&2
    printf '  %s\n' "${READY_DEVICES[@]}" >&2
    echo "Retry with ADB_SERIAL=<serial> INSTALL_TO_TV=1 ..." >&2
    exit 2
  fi
  ADB=(adb -s "${READY_DEVICES[0]}")
fi

# Select by the APK's actual native lib ABI instead of a fuzzy output-directory name.
if ! APK="$(python3 "$ROOT/scripts/find_apk.py" "$WORKSPACE/src/out" "$INPUT_ARCH")"; then
  echo "No valid Brave APK matching target architecture '$INPUT_ARCH' was found. Build it first." >&2
  exit 1
fi

if ! unzip -tq "$APK" >/dev/null; then
  echo "Refusing to install an invalid or truncated APK: $APK" >&2
  exit 1
fi

# The requested build architecture should also be supported by the selected Android target.
# This catches a stale command such as install-apk.sh arm64 against a 32-bit-only TV before adb
# reaches INSTALL_FAILED_NO_MATCHING_ABIS.
case "$INPUT_ARCH" in
  arm64|arm64-v8a) REQUIRED_DEVICE_ABI="arm64-v8a" ;;
  arm|armeabi-v7a) REQUIRED_DEVICE_ABI="armeabi-v7a" ;;
  x64|x86_64) REQUIRED_DEVICE_ABI="x86_64" ;;
  x86) REQUIRED_DEVICE_ABI="x86" ;;
  *) echo "Unsupported architecture: $INPUT_ARCH" >&2; exit 2 ;;
esac
DEVICE_ABILIST="$("${ADB[@]}" shell getprop ro.product.cpu.abilist 2>/dev/null | tr -d '\r')"
if [[ -n "$DEVICE_ABILIST" && ",${DEVICE_ABILIST}," != *",${REQUIRED_DEVICE_ABI},"* ]]; then
  echo "Connected Android device does not advertise required ABI $REQUIRED_DEVICE_ABI." >&2
  echo "Device ABI list: ${DEVICE_ABILIST:-unknown}" >&2
  echo "Refusing to attempt a cross-ABI install." >&2
  exit 2
fi

echo "Installing $APK"
"${ADB[@]}" install -r "$APK"
