package com.tihulu.tvlite;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class TvUi {
    static final int BG = Color.rgb(18, 18, 20);
    static final int PANEL = Color.rgb(28, 28, 32);
    static final int NORMAL = Color.rgb(44, 44, 50);
    static final int FOCUSED = Color.rgb(218, 32, 40);
    static final int TEXT = Color.rgb(236, 236, 240);
    static final int MUTED = Color.rgb(190, 190, 198);

    private TvUi() {}

    static Button button(Context context, String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(17);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setClickable(true);
        button.setMinHeight(dp(context, 58));
        button.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        button.setBackground(rounded(NORMAL, dp(context, 14), Color.TRANSPARENT, 0));
        button.setOnFocusChangeListener((v, focused) -> {
            button.setBackground(rounded(
                    focused ? FOCUSED : NORMAL,
                    dp(context, 14),
                    focused ? Color.WHITE : Color.TRANSPARENT,
                    focused ? dp(context, 2) : 0));
            button.setTextColor(Color.WHITE);
            button.setText(focused ? "▶  " + label + "  ◀" : label);
        });
        return button;
    }

    static TextView title(Context context, String text, float size) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        return view;
    }

    static GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    static LinearLayout.LayoutParams fullRow(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 62));
        params.setMargins(0, dp(context, 5), 0, dp(context, 5));
        return params;
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
