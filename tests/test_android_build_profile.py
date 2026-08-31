import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class AndroidBuildProfileTests(unittest.TestCase):
    def test_android_local_build_is_non_component_static(self):
        builder = (ROOT / "scripts/build-debug.sh").read_text(encoding="utf-8")
        self.assertIn('run build Static', builder)
        self.assertNotIn('run build Debug', builder)
        self.assertIn('--target_os=android', builder)
        self.assertIn('--target_android_output_format=apk', builder)
        self.assertIn('Chromium explicitly forbids component', builder)

    def test_tv_branding_survives_brave_branding_cleanup(self):
        patcher = (ROOT / "scripts/apply_overlay.py").read_text(encoding="utf-8")
        verifier = (ROOT / "scripts/verify_overlay.py").read_text(encoding="utf-8")
        bootstrap = (ROOT / "scripts/bootstrap.sh").read_text(encoding="utf-8")

        self.assertIn('brave_resource_dir = project / "src/brave/android/java/res/drawable-nodpi"', patcher)
        self.assertIn('chrome_resource_dir = project / "src/chrome/android/java/res/drawable-nodpi"', patcher)
        self.assertIn('Brave branding source', verifier)
        self.assertIn('Chromium branding destination', verifier)
        self.assertIn('android/java/res/drawable-nodpi/tihulu_tv_banner.png', bootstrap)
        self.assertIn('android/java/res/drawable-nodpi/tihulu_tv_icon.png', bootstrap)


if __name__ == "__main__":
    unittest.main()
