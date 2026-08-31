#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_ARCH="${1:-arm64}"
WORKSPACE="${BRAVE_TV_WORKSPACE:-$ROOT/.work/brave-browser}"
BRAVE_CORE="$WORKSPACE/src/brave"
COMPAT_FILES=(
  browser/brave_ads/ads_service_factory.h
  browser/brave_ads/ads_service_factory.cc
)
COMPAT_MARKER="TIHULU_ANDROID_ADS_TOOLTIP_COMPAT"
COMPAT_APPLIED=0

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

if [[ ! -d "$BRAVE_CORE/.git" ]]; then
  echo "Missing initialized Brave checkout at $BRAVE_CORE. Run bootstrap first." >&2
  exit 2
fi

# The compatibility patch is intentionally ephemeral. This keeps the pinned Brave
# checkout clean between runs, so bootstrap/ref switching never mistakes it for user
# work. Recover only our own marked leftovers from an interrupted prior run; never
# reset an unknown user modification.
for compat_file in "${COMPAT_FILES[@]}"; do
  if ! git -C "$BRAVE_CORE" diff --quiet -- "$compat_file" \
      || ! git -C "$BRAVE_CORE" diff --cached --quiet -- "$compat_file"; then
    if grep -q "$COMPAT_MARKER" "$BRAVE_CORE/$compat_file" 2>/dev/null; then
      echo "Recovering an owned compatibility patch left by an interrupted build: $compat_file" >&2
      git -C "$BRAVE_CORE" restore --staged --worktree -- "$compat_file" 2>/dev/null \
        || git -C "$BRAVE_CORE" restore -- "$compat_file"
    else
      echo "Refusing to overwrite unknown local Brave change: $compat_file" >&2
      echo "Commit/stash/revert that change before building." >&2
      exit 2
    fi
  fi
done

cleanup_compat() {
  if (( COMPAT_APPLIED != 0 )); then
    git -C "$BRAVE_CORE" restore -- "${COMPAT_FILES[@]}" 2>/dev/null || true
  fi
}
trap cleanup_compat EXIT INT TERM

# Mark cleanup active before patching, so even a rare partial filesystem write is
# reverted on failure. The two files were proven clean immediately above.
COMPAT_APPLIED=1
python3 "$ROOT/scripts/apply_brave_android_compat.py" "$WORKSPACE"

# Do not rewrite identical Tihulu Java/resources on every retry. The fingerprinted
# wrapper still runs the full verifier and reapplies automatically when any overlay
# input, branding asset, or generated engine version changes.
python3 "$ROOT/scripts/ensure_overlay.py" "$WORKSPACE"

cd "$BRAVE_CORE"
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
