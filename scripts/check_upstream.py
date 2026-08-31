#!/usr/bin/env python3
"""Lightweight network check for upstream patch anchors used by scheduled CI."""

from __future__ import annotations

import sys
import urllib.request

FILES = {
    "Brave Java sources": (
        "https://raw.githubusercontent.com/brave/brave-core/master/android/brave_java_sources.gni",
        ["brave_java_sources = [", "BraveApplicationImplBase.java"],
    ),
    "Chromium Android resources": (
        "https://raw.githubusercontent.com/chromium/chromium/main/chrome/android/chrome_java_resources.gni",
        ["chrome_java_resources = ["],
    ),
    "Brave application": (
        "https://raw.githubusercontent.com/brave/brave-core/master/android/java/org/chromium/chrome/browser/BraveApplicationImplBase.java",
        ["super.onCreate();", "SplitCompatApplication.isBrowserProcess()"],
    ),
    "Chromium Android manifest": (
        "https://raw.githubusercontent.com/chromium/chromium/main/chrome/android/java/AndroidManifest.xml",
        ["<!-- ChromeTabbedActivity related -->", "android.hardware.touchscreen"],
    ),
    "Chromium spatial navigation": (
        "https://raw.githubusercontent.com/chromium/chromium/main/content/public/common/content_switches.cc",
        ["kEnableSpatialNavigation"],
    ),
}


def main() -> int:
    errors = []
    for name, (url, anchors) in FILES.items():
        with urllib.request.urlopen(url, timeout=30) as response:
            text = response.read().decode("utf-8")
        for anchor in anchors:
            if anchor not in text:
                errors.append(f"{name}: missing upstream anchor {anchor!r}")
    if errors:
        print("Upstream drift detected:")
        for error in errors:
            print(f"- {error}")
        return 1
    print("Upstream anchor check passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Upstream check failed: {exc}", file=sys.stderr)
        raise
