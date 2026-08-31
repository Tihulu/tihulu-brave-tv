/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;

/** Reusable virtual mouse controlled only by a TV remote D-pad and select/OK key. */
public final class TvRemoteCursorController {
    private static final float CURSOR_STEP_DP = 32.0f;
    private static final int CURSOR_SIZE_DP = 28;
    private static final int CURSOR_MARGIN_DP = 8;

    private final Context mContext;
    private final ViewGroup mRoot;
    private final TvCursorState mState;
    private final TvCursorOverlay mOverlay;

    public static boolean isTelevision(Context context) {
        if (context == null) return false;
        UiModeManager manager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return manager != null
                && manager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    /** Attach a visible cursor immediately. Intended for touch-first Brave screens such as FRE. */
    public TvRemoteCursorController(Context context, ViewGroup root) {
        if (context == null || root == null) {
            throw new IllegalArgumentException("context and root are required");
        }
        mContext = context;
        mRoot = root;
        float density = context.getResources().getDisplayMetrics().density;
        mState = new TvCursorState(root.getWidth(), root.getHeight(), CURSOR_MARGIN_DP * density);
        mOverlay = new TvCursorOverlay(context);
        int size = Math.round(CURSOR_SIZE_DP * density);
        mOverlay.layout(0, 0, size, size);
        ViewGroupOverlay overlay = root.getOverlay();
        overlay.add(mOverlay);
        root.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    int width = right - left;
                    int height = bottom - top;
                    int oldWidth = oldRight - oldLeft;
                    int oldHeight = oldBottom - oldTop;
                    mState.resize(width, height);
                    if (oldWidth <= 0
                            || oldHeight <= 0
                            || Math.abs(width - oldWidth) > width / 3
                            || Math.abs(height - oldHeight) > height / 3) {
                        mState.center();
                    }
                    updateOverlay();
                });
        root.post(
                () -> {
                    mState.resize(root.getWidth(), root.getHeight());
                    mState.center();
                    updateOverlay();
                    TvMouseDispatcher.hover(root, mState.x(), mState.y());
                });
    }

    /** Returns true only for remote keys consumed by the virtual cursor. */
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event == null) return false;
        int keyCode = event.getKeyCode();
        if (isDirectionKey(keyCode)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) moveForKey(keyCode);
            return true;
        }
        if (isSelectKey(keyCode)) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                TvMouseDispatcher.primaryClick(mRoot, mState.x(), mState.y());
            }
            return true;
        }
        return false;
    }

    public void center() {
        mState.center();
        updateOverlay();
        TvMouseDispatcher.hover(mRoot, mState.x(), mState.y());
    }

    private void moveForKey(int keyCode) {
        float step = CURSOR_STEP_DP * mContext.getResources().getDisplayMetrics().density;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                mState.move(-step, 0);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mState.move(step, 0);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                mState.move(0, -step);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                mState.move(0, step);
                break;
            default:
                return;
        }
        updateOverlay();
        TvMouseDispatcher.hover(mRoot, mState.x(), mState.y());
    }

    private void updateOverlay() {
        float halfWidth = mOverlay.getWidth() / 2.0f;
        float halfHeight = mOverlay.getHeight() / 2.0f;
        mOverlay.setTranslationX(mState.x() - halfWidth);
        mOverlay.setTranslationY(mState.y() - halfHeight);
        mOverlay.invalidate();
    }

    private static boolean isDirectionKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private static boolean isSelectKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT;
    }
}
