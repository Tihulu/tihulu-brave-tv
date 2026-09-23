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
public final class TvMouseDispatcher {
    private TvMouseDispatcher() {}

    public static void hover(View root, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, x, y, 0);
        event.setSource(InputDevice.SOURCE_MOUSE);
        try {
            root.dispatchGenericMotionEvent(event);
        } finally {
            event.recycle();
        }
    }

    public static void primaryClick(View root, float x, float y) {
        long downTime = SystemClock.uptimeMillis();
        dispatchTouch(root, downTime, downTime, MotionEvent.ACTION_DOWN, x, y);
        dispatchTouch(root, downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y);
    }

    /** Native wheel event, targeted at the pointer, so nested scroll containers also work. */
    public static void scroll(View root, float x, float y, float horizontal, float vertical) {
        MotionEvent.PointerProperties pointer = new MotionEvent.PointerProperties();
        pointer.id = 0;
        pointer.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal);
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, vertical);
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1,
                new MotionEvent.PointerProperties[] {pointer},
                new MotionEvent.PointerCoords[] {coords}, 0, 0, 1, 1, 0, 0,
                InputDevice.SOURCE_MOUSE, 0);
        try {
            root.dispatchGenericMotionEvent(event);
        } finally {
            event.recycle();
        }
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
