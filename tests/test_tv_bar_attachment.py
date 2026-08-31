import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"


class TvBarAttachmentTests(unittest.TestCase):
    def setUp(self):
        self.activity = ACTIVITY.read_text(encoding="utf-8")

    def test_bar_never_uses_activity_add_content_view(self):
        install = self.activity.split("private void installTvBrowserBar()", 1)[1].split(
            "private void focusTvBrowserBar()", 1
        )[0]
        self.assertNotIn("addContentView(", install)
        self.assertNotIn("setContentView(", install)

    def test_bar_appends_to_existing_decor_hierarchy(self):
        install = self.activity.split("private void installTvBrowserBar()", 1)[1].split(
            "private void focusTvBrowserBar()", 1
        )[0]
        self.assertIn("mRoot.addView(", install)
        self.assertIn("ViewGroup.LayoutParams.MATCH_PARENT", install)
        self.assertIn("ViewGroup.LayoutParams.WRAP_CONTENT", install)
        self.assertIn("mTvBrowserBar = bar;", install)

    def test_root_stub_models_direct_child_attachment(self):
        stub = (ROOT / "tests/stubs/android/view/ViewGroup.java").read_text(encoding="utf-8")
        self.assertIn("public void addView(View v, LayoutParams p)", stub)


if __name__ == "__main__":
    unittest.main()
