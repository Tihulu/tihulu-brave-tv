import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class ChromiumLifecycleContractTests(unittest.TestCase):
    def test_tv_activity_uses_supported_post_inflation_hook(self):
        activity = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"
        ).read_text(encoding="utf-8")
        self.assertNotIn("protected void onCreate(", activity)
        self.assertNotIn("public void onCreate(", activity)
        self.assertIn("public void performPostInflationStartup()", activity)
        self.assertIn("super.performPostInflationStartup();", activity)
        self.assertIn("mTvUiInitialized || isFinishing()", activity)
        self.assertIn("mTvRuntimeEnabled = isTelevision();", activity)

        startup = activity.split("public void performPostInflationStartup()", 1)[1].split(
            "public void onDestroyInternal()", 1
        )[0]
        self.assertIn("mRoot = (ViewGroup) getWindow().getDecorView();", startup)
        self.assertNotIn("addView(", startup)
        self.assertNotIn("addOnLayoutChangeListener", startup)
        self.assertNotIn("getFullscreenManager()", startup)
        self.assertNotIn("ensureCursorInitialized()", startup)
        self.assertNotIn("TvControlPanel.show", startup)

    def test_tv_activity_uses_supported_destroy_hook_with_upstream_visibility(self):
        activity = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"
        ).read_text(encoding="utf-8")
        self.assertNotIn("protected void onDestroy()", activity)
        self.assertNotIn("protected void onDestroyInternal()", activity)
        self.assertIn("public void onDestroyInternal()", activity)
        self.assertIn("super.onDestroyInternal();", activity)

    def test_java_stub_models_upstream_final_on_create_and_destroy_hook(self):
        stub = (
            ROOT / "tests/stubs/org/chromium/chrome/browser/ChromeTabbedActivity.java"
        ).read_text(encoding="utf-8")
        self.assertIn("protected final void onCreate(Bundle b)", stub)
        self.assertIn("public void performPostInflationStartup()", stub)
        self.assertIn("public void onDestroyInternal()", stub)
        self.assertNotIn("protected void onDestroyInternal()", stub)
        self.assertIn("public FullscreenManager getFullscreenManager()", stub)


if __name__ == "__main__":
    unittest.main()
