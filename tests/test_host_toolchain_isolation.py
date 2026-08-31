import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class HostToolchainIsolationTests(unittest.TestCase):
    def test_fallback_git_ignores_conda_and_linker_overrides(self):
        installer = (ROOT / "scripts/install-host-deps.sh").read_text(encoding="utf-8")

        self.assertIn("run_clean_host_tool()", installer)
        for variable in [
            "CONDA_PREFIX",
            "CONDA_DEFAULT_ENV",
            "VIRTUAL_ENV",
            "LD_LIBRARY_PATH",
            "LIBRARY_PATH",
            "CPATH",
            "PKG_CONFIG_PATH",
            "CMAKE_PREFIX_PATH",
            "CFLAGS",
            "CPPFLAGS",
            "CXXFLAGS",
            "LDFLAGS",
            "LIBS",
            "CURL_CONFIG",
        ]:
            self.assertIn(f"-u {variable}", installer)

        self.assertIn("PATH=/usr/bin:/bin", installer)
        self.assertIn("run_clean_host_tool /usr/bin/make configure", installer)
        self.assertIn('run_clean_host_tool ./configure --prefix="$prefix"', installer)
        self.assertIn("run_clean_host_tool /usr/bin/make -j", installer)
        self.assertIn("run_clean_host_tool /usr/bin/make install", installer)
        self.assertIn("run_clean_host_tool /usr/bin/curl", installer)

    def test_partial_local_git_prefix_is_rebuilt_cleanly(self):
        installer = (ROOT / "scripts/install-host-deps.sh").read_text(encoding="utf-8")
        self.assertIn('rm -rf "$prefix"', installer)
        self.assertIn("Conda/venv libraries are ignored", installer)


if __name__ == "__main__":
    unittest.main()
