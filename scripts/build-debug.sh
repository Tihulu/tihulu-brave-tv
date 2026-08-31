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

python3 "$ROOT/scripts/apply_overlay.py" "$WORKSPACE"
python3 "$ROOT/scripts/verify_overlay.py" "$WORKSPACE"

cd "$WORKSPACE/src/brave"
pnpm run build Debug \
  --target_os=android \
  --target_arch="$ARCH" \
  --target_android_output_format=apk
