#!/usr/bin/env python3
"""Apply the Tihulu TV Browser overlay to an initialized Brave/Chromium tree."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OVERLAY_JAVA = ROOT / "overlay/brave/android/java/org/chromium/chrome/browser/tv"
BRANDING = ROOT / "assets/branding"
TV_MARKER = "TIHULU_TV_BROWSER"
INSTALL_PERMISSION = "android.permission.REQUEST_INSTALL_PACKAGES"
LEANBACK_FEATURE = "android.software.leanback"
FAKETOUCH_FEATURE = "android.hardware.faketouch"
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


def _owned_span(text: str, begin: str, end: str, description: str) -> tuple[int, int] | None:
    begin_count = text.count(begin)
    end_count = text.count(end)
    if begin_count == 0 and end_count == 0:
        return None
    if begin_count != 1 or end_count != 1:
        raise PatchError(
            f"{description}: expected exactly one owned marker pair, "
            f"found begin={begin_count}, end={end_count}."
        )
    start = text.index(begin)
    end_start = text.index(end)
    if end_start < start:
        raise PatchError(f"{description}: owned end marker appears before begin marker")
    stop = end_start + len(end)
    if stop < len(text) and text[stop] == "\n":
        stop += 1
    return start, stop


def strip_owned_block(text: str, begin: str, end: str, description: str) -> tuple[str, bool]:
    span = _owned_span(text, begin, end, description)
    if span is None:
        return text, False
    start, stop = span
    return text[:start] + text[stop:], True


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


def permission_block(include_declaration: bool) -> str:
    declaration = (
        f'    <uses-permission android:name="{INSTALL_PERMISSION}" />\n'
        if include_declaration
        else ""
    )
    return (
        f"    <!-- {TV_MARKER}_PERMISSIONS_BEGIN -->\n"
        + declaration
        + f"    <!-- {TV_MARKER}_PERMISSIONS_END -->\n"
    )


def feature_block(include_leanback: bool, include_faketouch: bool) -> str:
    lines = [f"    <!-- {TV_MARKER}_FEATURES_BEGIN -->\n"]
    if include_leanback:
        lines.append(
            f'    <uses-feature android:name="{LEANBACK_FEATURE}" android:required="true" />\n'
        )
    if include_faketouch:
        lines.append(
            f'    <uses-feature android:name="{FAKETOUCH_FEATURE}" android:required="false" />\n'
        )
    lines.append(f"    <!-- {TV_MARKER}_FEATURES_END -->\n")
    return "".join(lines)


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
        '            android:windowSoftInputMode="adjustResize"\n'
        '            android:hardwareAccelerated="false"\n'
        '            android:resizeableActivity="true"\n'
        '            android:supportsPictureInPicture="true"\n'
        '            android:configChanges="orientation|keyboardHidden|keyboard|screenSize|mcc|mnc|screenLayout|smallestScreenSize|uiMode|navigation|density|touchscreen|colorMode|fontScale">\n'
        '            <meta-data android:name="android.activity.launch_mode" android:value="singleInstancePerTask" />\n'
        '            <meta-data android:name="android.software.leanback.supports_touch" android:value="true" />\n'
        '            <intent-filter>\n'
        '                <action android:name="android.intent.action.MAIN" />\n'
        '                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />\n'
        '            </intent-filter>\n'
        '        </activity>\n'
        f"        <!-- {TV_MARKER}_MANIFEST_END -->\n"
    )


def transform_java_sources(text: str) -> str:
    begin = f"  # {TV_MARKER}_JAVA_BEGIN"
    end = f"  # {TV_MARKER}_JAVA_END"
    text, _ = strip_owned_block(text, begin, end, "brave_java_sources.gni TV block")
    for name in JAVA_CLASSES:
        entry = f"../../brave/android/java/org/chromium/chrome/browser/tv/{name}"
        if entry in text:
            raise PatchError(f"brave_java_sources.gni: unowned duplicate TV source {name}")
    anchor = "brave_java_sources = [\n"
    return replace_once(text, anchor, anchor + java_source_block(), "brave_java_sources.gni")


def transform_chrome_resources(text: str) -> str:
    begin = f"  # {TV_MARKER}_RESOURCE_BEGIN"
    end = f"  # {TV_MARKER}_RESOURCE_END"
    text, _ = strip_owned_block(text, begin, end, "chrome_java_resources.gni TV block")
    for name in ["tihulu_tv_banner.png", "tihulu_tv_icon.png"]:
        if f"java/res/drawable-nodpi/{name}" in text:
            raise PatchError(f"chrome_java_resources.gni: unowned duplicate TV resource {name}")
    anchor = "chrome_java_resources = [\n"
    return replace_once(text, anchor, anchor + chrome_resource_block(), "chrome_java_resources.gni")


def _ensure_import(text: str, import_line: str, anchor: str, description: str) -> str:
    if import_line in text:
        return text
    return replace_once(text, anchor, anchor + import_line, description)


def spatial_navigation_block() -> str:
    return (
        f"            // {TV_MARKER}_SPATIAL_NAV_BEGIN\n"
        "            UiModeManager tvModeManager =\n"
        "                    (UiModeManager) getApplication().getSystemService(Context.UI_MODE_SERVICE);\n"
        "            if (tvModeManager != null\n"
        "                    && tvModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {\n"
        '                CommandLine.getInstance().appendSwitch("enable-spatial-navigation");\n'
        "                org.chromium.chrome.browser.tv.TvMemoryProfile.apply(getApplication());\n"
        "            }\n"
        f"            // {TV_MARKER}_SPATIAL_NAV_END\n"
    )


def transform_brave_application(text: str) -> str:
    begin = f"// {TV_MARKER}_SPATIAL_NAV_BEGIN"
    end = f"// {TV_MARKER}_SPATIAL_NAV_END"
    text, _ = strip_owned_block(text, begin, end, "BraveApplicationImplBase spatial navigation")

    if 'appendSwitch("enable-spatial-navigation")' in text:
        raise PatchError("BraveApplicationImplBase contains an unowned spatial-navigation switch")
    if "TvMemoryProfile.apply(getApplication())" in text:
        raise PatchError("BraveApplicationImplBase contains an unowned TV memory profile call")

    text = _ensure_import(
        text,
        "import android.app.UiModeManager;\n",
        "import android.app.Application;\n",
        "BraveApplicationImplBase UiModeManager import",
    )
    text = _ensure_import(
        text,
        "import android.content.Context;\n",
        "import android.app.UiModeManager;\n",
        "BraveApplicationImplBase Context import",
    )
    text = _ensure_import(
        text,
        "import android.content.res.Configuration;\n",
        "import android.content.Context;\n",
        "BraveApplicationImplBase Configuration import",
    )
    text = _ensure_import(
        text,
        "import org.chromium.base.CommandLine;\n",
        "import org.chromium.base.JavaUtils;\n",
        "BraveApplicationImplBase CommandLine import",
    )

    browser_process_anchor = "        if (SplitCompatApplication.isBrowserProcess()) {\n"
    return replace_once(
        text,
        browser_process_anchor,
        browser_process_anchor + spatial_navigation_block(),
        "BraveApplicationImplBase browser-process onCreate",
    )


def _feature_declaration(text: str, feature: str) -> str | None:
    matches = re.findall(
        rf'<uses-feature\b[^>]*android:name="{re.escape(feature)}"[^>]*?/?>',
        text,
        flags=re.DOTALL,
    )
    if len(matches) > 1:
        raise PatchError(f"Chromium manifest contains duplicate {feature} declarations")
    return matches[0] if matches else None


def transform_manifest(text: str) -> str:
    feature_anchor = '    <uses-feature android:glEsVersion="0x00030000" />\n'

    permission_begin = f"    <!-- {TV_MARKER}_PERMISSIONS_BEGIN -->"
    permission_end = f"    <!-- {TV_MARKER}_PERMISSIONS_END -->"
    text, _ = strip_owned_block(
        text, permission_begin, permission_end, "Chromium manifest updater permission block"
    )
    permission_count = text.count(INSTALL_PERMISSION)
    if permission_count > 1:
        raise PatchError(
            f"Chromium manifest already contains {permission_count} unowned {INSTALL_PERMISSION} declarations"
        )
    include_permission = permission_count == 0

    feature_begin = f"    <!-- {TV_MARKER}_FEATURES_BEGIN -->"
    feature_end = f"    <!-- {TV_MARKER}_FEATURES_END -->"
    text, _ = strip_owned_block(text, feature_begin, feature_end, "Chromium manifest TV feature block")

    leanback = _feature_declaration(text, LEANBACK_FEATURE)
    if leanback is not None and 'android:required="true"' not in leanback:
        raise PatchError("Upstream leanback feature exists but is not required=true")
    faketouch = _feature_declaration(text, FAKETOUCH_FEATURE)
    if faketouch is not None and 'android:required="false"' not in faketouch:
        raise PatchError("Upstream faketouch feature exists but is not required=false")

    if text.count(feature_anchor) != 1:
        raise PatchError("Chromium manifest TV feature anchor moved")
    insertion = (
        permission_block(include_permission)
        + feature_anchor
        + feature_block(leanback is None, faketouch is None)
    )
    text = text.replace(feature_anchor, insertion, 1)

    activity_begin = f"        <!-- {TV_MARKER}_MANIFEST_BEGIN -->"
    activity_end = f"        <!-- {TV_MARKER}_MANIFEST_END -->"
    text, _ = strip_owned_block(text, activity_begin, activity_end, "Chromium manifest TV activity block")
    activity_name = "org.chromium.chrome.browser.tv.TvBraveActivity"
    if activity_name in text:
        raise PatchError("Chromium manifest contains an unowned TvBraveActivity declaration")
    activity_anchor = '        <!-- ChromeTabbedActivity related -->\n'
    text = replace_once(
        text,
        activity_anchor,
        manifest_activity_block() + activity_anchor,
        "Chromium manifest activity",
    )
    return text


def load_brave_versions(package_json: Path) -> tuple[str, str]:
    try:
        data = json.loads(package_json.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PatchError(f"Unable to read Brave package metadata: {exc}") from exc

    brave = str(data.get("version", "")).strip()
    chromium = str(
        data.get("config", {})
        .get("projects", {})
        .get("chrome", {})
        .get("tag", "")
    ).strip()
    if not brave or not chromium:
        raise PatchError("Brave package metadata is missing version or Chromium tag")
    return brave, chromium


def render_build_info_text(brave_version: str, chromium_version: str) -> str:
    template = (OVERLAY_JAVA / "TvBuildInfo.java").read_text(encoding="utf-8")
    brave_marker = 'static final String BRAVE_VERSION = "development";'
    chromium_marker = 'static final String CHROMIUM_VERSION = "unknown";'
    if template.count(brave_marker) != 1 or template.count(chromium_marker) != 1:
        raise PatchError("TvBuildInfo.java template markers changed")
    return template.replace(
        brave_marker,
        f"static final String BRAVE_VERSION = {json.dumps(brave_version)};",
        1,
    ).replace(
        chromium_marker,
        f"static final String CHROMIUM_VERSION = {json.dumps(chromium_version)};",
        1,
    )


def _png_dimensions(path: Path) -> tuple[int, int]:
    data = path.read_bytes()[:24]
    if len(data) < 24 or not data.startswith(b"\x89PNG\r\n\x1a\n") or data[12:16] != b"IHDR":
        raise PatchError(f"Invalid PNG branding asset: {path}")
    return int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big")


def validate_branding() -> None:
    expected = {
        BRANDING / "tihulu_tv_icon.png": (256, 256),
        BRANDING / "tihulu_tv_banner.png": (320, 180),
    }
    for asset, dimensions in expected.items():
        if not asset.is_file():
            raise PatchError(f"Missing branding asset: {asset}")
        actual = _png_dimensions(asset)
        if actual != dimensions:
            raise PatchError(
                f"Branding asset {asset.name} must be {dimensions[0]}x{dimensions[1]}, "
                f"found {actual[0]}x{actual[1]}"
            )


def _atomic_write_text(path: Path, text: str) -> None:
    tmp = path.with_name(path.name + ".tihulu-tmp")
    tmp.write_text(text, encoding="utf-8")
    os.replace(tmp, path)


def _atomic_write_bytes(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tihulu-tmp")
    tmp.write_bytes(data)
    os.replace(tmp, path)


def apply(project: Path) -> None:
    project = project.resolve()
    required = {
        "java sources": project / "src/brave/android/brave_java_sources.gni",
        "Chrome resources": project / "src/chrome/android/chrome_java_resources.gni",
        "Brave application": project
        / "src/brave/android/java/org/chromium/chrome/browser/BraveApplicationImplBase.java",
        "Chromium manifest": project / "src/chrome/android/java/AndroidManifest.xml",
        "Brave package metadata": project / "src/brave/package.json",
    }
    missing = [f"{name}: {path}" for name, path in required.items() if not path.is_file()]
    if missing:
        raise PatchError(
            "This does not look like an initialized Brave Android project. Missing:\n- "
            + "\n- ".join(missing)
        )

    validate_branding()
    brave_version, chromium_version = load_brave_versions(required["Brave package metadata"])

    # Build the complete patch plan before touching the checkout. Any upstream drift or
    # malformed previous marker therefore fails without leaving a half-applied overlay.
    transformed = {
        required["java sources"]: transform_java_sources(
            required["java sources"].read_text(encoding="utf-8")
        ),
        required["Chrome resources"]: transform_chrome_resources(
            required["Chrome resources"].read_text(encoding="utf-8")
        ),
        required["Brave application"]: transform_brave_application(
            required["Brave application"].read_text(encoding="utf-8")
        ),
        required["Chromium manifest"]: transform_manifest(
            required["Chromium manifest"].read_text(encoding="utf-8")
        ),
    }
    rendered_build_info = render_build_info_text(brave_version, chromium_version)

    destination = project / "src/brave/android/java/org/chromium/chrome/browser/tv"
    destination.mkdir(parents=True, exist_ok=True)
    for name in JAVA_CLASSES:
        content = (
            rendered_build_info.encode("utf-8")
            if name == "TvBuildInfo.java"
            else (OVERLAY_JAVA / name).read_bytes()
        )
        _atomic_write_bytes(destination / name, content)

    # Brave's build command runs branding.update() before GN generation. That routine removes
    # untracked files from Chromium's chrome/android/java/res unless the same resource is owned
    # by Brave's android/java/res source tree. Keep an authoritative Tihulu copy on the Brave side
    # and an immediate Chromium copy for apply->verify; branding then recopies/preserves it during
    # every build instead of deleting it as an unknown file.
    brave_resource_dir = project / "src/brave/android/java/res/drawable-nodpi"
    chrome_resource_dir = project / "src/chrome/android/java/res/drawable-nodpi"
    for name in ["tihulu_tv_banner.png", "tihulu_tv_icon.png"]:
        data = (BRANDING / name).read_bytes()
        _atomic_write_bytes(brave_resource_dir / name, data)
        _atomic_write_bytes(chrome_resource_dir / name, data)

    for path, text in transformed.items():
        _atomic_write_text(path, text)

    print(
        f"Tihulu TV Browser overlay applied to {project} "
        f"(Brave {brave_version}, Chromium {chromium_version})"
    )


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
