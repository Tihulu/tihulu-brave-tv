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

    def test_github_update_button_downloads_release_apk(self):
        panel = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvControlPanel.java"
        ).read_text(encoding="utf-8")
        activity = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"
        ).read_text(encoding="utf-8")
        updater = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvGitHubUpdater.java"
        ).read_text(encoding="utf-8")
        patcher = (ROOT / "scripts/apply_overlay.py").read_text(encoding="utf-8")
        self.assertIn('update.setText("Check for updates")', panel)
        self.assertIn("callback.checkForUpdates();", panel)
        self.assertIn("TvGitHubUpdater.checkAndInstall(this, mRoot);", activity)
        self.assertIn("/releases/latest", updater)
        self.assertIn("browser_download_url", updater)
        self.assertIn("DownloadManager.Request", updater)
        self.assertIn("FLAG_GRANT_READ_URI_PERMISSION", updater)
        self.assertIn("android.permission.REQUEST_INSTALL_PACKAGES", patcher)

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

    def test_optional_apt_packages_require_real_candidate(self):
        installer = (ROOT / "scripts/install-host-deps.sh").read_text(encoding="utf-8")
        self.assertIn("package_has_candidate()", installer)
        self.assertIn('LC_ALL=C apt-cache policy "$package"', installer)
        self.assertIn('"$candidate" != "(none)"', installer)
        self.assertIn("if package_has_candidate python-is-python3; then", installer)
        self.assertIn("if package_has_candidate python3-distutils; then", installer)
        self.assertNotIn("apt-cache show python3-distutils", installer)

    def test_host_installer_bootstraps_javac(self):
        installer = (ROOT / "scripts/install-host-deps.sh").read_text(encoding="utf-8")
        self.assertIn("if ! command -v javac", installer)
        self.assertIn("package_has_candidate openjdk-21-jdk-headless", installer)
        self.assertIn("package_has_candidate default-jdk-headless", installer)
        self.assertIn("JDK installation completed but javac is still unavailable", installer)
        self.assertIn('echo "Java: $(javac -version 2>&1)"', installer)


if __name__ == "__main__":
    unittest.main()
