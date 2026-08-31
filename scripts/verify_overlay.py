#!/usr/bin/env python3
"""Verify that a Tihulu TV Browser overlay was applied safely and exactly once."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

MARKER_PAIRS = {
    "src/brave/android/brave_java_sources.gni": [
        ("TIHULU_TV_BROWSER_JAVA_BEGIN", "TIHULU_TV_BROWSER_JAVA_END"),
    ],
    "src/chrome/android/chrome_java_resources.gni": [
        ("TIHULU_TV_BROWSER_RESOURCE_BEGIN", "TIHULU_TV_BROWSER_RESOURCE_END"),
    ],
    "src/brave/android/java/org/chromium/chrome/browser/BraveApplicationImplBase.java": [
        ("TIHULU_TV_BROWSER_SPATIAL_NAV_BEGIN", "TIHULU_TV_BROWSER_SPATIAL_NAV_END"),
    ],
    "src/chrome/android/java/AndroidManifest.xml": [
        ("TIHULU_TV_BROWSER_PERMISSIONS_BEGIN", "TIHULU_TV_BROWSER_PERMISSIONS_END"),
        ("TIHULU_TV_BROWSER_FEATURES_BEGIN", "TIHULU_TV_BROWSER_FEATURES_END"),
        ("TIHULU_TV_BROWSER_MANIFEST_BEGIN", "TIHULU_TV_BROWSER_MANIFEST_END"),
    ],
}

JAVA_CLASSES = [
    "TvNavigationMode.java",
    "TvCursorState.java",
    "TvCursorOverlay.java",
    "TvMouseDispatcher.java",
    "TvMemoryProfile.java",
    "TvControlPanel.java",
    "TvAboutPanel.java",
    "TvBuildInfo.java",
    "TvBraveUpstream.java",
    "TvBrowserBar.java",
    "TvTabPanel.java",
    "TvGitHubUpdater.java",
    "TvBraveActivity.java",
]

INSTALL_PERMISSION = "android.permission.REQUEST_INSTALL_PACKAGES"
LEANBACK_FEATURE = "android.software.leanback"
FAKETOUCH_FEATURE = "android.hardware.faketouch"


def _png_dimensions(path: Path) -> tuple[int, int] | None:
    data = path.read_bytes()[:24]
    if len(data) < 24 or not data.startswith(b"\x89PNG\r\n\x1a\n") or data[12:16] != b"IHDR":
        return None
    return int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big")


def _load_expected_versions(project: Path, errors: list[str]) -> tuple[str, str] | None:
    package_json = project / "src/brave/package.json"
    try:
        data = json.loads(package_json.read_text(encoding="utf-8"))
        brave = str(data.get("version", "")).strip()
        chromium = str(
            data.get("config", {})
            .get("projects", {})
            .get("chrome", {})
            .get("tag", "")
        ).strip()
    except (OSError, json.JSONDecodeError) as exc:
        errors.append(f"unable to read Brave package metadata: {exc}")
        return None
    if not brave or not chromium:
        errors.append("Brave package metadata is missing version or Chromium tag")
        return None
    return brave, chromium


def _require_count(text: str, token: str, count: int, rel: str, errors: list[str]) -> None:
    actual = text.count(token)
    if actual != count:
        errors.append(f"{rel}: expected {count} {token!r}, found {actual}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project", type=Path)
    args = parser.parse_args()
    project = args.project.resolve()
    errors: list[str] = []

    texts: dict[str, str] = {}
    for rel, pairs in MARKER_PAIRS.items():
        path = project / rel
        if not path.is_file():
            errors.append(f"missing {rel}")
            continue
        text = path.read_text(encoding="utf-8")
        texts[rel] = text
        for begin, end in pairs:
            _require_count(text, begin, 1, rel, errors)
            _require_count(text, end, 1, rel, errors)
            if text.count(begin) == 1 and text.count(end) == 1 and text.index(end) < text.index(begin):
                errors.append(f"{rel}: marker {end!r} appears before {begin!r}")

    source_rel = "src/brave/android/brave_java_sources.gni"
    source_text = texts.get(source_rel, "")
    for name in JAVA_CLASSES:
        _require_count(source_text, name, 1, source_rel, errors)

    resource_rel = "src/chrome/android/chrome_java_resources.gni"
    resource_text = texts.get(resource_rel, "")
    for name in ["tihulu_tv_banner.png", "tihulu_tv_icon.png"]:
        _require_count(resource_text, name, 1, resource_rel, errors)

    app_rel = "src/brave/android/java/org/chromium/chrome/browser/BraveApplicationImplBase.java"
    app_text = texts.get(app_rel, "")
    _require_count(app_text, 'appendSwitch("enable-spatial-navigation")', 1, app_rel, errors)
    _require_count(app_text, "TvMemoryProfile.apply(getApplication())", 1, app_rel, errors)
    browser_guard = "if (SplitCompatApplication.isBrowserProcess()) {"
    spatial_begin = "TIHULU_TV_BROWSER_SPATIAL_NAV_BEGIN"
    if app_text.count(browser_guard) == 1 and app_text.count(spatial_begin) == 1:
        if app_text.index(spatial_begin) < app_text.index(browser_guard):
            errors.append(f"{app_rel}: TV startup profile is outside the browser-process guard")

    manifest_rel = "src/chrome/android/java/AndroidManifest.xml"
    manifest = texts.get(manifest_rel, "")
    for token in [
        INSTALL_PERMISSION,
        'android:name="android.software.leanback"',
        'android:name="android.hardware.faketouch"',
        "org.chromium.chrome.browser.tv.TvBraveActivity",
        "android.intent.category.LEANBACK_LAUNCHER",
        "android.software.leanback.supports_touch",
        'android:icon="@drawable/tihulu_tv_icon"',
        'android:banner="@drawable/tihulu_tv_banner"',
    ]:
        _require_count(manifest, token, 1, manifest_rel, errors)

    activity_begin = manifest.find("TIHULU_TV_BROWSER_MANIFEST_BEGIN")
    activity_end = manifest.find("TIHULU_TV_BROWSER_MANIFEST_END")
    if activity_begin >= 0 and activity_end > activity_begin:
        activity_block = manifest[activity_begin:activity_end]
        for token in [
            'android:windowSoftInputMode="adjustResize"',
            'android:hardwareAccelerated="false"',
            'android:resizeableActivity="true"',
            'android:supportsPictureInPicture="true"',
            'android:name="android.activity.launch_mode" android:value="singleInstancePerTask"',
            'android:name="android.software.leanback.supports_touch" android:value="true"',
        ]:
            _require_count(activity_block, token, 1, manifest_rel + " TV activity", errors)

        config_changes = [
            "orientation",
            "keyboardHidden",
            "keyboard",
            "screenSize",
            "mcc",
            "mnc",
            "screenLayout",
            "smallestScreenSize",
            "uiMode",
            "navigation",
            "density",
            "touchscreen",
            "colorMode",
            "fontScale",
        ]
        for item in config_changes:
            if item not in activity_block:
                errors.append(f"{manifest_rel}: TV activity configChanges is missing {item}")

    if f'android:name="{LEANBACK_FEATURE}" android:required="true"' not in manifest:
        errors.append(f"{manifest_rel}: leanback feature must be required=true")
    if f'android:name="{FAKETOUCH_FEATURE}" android:required="false"' not in manifest:
        errors.append(f"{manifest_rel}: faketouch feature must be required=false")

    java_dir = project / "src/brave/android/java/org/chromium/chrome/browser/tv"
    for name in JAVA_CLASSES:
        rel = Path("src/brave/android/java/org/chromium/chrome/browser/tv") / name
        if not (project / rel).is_file():
            errors.append(f"missing {rel}")

    memory_profile = java_dir / "TvMemoryProfile.java"
    if memory_profile.is_file():
        memory_text = memory_profile.read_text(encoding="utf-8")
        for token in [
            'LOW_END_DEVICE_SWITCH = "enable-low-end-device-mode"',
            "if (!Process.is64Bit()) return true;",
            "manager.isLowRamDevice()",
        ]:
            _require_count(
                memory_text,
                token,
                1,
                str(memory_profile.relative_to(project)),
                errors,
            )

    expected_versions = _load_expected_versions(project, errors)
    build_info = java_dir / "TvBuildInfo.java"
    if build_info.is_file() and expected_versions is not None:
        text = build_info.read_text(encoding="utf-8")
        brave, chromium = expected_versions
        _require_count(text, f'BRAVE_VERSION = "{brave}"', 1, str(build_info.relative_to(project)), errors)
        _require_count(
            text,
            f'CHROMIUM_VERSION = "{chromium}"',
            1,
            str(build_info.relative_to(project)),
            errors,
        )
        if 'BRAVE_VERSION = "development"' in text or 'CHROMIUM_VERSION = "unknown"' in text:
            errors.append("TvBuildInfo.java still contains template engine versions")

    for name, label, expected in [
        ("tihulu_tv_banner.png", "TV banner", (320, 180)),
        ("tihulu_tv_icon.png", "TV icon", (256, 256)),
    ]:
        asset = project / "src/chrome/android/java/res/drawable-nodpi" / name
        if not asset.is_file():
            errors.append(f"missing {label}")
            continue
        dimensions = _png_dimensions(asset)
        if dimensions is None:
            errors.append(f"invalid PNG for {label}")
        elif dimensions != expected:
            errors.append(
                f"{label} must be {expected[0]}x{expected[1]}, "
                f"found {dimensions[0]}x{dimensions[1]}"
            )

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("Overlay verification passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
