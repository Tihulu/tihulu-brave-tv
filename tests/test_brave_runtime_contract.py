import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/verify_brave_runtime_contract.py"
SPEC = importlib.util.spec_from_file_location("verify_brave_runtime_contract", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class BraveRuntimeContractTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.workspace = self.root / "workspace"
        self.repo = self.root / "repo"
        self.chromium = self.workspace / "src"
        self.brave = self.chromium / "brave"
        self._write_fixture()

    def tearDown(self):
        self.tmp.cleanup()

    def _write(self, path: Path, text: str):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def _write_fixture(self):
        self._write(
            self.brave
            / "build/android/bytecode/java/org/brave/bytecode/BraveTabbedActivityClassAdapter.java",
            '''sChromeTabbedActivityClassName =\n            "org/chromium/chrome/browser/ChromeTabbedActivity";\n'''
            'sBraveActivityClassName = "org/chromium/chrome/browser/app/BraveActivity";\n'
            "changeSuperName(sChromeTabbedActivityClassName, sBraveActivityClassName);\n",
        )
        self._write(
            self.brave / "browser/brave_browser_main_extra_parts.cc",
            "void BraveBrowserMainExtraParts::PostBrowserStart() {}\n"
            "g_brave_browser_process->StartBraveServices();\n",
        )
        self._write(
            self.brave / "browser/brave_browser_process_impl.cc",
            "std::make_unique<brave_shields::AdBlockService>(args);\nad_block_service_;\n",
        )
        self._write(
            self.brave
            / "components/brave_shields/core/browser/ad_block_filter_list_catalog_provider.cc",
            "RegisterAdBlockFilterListCatalogComponent(cus, cb);\nOnFilterListCatalogLoaded();\n",
        )
        self._write(
            self.brave / "browser/android/brave_shields_content_settings.cc",
            "g_brave_browser_process->ad_block_service();\n"
            "JNI_BraveShieldsContentSettings_GetBraveShieldsEnabled();\n",
        )
        self._write(
            self.chromium
            / "chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java",
            "public class ChromeTabbedActivity extends ChromeActivity {\n"
            "if (getClass().equals(ChromeTabbedActivity.class)) {}\n"
            "if (getClass().equals(ChromeTabbedActivity.class)) {}\n}\n",
        )
        self._write(
            self.repo / "scripts/build-debug.sh",
            "BUILD_ARGS=(\n"
            "  run build Static\n"
            "  --target_os=android\n"
            "  --target_arch=arm\n"
            ")\n",
        )

    def test_accepts_pinned_brave_shields_contract(self):
        MODULE.verify(self.workspace, self.repo)

    def test_fails_closed_if_adblock_browser_process_wiring_disappears(self):
        path = self.brave / "browser/brave_browser_process_impl.cc"
        path.write_text("ad_block_service_;\n", encoding="utf-8")
        with self.assertRaises(MODULE.ContractError):
            MODULE.verify(self.workspace, self.repo)

    def test_fails_closed_if_subclass_exact_class_contract_drifts(self):
        path = (
            self.chromium
            / "chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java"
        )
        path.write_text(
            "public class ChromeTabbedActivity extends ChromeActivity {\n"
            "if (getClass().equals(ChromeTabbedActivity.class)) {}\n}\n",
            encoding="utf-8",
        )
        with self.assertRaises(MODULE.ContractError):
            MODULE.verify(self.workspace, self.repo)

    def test_rejects_unsafe_android_build_args(self):
        path = self.repo / "scripts/build-debug.sh"
        path.write_text(
            "BUILD_ARGS=(\n"
            "  run build Static\n"
            "  --target_os=android\n"
            "  --target_arch=arm\n"
            "  --gn-args=enable_brave_ads=false\n"
            ")\n",
            encoding="utf-8",
        )
        with self.assertRaises(MODULE.ContractError):
            MODULE.verify(self.workspace, self.repo)


if __name__ == "__main__":
    unittest.main()
