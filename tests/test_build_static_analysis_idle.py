import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class BuildStaticAnalysisIdleTests(unittest.TestCase):
    def test_build_waits_before_and_after_incremental_compile(self):
        build = (ROOT / "scripts/build-debug.sh").read_text(encoding="utf-8")
        self.assertGreaterEqual(build.count("--wait-for-idle"), 2)
        self.assertIn(
            'python3 "$CHROMIUM_ROOT/build/android/fast_local_dev_server.py" --wait-for-idle',
            build,
        )
        self.assertIn(
            "python3 build/android/fast_local_dev_server.py --wait-for-idle",
            build,
        )
        self.assertIn("Waiting for stale Chromium background static analysis before patching", build)
        self.assertIn("Waiting for Chromium background static analysis to finish", build)
        pre_wait = build.index("Waiting for stale Chromium background static analysis before patching")
        runtime_verify = build.index("verify_brave_runtime_contract.py")
        mouse_patch = build.index("apply_chromium_tv_mouse_compat.py")
        build_command = build.index('"${PNPM[@]}" "${BUILD_ARGS[@]}"')
        post_wait = build.index("Waiting for Chromium background static analysis to finish")
        self.assertLess(pre_wait, runtime_verify)
        self.assertLess(runtime_verify, mouse_patch)
        self.assertLess(mouse_patch, build_command)
        self.assertLess(build_command, post_wait)


if __name__ == "__main__":
    unittest.main()
