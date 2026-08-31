import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class IncrementalOverlayTests(unittest.TestCase):
    def test_build_uses_fingerprinted_overlay_wrapper(self):
        builder = (ROOT / "scripts/build-debug.sh").read_text(encoding="utf-8")
        self.assertIn('scripts/ensure_overlay.py', builder)
        self.assertNotIn('python3 "$ROOT/scripts/apply_overlay.py" "$WORKSPACE"', builder)
        self.assertNotIn('python3 "$ROOT/scripts/verify_overlay.py" "$WORKSPACE"', builder)

    def test_bootstrap_uses_same_fingerprinted_overlay_wrapper(self):
        bootstrap = (ROOT / "scripts/bootstrap.sh").read_text(encoding="utf-8")
        self.assertIn('python3 scripts/ensure_overlay.py "$WORKSPACE"', bootstrap)
        self.assertNotIn('python3 scripts/apply_overlay.py "$WORKSPACE"', bootstrap)
        self.assertNotIn('python3 scripts/verify_overlay.py "$WORKSPACE"', bootstrap)

    def test_overlay_wrapper_requires_both_matching_fingerprint_and_verifier(self):
        wrapper = (ROOT / "scripts/ensure_overlay.py").read_text(encoding="utf-8")
        self.assertIn('STAMP_NAME = ".tihulu_tv_overlay_fingerprint"', wrapper)
        self.assertIn("if current == wanted and verify(project, quiet=True):", wrapper)
        self.assertIn('scripts/apply_overlay.py', wrapper)
        self.assertIn('scripts/verify_overlay.py', wrapper)
        self.assertIn('hashlib.sha256()', wrapper)


if __name__ == "__main__":
    unittest.main()
