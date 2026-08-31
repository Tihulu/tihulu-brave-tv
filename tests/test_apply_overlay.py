import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("apply_overlay", ROOT / "scripts/apply_overlay.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class OverlayTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.project = Path(self.temp.name)
        self._write_fixture()

    def tearDown(self):
        self.temp.cleanup()

    def _write(self, rel, text):
        path = self.project / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def _write_fixture(self):
        self._write(
            "src/brave/package.json",
            """{
  "name": "brave-core",
  "version": "1.99.7",
  "config": {"projects": {"chrome": {"tag": "154.0.8000.1"}}}
}
""",
        )
        self._write(
            "src/brave/android/brave_java_sources.gni",
            'import("x")\n\nbrave_java_sources = [\n  "existing.java",\n]\n',
        )
        self._write(
            "src/chrome/android/chrome_java_resources.gni",
            'chrome_java_resources = [\n  "java/res/drawable/existing.xml",\n]\n',
        )
        self._write(
            "src/brave/android/java/org/chromium/chrome/browser/BraveApplicationImplBase.java",
            """package org.chromium.chrome.browser;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import org.chromium.base.JavaUtils;

public class BraveApplicationImplBase {
    public void onCreate() {
        super.onCreate();
        if (SplitCompatApplication.isBrowserProcess()) {
        }
    }
}
""",
        )
        self._write(
            "src/chrome/android/java/AndroidManifest.xml",
            """<manifest>
    <uses-feature android:glEsVersion="0x00030000" />
    <uses-permission-sdk-23 android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
      <application android:name="{% block application_name %}org.chromium.chrome.browser.base.SplitChromeApplication{% endblock %}"
        android:icon="@drawable/ic_launcher">
        <!-- ChromeTabbedActivity related -->
      </application>
</manifest>
""",
        )

    def _manifest(self):
        return (self.project / "src/chrome/android/java/AndroidManifest.xml").read_text()

    def test_apply_is_idempotent_with_upstream_install_permission(self):
        MODULE.apply(self.project)
        MODULE.apply(self.project)
        sources = (self.project / "src/brave/android/brave_java_sources.gni").read_text()
        manifest = self._manifest()
        resources = (self.project / "src/chrome/android/chrome_java_resources.gni").read_text()
        app = (
            self.project
            / "src/brave/android/java/org/chromium/chrome/browser/BraveApplicationImplBase.java"
        ).read_text()
        build_info = (
            self.project
            / "src/brave/android/java/org/chromium/chrome/browser/tv/TvBuildInfo.java"
        ).read_text()

        self.assertEqual(sources.count("TIHULU_TV_BROWSER_JAVA_BEGIN"), 1)
        for name in MODULE.JAVA_CLASSES:
            self.assertEqual(sources.count(name), 1)
        self.assertEqual(resources.count("TIHULU_TV_BROWSER_RESOURCE_BEGIN"), 1)
        self.assertEqual(resources.count("tihulu_tv_banner.png"), 1)
        self.assertEqual(resources.count("tihulu_tv_icon.png"), 1)
        self.assertEqual(manifest.count("TIHULU_TV_BROWSER_PERMISSIONS_BEGIN"), 1)
        self.assertEqual(manifest.count("android.permission.REQUEST_INSTALL_PACKAGES"), 1)
        self.assertIn(
            '<uses-permission-sdk-23 android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>',
            manifest,
        )
        self.assertEqual(manifest.count("android.software.leanback"), 2)  # feature + supports_touch metadata
        self.assertEqual(manifest.count("android.hardware.faketouch"), 1)
        self.assertEqual(manifest.count("TIHULU_TV_BROWSER_MANIFEST_BEGIN"), 1)
        self.assertEqual(manifest.count("LEANBACK_LAUNCHER"), 1)
        self.assertEqual(app.count("TIHULU_TV_BROWSER_SPATIAL_NAV_BEGIN"), 1)
        self.assertEqual(app.count('appendSwitch("enable-spatial-navigation")'), 1)
        self.assertGreater(
            app.index("TIHULU_TV_BROWSER_SPATIAL_NAV_BEGIN"),
            app.index("if (SplitCompatApplication.isBrowserProcess())"),
        )
        self.assertIn('BRAVE_VERSION = "1.99.7"', build_info)
        self.assertIn('CHROMIUM_VERSION = "154.0.8000.1"', build_info)
        for name in MODULE.JAVA_CLASSES:
            self.assertTrue(
                (
                    self.project
                    / "src/brave/android/java/org/chromium/chrome/browser/tv"
                    / name
                ).is_file()
            )

    def test_missing_upstream_install_permission_is_owned_once(self):
        manifest = self.project / "src/chrome/android/java/AndroidManifest.xml"
        manifest.write_text(
            manifest.read_text().replace(
                '    <uses-permission-sdk-23 android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>\n',
                "",
            ),
            encoding="utf-8",
        )
        MODULE.apply(self.project)
        patched = self._manifest()
        self.assertEqual(patched.count("android.permission.REQUEST_INSTALL_PACKAGES"), 1)
        self.assertIn(
            '<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />',
            patched,
        )

    def test_old_duplicate_permission_block_is_migrated_to_upstream_ownership(self):
        manifest = self.project / "src/chrome/android/java/AndroidManifest.xml"
        text = manifest.read_text(encoding="utf-8")
        old = (
            "    <!-- TIHULU_TV_BROWSER_PERMISSIONS_BEGIN -->\n"
            '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n'
            "    <!-- TIHULU_TV_BROWSER_PERMISSIONS_END -->\n"
        )
        text = text.replace('    <uses-feature android:glEsVersion="0x00030000" />\n', old + '    <uses-feature android:glEsVersion="0x00030000" />\n')
        manifest.write_text(text, encoding="utf-8")

        MODULE.apply(self.project)
        patched = self._manifest()
        self.assertEqual(patched.count("android.permission.REQUEST_INSTALL_PACKAGES"), 1)
        self.assertIn("<uses-permission-sdk-23", patched)
        begin = patched.index("TIHULU_TV_BROWSER_PERMISSIONS_BEGIN")
        end = patched.index("TIHULU_TV_BROWSER_PERMISSIONS_END")
        self.assertNotIn("REQUEST_INSTALL_PACKAGES", patched[begin:end])

    def test_existing_overlay_is_upgraded_for_current_activity_contract(self):
        sources = self.project / "src/brave/android/brave_java_sources.gni"
        sources.write_text(
            'import("x")\n\nbrave_java_sources = [\n'
            '  # TIHULU_TV_BROWSER_JAVA_BEGIN\n'
            '  "../../brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java",\n'
            '  # TIHULU_TV_BROWSER_JAVA_END\n'
            '  "existing.java",\n]\n',
            encoding="utf-8",
        )
        resources = self.project / "src/chrome/android/chrome_java_resources.gni"
        resources.write_text(
            'chrome_java_resources = [\n'
            '  # TIHULU_TV_BROWSER_RESOURCE_BEGIN\n'
            '  "java/res/drawable-nodpi/tihulu_tv_banner.png",\n'
            '  # TIHULU_TV_BROWSER_RESOURCE_END\n'
            '  "java/res/drawable/existing.xml",\n]\n',
            encoding="utf-8",
        )
        manifest = self.project / "src/chrome/android/java/AndroidManifest.xml"
        manifest.write_text(
            """<manifest>
    <!-- TIHULU_TV_BROWSER_PERMISSIONS_BEGIN -->
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <!-- TIHULU_TV_BROWSER_PERMISSIONS_END -->
    <uses-feature android:glEsVersion="0x00030000" />
    <uses-permission-sdk-23 android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>
    <!-- TIHULU_TV_BROWSER_FEATURES_BEGIN -->
    <uses-feature android:name="android.software.leanback" android:required="true" />
    <uses-feature android:name="android.hardware.faketouch" android:required="false" />
    <!-- TIHULU_TV_BROWSER_FEATURES_END -->
      <application android:name="{% block application_name %}org.chromium.chrome.browser.base.SplitChromeApplication{% endblock %}"
        android:icon="@drawable/ic_launcher">
        <!-- TIHULU_TV_BROWSER_MANIFEST_BEGIN -->
        <activity android:name="org.chromium.chrome.browser.tv.TvBraveActivity" />
        <!-- TIHULU_TV_BROWSER_MANIFEST_END -->
        <!-- ChromeTabbedActivity related -->
      </application>
</manifest>
""",
            encoding="utf-8",
        )

        MODULE.apply(self.project)
        upgraded_manifest = self._manifest()
        self.assertEqual(upgraded_manifest.count("android.permission.REQUEST_INSTALL_PACKAGES"), 1)
        self.assertIn('android:windowSoftInputMode="adjustResize"', upgraded_manifest)
        self.assertIn("|navigation|density|touchscreen|colorMode|fontScale", upgraded_manifest)
        self.assertIn(
            'android:name="android.activity.launch_mode" android:value="singleInstancePerTask"',
            upgraded_manifest,
        )
        self.assertEqual(sources.read_text().count("TvBraveUpstream.java"), 1)
        self.assertEqual(resources.read_text().count("tihulu_tv_icon.png"), 1)

    def test_invalid_brave_metadata_fails_before_writes(self):
        package_json = self.project / "src/brave/package.json"
        package_json.write_text('{"version":""}\n', encoding="utf-8")
        sources = self.project / "src/brave/android/brave_java_sources.gni"
        before = sources.read_text(encoding="utf-8")
        with self.assertRaises(MODULE.PatchError):
            MODULE.apply(self.project)
        self.assertEqual(sources.read_text(encoding="utf-8"), before)
        self.assertFalse(
            (self.project / "src/brave/android/java/org/chromium/chrome/browser/tv").exists()
        )

    def test_missing_anchor_fails_closed(self):
        path = self.project / "src/brave/android/brave_java_sources.gni"
        path.write_text("no source array here\n", encoding="utf-8")
        with self.assertRaises(MODULE.PatchError):
            MODULE.apply(self.project)

    def test_malformed_owned_marker_fails_before_writes(self):
        sources = self.project / "src/brave/android/brave_java_sources.gni"
        before = sources.read_text(encoding="utf-8")
        manifest = self.project / "src/chrome/android/java/AndroidManifest.xml"
        manifest.write_text(
            manifest.read_text().replace(
                '    <uses-feature android:glEsVersion="0x00030000" />\n',
                "    <!-- TIHULU_TV_BROWSER_FEATURES_BEGIN -->\n"
                '    <uses-feature android:glEsVersion="0x00030000" />\n',
            ),
            encoding="utf-8",
        )
        with self.assertRaises(MODULE.PatchError):
            MODULE.apply(self.project)
        self.assertEqual(sources.read_text(encoding="utf-8"), before)
        self.assertFalse(
            (self.project / "src/brave/android/java/org/chromium/chrome/browser/tv").exists()
        )

    def test_conflicting_upstream_tv_feature_fails_closed(self):
        manifest = self.project / "src/chrome/android/java/AndroidManifest.xml"
        manifest.write_text(
            manifest.read_text().replace(
                '    <uses-feature android:glEsVersion="0x00030000" />\n',
                '    <uses-feature android:glEsVersion="0x00030000" />\n'
                '    <uses-feature android:name="android.software.leanback" android:required="false" />\n',
            ),
            encoding="utf-8",
        )
        with self.assertRaises(MODULE.PatchError):
            MODULE.apply(self.project)

    def test_manifest_contains_tv_activity_parity_attributes(self):
        MODULE.apply(self.project)
        manifest = self._manifest()
        self.assertIn('android.software.leanback" android:required="true"', manifest)
        self.assertIn('android.hardware.faketouch" android:required="false"', manifest)
        self.assertIn('android.software.leanback.supports_touch', manifest)
        self.assertIn('android:windowSoftInputMode="adjustResize"', manifest)
        self.assertIn('android:hardwareAccelerated="false"', manifest)
        for value in ["navigation", "touchscreen", "colorMode", "fontScale"]:
            self.assertIn(value, manifest)


if __name__ == "__main__":
    unittest.main()
