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

    def test_fullscreen_closes_browser_bar_but_preserves_cursor_mode(self):
        state = self.activity.split("private void setTvFullscreenState", 1)[1].split(
            "private void refreshTvOverlayVisibility", 1
        )[0]
        visibility = self.activity.split("private void refreshTvOverlayVisibility", 1)[1].split(
            "private void ensureCursorInitialized", 1
        )[0]
        self.assertIn("dismissBrowserBar();", state)
        self.assertIn("mPointerMappingValid = false;", state)
        self.assertIn("scheduleCursorHover();", state)
        self.assertIn("boolean showCursor = mNavigationMode == TvNavigationMode.CURSOR;", visibility)
        self.assertNotIn("!mHtmlFullscreen && mNavigationMode", visibility)
        self.assertIn("mCursorOverlay.setVisibility(showCursor ? View.VISIBLE : View.GONE);", visibility)

    def test_fullscreen_dpad_is_native_but_cursor_owns_direction_keys(self):
        dispatch = self.activity.split("public boolean dispatchKeyEvent(KeyEvent event)", 1)[1].split(
            "public TvNavigationMode navigationMode()", 1
        )[0]
        native_guard = "if (mHtmlFullscreen && mNavigationMode == TvNavigationMode.DPAD)"
        cursor_branch = "if (mNavigationMode == TvNavigationMode.CURSOR)"
        self.assertIn(native_guard, dispatch)
        self.assertIn(cursor_branch, dispatch)
        self.assertLess(dispatch.index(native_guard), dispatch.index(cursor_branch))
        self.assertIn("moveCursorForKey(keyCode, event.getRepeatCount());", dispatch)
        self.assertIn("!mHtmlFullscreen", dispatch)
        self.assertNotIn("if (mHtmlFullscreen) return super.dispatchKeyEvent(event);", dispatch)

    def test_navigation_mode_is_preserved_across_fullscreen(self):
        state_method = self.activity.split("private void setTvFullscreenState", 1)[1].split(
            "private void refreshTvOverlayVisibility", 1
        )[0]
        self.assertNotIn("setNavigationMode(", state_method)
        self.assertNotIn("mNavigationMode = TvNavigationMode", state_method)
        self.assertIn("refreshTvOverlayVisibility();", state_method)

    def test_fullscreen_observer_is_lazy_and_removed_via_supported_destroy_hook(self):
        startup = self.activity.split("public void performPostInflationStartup()", 1)[1].split(
            "public void onDestroyInternal()", 1
        )[0]
        self.assertNotIn("getFullscreenManager()", startup)
        self.assertIn("private void ensureFullscreenObserverRegistered()", self.activity)
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
