import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TV_DIR = ROOT / "overlay/brave/android/java/org/chromium/chrome/browser/tv"


class ChromiumErrorProneContractTests(unittest.TestCase):
    def test_callback_implementations_have_override_annotations(self):
        text = (TV_DIR / "TvBraveActivity.java").read_text(encoding="utf-8")
        for signature in [
            "public void goBack()",
            "public void goForward()",
            "public void reloadPage()",
            "public void previousTab()",
            "public void nextTab()",
            "public void newTab()",
            "public void closeCurrentTab()",
            "public void showTvControls()",
        ]:
            index = text.index(signature)
            prefix = text[max(0, index - 40) : index]
            self.assertIn("@Override", prefix, signature)

    def test_networking_uses_chromium_annotated_adapter(self):
        for name in ["TvBraveUpstream.java", "TvGitHubUpdater.java"]:
            text = (TV_DIR / name).read_text(encoding="utf-8")
            self.assertIn("ChromiumNetworkAdapter.openConnection", text, name)
            self.assertIn("NetworkTrafficAnnotationTag.createComplete", text, name)
            self.assertNotIn(".openConnection();", text, name)
            self.assertNotIn("new URL(LATEST_STABLE_API).openConnection", text, name)
            self.assertNotIn("new URL(LATEST_RELEASE_API).openConnection", text, name)

    def test_onboarding_cursor_utilities_are_cross_package_accessible(self):
        overlay = (TV_DIR / "TvCursorOverlay.java").read_text(encoding="utf-8")
        mouse = (TV_DIR / "TvMouseDispatcher.java").read_text(encoding="utf-8")
        self.assertIn("public final class TvCursorOverlay", overlay)
        self.assertIn("public TvCursorOverlay(Context context)", overlay)
        self.assertIn("public final class TvMouseDispatcher", mouse)
        self.assertIn("public static void hover", mouse)
        self.assertIn("public static void primaryClick", mouse)

    def test_build_waits_for_background_static_analysis(self):
        builder = (ROOT / "scripts/build-debug.sh").read_text(encoding="utf-8")
        self.assertIn("fast_local_dev_server.py --wait-for-idle", builder)
        self.assertIn("Waiting for Chromium background static analysis", builder)
        self.assertIn('cd "$CHROMIUM_ROOT"', builder)


if __name__ == "__main__":
    unittest.main()
