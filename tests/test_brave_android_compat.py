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

    def test_arm32_build_uses_low_memory_feature_cut(self):
        builder = (ROOT / "scripts/build-debug.sh").read_text(encoding="utf-8")
        self.assertIn('if [[ "$ARCH" == "arm" ]]', builder)
        self.assertIn("--gn=enable_brave_rewards:false", builder)
        self.assertIn("--gn=enable_brave_ads:false", builder)
        self.assertIn("Shields remains enabled", builder)
        self.assertIn("apply_brave_android_compat.py", builder)

    def test_compat_patch_is_ephemeral_and_unknown_changes_are_preserved(self):
        builder = (ROOT / "scripts/build-debug.sh").read_text(encoding="utf-8")
        self.assertIn('COMPAT_MARKER="TIHULU_ANDROID_ADS_TOOLTIP_COMPAT"', builder)
        self.assertIn("Refusing to overwrite unknown local Brave change", builder)
        self.assertIn('trap cleanup_compat EXIT INT TERM', builder)
        self.assertIn('git -C "$BRAVE_CORE" restore -- "${COMPAT_FILES[@]}"', builder)


if __name__ == "__main__":
    unittest.main()
