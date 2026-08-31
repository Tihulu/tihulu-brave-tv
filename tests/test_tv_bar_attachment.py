import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"


class TvBarAttachmentTests(unittest.TestCase):
    def setUp(self):
        self.activity = ACTIVITY.read_text(encoding="utf-8")

    def test_runtime_activity_never_attaches_persistent_tv_bar(self):
        self.assertNotIn("private TvBrowserBar mTvBrowserBar", self.activity)
        self.assertNotIn("installTvBrowserBar()", self.activity)
        self.assertNotIn("focusTvBrowserBar()", self.activity)
        self.assertNotIn("mRoot.addView(", self.activity)
        self.assertNotIn("addContentView(", self.activity)

    def test_hold_up_defers_dialog_creation_until_key_up(self):
        self.assertIn("event.getRepeatCount() > 0", self.activity)
        self.assertIn("mUpLongPressConsumed = true;", self.activity)
        self.assertIn("super.dispatchKeyEvent(event);", self.activity)
        self.assertIn("postShowBrowserBar();", self.activity)
        self.assertIn("mRoot.post(this::showBrowserBar);", self.activity)
        self.assertNotIn("mRoot.addView(", self.activity)

    def test_navigation_mode_changes_from_bar_or_single_long_ok(self):
        bar = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBrowserBar.java"
        ).read_text(encoding="utf-8")
        self.assertIn("callback.toggleNavigationMode();", bar)
        self.assertIn('return mode == TvNavigationMode.CURSOR ? "Mode: Cursor" : "Mode: D-pad";', bar)
        self.assertIn("SELECT_LONG_PRESS_MS = 550L", self.activity)
        self.assertIn("mRoot.postDelayed(mSelectLongPressRunnable, SELECT_LONG_PRESS_MS);", self.activity)
        self.assertIn("if (mPendingSelectDownEvent == null || mSelectLongPressConsumed", self.activity)
        self.assertIn("mSelectLongPressConsumed = true;", self.activity)
        self.assertIn("if (wasLongPress) return true;", self.activity)

    def test_browser_bar_and_control_panel_are_dialog_backed(self):
        bar = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBrowserBar.java"
        ).read_text(encoding="utf-8")
        panel = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvControlPanel.java"
        ).read_text(encoding="utf-8")
        self.assertIn("Dialog dialog = new Dialog(context);", bar)
        self.assertIn("dialog.setContentView(shell);", bar)
        self.assertIn("dialog.show();", bar)
        self.assertIn("Dialog dialog = new Dialog(context);", panel)
        self.assertIn("dialog.setContentView(column);", panel)
        self.assertIn("dialog.show();", panel)


if __name__ == "__main__":
    unittest.main()
