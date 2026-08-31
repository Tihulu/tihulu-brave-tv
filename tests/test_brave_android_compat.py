import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "apply_brave_android_compat", ROOT / "scripts/apply_brave_android_compat.py"
)
assert SPEC and SPEC.loader
compat = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(compat)


BUGGY_HEADER = """namespace brave_ads {

class AdsService;
class AdsTooltipsDelegateImpl;

class AdsServiceFactory final {
 private:
  std::unique_ptr<AdsTooltipsDelegateImpl> CreateAdsTooltipsDelegate() const;
};

}  // namespace brave_ads
"""

BUGGY_SOURCE = """#include "brave/browser/brave_ads/ads_service_factory.h"
#include "brave/browser/brave_ads/tooltips/ads_tooltips_delegate_impl.h"

namespace brave_ads {

std::unique_ptr<AdsTooltipsDelegateImpl>
AdsServiceFactory::CreateAdsTooltipsDelegate() const {
#if BUILDFLAG(IS_ANDROID)
  return nullptr;
#else
  return std::make_unique<AdsTooltipsDelegateImpl>();
#endif
}

}  // namespace brave_ads
"""

TOOLTIPS_BUILD = """source_set("tooltips") {
  sources = []
  if (!is_android) {
    sources += [
      "ads_tooltips_controller.cc",
      "ads_tooltips_delegate_impl.cc",
    ]
  }
}
"""

BUGGY_SOURCES_GNI = """brave_chrome_browser_deps = []

if (enable_brave_ads) {
  brave_chrome_browser_deps += [
    "//brave/browser/brave_ads",
    "//brave/browser/brave_ads:impl",
    "//brave/browser/brave_ads/creatives/search_result_ad",
    "//brave/browser/brave_ads/tabs",
    "//brave/browser/notifications",
    "//brave/browser/ui/webui/ads_internals",
  ]
}

if (is_android) {
  brave_chrome_browser_allow_circular_includes_from += [
    "//brave/browser/android:android_browser_process",
    "//brave/browser/android:tabs_impl",
    "//brave/browser/android/preferences",
    "//brave/browser/notifications",
  ]
}
"""


