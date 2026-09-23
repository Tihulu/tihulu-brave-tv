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
    interface Callback {
        void previousTab();
        void nextTab();
        void newTab();
        void closeCurrentTab();
    }

    private TvTabPanel() {}

    static Dialog show(Context context, Callback callback) {
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

        TvUi.setPanelContent(context, dialog, column);
        dialog.setOnShowListener(
                ignored -> {
                    TvUi.sizePanel(context, dialog, 640);
                    next.requestFocus();
                });
        dialog.show();
        return dialog;
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
        TvUi.styleButton(context, button);
        return button;
    }

    private static LinearLayout.LayoutParams matchWrap(Context context) {
        return TvUi.row(context);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
