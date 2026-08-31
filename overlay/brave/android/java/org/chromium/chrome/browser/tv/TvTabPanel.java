/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** TV-sized tab controls using Chromium's keyboard-shortcut path. */
final class TvTabPanel {
    private static final int NORMAL_BG = Color.rgb(48, 48, 52);
    private static final int FOCUSED_BG = Color.rgb(218, 32, 40);
    private static final int NORMAL_TEXT = Color.rgb(236, 236, 240);

    interface Callback {
        void previousTab();
        void nextTab();
        void newTab();
        void closeCurrentTab();
    }

    private TvTabPanel() {}

    static void show(Context context, Callback callback) {
        Dialog dialog = new Dialog(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 24);
        column.setPadding(pad, pad, pad, pad);
        column.setBackgroundColor(Color.rgb(24, 24, 24));

        TextView title = new TextView(context);
        title.setText("Tabs");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        column.addView(title, matchWrap(context));

        Button previous = button(context, "Previous tab", callback::previousTab, dialog);
        Button next = button(context, "Next tab", callback::nextTab, dialog);
        Button create = button(context, "New tab", callback::newTab, dialog);
        Button closeTab =
                button(context, "Close current tab", callback::closeCurrentTab, dialog);
        Button closePanel = tvButton(context, "Close");
        closePanel.setOnClickListener(v -> dialog.dismiss());

        column.addView(previous, matchWrap(context));
        column.addView(next, matchWrap(context));
        column.addView(create, matchWrap(context));
        column.addView(closeTab, matchWrap(context));
        column.addView(closePanel, matchWrap(context));

        dialog.setContentView(column);
        dialog.setOnShowListener(
                ignored -> {
                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setLayout(
                                dp(context, 640), ViewGroup.LayoutParams.WRAP_CONTENT);
                    }
                    next.requestFocus();
                });
        dialog.show();
    }

    private static Button button(
            Context context, String label, Runnable action, Dialog dialog) {
        Button button = tvButton(context, label);
        button.setOnClickListener(
                v -> {
                    dialog.dismiss();
                    action.run();
                });
        return button;
    }

    private static Button tvButton(Context context, String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(18);
        button.setTextColor(NORMAL_TEXT);
        button.setBackgroundColor(NORMAL_BG);
        button.setFocusable(true);
        button.setOnFocusChangeListener(
                (view, focused) -> {
                    button.setBackgroundColor(focused ? FOCUSED_BG : NORMAL_BG);
                    button.setTextColor(focused ? Color.WHITE : NORMAL_TEXT);
                    button.setText(focused ? "▶ " + label + " ◀" : label);
                });
        return button;
    }

    private static LinearLayout.LayoutParams matchWrap(Context context) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 64));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
