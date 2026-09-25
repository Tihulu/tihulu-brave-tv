package com.tihulu.tvlite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

final class CursorOverlay extends View {
    private final Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float x;
    private float y;

    CursorOverlay(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        outer.setStyle(Paint.Style.STROKE);
        outer.setStrokeWidth(TvUi.dp(context, 3));
        outer.setColor(Color.BLACK);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(TvUi.dp(context, 2));
        inner.setColor(Color.WHITE);
    }

    void center() {
        x = getWidth() / 2f;
        y = getHeight() / 2f;
        invalidate();
    }

    void move(float dx, float dy) {
        float margin = TvUi.dp(getContext(), 12);
        x = Math.max(margin, Math.min(getWidth() - margin, x + dx));
        y = Math.max(margin, Math.min(getHeight() - margin, y + dy));
        invalidate();
    }

    float cursorX() {
        return x;
    }

    float cursorY() {
        return y;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (x == 0f && y == 0f) {
            x = w / 2f;
            y = h / 2f;
        } else {
            x = Math.min(x, Math.max(0, w));
            y = Math.min(y, Math.max(0, h));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float r = TvUi.dp(getContext(), 10);
        canvas.drawCircle(x, y, r + TvUi.dp(getContext(), 2), outer);
        canvas.drawCircle(x, y, r, inner);
        canvas.drawLine(x - r * 1.5f, y, x + r * 1.5f, y, inner);
        canvas.drawLine(x, y - r * 1.5f, x, y + r * 1.5f, inner);
    }
}
