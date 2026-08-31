import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"
WINDOW = ROOT / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvCursorWindow.java"


class TvCursorWindowTests(unittest.TestCase):
    def setUp(self):
        self.activity = ACTIVITY.read_text(encoding="utf-8")
        self.window = WINDOW.read_text(encoding="utf-8")

    def test_cursor_is_not_drawn_in_activity_view_overlay(self):
        self.assertNotIn("ViewGroupOverlay", self.activity)
        self.assertNotIn("mRoot.getOverlay()", self.activity)
        self.assertIn("private TvCursorWindow mCursorWindow;", self.activity)
        self.assertIn("mCursorWindow = new TvCursorWindow(this, size);", self.activity)
        self.assertIn("mCursorWindow.moveTo(mCursorState.x(), mCursorState.y());", self.activity)

    def test_cursor_window_is_passive_transparent_and_above_web_surface(self):
        self.assertIn("final class TvCursorWindow", self.window)
        self.assertIn("new Dialog(context)", self.window)
        self.assertIn("FLAG_NOT_FOCUSABLE", self.window)
        self.assertIn("FLAG_NOT_TOUCHABLE", self.window)
        self.assertIn("FLAG_NOT_TOUCH_MODAL", self.window)
        self.assertIn("FLAG_DIM_BEHIND", self.window)
        self.assertIn("Color.TRANSPARENT", self.window)
        self.assertIn("ViewGroup.LayoutParams.MATCH_PARENT", self.window)
        self.assertIn("mDialog.show();", self.window)
        self.assertIn("mDialog.dismiss();", self.window)
        self.assertNotIn("TYPE_APPLICATION_OVERLAY", self.window)
        self.assertNotIn("SYSTEM_ALERT_WINDOW", self.window)

    def test_cursor_window_stays_lazy_and_is_cleaned_up(self):
        startup = self.activity.split("public void performPostInflationStartup()", 1)[1].split(
            "public void onDestroyInternal()", 1
        )[0]
        self.assertNotIn("TvCursorWindow", startup)
        self.assertNotIn("Dialog", startup)
        self.assertIn("mCursorWindow.dismiss();", self.activity)
        self.assertIn("mCursorWindow = null;", self.activity)


if __name__ == "__main__":
    unittest.main()
