#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_ARCH="${1:-${ARCH:-arm64}}"
WORKSPACE="${BRAVE_TV_WORKSPACE:-$ROOT/.work/brave-browser}"

"$ROOT/scripts/install-host-deps.sh"
# shellcheck disable=SC1091
source "$ROOT/.tools/env.sh"

"$ROOT/scripts/check.sh"
"$ROOT/scripts/bootstrap.sh" "$INPUT_ARCH"

# Chromium's dependency helper installs the large platform-specific dependency set
# after init. Pop!_OS is Ubuntu-derived but may be rejected by Chromium's distro gate.
# Use --unsupported there while keeping the normal path for Ubuntu and Debian.
# shellcheck disable=SC1091
source /etc/os-release
if [[ "${ID:-}" == "pop" ]]; then
  "$WORKSPACE/src/build/install-build-deps.sh" --android --unsupported
else
  if ! "$WORKSPACE/src/build/install-build-deps.sh" --android; then
    echo "Standard Chromium dependency install failed; retrying with --unsupported." >&2
    "$WORKSPACE/src/build/install-build-deps.sh" --android --unsupported
  fi
fi

if [[ -z "${JAVA_OPTS:-}" ]]; then
  MEM_KB="$(awk '/MemTotal:/ {print $2; exit}' /proc/meminfo)"
  if (( MEM_KB >= 16 * 1024 * 1024 )); then
    export JAVA_OPTS="-Xmx10G -Xms1G"
  elif (( MEM_KB >= 10 * 1024 * 1024 )); then
    export JAVA_OPTS="-Xmx6G -Xms512m"
  else
    export JAVA_OPTS="-Xmx4G -Xms512m"
  fi
fi
"$ROOT/scripts/build-debug.sh" "$INPUT_ARCH"

case "$INPUT_ARCH" in
  arm64|arm64-v8a) ARCH_NAME=arm64 ;;
  arm|armeabi-v7a) ARCH_NAME=arm ;;
  x64|x86_64) ARCH_NAME=x64 ;;
  x86) ARCH_NAME=x86 ;;
  *) echo "Unsupported architecture: $INPUT_ARCH" >&2; exit 2 ;;
esac

mapfile -t APKS < <(
  find "$WORKSPACE/src/out" -type f -path "*/android_*${ARCH_NAME}*/apks/*.apk" \
    -iname '*Brave*.apk' -printf '%T@ %p\n' 2>/dev/null | sort -nr | cut -d' ' -f2-
)
if (( ${#APKS[@]} == 0 )); then
  echo "Build finished but no Brave APK was found under $WORKSPACE/src/out." >&2
  exit 1
fi

echo
echo "Tihulu TV Browser APK ready:"
echo "${APKS[0]}"
echo
if [[ "${INSTALL_TO_TV:-0}" == "1" ]]; then
  "$ROOT/scripts/install-apk.sh" "$INPUT_ARCH"
else
  echo "Set INSTALL_TO_TV=1 to install automatically when an adb device is connected."
fi
