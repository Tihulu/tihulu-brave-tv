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

import org.chromium.components.embedder_support.view.ContentView;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.EventForwarder;

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
                obtainMouseEvent(now, now, MotionEvent.ACTION_HOVER_MOVE, x, y, 0);
        try {
            target.dispatchGenericMotionEvent(event);
        } finally {
            event.recycle();
        }
    }

    /**
     * Sends one primary-button click at content-local coordinates.
     *
     * <p>Android exposes {@link MotionEvent#getActionButton()} but its corresponding setter is a
     * hidden/TestApi on Android 11. Do not call hidden event.setActionButton(actionButton); from
     * Brave Java. Web content uses a narrow Chromium EventForwarder compatibility method that
     * supplies the changed button explicitly to Chromium's existing JNI mouse path. Native
     * onboarding views keep the ordinary Android down/up fallback.
     */
    public static void primaryClick(View target, float x, float y) {
        // Make sure hover-driven controls (notably the YouTube HTML5 player overlay) are visible at
        // the click location before the button transition is delivered.
        hover(target, x, y);

        if (target instanceof ContentView) {
            dispatchContentPrimaryClick((ContentView) target, x, y);
            return;
        }

        // Brave onboarding is an Android-native surface rather than Blink content. ACTION_DOWN /
        // ACTION_UP is the supported public Android API path for those Views and does not need an
        // ACTION_BUTTON_PRESS actionButton field.
        long downTime = SystemClock.uptimeMillis();
        dispatchTouch(
                target,
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                x,
                y,
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

    private static void dispatchContentPrimaryClick(ContentView contentView, float x, float y) {
        WebContents webContents = contentView.getWebContents();
        if (webContents == null) return;
        EventForwarder eventForwarder = webContents.getEventForwarder();
        if (eventForwarder == null) return;

        long downTime = SystemClock.uptimeMillis();
        MotionEvent press =
                obtainMouseEvent(
                        downTime,
                        downTime,
                        MotionEvent.ACTION_BUTTON_PRESS,
                        x,
                        y,
                        MotionEvent.BUTTON_PRIMARY);
        try {
            eventForwarder.sendTihuluSyntheticMouseButtonEvent(
                    press, MotionEvent.BUTTON_PRIMARY);
        } finally {
            press.recycle();
        }

        MotionEvent release =
                obtainMouseEvent(
                        downTime,
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_BUTTON_RELEASE,
                        x,
                        y,
                        0);
        try {
            eventForwarder.sendTihuluSyntheticMouseButtonEvent(
                    release, MotionEvent.BUTTON_PRIMARY);
        } finally {
            release.recycle();
        }
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
                obtainMouseEvent(downTime, eventTime, action, x, y, buttonState);
        try {
            target.dispatchTouchEvent(event);
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
            int buttonState) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;

        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
        coordinates.x = x;
        coordinates.y = y;
        coordinates.pressure = buttonState == 0 ? 0.0f : 1.0f;

        return MotionEvent.obtain(
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
    }
}
