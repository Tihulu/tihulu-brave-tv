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

    def test_long_ok_uses_dialog_control_surface(self):
        self.assertIn("event.getRepeatCount() > 0", self.activity)
        long_press = self.activity.split("event.getRepeatCount() > 0", 1)[1].split(
            "if (isSelectKey(event.getKeyCode())", 1
        )[0]
        self.assertIn("showTvControls();", long_press)
        self.assertNotIn("addView(", long_press)

    def test_control_panel_is_dialog_backed(self):
        panel = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvControlPanel.java"
        ).read_text(encoding="utf-8")
        self.assertIn("Dialog dialog = new Dialog(context);", panel)
        self.assertIn("dialog.setContentView(column);", panel)
        self.assertIn("dialog.show();", panel)


if __name__ == "__main__":
    unittest.main()
