import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TV = ROOT / "overlay/brave/android/java/org/chromium/chrome/browser/tv"


class TvFocusSurfaceTests(unittest.TestCase):
    def test_control_panel_uses_high_contrast_focus_without_scale_animation(self):
        text = (TV / "TvControlPanel.java").read_text(encoding="utf-8")
        self.assertIn("FOCUSED_BG = Color.rgb(218, 32, 40)", text)
        self.assertIn("setOnFocusChangeListener", text)
        self.assertIn("button.setBackgroundColor(focused ? FOCUSED_BG : NORMAL_BG);", text)
        self.assertNotIn("setScaleX", text)
        self.assertNotIn("setScaleY", text)

    def test_tab_panel_uses_high_contrast_focus_and_text_markers(self):
        text = (TV / "TvTabPanel.java").read_text(encoding="utf-8")
        self.assertIn("FOCUSED_BG = Color.rgb(218, 32, 40)", text)
        self.assertIn("setOnFocusChangeListener", text)
        self.assertIn('button.setText(focused ? "▶ " + label + " ◀" : label);', text)
        self.assertNotIn("setScaleX", text)
        self.assertNotIn("setScaleY", text)

    def test_all_tihulu_dialog_focus_surfaces_avoid_animation_transforms(self):
        for name in ["TvBrowserBar.java", "TvControlPanel.java", "TvTabPanel.java"]:
            text = (TV / name).read_text(encoding="utf-8")
            self.assertNotIn("setScaleX", text, name)
            self.assertNotIn("setScaleY", text, name)
            self.assertNotIn("animate()", text, name)


if __name__ == "__main__":
    unittest.main()
