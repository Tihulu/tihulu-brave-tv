import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class TvUiAndInstallerTests(unittest.TestCase):
    def test_tv_browser_bar_has_large_remote_actions(self):
        text = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBrowserBar.java"
        ).read_text(encoding="utf-8")
        for label in ["Back", "Forward", "Reload", "Search / Address", "Tabs", "TV Controls"]:
            self.assertIn(label, text)
        self.assertIn("dp(context, 64)", text)

    def test_long_ok_focuses_tv_bar(self):
        text = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"
        ).read_text(encoding="utf-8")
        self.assertIn("event.getRepeatCount() > 0", text)
        self.assertIn("focusTvBrowserBar();", text)

    def test_one_line_builder_runs_full_dependency_chain(self):
        installer = (ROOT / "scripts/install-host-deps.sh").read_text(encoding="utf-8")
        builder = (ROOT / "scripts/build-apk-one-line.sh").read_text(encoding="utf-8")
        entrypoint = (ROOT / "install.sh").read_text(encoding="utf-8")
        self.assertIn('GIT_MIN="2.41.0"', installer)
        self.assertIn('NODE_MIN="24.16.0"', installer)
        self.assertIn('NODE_MAX="25.0.0"', installer)
        self.assertIn('PNPM_MIN="11.9.0"', installer)
        self.assertIn('npm install --prefix', installer)
        self.assertIn("latest-v24.x/SHASUMS256.txt", installer)
        self.assertIn("sha256sum --check --strict", installer)
        self.assertIn("install-build-deps.sh", builder)
        self.assertIn("build-debug.sh", builder)
        self.assertIn("MEM_KB", builder)
        self.assertIn("git clone https://github.com/Tihulu/tihulu-brave-tv.git", entrypoint)


if __name__ == "__main__":
    unittest.main()
