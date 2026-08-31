#!/usr/bin/env python3
"""Patch Brave's touch-first onboarding so it is usable with only a TV remote.

The patch is intentionally narrow and fail-closed against the pinned Brave onboarding source.
It is applied ephemerally by build-debug.sh and restored after the build.
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

MARKER = "TIHULU_TV_ONBOARDING_CURSOR_COMPAT"


class CompatError(RuntimeError):
    pass


def _replace_once(text: str, old: str, new: str, description: str) -> str:
    count = text.count(old)
    if count != 1:
        raise CompatError(
            f"{description}: expected exactly one upstream anchor, found {count}. "
            "Brave onboarding changed; review before updating the TV compatibility patch."
        )
    return text.replace(old, new, 1)


def _atomic_write_text(path: Path, text: str) -> None:
    current = path.read_text(encoding="utf-8")
    if current == text:
        return
    tmp = path.with_name(path.name + ".tihulu-tmp")
    tmp.write_text(text, encoding="utf-8")
    os.replace(tmp, path)


def _is_applied(text: str) -> bool:
    begin = f"{MARKER}_BEGIN"
    end = f"{MARKER}_END"
    begin_count = text.count(begin)
    end_count = text.count(end)
    if begin_count == 0 and end_count == 0:
        return False
    if begin_count != 1 or end_count != 1 or text.index(end) < text.index(begin):
        raise CompatError(
            f"Malformed onboarding cursor compatibility markers: begin={begin_count}, end={end_count}"
        )
    required = [
        "installTihuluTvCursor();",
        "public boolean dispatchKeyEvent(KeyEvent event)",
        "TvMouseDispatcher.primaryClick",
        "TvMouseDispatcher.hover",
        "KEYCODE_DPAD_CENTER",
    ]
    missing = [token for token in required if token not in text]
    if missing:
        raise CompatError(
            "Onboarding cursor markers exist but the patch body drifted: " + ", ".join(missing)
        )
    return True


def transform(text: str) -> tuple[str, bool]:
    if _is_applied(text):
        return text, True

    if "public class WelcomeOnboardingActivity extends FirstRunActivityBase" not in text:
        raise CompatError("WelcomeOnboardingActivity class signature changed upstream")
    if "TvCursorState" in text or "TvMouseDispatcher" in text or "installTihuluTvCursor" in text:
        raise CompatError("WelcomeOnboardingActivity contains an unowned TV cursor modification")

    text = _replace_once(
        text,
        "import android.animation.AnimatorInflater;\nimport android.app.Activity;\n",
        "import android.animation.AnimatorInflater;\nimport android.app.Activity;\nimport android.app.UiModeManager;\n",
        "WelcomeOnboardingActivity UiModeManager import",
    )
    text = _replace_once(
        text,
        "import android.app.UiModeManager;\nimport android.graphics.drawable.Animatable2;\n",
        "import android.app.UiModeManager;\nimport android.content.Context;\nimport android.content.res.Configuration;\nimport android.graphics.drawable.Animatable2;\n",
        "WelcomeOnboardingActivity TV context imports",
    )
    text = _replace_once(
        text,
        "import android.text.SpannableString;\nimport android.view.View;\n",
        "import android.text.SpannableString;\nimport android.view.KeyEvent;\nimport android.view.View;\nimport android.view.ViewGroup;\nimport android.view.ViewGroupOverlay;\n",
        "WelcomeOnboardingActivity key/view imports",
    )
    text = _replace_once(
        text,
        "import org.chromium.chrome.browser.util.PackageUtils;\n",
        "import org.chromium.chrome.browser.util.PackageUtils;\n"
        "import org.chromium.chrome.browser.tv.TvCursorOverlay;\n"
        "import org.chromium.chrome.browser.tv.TvCursorState;\n"
        "import org.chromium.chrome.browser.tv.TvMouseDispatcher;\n",
        "WelcomeOnboardingActivity Tihulu cursor imports",
    )

    methods = f"""    // {MARKER}_BEGIN
    private static final float TIHULU_CURSOR_STEP_DP = 32.0f;
    private static final int TIHULU_CURSOR_SIZE_DP = 28;
    private static final int TIHULU_CURSOR_MARGIN_DP = 8;

    @Nullable private ViewGroup mTihuluTvRoot;
    @Nullable private TvCursorState mTihuluTvCursorState;
    @Nullable private TvCursorOverlay mTihuluTvCursorOverlay;

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {{
        if (handleTihuluTvCursorKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }}

    private void installTihuluTvCursor() {{
        UiModeManager manager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        if (manager == null
                || manager.getCurrentModeType() != Configuration.UI_MODE_TYPE_TELEVISION) {{
            return;
        }}

        final ViewGroup root = (ViewGroup) getWindow().getDecorView();
        final float density = getResources().getDisplayMetrics().density;
        final TvCursorState state =
                new TvCursorState(
                        root.getWidth(), root.getHeight(), TIHULU_CURSOR_MARGIN_DP * density);
        final TvCursorOverlay overlay = new TvCursorOverlay(this);
        final int size = Math.round(TIHULU_CURSOR_SIZE_DP * density);
        overlay.layout(0, 0, size, size);
        final ViewGroupOverlay viewOverlay = root.getOverlay();
        viewOverlay.add(overlay);

        mTihuluTvRoot = root;
        mTihuluTvCursorState = state;
        mTihuluTvCursorOverlay = overlay;

        root.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {{
                    final int width = right - left;
                    final int height = bottom - top;
                    final int oldWidth = oldRight - oldLeft;
                    final int oldHeight = oldBottom - oldTop;
                    state.resize(width, height);
                    if (oldWidth <= 0
                            || oldHeight <= 0
                            || Math.abs(width - oldWidth) > width / 3
                            || Math.abs(height - oldHeight) > height / 3) {{
                        state.center();
                    }}
                    updateTihuluTvCursor(state, overlay);
                }});
        root.post(
                () -> {{
                    state.resize(root.getWidth(), root.getHeight());
                    state.center();
                    updateTihuluTvCursor(state, overlay);
                    TvMouseDispatcher.hover(root, state.x(), state.y());
                }});
    }}

    private boolean handleTihuluTvCursorKey(KeyEvent event) {{
        final ViewGroup root = mTihuluTvRoot;
        final TvCursorState state = mTihuluTvCursorState;
        final TvCursorOverlay overlay = mTihuluTvCursorOverlay;
        if (root == null || state == null || overlay == null) return false;

        final int keyCode = event.getKeyCode();
        if (isTihuluDirectionKey(keyCode)) {{
            if (event.getAction() == KeyEvent.ACTION_DOWN) {{
                final float step =
                        TIHULU_CURSOR_STEP_DP * getResources().getDisplayMetrics().density;
                switch (keyCode) {{
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        state.move(-step, 0);
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        state.move(step, 0);
                        break;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        state.move(0, -step);
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        state.move(0, step);
                        break;
                    default:
                        return false;
                }}
                updateTihuluTvCursor(state, overlay);
                TvMouseDispatcher.hover(root, state.x(), state.y());
            }}
            return true;
        }}

        if (isTihuluSelectKey(keyCode)) {{
            if (event.getAction() == KeyEvent.ACTION_UP) {{
                TvMouseDispatcher.primaryClick(root, state.x(), state.y());
            }}
            return true;
        }}
        return false;
    }}

    private static void updateTihuluTvCursor(
            TvCursorState state, TvCursorOverlay overlay) {{
        final float halfWidth = overlay.getWidth() / 2.0f;
        final float halfHeight = overlay.getHeight() / 2.0f;
        overlay.setTranslationX(state.x() - halfWidth);
        overlay.setTranslationY(state.y() - halfHeight);
        overlay.invalidate();
    }}

    private static boolean isTihuluDirectionKey(int keyCode) {{
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }}

    private static boolean isTihuluSelectKey(int keyCode) {{
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT;
    }}
    // {MARKER}_END

