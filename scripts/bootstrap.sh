#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_ARCH="${1:-arm64}"
WORKSPACE="${BRAVE_TV_WORKSPACE:-$ROOT/.work/brave-browser}"
RECOVERY_JOBS="${BRAVE_GCLIENT_RECOVERY_JOBS:-8}"
RECOVERY_ATTEMPTS="${BRAVE_GCLIENT_RECOVERY_ATTEMPTS:-5}"
RECOVERY_DELAY_SECONDS="${BRAVE_GCLIENT_RECOVERY_DELAY_SECONDS:-90}"
HOOK_PYTHON="$ROOT/.tools/python/bin/python3"
SYNC_MARKER="$WORKSPACE/.brave_latest_successful_sync.json"
PINNED_REF_FILE="$ROOT/config/brave-core-ref"

if [[ -f "$ROOT/.tools/env.sh" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT/.tools/env.sh"
fi

if [[ -n "${BRAVE_CORE_REF:-}" ]]; then
  TARGET_BRAVE_REF="$BRAVE_CORE_REF"
elif [[ -f "$PINNED_REF_FILE" ]]; then
  TARGET_BRAVE_REF="$(tr -d '[:space:]' < "$PINNED_REF_FILE")"
else
  echo "Missing pinned Brave ref: $PINNED_REF_FILE" >&2
  exit 2
fi
if [[ -z "$TARGET_BRAVE_REF" ]]; then
  echo "The pinned Brave ref is empty." >&2
  exit 2
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

if [[ ! -x "$HOOK_PYTHON" ]] || ! "$HOOK_PYTHON" -m pip --version >/dev/null 2>&1; then
  echo "Brave runhooks require the isolated build Python with pip at $HOOK_PYTHON." >&2
  echo "Run ./scripts/install-host-deps.sh, then retry." >&2
  exit 2
fi

GIT_VERSION="$(git --version | awk '{print $3}')"
if [[ "$(printf '%s\n%s\n' 2.46.0 "$GIT_VERSION" | sort -V | head -n1)" != "2.46.0" ]]; then
  echo "Git 2.46+ is required by this build wrapper (found $GIT_VERSION)." >&2
  echo "Run ./scripts/install-host-deps.sh to install the project-local Git." >&2
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
BRAVE_CORE="$WORKSPACE/src/brave"
if [[ ! -d "$BRAVE_CORE/.git" ]]; then
  git clone https://github.com/brave/brave-core.git "$BRAVE_CORE"
fi

# The Tihulu overlay intentionally edits two tracked brave-core files, adds the tv/
# directory and adds two generated Android branding files. Remove only those known
# generated changes before changing or syncing the Brave ref. Any other local
# brave-core edit is treated as user work and blocks the build rather than being reset.
clean_generated_brave_overlay() {
  local status line path unexpected=0
  status="$(git -C "$BRAVE_CORE" status --porcelain --untracked-files=normal)"
  [[ -z "$status" ]] && return 0

  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    path="${line:3}"
    case "$path" in
      android/brave_java_sources.gni)
        if ! grep -q "TIHULU_TV_BROWSER_JAVA_BEGIN" "$BRAVE_CORE/$path" 2>/dev/null; then
          echo "Unexpected local change in brave-core: $path" >&2
          unexpected=1
        fi
        ;;
      android/java/org/chromium/chrome/browser/BraveApplicationImplBase.java)
        if ! grep -q "TIHULU_TV_BROWSER_SPATIAL_NAV_BEGIN" "$BRAVE_CORE/$path" 2>/dev/null; then
          echo "Unexpected local change in brave-core: $path" >&2
          unexpected=1
        fi
        ;;
      android/java/org/chromium/chrome/browser/tv|android/java/org/chromium/chrome/browser/tv/)
        ;;
      android/java/res/drawable-nodpi/tihulu_tv_banner.png|android/java/res/drawable-nodpi/tihulu_tv_icon.png)
        ;;
      *)
        echo "Unexpected local change in brave-core: $path" >&2
        unexpected=1
        ;;
    esac
  done <<<"$status"

  if (( unexpected != 0 )); then
    echo "Refusing to reset unknown brave-core changes. Commit/stash them or use a clean build workspace." >&2
    return 1
  fi

  git -C "$BRAVE_CORE" restore -- android/brave_java_sources.gni \
    android/java/org/chromium/chrome/browser/BraveApplicationImplBase.java 2>/dev/null || true
  rm -rf "$BRAVE_CORE/android/java/org/chromium/chrome/browser/tv"
  rm -f "$BRAVE_CORE/android/java/res/drawable-nodpi/tihulu_tv_banner.png" \
    "$BRAVE_CORE/android/java/res/drawable-nodpi/tihulu_tv_icon.png"
}

checkout_brave_ref() {
  local current_tag=""
  clean_generated_brave_overlay
  current_tag="$(git -C "$BRAVE_CORE" describe --tags --exact-match HEAD 2>/dev/null || true)"

  if [[ "$current_tag" == "$TARGET_BRAVE_REF" ]]; then
    echo "Brave core already pinned at $TARGET_BRAVE_REF." >&2
    return 0
  fi

  echo "Pinning Brave core to $TARGET_BRAVE_REF (override with BRAVE_CORE_REF only for deliberate testing)." >&2
  if [[ "$TARGET_BRAVE_REF" == v* ]]; then
    git -C "$BRAVE_CORE" fetch --force origin \
      "refs/tags/$TARGET_BRAVE_REF:refs/tags/$TARGET_BRAVE_REF"
    git -C "$BRAVE_CORE" checkout --detach "$TARGET_BRAVE_REF"
  else
    git -C "$BRAVE_CORE" fetch --force origin "$TARGET_BRAVE_REF"
    git -C "$BRAVE_CORE" checkout --detach FETCH_HEAD
  fi
}

