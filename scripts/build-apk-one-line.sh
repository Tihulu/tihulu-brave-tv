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

# Do not infer ABI from an output-directory substring: an ARM32 search such as "*arm*"
# can also match an ARM64 output. Inspect the APK's lib/<abi>/ entries instead.
if ! APK="$(python3 "$ROOT/scripts/find_apk.py" "$WORKSPACE/src/out" "$INPUT_ARCH")"; then
  echo "Build finished but no APK matching target architecture '$INPUT_ARCH' was found." >&2
  exit 1
fi

if ! unzip -tq "$APK" >/dev/null; then
  echo "Build produced an invalid or truncated APK: $APK" >&2
  exit 1
fi

echo
echo "Tihulu TV Browser APK ready:"
echo "$APK"
echo
if [[ "${INSTALL_TO_TV:-0}" == "1" ]]; then
  "$ROOT/scripts/install-apk.sh" "$INPUT_ARCH"
else
  echo "Set INSTALL_TO_TV=1 to install automatically when an adb device is connected."
fi
