#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_ARCH="${1:-arm64}"
WORKSPACE="${BRAVE_TV_WORKSPACE:-$ROOT/.work/brave-browser}"
RECOVERY_JOBS="${BRAVE_GCLIENT_RECOVERY_JOBS:-8}"
RECOVERY_ATTEMPTS="${BRAVE_GCLIENT_RECOVERY_ATTEMPTS:-5}"
RECOVERY_DELAY_SECONDS="${BRAVE_GCLIENT_RECOVERY_DELAY_SECONDS:-90}"

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

validate_positive_integer() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    echo "$name must be a positive integer (found: $value)." >&2
    exit 2
  fi
}

validate_positive_integer BRAVE_GCLIENT_RECOVERY_JOBS "$RECOVERY_JOBS"
validate_positive_integer BRAVE_GCLIENT_RECOVERY_ATTEMPTS "$RECOVERY_ATTEMPTS"
validate_positive_integer BRAVE_GCLIENT_RECOVERY_DELAY_SECONDS "$RECOVERY_DELAY_SECONDS"

mkdir -p "$WORKSPACE/src"
if [[ ! -d "$WORKSPACE/src/brave/.git" ]]; then
  git clone https://github.com/brave/brave-core.git "$WORKSPACE/src/brave"
fi

cd "$WORKSPACE/src/brave"
pnpm_run install

# A first Chromium checkout launches many gclient SCM operations in parallel. Public
# googlesource endpoints can temporarily return HTTP 429 / RESOURCE_EXHAUSTED even
# though the large Chromium repository itself downloaded successfully. Never delete
# the checkout in that case: resume only the missing dependencies with bounded gclient
# parallelism and exponential-ish backoff, then let Brave's normal sync finish patches
# and hooks.
recover_gclient_sync() {
  local chromium_tag chromium_ref attempt delay
  chromium_tag="$(node -e 'const p=require("./package.json"); process.stdout.write(String(p.config?.projects?.chrome?.tag || ""))')"
  if [[ -z "$chromium_tag" ]]; then
    echo "Unable to determine Chromium tag from Brave package.json." >&2
    return 1
  fi
  if [[ "$chromium_tag" == refs/* ]]; then
    chromium_ref="$chromium_tag"
  else
    chromium_ref="refs/tags/$chromium_tag"
  fi

  for ((attempt = 1; attempt <= RECOVERY_ATTEMPTS; attempt++)); do
    delay=$((RECOVERY_DELAY_SECONDS * attempt))
    echo >&2
    echo "Brave init did not finish. Preserving the existing Chromium checkout." >&2
    echo "Recovery attempt $attempt/$RECOVERY_ATTEMPTS: waiting ${delay}s, then resuming gclient with --jobs=$RECOVERY_JOBS." >&2
    sleep "$delay"

    if pnpm_run run gclient -- \
        sync \
        --nohooks \
        --reset \
        --upstream \
        --revision "src@$chromium_ref" \
        --force \
        --jobs="$RECOVERY_JOBS"; then
      echo "Bounded gclient recovery completed; finishing through Brave sync." >&2
      if pnpm_run run sync --target_os=android --target_arch="$ARCH"; then
        return 0
      fi
      echo "Brave sync still failed; the next attempt will reuse everything already downloaded." >&2
    else
      echo "gclient recovery failed; the next attempt will reuse everything already downloaded." >&2
    fi
  done

  echo "Brave bootstrap still failed after $RECOVERY_ATTEMPTS recovery attempts." >&2
  echo "Do not delete $WORKSPACE; rerun this script later to continue the existing checkout." >&2
  return 1
}

if ! pnpm_run run init --target_os=android --target_arch="$ARCH"; then
  recover_gclient_sync
fi

cd "$ROOT"
python3 scripts/apply_overlay.py "$WORKSPACE"
python3 scripts/verify_overlay.py "$WORKSPACE"

echo
echo "Brave Android initialized with the TV overlay."
echo "Next: $WORKSPACE/src/build/install-build-deps.sh --android"
echo "Then: ./scripts/build-debug.sh $ARCH"
