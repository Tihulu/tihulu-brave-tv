import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class BuildInstallGuardTests(unittest.TestCase):
    def test_build_selects_apk_by_actual_native_abi(self):
        builder = (ROOT / "scripts/build-apk-one-line.sh").read_text(encoding="utf-8")
        self.assertIn('scripts/find_apk.py', builder)
        self.assertIn('"$WORKSPACE/src/out" "$INPUT_ARCH"', builder)
        self.assertNotIn('android_*${ARCH_NAME}', builder)

    def test_installer_checks_apk_and_device_abi(self):
        installer = (ROOT / "scripts/install-apk.sh").read_text(encoding="utf-8")
        self.assertIn('scripts/find_apk.py', installer)
        self.assertIn('shell getprop ro.product.cpu.abilist', installer)
        self.assertIn('REQUIRED_DEVICE_ABI="armeabi-v7a"', installer)
        self.assertIn('REQUIRED_DEVICE_ABI="arm64-v8a"', installer)
        self.assertIn('Refusing to attempt a cross-ABI install.', installer)
        self.assertNotIn('android_*${ARCH}', installer)


if __name__ == "__main__":
    unittest.main()
