import ast
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OVERLAY_JAVA = ROOT / "overlay/brave/android/java/org/chromium/chrome/browser/tv"


def read_java_classes(script: Path) -> list[str]:
    tree = ast.parse(script.read_text(encoding="utf-8"), filename=str(script))
    for node in tree.body:
        if isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name) and target.id == "JAVA_CLASSES":
                    value = ast.literal_eval(node.value)
                    return list(value)
    raise AssertionError(f"JAVA_CLASSES not found in {script}")


class OverlayJavaInventoryTests(unittest.TestCase):
    def test_every_overlay_java_source_is_owned_by_gn_inventory(self):
        actual = sorted(path.name for path in OVERLAY_JAVA.glob("*.java"))
        self.assertTrue(actual)
        for script_name in ["apply_overlay.py", "verify_overlay.py"]:
            declared = sorted(read_java_classes(ROOT / "scripts" / script_name))
            self.assertEqual(
                actual,
                declared,
                f"{script_name} JAVA_CLASSES must exactly match overlay Java sources; "
                "otherwise a file can pass local javac but be missing from Chromium GN",
            )


if __name__ == "__main__":
    unittest.main()
