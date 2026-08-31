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

for tool in git python3 node; do
  command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 2; }
done

GIT_VERSION="$(git --version | awk '{print $3}')"
if [[ "$(printf '%s\n%s\n' 2.41.0 "$GIT_VERSION" | sort -V | head -n1)" != "2.41.0" ]]; then
  echo "Git 2.41+ is required by current Brave Android tooling (found $GIT_VERSION)." >&2
  exit 2
fi
NODE_VERSION="$(node -p 'process.versions.node')"
if [[ "$(printf '%s\n%s\n' 24.16.0 "$NODE_VERSION" | sort -V | head -n1)" != "24.16.0" ]] \
    || [[ "$(printf '%s\n%s\n' 25.0.0 "$NODE_VERSION" | sort -V | head -n1)" == "25.0.0" ]]; then
  echo "Current Brave Core requires Node >=24.16.0 and <25 (found $(node --version))." >&2
  exit 2
fi
if command -v pnpm >/dev/null 2>&1; then
  PNPM_VERSION="$(pnpm --version)"
elif command -v corepack >/dev/null 2>&1; then
  PNPM_VERSION="$(corepack pnpm --version 2>/dev/null || echo 0)"
else
  PNPM_VERSION="0"
fi
if [[ "$(printf '%s\n%s\n' 11.9.0 "$PNPM_VERSION" | sort -V | head -n1)" != "11.9.0" ]]; then
  echo "Current Brave Core requires pnpm >=11.9.0 (found $PNPM_VERSION)." >&2
  exit 2
fi

pnpm_run() {
  if command -v pnpm >/dev/null 2>&1; then
    pnpm "$@"
  elif command -v corepack >/dev/null 2>&1; then
    corepack pnpm "$@"
  else
    echo "pnpm/Corepack is required." >&2
    return 2
  fi
}

mkdir -p "$WORKSPACE/src"
if [[ ! -d "$WORKSPACE/src/brave/.git" ]]; then
  git clone https://github.com/brave/brave-core.git "$WORKSPACE/src/brave"
fi

cd "$WORKSPACE/src/brave"
pnpm_run install
pnpm_run run init --target_os=android --target_arch="$ARCH"

cd "$ROOT"
python3 scripts/apply_overlay.py "$WORKSPACE"
python3 scripts/verify_overlay.py "$WORKSPACE"

echo
echo "Brave Android initialized with the TV overlay."
echo "Next: $WORKSPACE/src/build/install-build-deps.sh --android"
echo "Then: ./scripts/build-debug.sh $ARCH"
