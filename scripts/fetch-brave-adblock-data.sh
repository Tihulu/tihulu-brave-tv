#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/app/src/main/assets/brave"
mkdir -p "$OUT"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

fetch() {
  local url="$1"
  local dest="$2"
  echo "Fetching $url"
  curl --fail --location --retry 3 --retry-delay 2 --silent --show-error "$url" -o "$dest"
}

# Community lists Brave relies on heavily.
fetch "https://easylist.to/easylist/easylist.txt" "$tmp/easylist.txt"
fetch "https://easylist.to/easylist/easyprivacy.txt" "$tmp/easyprivacy.txt"
fetch "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt" "$tmp/ubo-filters.txt"
fetch "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt" "$tmp/ubo-privacy.txt"
fetch "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/unbreak.txt" "$tmp/ubo-unbreak.txt"
fetch "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/quick-fixes.txt" "$tmp/ubo-quick-fixes.txt"

cat   "$tmp/easylist.txt"   "$tmp/easyprivacy.txt"   "$tmp/ubo-filters.txt"   "$tmp/ubo-privacy.txt"   "$tmp/ubo-unbreak.txt"   "$tmp/ubo-quick-fixes.txt"   > "$OUT/community-filters.txt"

# Brave-specific rules are separate so they can receive Brave-authored scriptlet permission.
fetch "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/brave-specific.txt" "$tmp/brave-specific.txt"
fetch "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-unbreak.txt" "$tmp/brave-unbreak.txt"
cat "$tmp/brave-specific.txt" "$tmp/brave-unbreak.txt" > "$OUT/brave-filters.txt"

# Prebuilt resource/scriptlet bundle consumed by adblock-rust.
fetch "https://raw.githubusercontent.com/brave/adblock-resources/master/dist/resources.json" "$OUT/resources.json"

echo "Generated adblock assets:"
du -h "$OUT/community-filters.txt" "$OUT/brave-filters.txt" "$OUT/resources.json"
