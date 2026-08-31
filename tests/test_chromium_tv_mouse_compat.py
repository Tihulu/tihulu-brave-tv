import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/apply_chromium_tv_mouse_compat.py"

spec = importlib.util.spec_from_file_location("chromium_tv_mouse_compat", SCRIPT)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ChromiumTvMouseCompatTests(unittest.TestCase):
    def fixture(self):
        return """package org.chromium.ui.base;

import android.view.MotionEvent;

public class EventForwarder {
    private long mNativeEventForwarder;

    public static int getMouseEventActionButton(MotionEvent event) {
        return event.getActionButton();
    }

    public boolean isTrackpadToMouseEventConversionEnabled() {
        return false;
    }
}
"""

    def test_transform_adds_narrow_primary_button_jni_bridge(self):
        patched = module.transform(self.fixture())
        self.assertIn(module.BEGIN, patched)
        self.assertIn(module.END, patched)
        self.assertIn("sendTihuluSyntheticMouseButtonEvent", patched)
        self.assertIn("changedButton != MotionEvent.BUTTON_PRIMARY", patched)
        self.assertIn("event.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE", patched)
        self.assertIn("MotionEvent.ACTION_BUTTON_PRESS", patched)
        self.assertIn("MotionEvent.ACTION_BUTTON_RELEASE", patched)
        self.assertIn("createOffsetMotionEventIfNeeded(event)", patched)
        self.assertIn("updateMouseEventState(event)", patched)
        self.assertIn("EventForwarderJni.get()", patched)
        self.assertIn("MotionEventUtils.getEventTimeNanos(event)", patched)
        self.assertNotIn("setActionButton", patched)

    def test_transform_is_idempotent(self):
        once = module.transform(self.fixture())
        self.assertEqual(once, module.transform(once))

    def test_upstream_drift_fails_closed(self):
        drifted = self.fixture().replace(
            "return event.getActionButton();", "return 0;"
        )
        with self.assertRaises(module.PatchError):
            module.transform(drifted)

    def test_partial_marker_fails_closed(self):
        with self.assertRaises(module.PatchError):
            module.transform(self.fixture() + "\n" + module.BEGIN)

    def test_dispatcher_uses_public_android_api_and_event_forwarder(self):
        mouse = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvMouseDispatcher.java"
        ).read_text(encoding="utf-8")
        stub = (ROOT / "tests/stubs/android/view/MotionEvent.java").read_text(encoding="utf-8")
        self.assertIn("target instanceof ContentView", mouse)
        self.assertIn("contentView.getWebContents()", mouse)
        self.assertIn("webContents.getEventForwarder()", mouse)
        self.assertIn("sendTihuluSyntheticMouseButtonEvent", mouse)
        self.assertNotIn(
            "if (actionButton != 0) event.setActionButton(actionButton);", mouse
        )
        self.assertNotIn("setActionButton", stub)

    def test_build_applies_and_restores_event_forwarder_patch(self):
        build = (ROOT / "scripts/build-debug.sh").read_text(encoding="utf-8")
        self.assertIn("apply_chromium_tv_mouse_compat.py", build)
        self.assertIn("ui/android/java/src/org/chromium/ui/base/EventForwarder.java", build)
        self.assertIn("TIHULU_TV_MOUSE_EVENT_COMPAT", build)
        self.assertIn('git -C "$CHROMIUM_ROOT" restore -- "${CHROMIUM_COMPAT_FILES[@]}"', build)


if __name__ == "__main__":
    unittest.main()
