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

# Chromium forces a small i386 multilib set for Android builds on x86_64 hosts. Pop!_OS can
# expose a newer amd64 linux-libc-dev from its kernel stack while Ubuntu's i386 archive has an
# older candidate, which APT refuses to co-install. Run the upstream installer through our narrow
# compatibility wrapper: it preserves every other dependency and only omits that one conflicting
# i386 kernel-UAPI package when the mismatch is actually detected. Never downgrade Pop's amd64
# linux-libc-dev automatically.
DEPS_INSTALLER="$WORKSPACE/src/build/install-build-deps.py"
DEPS_WRAPPER="$ROOT/scripts/install_chromium_build_deps.py"
DEPS_ARGS=(--android --no-syms --no-chromeos-fonts --no-backwards-compatible --no-prompt)
# shellcheck disable=SC1091
source /etc/os-release
if [[ "${ID:-}" == "pop" ]]; then
  python3 "$DEPS_WRAPPER" "$DEPS_INSTALLER" "${DEPS_ARGS[@]}" --unsupported
else
  if ! python3 "$DEPS_WRAPPER" "$DEPS_INSTALLER" "${DEPS_ARGS[@]}"; then
    echo "Standard Chromium dependency install failed; retrying with --unsupported." >&2
    python3 "$DEPS_WRAPPER" "$DEPS_INSTALLER" "${DEPS_ARGS[@]}" --unsupported
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
