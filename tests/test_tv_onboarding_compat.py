import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "apply_brave_tv_onboarding_compat",
    ROOT / "scripts/apply_brave_tv_onboarding_compat.py",
)
assert SPEC and SPEC.loader
compat = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(compat)


UPSTREAM = """package org.chromium.chrome.browser.firstrun;

import android.animation.AnimatorInflater;
import android.app.Activity;
import android.graphics.drawable.Animatable2;
import android.text.SpannableString;
import android.view.View;

import org.chromium.chrome.browser.util.PackageUtils;

public class WelcomeOnboardingActivity extends FirstRunActivityBase
        implements OnboardingStepAdapter.OnboardingNavigationListener {
    private boolean mIsP3aManaged;
    private boolean mIsCrashReportingManaged;

    @Override
    public void triggerLayoutInflation() {
        super.triggerLayoutInflation();
        setContentView(R.layout.activity_welcome_onboarding);

        Object rest = null;
    }
}
"""


class TvOnboardingCompatTests(unittest.TestCase):
    def test_tv_onboarding_gets_visible_dpad_cursor_without_hover_storms(self):
        fixed, applied = compat.transform(UPSTREAM)
        self.assertTrue(applied)
        self.assertIn("TIHULU_TV_ONBOARDING_CURSOR_COMPAT_BEGIN", fixed)
        self.assertIn("public boolean dispatchKeyEvent(KeyEvent event)", fixed)
        self.assertIn("KEYCODE_DPAD_LEFT", fixed)
        self.assertIn("KEYCODE_DPAD_CENTER", fixed)
        self.assertNotIn("TvMouseDispatcher.hover", fixed)
        self.assertIn("TvMouseDispatcher.primaryClick", fixed)
        self.assertIn("installTihuluTvCursor();", fixed)
        self.assertIn("UI_MODE_TYPE_TELEVISION", fixed)

    def test_transform_is_idempotent(self):
        fixed, _ = compat.transform(UPSTREAM)
        again, applied = compat.transform(fixed)
        self.assertTrue(applied)
        self.assertEqual(fixed, again)

    def test_partial_marker_fails_closed(self):
        broken = UPSTREAM.replace(
            "private boolean mIsP3aManaged;",
            "// TIHULU_TV_ONBOARDING_CURSOR_COMPAT_BEGIN\n    private boolean mIsP3aManaged;",
        )
        with self.assertRaises(compat.CompatError):
            compat.transform(broken)

    def test_upstream_drift_fails_closed(self):
        drifted = UPSTREAM.replace(
            "setContentView(R.layout.activity_welcome_onboarding);",
            "setContentView(R.layout.changed);",
        )
        with self.assertRaises(compat.CompatError):
            compat.transform(drifted)

    def test_build_restores_onboarding_patch_after_build(self):
        builder = (ROOT / "scripts/build-debug.sh").read_text(encoding="utf-8")
        self.assertIn(
            "android/java/org/chromium/chrome/browser/firstrun/WelcomeOnboardingActivity.java",
            builder,
        )
        self.assertIn("TIHULU_TV_ONBOARDING_CURSOR_COMPAT", builder)
        self.assertIn("apply_brave_tv_onboarding_compat.py", builder)
        self.assertIn('trap cleanup_compat EXIT INT TERM', builder)


if __name__ == "__main__":
    unittest.main()
