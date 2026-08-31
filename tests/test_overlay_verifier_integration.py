import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("apply_overlay_integration", ROOT / "scripts/apply_overlay.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class OverlayVerifierIntegrationTests(unittest.TestCase):
    def test_current_chromium_permission_survives_apply_and_verify(self):
        with tempfile.TemporaryDirectory() as temp:
            project = Path(temp)

            def write(rel, text):
                path = project / rel
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")

            write(
                "src/brave/package.json",
                '{"version":"1.96.30","config":{"projects":{"chrome":{"tag":"152.0.7977.64"}}}}\n',
            )
            write(
                "src/brave/android/brave_java_sources.gni",
                'brave_java_sources = [\n  "existing.java",\n]\n',
            )
            write(
                "src/chrome/android/chrome_java_resources.gni",
                'chrome_java_resources = [\n  "java/res/drawable/existing.xml",\n]\n',
            )
            write(
                "src/brave/android/java/org/chromium/chrome/browser/BraveApplicationImplBase.java",
                """package org.chromium.chrome.browser;
import android.app.Application;
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
            write(
                "src/chrome/android/java/AndroidManifest.xml",
                """<manifest>
    <uses-feature android:glEsVersion="0x00030000" />
    <!-- Needed for allowing downloaded APKs to be installed. -->
    <uses-permission-sdk-23 android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
      <application android:name="{% block application_name %}org.chromium.chrome.browser.base.SplitChromeApplication{% endblock %}">
        <!-- ChromeTabbedActivity related -->
      </application>
</manifest>
""",
            )

            MODULE.apply(project)
            result = subprocess.run(
                [sys.executable, str(ROOT / "scripts/verify_overlay.py"), str(project)],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
            self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)
            self.assertIn("Overlay verification passed", result.stdout)


if __name__ == "__main__":
    unittest.main()
