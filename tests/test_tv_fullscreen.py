import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"


class TvFullscreenTests(unittest.TestCase):
    def setUp(self):
        self.activity = ACTIVITY.read_text(encoding="utf-8")

    def test_uses_chromium_fullscreen_manager_observer(self):
        self.assertIn("import org.chromium.chrome.browser.fullscreen.FullscreenManager;", self.activity)
        self.assertIn("new FullscreenManager.Observer()", self.activity)
        self.assertIn("public void onEnterFullscreen(Tab tab, FullscreenOptions options)", self.activity)
        self.assertIn("public void onExitFullscreen(Tab tab)", self.activity)
        self.assertIn("fullscreenManager.addObserver(mFullscreenObserver);", self.activity)
        self.assertIn("setTvFullscreenState(fullscreenManager.getPersistentFullscreenMode());", self.activity)

    def test_fullscreen_hides_tihulu_bar_and_cursor(self):
        self.assertIn("mTvBrowserBar.setVisibility(mHtmlFullscreen ? View.GONE : View.VISIBLE);", self.activity)
        self.assertIn("!mHtmlFullscreen && mNavigationMode == TvNavigationMode.CURSOR", self.activity)
        self.assertIn("mCursorOverlay.setVisibility(showCursor ? View.VISIBLE : View.GONE);", self.activity)

    def test_fullscreen_remote_input_bypasses_tihulu_cursor_and_shortcuts(self):
        fullscreen_guard = "if (mHtmlFullscreen) return super.dispatchKeyEvent(event);"
        controls_shortcut = "if (isControlsShortcut(event))"
        cursor_branch = "if (mNavigationMode == TvNavigationMode.CURSOR)"
        self.assertIn(fullscreen_guard, self.activity)
        self.assertLess(self.activity.index(fullscreen_guard), self.activity.index(controls_shortcut))
        self.assertLess(self.activity.index(fullscreen_guard), self.activity.index(cursor_branch))

    def test_navigation_mode_is_preserved_across_fullscreen(self):
        state_method = self.activity.split("private void setTvFullscreenState", 1)[1].split(
            "private void refreshTvOverlayVisibility", 1
        )[0]
        self.assertNotIn("mNavigationMode =", state_method)
        self.assertIn("refreshTvOverlayVisibility();", state_method)

    def test_fullscreen_observer_is_removed_via_supported_destroy_hook(self):
        self.assertIn("public void onDestroyInternal()", self.activity)
        self.assertNotIn("protected void onDestroyInternal()", self.activity)
        self.assertIn("getFullscreenManager().removeObserver(mFullscreenObserver);", self.activity)
        self.assertIn("super.onDestroyInternal();", self.activity)

    def test_tihulu_does_not_compete_with_chromium_system_bar_management(self):
        self.assertNotIn("WindowInsetsController", self.activity)
        self.assertNotIn("SYSTEM_UI_FLAG_FULLSCREEN", self.activity)
        self.assertNotIn("SYSTEM_UI_FLAG_IMMERSIVE", self.activity)


if __name__ == "__main__":
    unittest.main()
