import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/apply_chromium_tv_focus_compat.py"

spec = importlib.util.spec_from_file_location("chromium_tv_focus_compat", SCRIPT)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ChromiumTvFocusCompatTests(unittest.TestCase):
    def fixture(self):
        return "\n\n".join(
            [
                "/* states */",
                module.ORIGINAL_FOCUS,
                "button[interestfor] { user-select: none; }",
                module.ORIGINAL_ROOT_EXCLUSION,
                module.ORIGINAL_EMBED_EXCLUSION,
                "a:-webkit-any-link:focus-visible { outline-offset: 1px; }",
            ]
        )

    def test_focus_ring_is_large_high_contrast_and_author_resistant(self):
        patched = module.transform(self.fixture())
        self.assertIn(module.BEGIN, patched)
        self.assertIn(module.END, patched)
        self.assertIn("outline: solid 4px rgb(255, 45, 85) !important;", patched)
        self.assertIn("outline-offset: 3px !important;", patched)
        self.assertIn(module.PATCHED_ROOT_EXCLUSION, patched)
        self.assertIn(module.PATCHED_EMBED_EXCLUSION, patched)
        self.assertNotIn(module.ORIGINAL_FOCUS, patched)

    def test_transform_is_idempotent(self):
        once = module.transform(self.fixture())
        self.assertEqual(once, module.transform(once))

    def test_upstream_drift_fails_closed(self):
        drifted = self.fixture().replace(
            module.ORIGINAL_FOCUS,
            ":focus-visible { outline: auto 2px -webkit-focus-ring-color }",
        )
        with self.assertRaises(module.PatchError):
            module.transform(drifted)

    def test_partial_marker_fails_closed(self):
        with self.assertRaises(module.PatchError):
            module.transform(self.fixture() + "\n" + module.BEGIN)

    def test_build_applies_and_restores_chromium_patch(self):
        build = (ROOT / "scripts/build-debug.sh").read_text(encoding="utf-8")
        self.assertIn("apply_chromium_tv_focus_compat.py", build)
        self.assertIn("CHROMIUM_COMPAT_FILES", build)
        self.assertIn("TIHULU_TV_FOCUS_RING_COMPAT", build)
        self.assertIn('git -C "$CHROMIUM_ROOT" restore -- "${CHROMIUM_COMPAT_FILES[@]}"', build)
        self.assertIn("Refusing to overwrite unknown local Chromium change", build)


if __name__ == "__main__":
    unittest.main()
