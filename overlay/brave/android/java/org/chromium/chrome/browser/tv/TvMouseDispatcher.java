/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

/** Routes virtual-cursor events through Android's mouse input path. */
final class TvMouseDispatcher {
    private TvMouseDispatcher() {}

    static void hover(View root, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, x, y, 0);
        event.setSource(InputDevice.SOURCE_MOUSE);
        try {
            root.dispatchGenericMotionEvent(event);
        } finally {
            event.recycle();
        }
    }

    static void primaryClick(View root, float x, float y) {
        long downTime = SystemClock.uptimeMillis();
        dispatchTouch(root, downTime, downTime, MotionEvent.ACTION_DOWN, x, y);
        dispatchTouch(root, downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y);
    }

    private static void dispatchTouch(
            View root, long downTime, long eventTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_MOUSE);
        try {
            root.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }
}
