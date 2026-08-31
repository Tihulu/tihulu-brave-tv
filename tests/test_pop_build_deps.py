import importlib.util
import types
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts/install_chromium_build_deps.py"
SPEC = importlib.util.spec_from_file_location("install_chromium_build_deps", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PopBuildDependencyTests(unittest.TestCase):
    def test_pop_version_mismatch_is_detected(self):
        with (
            mock.patch.object(MODULE, "is_pop_os", return_value=True),
            mock.patch.object(MODULE, "installed_linux_libc_version", return_value="7.0-pop"),
            mock.patch.object(MODULE, "i386_linux_libc_candidate", return_value="6.8-ubuntu"),
        ):
            skip, installed, candidate = MODULE.should_skip_pop_i386_linux_libc()
        self.assertTrue(skip)
        self.assertEqual(installed, "7.0-pop")
        self.assertEqual(candidate, "6.8-ubuntu")

    def test_matching_versions_are_not_filtered(self):
        with (
            mock.patch.object(MODULE, "is_pop_os", return_value=True),
            mock.patch.object(MODULE, "installed_linux_libc_version", return_value="6.8"),
            mock.patch.object(MODULE, "i386_linux_libc_candidate", return_value="6.8"),
        ):
            skip, _, _ = MODULE.should_skip_pop_i386_linux_libc()
        self.assertFalse(skip)

    def test_only_conflicting_linux_libc_i386_is_removed(self):
        fake = types.SimpleNamespace()
        fake.lib32_list = lambda _options: [
            "libstdc++6:i386",
            "linux-libc-dev:i386",
            "zlib1g:i386",
        ]
        MODULE.install_pop_compat_filter(fake)
        self.assertEqual(
            fake.lib32_list(object()),
            ["libstdc++6:i386", "zlib1g:i386"],
        )

    def test_builder_never_downgrades_pop_linux_libc_dev(self):
        builder = (ROOT / "scripts/build-apk-one-line.sh").read_text(encoding="utf-8")
        wrapper = MODULE_PATH.read_text(encoding="utf-8")
        self.assertIn("install_chromium_build_deps.py", builder)
        self.assertIn("--android", builder)
        self.assertIn("--no-chromeos-fonts", builder)
        self.assertNotIn("apt-get install linux-libc-dev", builder)
        self.assertNotIn("--allow-downgrades", wrapper)
        self.assertIn("CONFLICTING_PACKAGE = \"linux-libc-dev:i386\"", wrapper)


if __name__ == "__main__":
    unittest.main()
