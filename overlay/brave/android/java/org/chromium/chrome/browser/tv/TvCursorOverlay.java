/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

/** Non-interactive view used to visualize the synthetic TV pointer. */
public final class TvCursorOverlay extends View {
    private final Paint mOuter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mInner = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TvCursorOverlay(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        mOuter.setStyle(Paint.Style.FILL);
        mOuter.setColor(Color.BLACK);
        mInner.setStyle(Paint.Style.FILL);
        mInner.setColor(Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2.0f;
        float cy = getHeight() / 2.0f;
        float radius = Math.min(cx, cy);
        canvas.drawCircle(cx, cy, radius, mOuter);
        canvas.drawCircle(cx, cy, radius * 0.66f, mInner);
    }
}

/**
 * Passive application window that keeps the virtual cursor above Chromium web surfaces.
 *
 * <p>Chromium web content can be backed by a SurfaceView/SurfaceControl layer composed above the
 * activity ViewOverlay. Keeping this package-private helper in the already-GN-owned cursor source
 * avoids adding another Java source dependency while still giving the cursor its own application
 * window. The window is lazy, transparent, non-focusable and non-touchable.
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
        mCursor.setVisibility(View.VISIBLE);
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
