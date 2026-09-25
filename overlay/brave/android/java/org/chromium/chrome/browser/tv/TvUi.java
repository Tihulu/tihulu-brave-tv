/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Lightweight native TV surfaces: no bitmap backdrops, blur or focus animations. */
final class TvUi {
    static final int BACKGROUND = Color.rgb(16, 22, 34);
    static final int SURFACE = Color.rgb(30, 41, 59);
    static final int ACCENT = Color.rgb(125, 231, 207);
    static final int TEXT = Color.rgb(241, 245, 249);
    static final int MUTED = Color.rgb(157, 174, 195);

    private TvUi() {}

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable background(Context context, boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(focused ? ACCENT : SURFACE);
        shape.setCornerRadius(dp(context, 12));
        shape.setStroke(dp(context, 2), focused ? Color.WHITE : SURFACE);
        return shape;
    }

    static void styleButton(Context context, Button button) {
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setMaxLines(2);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setTextColor(TEXT);
        button.setBackground(background(context, false));
        button.setFocusable(true);
        button.setClickable(true);
        button.setMinHeight(dp(context, 56));
        button.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        // Focus never changes the label or dimensions. This also preserves dynamic mode labels.
        button.setOnFocusChangeListener((view, focused) -> {
            button.setBackground(background(context, focused));
            button.setTextColor(focused ? BACKGROUND : TEXT);
        });
    }

    static Button button(Context context, String label, Runnable action) {
        Button button = new Button(context);
        button.setText(label);
        styleButton(context, button);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    static TextView text(Context context, String value, int size, int color) {
        TextView label = new TextView(context);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setFocusable(false);
        return label;
    }

    static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 24);
        column.setPadding(padding, padding, padding, padding);
        return column;
    }

    static void addPair(Context context, LinearLayout column, Button left, Button right) {
        LinearLayout row = new LinearLayout(context);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1);
        first.setMargins(0, dp(context, 4), dp(context, 6), dp(context, 4));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1);
        second.setMargins(dp(context, 6), dp(context, 4), 0, dp(context, 4));
        row.addView(left, first);
        row.addView(right, second);
        column.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    static LinearLayout.LayoutParams row(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(context, 4), 0, dp(context, 4));
        return params;
    }

    static void setPanelContent(Context context, Dialog dialog, LinearLayout column) {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        column.setBackgroundColor(BACKGROUND);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(column);
        dialog.setContentView(scroll);
    }

    static void sizePanel(Context context, Dialog dialog, int preferredWidth) {
        if (dialog.getWindow() == null) return;
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        dialog.getWindow().setLayout(
                Math.min(dp(context, preferredWidth), Math.max(1, metrics.widthPixels - dp(context, 48))),
                Math.max(1, metrics.heightPixels - dp(context, 48)));
        dialog.getWindow().setDimAmount(0.35f);
    }
}
