#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ARCH="${1:-arm}"
WORKSPACE="${BRAVE_TV_WORKSPACE:-$ROOT/.work/brave-browser}"
case "$ARCH" in arm|arm64) ;; *) echo 'Expected arm or arm64' >&2; exit 2 ;; esac

mkdir -p "$WORKSPACE"
# Avoid a many-hour download failing near the end because the runner is too small.
FREE_KB="$(df -Pk "$WORKSPACE" | awk 'NR==2 {print $4}')"
REQUIRED_GIB=200
if [[ -d "$WORKSPACE/src/.git" && -d "$WORKSPACE/src/brave/.git" ]]; then
  REQUIRED_GIB=60
fi
if (( FREE_KB < REQUIRED_GIB * 1024 * 1024 )); then
  echo "Native Brave build needs at least $REQUIRED_GIB GiB free at $WORKSPACE (found $((FREE_KB / 1024 / 1024)) GiB)." >&2
  echo 'Use a Linux x64 build runner with a large SSD; ordinary validation does not produce an APK.' >&2
  exit 2
fi
"$ROOT/scripts/install-host-deps.sh"
# shellcheck disable=SC1091
source "$ROOT/.tools/env.sh"
"$ROOT/scripts/check.sh"
"$ROOT/scripts/bootstrap.sh" "$ARCH"
python3 "$ROOT/scripts/install_chromium_build_deps.py" \
  "$WORKSPACE/src/build/install-build-deps.py" \
  --android --no-syms --no-chromeos-fonts --no-backwards-compatible --no-prompt

# Upstream and the overlay must both be the versions recorded in the artifact.
ACTUAL_REF="$(git -C "$WORKSPACE/src/brave" describe --tags --exact-match HEAD)"
EXPECTED_REF="$(tr -d '[:space:]' < "$ROOT/config/brave-core-ref")"
if [[ "$ACTUAL_REF" != "$EXPECTED_REF" ]]; then
  echo "Pinned Brave mismatch: expected $EXPECTED_REF, found $ACTUAL_REF" >&2
  exit 2
fi
# Force the selected APK target to be re-linked on an incremental checkout, so an old
# artifact cannot be passed off as a new commit after a partial/failed build.
BUILD_START="$(date +%s)"
APK_LIST="$(mktemp)"
trap 'rm -f "$APK_LIST"' EXIT
python3 - "$ROOT/scripts" "$WORKSPACE/src/out" "$ARCH" > "$APK_LIST" <<'PY'
import sys
from pathlib import Path
sys.path.insert(0, sys.argv[1])
from find_apk import find_apks
for apk in find_apks(Path(sys.argv[2]), sys.argv[3]):
    print(apk)
PY
while IFS= read -r apk; do
  # Only known generated APK files for this ABI are removed, never source or user data.
  rm -f -- "$apk"
done < "$APK_LIST"
"$ROOT/scripts/build-debug.sh" "$ARCH"
APK="$(python3 "$ROOT/scripts/package-apk.py" "$WORKSPACE/src/out" "$ARCH" "$ROOT/out/apk" \
  --not-before "$BUILD_START")"
APK_SIGNER="$(python3 - "$WORKSPACE/src/third_party/android_sdk/public/build-tools" <<'PY'
import sys
from pathlib import Path
tools = sorted(Path(sys.argv[1]).glob('*/apksigner'))
if not tools:
    raise SystemExit('Android SDK apksigner not found; refusing to upload an unverified APK')
print(tools[-1])
PY
)"
"$APK_SIGNER" verify --verbose "$APK"
echo "Verified native APK: $APK"