class BraveAndroidCompatTests(unittest.TestCase):
    def test_concrete_android_unique_ptr_is_replaced_by_interface_ownership(self):
        header, source, applied = compat.transform(BUGGY_HEADER, BUGGY_SOURCE, TOOLTIPS_BUILD)
        self.assertTrue(applied)
        self.assertIn("std::unique_ptr<AdsTooltipsDelegate> CreateAdsTooltipsDelegate() const;", header)
        self.assertIn(
            "std::unique_ptr<AdsTooltipsDelegate>\nAdsServiceFactory::CreateAdsTooltipsDelegate() const {",
            source,
        )
        self.assertIn(
            '#include "brave/components/brave_ads/browser/tooltips/ads_tooltips_delegate.h"',
            source,
        )
        self.assertNotIn(
            "std::unique_ptr<AdsTooltipsDelegateImpl>\nAdsServiceFactory::CreateAdsTooltipsDelegate() const {",
            source,
        )
        self.assertIn("TIHULU_ANDROID_ADS_TOOLTIP_COMPAT_HEADER_BEGIN", header)
        self.assertIn("TIHULU_ANDROID_ADS_TOOLTIP_COMPAT_SOURCE_BEGIN", source)

    def test_transform_is_idempotent(self):
        header, source, _ = compat.transform(BUGGY_HEADER, BUGGY_SOURCE, TOOLTIPS_BUILD)
        again_header, again_source, applied = compat.transform(header, source, TOOLTIPS_BUILD)
        self.assertTrue(applied)
        self.assertEqual(header, again_header)
        self.assertEqual(source, again_source)

    def test_partial_marker_fails_closed(self):
        broken = BUGGY_HEADER.replace(
            "class AdsTooltipsDelegateImpl;",
            "// TIHULU_ANDROID_ADS_TOOLTIP_COMPAT_HEADER_BEGIN\nclass AdsTooltipsDelegateImpl;",
        )
        with self.assertRaises(compat.CompatError):
            compat.transform(broken, BUGGY_SOURCE, TOOLTIPS_BUILD)

    def test_changed_android_tooltip_graph_fails_closed(self):
        with self.assertRaises(compat.CompatError):
            compat.transform(BUGGY_HEADER, BUGGY_SOURCE, 'sources = ["ads_tooltips_controller.cc"]\n')

    def test_ads_disabled_android_circular_allowlist_matches_deps(self):
        fixed, applied = compat.transform_sources_gni(BUGGY_SOURCES_GNI)
        self.assertTrue(applied)
        self.assertIn("TIHULU_ANDROID_ADS_TOOLTIP_COMPAT_GN_BEGIN", fixed)
        self.assertIn("if (enable_brave_ads) {", fixed)
        self.assertIn('[ "//brave/browser/notifications" ]', fixed)
        unconditional = """    "//brave/browser/android/preferences",
    "//brave/browser/notifications",
  ]
}"""
        self.assertNotIn(unconditional, fixed)

    def test_gn_markers_use_gn_comment_syntax(self):
        fixed, _ = compat.transform_sources_gni(BUGGY_SOURCES_GNI)
        self.assertIn("# TIHULU_ANDROID_ADS_TOOLTIP_COMPAT_GN_BEGIN", fixed)
        self.assertIn("# TIHULU_ANDROID_ADS_TOOLTIP_COMPAT_GN_END", fixed)
        self.assertNotIn("// TIHULU_ANDROID_ADS_TOOLTIP_COMPAT_GN_BEGIN", fixed)
        self.assertNotIn("// TIHULU_ANDROID_ADS_TOOLTIP_COMPAT_GN_END", fixed)

    def test_gn_transform_is_idempotent(self):
        fixed, _ = compat.transform_sources_gni(BUGGY_SOURCES_GNI)
        again, applied = compat.transform_sources_gni(fixed)
        self.assertTrue(applied)
        self.assertEqual(fixed, again)

    def test_gn_dependency_drift_fails_closed(self):
        drifted = BUGGY_SOURCES_GNI.replace(
            '    "//brave/browser/notifications",\n', "", 1
        )
        with self.assertRaises(compat.CompatError):
            compat.transform_sources_gni(drifted)

    def test_arm32_build_preserves_brave_android_jni_graph(self):
        builder = (ROOT / "scripts/build-debug.sh").read_text(encoding="utf-8")
        self.assertIn('if [[ "$ARCH" == "arm" ]]', builder)
        self.assertNotIn("--gn=enable_brave_rewards:false", builder)
        self.assertNotIn("--gn=enable_brave_ads:false", builder)
        self.assertIn("preserving Brave Android JNI feature graph", builder)
        self.assertIn("Chromium low-end mode remains enabled", builder)
        self.assertIn("Shields remains enabled", builder)
        self.assertIn("BraveRewardsNativeWorker", builder)
        self.assertIn("BraveAdsNativeHelper", builder)
        self.assertIn("apply_brave_android_compat.py", builder)

    def test_compat_patch_is_ephemeral_and_unknown_changes_are_preserved(self):
        builder = (ROOT / "scripts/build-debug.sh").read_text(encoding="utf-8")
        self.assertIn('COMPAT_MARKER="TIHULU_ANDROID_ADS_TOOLTIP_COMPAT"', builder)
        self.assertIn("browser/sources.gni", builder)
        self.assertIn("Refusing to overwrite unknown local Brave change", builder)
        self.assertIn('trap cleanup_compat EXIT INT TERM', builder)
        self.assertIn('git -C "$BRAVE_CORE" restore -- "${COMPAT_FILES[@]}"', builder)


if __name__ == "__main__":
    unittest.main()
