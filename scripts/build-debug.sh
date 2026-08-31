#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_ARCH="${1:-arm64}"
WORKSPACE="${BRAVE_TV_WORKSPACE:-$ROOT/.work/brave-browser}"
CHROMIUM_ROOT="$WORKSPACE/src"
BRAVE_CORE="$CHROMIUM_ROOT/brave"
COMPAT_FILES=(
  browser/brave_ads/ads_service_factory.h
  browser/brave_ads/ads_service_factory.cc
  browser/sources.gni
  android/java/org/chromium/chrome/browser/firstrun/WelcomeOnboardingActivity.java
)
COMPAT_MARKERS_REGEX='TIHULU_ANDROID_ADS_TOOLTIP_COMPAT|TIHULU_TV_ONBOARDING_CURSOR_COMPAT'
CHROMIUM_COMPAT_FILES=(
  third_party/blink/renderer/core/html/resources/html.css
  ui/android/java/src/org/chromium/ui/base/EventForwarder.java
)
CHROMIUM_COMPAT_MARKERS_REGEX='TIHULU_TV_FOCUS_RING_COMPAT|TIHULU_TV_MOUSE_EVENT_COMPAT'
COMPAT_APPLIED=0
CHROMIUM_COMPAT_APPLIED=0

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

if [[ ! -d "$BRAVE_CORE/.git" ]] || [[ ! -d "$CHROMIUM_ROOT/.git" ]]; then
  echo "Missing initialized Brave/Chromium checkout at $WORKSPACE. Run bootstrap first." >&2
  exit 2
fi

# Fail before touching the checkout or starting a large Android build if the pinned Brave/Chromium
# initialization contract drifted. In particular, keep Brave Shields/adblock wired through the
# normal browser-process/component-updater path and reject unsafe ARM32 process/security shortcuts.
python3 "$ROOT/scripts/verify_brave_runtime_contract.py" "$WORKSPACE"

# Compatibility patches are intentionally ephemeral. This keeps the pinned Brave
# checkout clean between runs, so bootstrap/ref switching never mistakes them for user
# work. Recover only our own marked leftovers from an interrupted prior run; never
# reset an unknown user modification.
for compat_file in "${COMPAT_FILES[@]}"; do
  if ! git -C "$BRAVE_CORE" diff --quiet -- "$compat_file" \
      || ! git -C "$BRAVE_CORE" diff --cached --quiet -- "$compat_file"; then
    if grep -Eq "$COMPAT_MARKERS_REGEX" "$BRAVE_CORE/$compat_file" 2>/dev/null; then
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

# The TV focus ring and the virtual-mouse bridge modify two tracked Chromium files only while
# building. Treat interrupted owned markers as recoverable, but never reset unrelated local edits.
for compat_file in "${CHROMIUM_COMPAT_FILES[@]}"; do
  if ! git -C "$CHROMIUM_ROOT" diff --quiet -- "$compat_file" \
      || ! git -C "$CHROMIUM_ROOT" diff --cached --quiet -- "$compat_file"; then
    if grep -Eq "$CHROMIUM_COMPAT_MARKERS_REGEX" "$CHROMIUM_ROOT/$compat_file" 2>/dev/null; then
      echo "Recovering an owned Chromium TV compatibility patch: $compat_file" >&2
      git -C "$CHROMIUM_ROOT" restore --staged --worktree -- "$compat_file" 2>/dev/null \
        || git -C "$CHROMIUM_ROOT" restore -- "$compat_file"
    else
      echo "Refusing to overwrite unknown local Chromium change: $compat_file" >&2
      echo "Commit/stash/revert that change before building." >&2
      exit 2
    fi
  fi
done

cleanup_compat() {
  if (( COMPAT_APPLIED != 0 )); then
    git -C "$BRAVE_CORE" restore -- "${COMPAT_FILES[@]}" 2>/dev/null || true
  fi
  if (( CHROMIUM_COMPAT_APPLIED != 0 )); then
    git -C "$CHROMIUM_ROOT" restore -- "${CHROMIUM_COMPAT_FILES[@]}" 2>/dev/null || true
  fi
}
trap cleanup_compat EXIT INT TERM

# Mark cleanup active before patching, so even a rare partial filesystem write is
# reverted on failure. All compatibility files were proven clean immediately above.
COMPAT_APPLIED=1
python3 "$ROOT/scripts/apply_brave_android_compat.py" "$WORKSPACE"
python3 "$ROOT/scripts/apply_brave_tv_onboarding_compat.py" "$WORKSPACE"

# Do not rewrite identical Tihulu Java/resources on every retry. The fingerprinted
# wrapper still runs the full verifier and reapplies automatically when any overlay
# input, branding asset, or generated engine version changes.
python3 "$ROOT/scripts/ensure_overlay.py" "$WORKSPACE"

# Chromium 152 renders normal keyboard focus with a subtle 1px UA outline. On a TV that
# is not reliably visible, and author CSS can suppress it. Apply a build-only UA rule
# with a strong !important outline. This is renderer-native and adds no JavaScript or
# per-key IPC to the D-pad hot path.
CHROMIUM_COMPAT_APPLIED=1
python3 "$ROOT/scripts/apply_chromium_tv_focus_compat.py" "$WORKSPACE"

# Android 11 keeps MotionEvent.setActionButton() out of the public SDK even though Chromium needs
# the changed-button value for mouse press/release. Expose one narrow EventForwarder method that
# forwards Tihulu's primary-button transitions through Chromium's existing JNI mouse path.
python3 "$ROOT/scripts/apply_chromium_tv_mouse_compat.py" "$WORKSPACE"

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

# Do not compile out Rewards/Brave Ads with enable_brave_rewards=false or
# enable_brave_ads=false on the pinned Brave 1.94 Android graph. In this revision,
# Android Java/JNI registration still lists BraveRewardsNativeWorker and
# BraveAdsNativeHelper unconditionally. Removing only their native deps leaves
# generated libchrome JNI registrations pointing at undefined Muxed_* symbols.
#
# ARM32 memory savings therefore stay on the supported runtime path instead:
# Chromium low-end-device mode plus lazy Tihulu overlays. Rewards remains an
# upstream opt-in feature and Tihulu's TV UI does not invoke it. Shields/ad blocking
# remains unaffected.
if [[ "$ARCH" == "arm" ]]; then
  echo "ARM32 low-memory build: preserving Brave Android JNI feature graph; Chromium low-end mode remains enabled and Shields remains enabled." >&2
fi

"${PNPM[@]}" "${BUILD_ARGS[@]}"

# Brave may leave ErrorProne/NullAway work running after the main Ninja graph reports
# success. Do not let a caller install an APK while those checks can still fail in the
# background. The user's shell commonly chains this script with `&& install-apk.sh`,
# so a static-analysis failure must be visible as a build failure first.
cd "$CHROMIUM_ROOT"
if [[ -f build/android/fast_local_dev_server.py ]]; then
  echo "Waiting for Chromium background static analysis to finish..." >&2
  python3 build/android/fast_local_dev_server.py --wait-for-idle
fi
