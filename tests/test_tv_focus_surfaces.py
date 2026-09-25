import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TV = ROOT / "overlay/brave/android/java/org/chromium/chrome/browser/tv"


class TvFocusSurfaceTests(unittest.TestCase):
    def test_shared_focus_preserves_dynamic_labels_and_dimensions(self):
        text = (TV / "TvUi.java").read_text(encoding="utf-8")
        focus = text.split("button.setOnFocusChangeListener", 1)[1].split("});", 1)[0]
        self.assertIn("setBackground", focus)
        self.assertIn("setTextColor", focus)
        self.assertNotIn("setText(", focus)
        self.assertNotIn("setPadding", focus)
        for name in ["TvControlPanel.java", "TvTabPanel.java", "TvAboutPanel.java"]:
            self.assertIn("TvUi.styleButton", (TV / name).read_text())

    def test_all_tihulu_dialog_focus_surfaces_avoid_animation_transforms(self):
        for name in ["TvBrowserBar.java", "TvControlPanel.java", "TvTabPanel.java"]:
            text = (TV / name).read_text(encoding="utf-8")
            self.assertNotIn("setScaleX", text, name)
            self.assertNotIn("setScaleY", text, name)
            self.assertNotIn("animate()", text, name)


if __name__ == "__main__":
    unittest.main()
