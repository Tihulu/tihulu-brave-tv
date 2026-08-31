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
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
      <application android:name="{% block application_name %}org.chromium.chrome.browser.base.SplitChromeApplication{% endblock %}"
        android:icon="@drawable/ic_launcher">
        <!-- ChromeTabbedActivity related -->
      </application>
</manifest>
""",
        )

    def test_apply_is_idempotent(self):
        MODULE.apply(self.project)
        MODULE.apply(self.project)
        sources = (self.project / "src/brave/android/brave_java_sources.gni").read_text()
        manifest = (self.project / "src/chrome/android/java/AndroidManifest.xml").read_text()
        resources = (self.project / "src/chrome/android/chrome_java_resources.gni").read_text()
        app = (
            self.project
            / "src/brave/android/java/org/chromium/chrome/browser/BraveApplicationImplBase.java"
        ).read_text()
        self.assertEqual(sources.count("TIHULU_TV_BROWSER_JAVA_BEGIN"), 1)
        self.assertEqual(sources.count("TvBraveActivity.java"), 1)
        self.assertEqual(sources.count("TvGitHubUpdater.java"), 1)
        self.assertEqual(resources.count("TIHULU_TV_BROWSER_RESOURCE_BEGIN"), 1)
        self.assertEqual(resources.count("tihulu_tv_banner.png"), 1)
        self.assertEqual(manifest.count("TIHULU_TV_BROWSER_PERMISSIONS_BEGIN"), 1)
        self.assertEqual(manifest.count("android.permission.REQUEST_INSTALL_PACKAGES"), 1)
        self.assertEqual(manifest.count("TIHULU_TV_BROWSER_MANIFEST_BEGIN"), 1)
        self.assertEqual(manifest.count("LEANBACK_LAUNCHER"), 1)
        self.assertEqual(app.count("TIHULU_TV_BROWSER_SPATIAL_NAV_BEGIN"), 1)
        self.assertEqual(app.count('appendSwitch("enable-spatial-navigation")'), 1)
        for name in MODULE.JAVA_CLASSES:
            self.assertTrue(
                (
                    self.project
                    / "src/brave/android/java/org/chromium/chrome/browser/tv"
                    / name
                ).is_file()
            )
        self.assertTrue(
            (
                self.project
                / "src/chrome/android/java/res/drawable-nodpi/tihulu_tv_banner.png"
            ).is_file()
        )

    def test_existing_overlay_is_upgraded_for_updater(self):
        sources = self.project / "src/brave/android/brave_java_sources.gni"
        sources.write_text(
            'import("x")\n\nbrave_java_sources = [\n'
            '  # TIHULU_TV_BROWSER_JAVA_BEGIN\n'
            '  "../../brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java",\n'
            '  # TIHULU_TV_BROWSER_JAVA_END\n'
            '  "existing.java",\n]\n',
            encoding="utf-8",
        )
        manifest = self.project / "src/chrome/android/java/AndroidManifest.xml"
        manifest.write_text(
            """<manifest>
    <uses-feature android:glEsVersion="0x00030000" />
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
        upgraded_sources = sources.read_text(encoding="utf-8")
        upgraded_manifest = manifest.read_text(encoding="utf-8")
        self.assertEqual(upgraded_sources.count("TvGitHubUpdater.java"), 1)
        self.assertEqual(upgraded_manifest.count("android.permission.REQUEST_INSTALL_PACKAGES"), 1)

    def test_missing_anchor_fails_closed(self):
        path = self.project / "src/brave/android/brave_java_sources.gni"
        path.write_text("no source array here\n", encoding="utf-8")
        with self.assertRaises(MODULE.PatchError):
            MODULE.apply(self.project)

    def test_late_anchor_failure_does_not_partially_modify_checkout(self):
        manifest = self.project / "src/chrome/android/java/AndroidManifest.xml"
        manifest.write_text("<manifest>upstream changed</manifest>\n", encoding="utf-8")
        sources = self.project / "src/brave/android/brave_java_sources.gni"
        before = sources.read_text(encoding="utf-8")
        with self.assertRaises(MODULE.PatchError):
            MODULE.apply(self.project)
        self.assertEqual(sources.read_text(encoding="utf-8"), before)
        self.assertFalse(
            (self.project / "src/brave/android/java/org/chromium/chrome/browser/tv").exists()
        )

    def test_manifest_contains_tv_hardware_declarations(self):
        MODULE.apply(self.project)
        manifest = (self.project / "src/chrome/android/java/AndroidManifest.xml").read_text()
        self.assertIn('android.software.leanback" android:required="true"', manifest)
        self.assertIn('android.hardware.faketouch" android:required="false"', manifest)
        self.assertIn('android.software.leanback.supports_touch', manifest)
        self.assertIn('android.permission.REQUEST_INSTALL_PACKAGES', manifest)


if __name__ == "__main__":
    unittest.main()