"""

    fields_anchor = "    private boolean mIsP3aManaged;\n    private boolean mIsCrashReportingManaged;\n\n"
    text = _replace_once(
        text,
        fields_anchor,
        fields_anchor + methods,
        "WelcomeOnboardingActivity TV cursor methods",
    )
    text = _replace_once(
        text,
        "        setContentView(R.layout.activity_welcome_onboarding);\n\n",
        "        setContentView(R.layout.activity_welcome_onboarding);\n"
        "        installTihuluTvCursor();\n\n",
        "WelcomeOnboardingActivity TV cursor install hook",
    )

    if not _is_applied(text):
        raise CompatError("Internal error: onboarding TV cursor patch did not reach expected state")
    return text, True


def apply(project: Path) -> None:
    project = project.resolve()
    path = (
        project
        / "src/brave/android/java/org/chromium/chrome/browser/firstrun/WelcomeOnboardingActivity.java"
    )
    if not path.is_file():
        raise CompatError(f"Missing Brave onboarding source: {path}")
    original = path.read_text(encoding="utf-8")
    transformed, applied = transform(original)
    _atomic_write_text(path, transformed)
    if applied:
        print(
            "Brave TV onboarding compatibility: D-pad now drives a visible virtual cursor; OK/select clicks it."
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project", type=Path, help="Brave workspace root containing src/brave")
    args = parser.parse_args()
    try:
        apply(args.project)
    except CompatError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
