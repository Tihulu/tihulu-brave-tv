import hashlib
import importlib.util
import json
import os
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
SPEC = importlib.util.spec_from_file_location("package_apk", ROOT / "scripts/package-apk.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PackageApkTests(unittest.TestCase):
    def apk(self, root, name, abi, timestamp, complete=True):
        path = root / name
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(f"lib/{abi}/libchrome.so", b"native-fixture")
            if complete:
                archive.writestr("AndroidManifest.xml", b"manifest-fixture")
                archive.writestr("classes.dex", b"dex-fixture")
        os.utime(path, (timestamp, timestamp))
        return path

    def test_correct_abi_checksum_and_provenance(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            arm = self.apk(root, "Brave-arm.apk", "armeabi-v7a", 150)
            self.apk(root, "Brave-arm64.apk", "arm64-v8a", 200)
            target = MODULE.package_apk(root, "arm", root / "delivery", 100, "abc123", "v1.94.117")
            self.assertEqual(target.name, "tihulu-tv-armeabi-v7a.apk")
            self.assertEqual(target.read_bytes(), arm.read_bytes())
            data = json.loads(target.with_suffix(".json").read_text())
            digest = hashlib.sha256(target.read_bytes()).hexdigest()
            self.assertEqual(data["sha256"], digest)
            self.assertEqual(data["overlay_commit"], "abc123")
            self.assertFalse(data["device_tested"])
            self.assertEqual(target.with_suffix(".apk.sha256").read_text(), f"{digest}  {target.name}\n")

    def test_stale_build_is_never_published(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.apk(root, "Brave.apk", "arm64-v8a", 10)
            with self.assertRaisesRegex(MODULE.ApkSelectionError, "stale"):
                MODULE.package_apk(root, "arm64", root / "delivery", 100, "sha", "ref")
            self.assertFalse((root / "delivery").exists())

    def test_partial_android_package_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.apk(root, "Brave.apk", "armeabi-v7a", 200, complete=False)
            with self.assertRaisesRegex(MODULE.ApkSelectionError, "manifest"):
                MODULE.package_apk(root, "arm", root / "delivery", 100, "sha", "ref")

    def test_other_architecture_is_not_a_fallback(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.apk(root, "Brave.apk", "arm64-v8a", 200)
            with self.assertRaises(MODULE.ApkSelectionError):
                MODULE.package_apk(root, "arm", root / "delivery", 100, "sha", "ref")


if __name__ == "__main__":
    unittest.main()
