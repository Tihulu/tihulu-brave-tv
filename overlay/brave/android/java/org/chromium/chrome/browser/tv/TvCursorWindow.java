/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

/**
 * Non-interactive application window for the virtual TV cursor.
 *
 * <p>Chromium web content can be backed by a SurfaceView/SurfaceControl layer that is composed
 * above the activity's ordinary ViewOverlay. A transparent, non-focusable application window
 * keeps the cursor visible above that web surface without intercepting remote or touch input.
 * The window is created lazily only when Cursor mode is first requested.
 */
final class TvCursorWindow {
    private final Dialog mDialog;
    private final TvCursorOverlay mCursor;
    private final int mCursorSizePx;

    TvCursorWindow(Context context, int cursorSizePx) {
        mCursorSizePx = cursorSizePx;
        mDialog = new Dialog(context);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layer = new LinearLayout(context);
        layer.setBackgroundColor(Color.TRANSPARENT);
        mCursor = new TvCursorOverlay(context);
        layer.addView(
                mCursor,
                new LinearLayout.LayoutParams(cursorSizePx, cursorSizePx));
        mDialog.setContentView(layer);

        configureWindow(mDialog.getWindow(), false);
    }

    void show() {
        if (!mDialog.isShowing()) {
            mDialog.show();
            // Dialog themes may replace layout attributes during show(). Re-assert our passive,
            // transparent fullscreen surface after the window is attached.
            configureWindow(mDialog.getWindow(), true);
        }
        mCursor.setVisibility(android.view.View.VISIBLE);
        mCursor.invalidate();
    }

    void hide() {
        if (mDialog.isShowing()) mDialog.dismiss();
    }

    void dismiss() {
        hide();
    }

    void moveTo(float centerX, float centerY) {
        float half = mCursorSizePx / 2.0f;
        mCursor.setTranslationX(centerX - half);
        mCursor.setTranslationY(centerY - half);
        mCursor.invalidate();
    }

    private static void configureWindow(Window window, boolean applyLayout) {
        if (window == null) return;
        window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.0f);
        window.setGravity(Gravity.TOP | Gravity.START);
        window.getDecorView().setBackgroundColor(Color.TRANSPARENT);
        if (applyLayout) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }
}
