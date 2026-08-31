#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_ARCH="${1:-arm64}"
WORKSPACE="${BRAVE_TV_WORKSPACE:-$ROOT/.work/brave-browser}"
case "$INPUT_ARCH" in
  arm64|arm64-v8a) ARCH=arm64 ;;
  arm|armeabi-v7a) ARCH=arm ;;
  x64|x86_64) ARCH=x64 ;;
  x86) ARCH=x86 ;;
  *) echo "Unsupported architecture: $INPUT_ARCH" >&2; exit 2 ;;
esac

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

mapfile -t APKS < <(
  find "$WORKSPACE/src/out" -type f -path "*/android_*${ARCH}*/apks/*.apk" \
    -iname '*Brave*.apk' -printf '%T@ %p\n' 2>/dev/null | sort -nr | cut -d' ' -f2-
)
if (( ${#APKS[@]} == 0 )); then
  echo "No Brave APK found under $WORKSPACE/src/out. Build first." >&2
  exit 1
fi

APK="${APKS[0]}"
if ! unzip -tq "$APK" >/dev/null; then
  echo "Refusing to install an invalid or truncated APK: $APK" >&2
  exit 1
fi

echo "Installing $APK"
"${ADB[@]}" install -r "$APK"
