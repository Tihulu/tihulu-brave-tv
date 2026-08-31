import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class BraveStablePinTests(unittest.TestCase):
    def test_default_build_uses_explicit_stable_ref(self):
        pinned = (ROOT / "config/brave-core-ref").read_text(encoding="utf-8").strip()
        bootstrap = (ROOT / "scripts/bootstrap.sh").read_text(encoding="utf-8")
        self.assertEqual(pinned, "v1.94.117")
        self.assertIn('PINNED_REF_FILE="$ROOT/config/brave-core-ref"', bootstrap)
        self.assertIn('TARGET_BRAVE_REF="$BRAVE_CORE_REF"', bootstrap)
        self.assertIn("Pinning Brave core to $TARGET_BRAVE_REF", bootstrap)
        self.assertIn('git -C "$BRAVE_CORE" checkout --detach "$TARGET_BRAVE_REF"', bootstrap)

    def test_ref_switch_only_cleans_owned_overlay_changes(self):
        bootstrap = (ROOT / "scripts/bootstrap.sh").read_text(encoding="utf-8")
        self.assertIn("clean_generated_brave_overlay()", bootstrap)
        self.assertIn("TIHULU_TV_BROWSER_JAVA_BEGIN", bootstrap)
        self.assertIn("TIHULU_TV_BROWSER_SPATIAL_NAV_BEGIN", bootstrap)
        self.assertIn("Refusing to reset unknown brave-core changes", bootstrap)
        self.assertNotIn('git -C "$BRAVE_CORE" reset --hard', bootstrap)


if __name__ == "__main__":
    unittest.main()
