#!/usr/bin/env bash
set -euo pipefail

DEST="${TIHULU_BRAVE_TV_DIR:-$HOME/tihulu-brave-tv}"
ARCH_VALUE="${ARCH:-${1:-arm64}}"

if (( EUID == 0 )); then
  SUDO=()
elif command -v sudo >/dev/null 2>&1; then
  SUDO=(sudo)
else
  echo "sudo is required for the one-line installer." >&2
  exit 2
fi

export DEBIAN_FRONTEND=noninteractive
"${SUDO[@]}" apt-get update
"${SUDO[@]}" apt-get install -y ca-certificates curl git

if [[ -d "$DEST/.git" ]]; then
  git -C "$DEST" pull --ff-only
elif [[ -e "$DEST" ]]; then
  echo "$DEST already exists but is not a Git checkout." >&2
  exit 2
else
  git clone https://github.com/Tihulu/tihulu-brave-tv.git "$DEST"
fi

exec "$DEST/scripts/build-apk-one-line.sh" "$ARCH_VALUE"
