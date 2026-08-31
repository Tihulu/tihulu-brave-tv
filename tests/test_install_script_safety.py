import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class InstallScriptSafetyTests(unittest.TestCase):
    def test_builder_validates_apk_archive_before_handoff(self):
        text = (ROOT / "scripts/build-apk-one-line.sh").read_text(encoding="utf-8")
        self.assertIn('unzip -tq "$APK"', text)
        self.assertIn("invalid or truncated APK", text)

    def test_adb_installer_refuses_ambiguous_device_selection(self):
        text = (ROOT / "scripts/install-apk.sh").read_text(encoding="utf-8")
        self.assertIn('if [[ -n "${ADB_SERIAL:-}" ]]', text)
        self.assertIn("More than one adb device is ready", text)
        self.assertIn("ADB_SERIAL=<serial>", text)
        self.assertIn('unzip -tq "$APK"', text)
        self.assertIn('"${ADB[@]}" install -r "$APK"', text)


if __name__ == "__main__":
    unittest.main()
