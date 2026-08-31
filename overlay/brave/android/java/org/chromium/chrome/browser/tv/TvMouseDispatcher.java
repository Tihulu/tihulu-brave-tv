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

/** Routes virtual-cursor events through Android's real mouse input path. */
public final class TvMouseDispatcher {
    private TvMouseDispatcher() {}

    /**
     * Sends a mouse hover event to Chromium's content view.
     *
     * <p>Setting only {@link InputDevice#SOURCE_MOUSE} is not sufficient. Chromium also checks the
     * pointer tool type before forwarding generic motion to Blink. Build the same shape of
     * MotionEvent used by Chromium's own mouse-pointer tests so HTML hover state is updated on
     * sites such as YouTube.
     */
    public static void hover(View target, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event =
                obtainMouseEvent(
                        now,
                        now,
                        MotionEvent.ACTION_HOVER_MOVE,
                        x,
                        y,
                        0,
                        0);
        try {
            target.dispatchGenericMotionEvent(event);
        } finally {
            event.recycle();
        }
    }

    /** Sends one primary-button mouse click at content-local coordinates. */
    public static void primaryClick(View target, float x, float y) {
        long downTime = SystemClock.uptimeMillis();

        // Make sure hover-driven controls (notably the YouTube HTML5 player overlay) are visible at
        // the click location before the button transition is delivered.
        hover(target, x, y);

        dispatchTouch(
                target,
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                x,
                y,
                MotionEvent.BUTTON_PRIMARY);
        dispatchButton(
                target,
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_BUTTON_PRESS,
                x,
                y,
                MotionEvent.BUTTON_PRIMARY,
                MotionEvent.BUTTON_PRIMARY);
        dispatchButton(
                target,
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_BUTTON_RELEASE,
                x,
                y,
                0,
                MotionEvent.BUTTON_PRIMARY);
        dispatchTouch(
                target,
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP,
                x,
                y,
                0);
    }

    private static void dispatchTouch(
            View target,
            long downTime,
            long eventTime,
            int action,
            float x,
            float y,
            int buttonState) {
        MotionEvent event =
                obtainMouseEvent(downTime, eventTime, action, x, y, buttonState, 0);
        try {
            target.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static void dispatchButton(
            View target,
            long downTime,
            long eventTime,
            int action,
            float x,
            float y,
            int buttonState,
            int actionButton) {
        MotionEvent event =
                obtainMouseEvent(
                        downTime, eventTime, action, x, y, buttonState, actionButton);
        try {
            target.dispatchGenericMotionEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static MotionEvent obtainMouseEvent(
            long downTime,
            long eventTime,
            int action,
            float x,
            float y,
            int buttonState,
            int actionButton) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;

        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
        coordinates.x = x;
        coordinates.y = y;
        coordinates.pressure = buttonState == 0 ? 0.0f : 1.0f;

        MotionEvent event =
                MotionEvent.obtain(
                        downTime,
                        eventTime,
                        action,
                        1,
                        new MotionEvent.PointerProperties[] {properties},
                        new MotionEvent.PointerCoords[] {coordinates},
                        0,
                        buttonState,
                        1.0f,
                        1.0f,
                        0,
                        0,
                        InputDevice.SOURCE_MOUSE,
                        0);
        if (actionButton != 0) event.setActionButton(actionButton);
        return event;
    }
}
