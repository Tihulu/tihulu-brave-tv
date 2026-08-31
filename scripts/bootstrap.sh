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

for tool in git python3 node; do
  command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 2; }
done

NODE_MAJOR="$(node -p 'process.versions.node.split(".")[0]')"
if (( NODE_MAJOR < 24 )); then
  echo "Node.js 24+ is required by current Brave Android tooling (found $(node --version))." >&2
  exit 2
fi

mkdir -p "$WORKSPACE/src"
if [[ ! -d "$WORKSPACE/src/brave/.git" ]]; then
  git clone https://github.com/brave/brave-core.git "$WORKSPACE/src/brave"
fi

cd "$WORKSPACE/src/brave"
if command -v corepack >/dev/null 2>&1; then
  corepack enable >/dev/null 2>&1 || true
fi
if ! command -v pnpm >/dev/null 2>&1; then
  echo "pnpm is required. With Node 24, run: corepack enable" >&2
  exit 2
fi

pnpm install
pnpm run init --target_os=android --target_arch="$ARCH"

cd "$ROOT"
python3 scripts/apply_overlay.py "$WORKSPACE"
python3 scripts/verify_overlay.py "$WORKSPACE"

echo
echo "Brave Android initialized with the TV overlay."
echo "Next: $WORKSPACE/src/build/install-build-deps.sh --android"
echo "Then: ./scripts/build-debug.sh $ARCH"
