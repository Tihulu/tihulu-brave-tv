/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Non-interactive view drawn inside a ViewOverlay to visualize the synthetic TV pointer. */
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
