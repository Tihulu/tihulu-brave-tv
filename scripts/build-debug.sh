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

"${PNPM[@]}" run build Debug \
  --target_os=android \
  --target_arch="$ARCH" \
  --target_android_output_format=apk
