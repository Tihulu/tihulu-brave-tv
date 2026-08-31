/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

/** Pure state model for the virtual TV cursor. Kept Android-free so it can be unit tested. */
public final class TvCursorState {
    private float mX;
    private float mY;
    private int mWidth;
    private int mHeight;
    private final float mMargin;

    public TvCursorState(int width, int height, float margin) {
        if (margin < 0) throw new IllegalArgumentException("margin must be >= 0");
        mMargin = margin;
        resize(width, height);
        center();
    }

    public void resize(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("width and height must be >= 0");
        }
        mWidth = width;
        mHeight = height;
        mX = clampX(mX);
        mY = clampY(mY);
    }

    public void center() {
        mX = mWidth / 2.0f;
        mY = mHeight / 2.0f;
        mX = clampX(mX);
        mY = clampY(mY);
    }

    public void move(float dx, float dy) {
        mX = clampX(mX + dx);
        mY = clampY(mY + dy);
    }

    public float x() {
        return mX;
    }

    public float y() {
        return mY;
    }

    private float clampX(float value) {
        return clamp(value, effectiveMargin(mWidth), Math.max(effectiveMargin(mWidth), mWidth - effectiveMargin(mWidth)));
    }

    private float clampY(float value) {
        return clamp(value, effectiveMargin(mHeight), Math.max(effectiveMargin(mHeight), mHeight - effectiveMargin(mHeight)));
    }

    private float effectiveMargin(int size) {
        return Math.min(mMargin, size / 2.0f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
