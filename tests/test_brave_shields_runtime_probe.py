import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/apply_brave_shields_runtime_probe.py"

spec = importlib.util.spec_from_file_location("brave_shields_runtime_probe", SCRIPT)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class BraveShieldsRuntimeProbeTests(unittest.TestCase):
    def service_fixture(self):
        return """#include \"base/logging.h\"
AdBlockService::AdBlockService() {
  TRACE_EVENT(\"brave.adblock\", \"AdBlockService\");
}

void AdBlockService::OnDATLoaded(bool is_default_engine, bool success) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  if (is_default_engine) {
    default_dat_loaded_for_testing_ = true;
  }
}

void AdBlockService::OnEngineLoaded(
    bool is_default_engine,
    std::pair<FilterListLoadResult, std::optional<DATFileDataBuffer>> result) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  auto [load_result, serialized_dat] = std::move(result);

  if (load_result != FilterListLoadResult::kResourcesOnly) {
    observers_.Notify(&Observer::OnFilterListLoaded, is_default_engine, load_result);
  }
}
"""

    def network_fixture(self):
        return """#include \"base/functional/bind.h\"

template <template <typename> class T>
void OnShouldBlockRequestResult(bool then_check_uncloaked,
                                const ResponseCallback& next_callback,
                                T<BraveRequestInfo> ctx,
                                ShouldBlockRequestResult result) {
  ctx->set_blocked_by(result.blocked_by);
  if (ctx->blocked_by() == kAdBlocked) {
    brave_shields::BraveShieldsWebContentsObserver::DispatchBlockedEvent(
        ctx->request_url(), ctx->render_frame_token(), brave_shields::kAds);
  }
}

template <template <typename> class T>
int OnBeforeURLRequest_AdBlockTPPreWork(const ResponseCallback& next_callback,
                                        T<BraveRequestInfo> ctx) {
  if (ctx->request_url().is_empty() || ctx->initiator_url().is_empty() ||
      !ctx->initiator_url().has_host() || !ctx->allow_brave_shields() ||
      ctx->allow_ads() ||
      ctx->resource_type() == BraveRequestInfo::kInvalidResourceType) {
    return net::OK;
  }
  OnBeforeURLRequestAdBlockTP(next_callback, ctx);
  return net::ERR_IO_PENDING;
}
"""

    def test_service_probe_reports_creation_and_default_engine_load(self):
        patched, applied = module.transform_ad_block_service(self.service_fixture())
        self.assertTrue(applied)
        self.assertIn('"TIHULU_SHIELDS service-created"', patched)
        self.assertIn('"TIHULU_SHIELDS default-dat success="', patched)
        self.assertIn('"TIHULU_SHIELDS default-filter result="', patched)
        self.assertIn("static_cast<int>(load_result)", patched)

    def test_network_probe_is_bounded_and_reports_early_return_inputs(self):
        patched, applied = module.transform_network_helper(self.network_fixture())
        self.assertTrue(applied)
        self.assertIn('#include "base/logging.h"', patched)
        self.assertIn("tihulu_probe_count < 8", patched)
        self.assertIn('" request_empty="', patched)
        self.assertIn('" initiator_empty="', patched)
        self.assertIn('" initiator_has_host="', patched)
        self.assertIn('" allow_shields="', patched)
        self.assertIn('" allow_ads="', patched)
        self.assertIn('" resource_type="', patched)
        self.assertIn('"TIHULU_SHIELDS first-blocked-request"', patched)
        self.assertNotIn("ctx->request_url().spec()", patched)
        self.assertNotIn("ctx->initiator_url().spec()", patched)

    def test_transforms_are_idempotent(self):
        service_once, _ = module.transform_ad_block_service(self.service_fixture())
        service_twice, _ = module.transform_ad_block_service(service_once)
        self.assertEqual(service_once, service_twice)
        network_once, _ = module.transform_network_helper(self.network_fixture())
        network_twice, _ = module.transform_network_helper(network_once)
        self.assertEqual(network_once, network_twice)

    def test_upstream_drift_fails_closed(self):
        drifted_service = self.service_fixture().replace(
            'TRACE_EVENT("brave.adblock", "AdBlockService");',
            'TRACE_EVENT("brave.adblock", "AdBlockServiceChanged");',
        )
        with self.assertRaises(module.ProbeError):
            module.transform_ad_block_service(drifted_service)

        drifted_network = self.network_fixture().replace(
            "T<BraveRequestInfo> ctx) {",
            "T<BraveRequestInfo> request) {",
            1,
        )
        with self.assertRaises(module.ProbeError):
            module.transform_network_helper(drifted_network)

    def test_debug_build_applies_and_restores_probe_files(self):
        build = (ROOT / "scripts/build-debug.sh").read_text(encoding="utf-8")
        self.assertIn("apply_brave_shields_runtime_probe.py", build)
        self.assertIn("components/brave_shields/content/browser/ad_block_service.cc", build)
        self.assertIn("browser/net/brave_ad_block_tp_network_delegate_helper.cc", build)
        self.assertIn("TIHULU_SHIELDS_RUNTIME_PROBE", build)
        self.assertIn('git -C "$BRAVE_CORE" restore -- "${COMPAT_FILES[@]}"', build)


if __name__ == "__main__":
    unittest.main()
