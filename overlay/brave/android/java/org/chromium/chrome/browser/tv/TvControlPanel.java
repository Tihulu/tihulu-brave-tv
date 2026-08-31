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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small focusable control surface intended for a TV viewing distance. */
final class TvControlPanel {
    interface Callback {
        TvNavigationMode navigationMode();
        void setNavigationMode(TvNavigationMode mode);
        void focusAddressBar();
        void centerCursor();
        void showTabs();
        void checkForUpdates();
        void showAbout();
    }

    private TvControlPanel() {}

    static void show(Context context, Callback callback) {
        Dialog dialog = new Dialog(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 24);
        column.setPadding(pad, pad, pad, pad);
        column.setBackgroundColor(Color.rgb(32, 32, 32));

        TextView title = new TextView(context);
        title.setText("Tihulu TV Browser");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.START);
        column.addView(title, matchWrap());

        TextView subtitle = new TextView(context);
        subtitle.setText("TV Controls · Based on Brave & Chromium");
        subtitle.setTextColor(Color.rgb(210, 210, 214));
        subtitle.setTextSize(16);
        column.addView(subtitle, matchWrap());

        Button mode = new Button(context);
        updateModeText(mode, callback.navigationMode());
        mode.setOnClickListener(
                v -> {
                    TvNavigationMode next = callback.navigationMode().toggle();
                    callback.setNavigationMode(next);
                    updateModeText(mode, next);
                });
        column.addView(mode, matchWrap());

        Button keyboard = new Button(context);
        keyboard.setText("Search / Address / Keyboard");
        keyboard.setOnClickListener(
                v -> {
                    dialog.dismiss();
                    callback.focusAddressBar();
                });
        column.addView(keyboard, matchWrap());

        Button tabs = new Button(context);
        tabs.setText("Tabs");
        tabs.setOnClickListener(
                v -> {
                    dialog.dismiss();
                    callback.showTabs();
                });
        column.addView(tabs, matchWrap());

        Button update = new Button(context);
        update.setText("Check for updates");
        update.setOnClickListener(
                v -> {
                    dialog.dismiss();
                    callback.checkForUpdates();
                });
        column.addView(update, matchWrap());

        Button about = new Button(context);
        about.setText("About Tihulu TV Browser");
        about.setOnClickListener(
                v -> {
                    dialog.dismiss();
                    callback.showAbout();
                });
        column.addView(about, matchWrap());

        Button center = new Button(context);
        center.setText("Center cursor");
        center.setOnClickListener(v -> callback.centerCursor());
        column.addView(center, matchWrap());

        Button close = new Button(context);
        close.setText("Close");
        close.setOnClickListener(v -> dialog.dismiss());
        column.addView(close, matchWrap());

        dialog.setContentView(column);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(dp(context, 560), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setOnShowListener(
                ignored -> {
                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setLayout(
                                dp(context, 560), ViewGroup.LayoutParams.WRAP_CONTENT);
                    }
                    mode.requestFocus();
                });
        dialog.show();
    }

    private static void updateModeText(Button button, TvNavigationMode mode) {
        button.setText(mode == TvNavigationMode.DPAD ? "Navigation: D-pad" : "Navigation: Cursor");
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
