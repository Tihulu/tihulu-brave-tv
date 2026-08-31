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
if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device is ready. Check 'adb devices'." >&2
  exit 2
fi

mapfile -t APKS < <(find "$WORKSPACE/src/out" -type f -path "*/android_*${ARCH}*/apks/*.apk" -iname '*Brave*.apk' -printf '%T@ %p\n' 2>/dev/null | sort -nr | cut -d' ' -f2-)
if (( ${#APKS[@]} == 0 )); then
  echo "No Brave APK found under $WORKSPACE/src/out. Build first." >&2
  exit 1
fi

echo "Installing ${APKS[0]}"
adb install -r "${APKS[0]}"
