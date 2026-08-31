#!/usr/bin/env python3
"""Apply the Tihulu TV Browser overlay to an initialized Brave/Chromium tree."""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OVERLAY_JAVA = ROOT / "overlay/brave/android/java/org/chromium/chrome/browser/tv"
BRANDING = ROOT / "assets/branding"
TV_MARKER = "TIHULU_TV_BROWSER"
JAVA_CLASSES = [
    "TvNavigationMode.java",
    "TvCursorState.java",
    "TvCursorOverlay.java",
    "TvMouseDispatcher.java",
    "TvControlPanel.java",
    "TvAboutPanel.java",
    "TvBrowserBar.java",
    "TvTabPanel.java",
    "TvGitHubUpdater.java",
    "TvBraveActivity.java",
]


class PatchError(RuntimeError):
    pass


def replace_once(text: str, needle: str, replacement: str, description: str) -> str:
    count = text.count(needle)
    if count != 1:
        raise PatchError(
            f"{description}: expected exactly one upstream anchor, found {count}. "
            "Upstream probably changed; review it before updating the patcher."
        )
    return text.replace(needle, replacement, 1)


def replace_owned_block(text: str, begin: str, end: str, block: str, description: str) -> str:
    begin_count = text.count(begin)
    end_count = text.count(end)
    if begin_count != 1 or end_count != 1:
        raise PatchError(
            f"{description}: expected exactly one owned marker pair, "
            f"found begin={begin_count}, end={end_count}."
        )
    start = text.index(begin)
    stop = text.index(end, start) + len(end)
    if stop < len(text) and text[stop] == "\n":
        stop += 1
    return text[:start] + block + text[stop:]


def java_source_block() -> str:
    entries = "".join(
        f'  "../../brave/android/java/org/chromium/chrome/browser/tv/{name}",\n'
        for name in JAVA_CLASSES
    )
    return f"  # {TV_MARKER}_JAVA_BEGIN\n" + entries + f"  # {TV_MARKER}_JAVA_END\n"


def chrome_resource_block() -> str:
    return (
        f"  # {TV_MARKER}_RESOURCE_BEGIN\n"
        '  "java/res/drawable-nodpi/tihulu_tv_banner.png",\n'
        '  "java/res/drawable-nodpi/tihulu_tv_icon.png",\n'
        f"  # {TV_MARKER}_RESOURCE_END\n"
    )


def manifest_activity_block() -> str:
    return (
        f"        <!-- {TV_MARKER}_MANIFEST_BEGIN -->\n"
        '        <activity android:name="org.chromium.chrome.browser.tv.TvBraveActivity"\n'
        '            android:theme="@style/Theme.Chromium.TabbedMode"\n'
        '            android:label="Tihulu TV Browser"\n'
        '            android:icon="@drawable/tihulu_tv_icon"\n'
        '            android:banner="@drawable/tihulu_tv_banner"\n'
        '            android:exported="true"\n'
        '            android:launchMode="singleTask"\n'
        '            android:hardwareAccelerated="false"\n'
        '            android:resizeableActivity="true"\n'
        '            android:supportsPictureInPicture="true"\n'
        '            android:configChanges="orientation|keyboardHidden|keyboard|screenSize|mcc|mnc|screenLayout|smallestScreenSize|uiMode|density">\n'
        '            <meta-data android:name="android.software.leanback.supports_touch" android:value="true" />\n'
        '            <intent-filter>\n'
        '                <action android:name="android.intent.action.MAIN" />\n'
        '                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />\n'
        '            </intent-filter>\n'
        '        </activity>\n'
        f"        <!-- {TV_MARKER}_MANIFEST_END -->\n"
    )