checkout_brave_ref
cd "$BRAVE_CORE"
pnpm_run install

# Brave's generated environment contains required PYTHONPATH entries such as
# src/brave/script (for brave_chromium_utils), but it also puts depot_tools' hermetic
# Python first. Load Brave's environment, then restore our pip-enabled Python at PATH[0].
run_brave_hooks() {
  local brave_env="$BRAVE_CORE/build/env.sh"
  local depot_tools="$BRAVE_CORE/vendor/depot_tools"
  local gclient_py="$depot_tools/gclient.py"

  if [[ ! -f "$gclient_py" ]]; then
    echo "Missing depot_tools gclient.py at $gclient_py." >&2
    return 1
  fi
  if [[ ! -f "$brave_env" ]]; then
    echo "Missing Brave environment script at $brave_env." >&2
    return 1
  fi

  echo "Running Brave/Chromium hooks with Brave's PYTHONPATH and the isolated pip-enabled Python." >&2
  (
    cd "$BRAVE_CORE"
    set +u
    # shellcheck disable=SC1090
    source "$brave_env"
    set -u

    export PATH="$ROOT/.tools/python/bin:$PATH"
    if [[ ":${PYTHONPATH:-}:" != *":$BRAVE_CORE/script:"* ]]; then
      echo "Brave hook PYTHONPATH is missing $BRAVE_CORE/script." >&2
      return 1
    fi
    if ! python3 -m pip --version >/dev/null 2>&1; then
      echo "Brave hook Python unexpectedly has no pip after environment setup." >&2
      return 1
    fi

    cd "$WORKSPACE"
    export GCLIENT_FILE="$WORKSPACE/.gclient"
    "$HOOK_PYTHON" "$gclient_py" runhooks
  )
}

brave_sync_without_hooks() {
  pnpm_run run sync --target_os=android --target_arch="$ARCH" --nohooks
}

run_hooks_once_or_fail() {
  if run_brave_hooks; then
    return 0
  fi
  echo >&2
  echo "Brave dependency sync completed, but runhooks failed." >&2
  echo "This is not treated as a network-rate-limit failure, so it will not be retried in a loop." >&2
  echo "The existing checkout is preserved at $WORKSPACE." >&2
  return 1
}

# A first Chromium checkout launches many gclient SCM operations in parallel. Public
# googlesource endpoints can temporarily return HTTP 429 / RESOURCE_EXHAUSTED. Network
# recovery is bounded and only repeated while dependency sync itself fails. Once sync
# succeeds, a deterministic hook error exits immediately instead of sleeping/re-fetching.
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
    echo "Brave dependency sync did not finish. Preserving the existing Chromium checkout." >&2
    echo "Recovery attempt $attempt/$RECOVERY_ATTEMPTS: waiting ${delay}s, then resuming gclient with --jobs=$RECOVERY_JOBS." >&2
    sleep "$delay"

    if ! pnpm_run run gclient -- \
        sync \
        --nohooks \
        --reset \
        --upstream \
        --revision "src@$chromium_ref" \
        --force \
        --jobs="$RECOVERY_JOBS"; then
      echo "gclient recovery failed; the next attempt will reuse everything already downloaded." >&2
      continue
    fi

    echo "Bounded gclient recovery completed; finishing Brave sync without hooks." >&2
    if ! brave_sync_without_hooks; then
      echo "Brave sync still failed after gclient recovery; the next attempt will reuse the checkout." >&2
      continue
    fi

    run_hooks_once_or_fail
    return $?
  done

  echo "Brave bootstrap still failed after $RECOVERY_ATTEMPTS dependency-recovery attempts." >&2
  echo "Do not delete $WORKSPACE; rerun this script later to continue the existing checkout." >&2
  return 1
}

HAS_CHROMIUM=0
if [[ -d "$WORKSPACE/src/.git" && -f "$WORKSPACE/.gclient" ]]; then
  HAS_CHROMIUM=1
fi

if (( HAS_CHROMIUM == 0 )); then
  echo "No initialized Chromium checkout detected; running the first Brave init." >&2
  if pnpm_run run init --target_os=android --target_arch="$ARCH" --nohooks; then
    run_hooks_once_or_fail
  else
    recover_gclient_sync
  fi
elif [[ ! -f "$SYNC_MARKER" ]]; then
  echo "Detected an incomplete existing Chromium checkout; resuming it instead of reinitializing." >&2
  recover_gclient_sync
else
  echo "Detected an existing synced Brave/Chromium checkout; skipping full init." >&2
  if brave_sync_without_hooks; then
    run_hooks_once_or_fail
  else
    echo "Normal Brave sync failed; falling back to bounded dependency recovery." >&2
    recover_gclient_sync
  fi
fi

cd "$ROOT"
python3 scripts/apply_overlay.py "$WORKSPACE"
python3 scripts/verify_overlay.py "$WORKSPACE"

echo
echo "Brave Android initialized with the TV overlay."
echo "Pinned Brave core: $TARGET_BRAVE_REF"
echo "Next: $WORKSPACE/src/build/install-build-deps.sh --android"
echo "Then: ./scripts/build-debug.sh $ARCH"
