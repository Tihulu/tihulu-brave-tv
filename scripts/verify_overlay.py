#!/usr/bin/env python3
"""Verify that a Tihulu TV Browser overlay was applied exactly once."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

MARKERS = {
    "src/brave/android/brave_java_sources.gni": [
        "TIHULU_TV_BROWSER_JAVA_BEGIN",
        "TvBraveActivity.java",
        "TvCursorState.java",
    ],
    "src/chrome/android/chrome_java_resources.gni": [
        "TIHULU_TV_BROWSER_RESOURCE_BEGIN",
        "java/res/drawable-nodpi/tihulu_tv_banner.png",
    ],
    "src/brave/android/java/org/chromium/chrome/browser/BraveApplicationImplBase.java": [
        "TIHULU_TV_BROWSER_SPATIAL_NAV_BEGIN",
        'appendSwitch("enable-spatial-navigation")',
    ],
    "src/chrome/android/java/AndroidManifest.xml": [
        "TIHULU_TV_BROWSER_MANIFEST_BEGIN",
        "android.intent.category.LEANBACK_LAUNCHER",
        "android.software.leanback.supports_touch",
    ],
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project", type=Path)
    args = parser.parse_args()
    project = args.project.resolve()
    errors: list[str] = []

    for rel, markers in MARKERS.items():
        path = project / rel
        if not path.is_file():
            errors.append(f"missing {rel}")
            continue
        text = path.read_text(encoding="utf-8")
        for marker in markers:
            count = text.count(marker)
            if count != 1:
                errors.append(f"{rel}: expected one {marker!r}, found {count}")

    for name in [
        "TvNavigationMode.java",
        "TvCursorState.java",
        "TvCursorOverlay.java",
        "TvMouseDispatcher.java",
        "TvControlPanel.java",
        "TvBrowserBar.java",
        "TvTabPanel.java",
        "TvBraveActivity.java",
    ]:
        rel = Path("src/brave/android/java/org/chromium/chrome/browser/tv") / name
        if not (project / rel).is_file():
            errors.append(f"missing {rel}")

    banner = project / "src/chrome/android/java/res/drawable-nodpi/tihulu_tv_banner.png"
    if not banner.is_file():
        errors.append("missing TV banner")

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("Overlay verification passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