def patch_java_sources(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    begin = f"  # {TV_MARKER}_JAVA_BEGIN"
    end = f"  # {TV_MARKER}_JAVA_END"
    block = java_source_block()
    if begin in text or end in text:
        text = replace_owned_block(text, begin, end, block, "brave_java_sources.gni TV block")
    else:
        anchor = "brave_java_sources = [\n"
        text = replace_once(text, anchor, anchor + block, "brave_java_sources.gni")
    path.write_text(text, encoding="utf-8")


def patch_chrome_resources(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    begin = f"  # {TV_MARKER}_RESOURCE_BEGIN"
    end = f"  # {TV_MARKER}_RESOURCE_END"
    block = chrome_resource_block()
    if begin in text or end in text:
        text = replace_owned_block(text, begin, end, block, "chrome_java_resources.gni TV block")
    else:
        anchor = "chrome_java_resources = [\n"
        text = replace_once(text, anchor, anchor + block, "chrome_java_resources.gni")
    path.write_text(text, encoding="utf-8")


def patch_brave_application(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if f"// {TV_MARKER}_SPATIAL_NAV_BEGIN" in text:
        return

    import_anchor = "import android.app.Application;\n"
    import_block = (
        import_anchor
        + "import android.app.UiModeManager;\n"
        + "import android.content.Context;\n"
        + "import android.content.res.Configuration;\n"
    )
    text = replace_once(text, import_anchor, import_block, "BraveApplicationImplBase imports")

    chromium_import_anchor = "import org.chromium.base.JavaUtils;\n"
    text = replace_once(
        text,
        chromium_import_anchor,
        chromium_import_anchor + "import org.chromium.base.CommandLine;\n",
        "BraveApplicationImplBase Chromium imports",
    )

    oncreate_anchor = "        super.onCreate();\n        if (SplitCompatApplication.isBrowserProcess()) {\n"
    spatial = (
        "        super.onCreate();\n"
        f"        // {TV_MARKER}_SPATIAL_NAV_BEGIN\n"
        "        UiModeManager tvModeManager =\n"
        "                (UiModeManager) getApplication().getSystemService(Context.UI_MODE_SERVICE);\n"
        "        if (tvModeManager != null\n"
        "                && tvModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {\n"
        "            CommandLine.getInstance().appendSwitch(\"enable-spatial-navigation\");\n"
        "        }\n"
        f"        // {TV_MARKER}_SPATIAL_NAV_END\n"
        "        if (SplitCompatApplication.isBrowserProcess()) {\n"
    )
    text = replace_once(text, oncreate_anchor, spatial, "BraveApplicationImplBase onCreate")
    path.write_text(text, encoding="utf-8")


def patch_manifest(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    feature_anchor = '    <uses-feature android:glEsVersion="0x00030000" />\n'

    if f"<!-- {TV_MARKER}_PERMISSIONS_BEGIN -->" not in text:
        permissions = (
            f"    <!-- {TV_MARKER}_PERMISSIONS_BEGIN -->\n"
            + '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n'
            + f"    <!-- {TV_MARKER}_PERMISSIONS_END -->\n"
        )
        text = replace_once(
            text,
            feature_anchor,
            permissions + feature_anchor,
            "Chromium manifest updater permission",
        )

    if f"<!-- {TV_MARKER}_FEATURES_BEGIN -->" not in text:
        features = (
            feature_anchor
            + f"    <!-- {TV_MARKER}_FEATURES_BEGIN -->\n"
            + '    <uses-feature android:name="android.software.leanback" android:required="true" />\n'
            + '    <uses-feature android:name="android.hardware.faketouch" android:required="false" />\n'
            + f"    <!-- {TV_MARKER}_FEATURES_END -->\n"
        )
        text = replace_once(text, feature_anchor, features, "Chromium manifest TV features")

    application_anchor = (
        '      <application android:name="{% block application_name %}'
        'org.chromium.chrome.browser.base.SplitChromeApplication{% endblock %}"\n'
    )
    if application_anchor not in text:
        raise PatchError("Chromium manifest application anchor moved")

    activity_begin = f"        <!-- {TV_MARKER}_MANIFEST_BEGIN -->"
    activity_end = f"        <!-- {TV_MARKER}_MANIFEST_END -->"
    activity_block = manifest_activity_block()
    if activity_begin in text or activity_end in text:
        text = replace_owned_block(
            text,
            activity_begin,
            activity_end,
            activity_block,
            "Chromium manifest TV activity block",
        )
    else:
        activity_anchor = '        <!-- ChromeTabbedActivity related -->\n'
        text = replace_once(
            text,
            activity_anchor,
            activity_block + activity_anchor,
            "Chromium manifest activity",
        )

    path.write_text(text, encoding="utf-8")


def copy_overlay(project: Path) -> None:
    destination = project / "src/brave/android/java/org/chromium/chrome/browser/tv"
    destination.mkdir(parents=True, exist_ok=True)
    for name in JAVA_CLASSES:
        shutil.copy2(OVERLAY_JAVA / name, destination / name)

    resource_dir = project / "src/chrome/android/java/res/drawable-nodpi"
    resource_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(BRANDING / "tihulu_tv_banner.png", resource_dir / "tihulu_tv_banner.png")
    shutil.copy2(BRANDING / "tihulu_tv_icon.png", resource_dir / "tihulu_tv_icon.png")


def _require_anchor(text: str, marker: str, anchor: str, description: str) -> None:
    if marker in text:
        return
    count = text.count(anchor)
    if count != 1:
        raise PatchError(
            f"{description}: expected exactly one upstream anchor, found {count}. "
            "Upstream probably changed; review it before updating the patcher."
        )


def _require_marker_pair(text: str, begin: str, end: str, description: str) -> bool:
    if begin not in text and end not in text:
        return False
    if text.count(begin) != 1 or text.count(end) != 1:
        raise PatchError(f"{description}: malformed existing owned block")
    return True


def preflight(required: dict[str, Path]) -> None:
    java_sources = required["java sources"].read_text(encoding="utf-8")
    resources = required["Chrome resources"].read_text(encoding="utf-8")
    brave_app = required["Brave application"].read_text(encoding="utf-8")
    manifest = required["Chromium manifest"].read_text(encoding="utf-8")

    java_begin = f"# {TV_MARKER}_JAVA_BEGIN"
    java_end = f"# {TV_MARKER}_JAVA_END"
    if not _require_marker_pair(java_sources, java_begin, java_end, "brave_java_sources.gni"):
        _require_anchor(
            java_sources,
            java_begin,
            "brave_java_sources = [\n",
            "brave_java_sources.gni",
        )

    resource_begin = f"# {TV_MARKER}_RESOURCE_BEGIN"
    resource_end = f"# {TV_MARKER}_RESOURCE_END"
    if not _require_marker_pair(resources, resource_begin, resource_end, "chrome_java_resources.gni"):
        _require_anchor(
            resources,
            resource_begin,
            "chrome_java_resources = [\n",
            "chrome_java_resources.gni",
        )

    if f"// {TV_MARKER}_SPATIAL_NAV_BEGIN" not in brave_app:
        for anchor, description in [
            ("import android.app.Application;\n", "BraveApplicationImplBase Android imports"),
            ("import org.chromium.base.JavaUtils;\n", "BraveApplicationImplBase Chromium imports"),
            (
                "        super.onCreate();\n        if (SplitCompatApplication.isBrowserProcess()) {\n",
                "BraveApplicationImplBase onCreate",
            ),
        ]:
            if brave_app.count(anchor) != 1:
                raise PatchError(f"{description}: upstream anchor moved")

    feature_anchor = '    <uses-feature android:glEsVersion="0x00030000" />\n'
    if f"<!-- {TV_MARKER}_PERMISSIONS_BEGIN -->" not in manifest:
        if manifest.count(feature_anchor) != 1:
            raise PatchError("Chromium manifest updater permission anchor moved")
    if f"<!-- {TV_MARKER}_FEATURES_BEGIN -->" not in manifest:
        if manifest.count(feature_anchor) != 1:
            raise PatchError("Chromium manifest TV feature anchor moved")

    manifest_begin = f"<!-- {TV_MARKER}_MANIFEST_BEGIN -->"
    manifest_end = f"<!-- {TV_MARKER}_MANIFEST_END -->"
    if not _require_marker_pair(manifest, manifest_begin, manifest_end, "Chromium manifest TV activity"):
        for anchor, description in [
            ('        <!-- ChromeTabbedActivity related -->\n', "Chromium manifest activity"),
            (
                '      <application android:name="{% block application_name %}'
                'org.chromium.chrome.browser.base.SplitChromeApplication{% endblock %}"\n',
                "Chromium manifest application",
            ),
        ]:
            if manifest.count(anchor) != 1:
                raise PatchError(f"{description}: upstream anchor moved")


def apply(project: Path) -> None:
    project = project.resolve()
    required = {
        "java sources": project / "src/brave/android/brave_java_sources.gni",
        "Chrome resources": project / "src/chrome/android/chrome_java_resources.gni",
        "Brave application": project
        / "src/brave/android/java/org/chromium/chrome/browser/BraveApplicationImplBase.java",
        "Chromium manifest": project / "src/chrome/android/java/AndroidManifest.xml",
    }
    missing = [f"{name}: {path}" for name, path in required.items() if not path.is_file()]
    if missing:
        raise PatchError(
            "This does not look like an initialized Brave Android project. Missing:\n- "
            + "\n- ".join(missing)
        )

    for asset in [BRANDING / "tihulu_tv_banner.png", BRANDING / "tihulu_tv_icon.png"]:
        if not asset.is_file():
            raise PatchError(f"Missing branding asset: {asset}")

    # Validate every upstream anchor before touching the checkout. A drift failure must not
    # leave a half-applied overlay behind.
    preflight(required)
    copy_overlay(project)
    patch_java_sources(required["java sources"])
    patch_chrome_resources(required["Chrome resources"])
    patch_brave_application(required["Brave application"])
    patch_manifest(required["Chromium manifest"])

    print(f"Tihulu TV Browser overlay applied to {project}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "project",
        type=Path,
        help="Brave project root containing src/brave and src/chrome",
    )
    args = parser.parse_args()
    try:
        apply(args.project)
    except PatchError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
