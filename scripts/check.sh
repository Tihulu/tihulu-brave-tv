#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo '[1/6] Python tests'
python3 -m unittest discover -s tests -p 'test_*.py' -v

echo '[2/6] Pure Java cursor-state test'
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -d "$TMP" \
  overlay/brave/android/java/org/chromium/chrome/browser/tv/TvCursorState.java \
  tests/java/org/chromium/chrome/browser/tv/TvCursorStateTest.java
java -ea -cp "$TMP" org.chromium.chrome.browser.tv.TvCursorStateTest

echo '[3/6] Android Java surface compile + updater parser test'
STUB_TMP="$(mktemp -d)"
find tests/stubs -name '*.java' -print0 | xargs -0 javac -d "$STUB_TMP" overlay/brave/android/java/org/chromium/chrome/browser/tv/*.java
javac -cp "$STUB_TMP" -d "$STUB_TMP" tests/java/org/chromium/chrome/browser/tv/TvGitHubUpdaterTest.java
java -ea -cp "$STUB_TMP" org.chromium.chrome.browser.tv.TvGitHubUpdaterTest
rm -rf "$STUB_TMP"

echo '[4/6] Shell syntax'
for file in scripts/*.sh; do bash -n "$file"; done

echo '[5/6] Python syntax'
python3 -m compileall -q scripts tests

echo '[6/6] License/source sanity'
grep -q 'GNU AFFERO GENERAL PUBLIC LICENSE' LICENSE
for file in overlay/brave/android/java/org/chromium/chrome/browser/tv/*.java; do
  grep -q 'SPDX-License-Identifier: AGPL-3.0-only' "$file"
done

echo 'All checks passed.'
