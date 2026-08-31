#!/usr/bin/env python3
"""Expose a narrow Chromium mouse-button bridge for the TV virtual cursor.

Android 11 exposes MotionEvent.getActionButton() but not its setter in the public SDK. Chromium's
mouse pipeline requires the changed-button value for ACTION_BUTTON_PRESS/RELEASE. Patch the pinned
EventForwarder ephemerally so Tihulu can pass that value directly to Chromium's existing JNI path
without reflection, hidden APIs, JavaScript clicks, or global input-policy changes.
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

BEGIN = "    // TIHULU_TV_MOUSE_EVENT_COMPAT_BEGIN"
END = "    // TIHULU_TV_MOUSE_EVENT_COMPAT_END"
ANCHOR = """    public static int getMouseEventActionButton(MotionEvent event) {
        return event.getActionButton();
    }
"""

METHOD = f"""
{BEGIN}
    /**
     * Sends a synthetic mouse button transition using Chromium's normal native mouse path.
     *
     * <p>Android's public SDK cannot populate MotionEvent.actionButton, so callers provide the
     * changed button explicitly. This method deliberately accepts only a primary-button
     * ACTION_BUTTON_PRESS/RELEASE generated with TOOL_TYPE_MOUSE.
     */
    public boolean sendTihuluSyntheticMouseButtonEvent(MotionEvent event, int changedButton) {{
        if (mNativeEventForwarder == 0
                || changedButton != MotionEvent.BUTTON_PRIMARY
                || event.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE) {{
            return false;
        }}
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_BUTTON_PRESS
                && action != MotionEvent.ACTION_BUTTON_RELEASE) {{
            return false;
        }}

        boolean didOffsetEvent = false;
        try {{
            if (hasTouchEventOffset()) {{
                event = createOffsetMotionEventIfNeeded(event);
                didOffsetEvent = true;
            }}
            updateMouseEventState(event);
            mPointerLockEventHelper.onNonCapturedPointerEvent(event.getX(), event.getY());
            EventForwarderJni.get()
                    .onMouseEvent(
                            mNativeEventForwarder,
                            event,
                            MotionEventUtils.getEventTimeNanos(event),
                            action,
                            changedButton,
                            MotionEvent.TOOL_TYPE_MOUSE);
            return true;
        }} finally {{
            if (didOffsetEvent) event.recycle();
        }}
    }}
{END}
"""


class PatchError(RuntimeError):
    pass


def _atomic_write_text(path: Path, text: str) -> None:
    current = path.read_text(encoding="utf-8")
    if current == text:
        return
    tmp = path.with_name(path.name + ".tihulu-tmp")
    tmp.write_text(text, encoding="utf-8")
    os.replace(tmp, path)


def _is_applied(text: str) -> bool:
    begin_count = text.count(BEGIN)
    end_count = text.count(END)
    if begin_count == 0 and end_count == 0:
        return False
    if begin_count != 1 or end_count != 1 or text.index(END) < text.index(BEGIN):
        raise PatchError(
            f"Malformed Chromium TV mouse compatibility markers: begin={begin_count}, end={end_count}"
        )
    required = [
        "sendTihuluSyntheticMouseButtonEvent",
        "changedButton != MotionEvent.BUTTON_PRIMARY",
        "MotionEvent.ACTION_BUTTON_PRESS",
        "MotionEvent.ACTION_BUTTON_RELEASE",
        "EventForwarderJni.get()",
        "MotionEventUtils.getEventTimeNanos(event)",
    ]
    missing = [token for token in required if token not in text]
    if missing:
        raise PatchError("Chromium TV mouse compatibility body drifted: " + ", ".join(missing))
    return True


def transform(text: str) -> str:
    if _is_applied(text):
        return text
    if "public class EventForwarder" not in text:
        raise PatchError("Chromium EventForwarder class signature changed upstream")
    if "sendTihuluSyntheticMouseButtonEvent" in text:
        raise PatchError("Chromium EventForwarder contains an unowned Tihulu mouse modification")
    count = text.count(ANCHOR)
    if count != 1:
        raise PatchError(
            "Chromium EventForwarder action-button anchor changed; "
            f"expected exactly one anchor, found {count}"
        )
    patched = text.replace(ANCHOR, ANCHOR + METHOD, 1)
    if not _is_applied(patched):
        raise PatchError("Internal error: Chromium TV mouse patch did not reach expected state")
    return patched


def apply(project: Path) -> None:
    project = project.resolve()
    path = project / "src/ui/android/java/src/org/chromium/ui/base/EventForwarder.java"
    if not path.is_file():
        raise PatchError(f"Missing Chromium EventForwarder source: {path}")
    original = path.read_text(encoding="utf-8")
    patched = transform(original)
    _atomic_write_text(path, patched)
    print(
        "Chromium TV mouse compatibility: primary button transitions use the native EventForwarder JNI path; no hidden MotionEvent API."
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project", type=Path, help="Brave workspace root containing src/")
    args = parser.parse_args()
    try:
        apply(args.project)
    except PatchError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
