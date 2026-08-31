import importlib.util
import os
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("find_apk", ROOT / "scripts/find_apk.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class ApkSelectionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.out = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def _apk(self, name: str, *abis: str, mtime_ns: int) -> Path:
        path = self.out / name
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("AndroidManifest.xml", b"manifest")
            for abi in abis:
                archive.writestr(f"lib/{abi}/libchrome.so", b"native")
        os.utime(path, ns=(mtime_ns, mtime_ns))
        return path

    def test_arm_does_not_match_newer_arm64_apk(self):
        arm = self._apk("Brave-arm.apk", "armeabi-v7a", mtime_ns=1_000)
        self._apk("Brave-arm64.apk", "arm64-v8a", mtime_ns=2_000)
        self.assertEqual(MODULE.find_apk(self.out, "arm"), arm)

    def test_arm64_does_not_match_arm_apk(self):
        self._apk("Brave-arm.apk", "armeabi-v7a", mtime_ns=2_000)
        arm64 = self._apk("Brave-arm64.apk", "arm64-v8a", mtime_ns=1_000)
        self.assertEqual(MODULE.find_apk(self.out, "arm64"), arm64)

    def test_universal_apk_is_accepted_when_it_contains_target_abi(self):
        universal = self._apk(
            "Brave-universal.apk", "armeabi-v7a", "arm64-v8a", mtime_ns=1_000
        )
        self.assertEqual(MODULE.find_apk(self.out, "arm"), universal)
        self.assertEqual(MODULE.find_apk(self.out, "arm64"), universal)

    def test_truncated_newer_apk_is_ignored(self):
        valid = self._apk("Brave-valid.apk", "armeabi-v7a", mtime_ns=1_000)
        broken = self.out / "Brave-broken.apk"
        broken.write_bytes(b"not a zip")
        os.utime(broken, ns=(2_000, 2_000))
        self.assertEqual(MODULE.find_apk(self.out, "arm"), valid)

    def test_no_cross_abi_fallback(self):
        self._apk("Brave-arm64.apk", "arm64-v8a", mtime_ns=1_000)
        with self.assertRaises(MODULE.ApkSelectionError):
            MODULE.find_apk(self.out, "arm")

    def test_arch_aliases_are_exact(self):
        self.assertEqual(MODULE.normalize_abi("armeabi-v7a"), "armeabi-v7a")
        self.assertEqual(MODULE.normalize_abi("arm64-v8a"), "arm64-v8a")
        self.assertEqual(MODULE.normalize_abi("x64"), "x86_64")
        self.assertEqual(MODULE.normalize_abi("x86"), "x86")


if __name__ == "__main__":
    unittest.main()
