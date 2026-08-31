#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_ARCH="${1:-arm64}"
WORKSPACE="${BRAVE_TV_WORKSPACE:-$ROOT/.work/brave-browser}"

if [[ -f "$ROOT/.tools/env.sh" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT/.tools/env.sh"
fi

case "$INPUT_ARCH" in
  arm64|arm64-v8a) ARCH=arm64 ;;
  arm|armeabi-v7a) ARCH=arm ;;
  x64|x86_64) ARCH=x64 ;;
  x86) ARCH=x86 ;;
  *) echo "Unsupported architecture: $INPUT_ARCH" >&2; exit 2 ;;
esac

# Apply narrow Brave upstream compatibility fixes before the Tihulu overlay. The
# compatibility script is idempotent and fails closed if the pinned Brave source
# no longer matches the audited Android link hazard.
python3 "$ROOT/scripts/apply_brave_android_compat.py" "$WORKSPACE"
python3 "$ROOT/scripts/apply_overlay.py" "$WORKSPACE"
python3 "$ROOT/scripts/verify_overlay.py" "$WORKSPACE"

cd "$WORKSPACE/src/brave"
if command -v pnpm >/dev/null 2>&1; then
  PNPM=(pnpm)
elif command -v corepack >/dev/null 2>&1; then
  PNPM=(corepack pnpm)
else
  echo "pnpm/Corepack is required." >&2
  exit 2
fi

# Brave's Debug preset is a component build. Chromium explicitly forbids component
# builds on Android, so use the non-component Static preset for local APK builds.
BUILD_ARGS=(
  run build Static
  --target_os=android
  --target_arch="$ARCH"
  --target_android_output_format=apk
)

# ARM32 TV boxes have a much smaller process address space and are commonly RAM
# constrained. Brave Rewards/Brave Ads are not required for Shields/ad blocking or
# normal web browsing, so omit those subsystems from the 32-bit TV build. This cuts
# background/service code and also avoids desktop-only Ads tooltip code paths that
# have no Android implementation. Keep the normal Brave feature set on 64-bit builds.
if [[ "$ARCH" == "arm" ]]; then
  echo "ARM32 low-memory build: disabling Brave Rewards and Brave Ads; Shields remains enabled." >&2
  BUILD_ARGS+=(
    --gn=enable_brave_rewards:false
    --gn=enable_brave_ads:false
  )
fi

"${PNPM[@]}" "${BUILD_ARGS[@]}"
