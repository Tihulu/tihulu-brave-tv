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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.chromium.chrome.R;

/** Compact TV-friendly About surface with project branding and update access. */
final class TvAboutPanel {
    private TvAboutPanel() {}

    static void show(Context context, Runnable onCheckForUpdates) {
        Dialog dialog = new Dialog(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 28);
        column.setPadding(pad, pad, pad, pad);
        column.setBackgroundColor(Color.rgb(20, 20, 24));

        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.tihulu_tv_icon);
        logo.setContentDescription("Tihulu TV Browser logo");
        LinearLayout.LayoutParams logoParams =
                new LinearLayout.LayoutParams(dp(context, 156), dp(context, 156));
        column.addView(logo, logoParams);

        TextView title = new TextView(context);
        title.setText("Tihulu TV Browser");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.START);
        column.addView(title, matchWrap());

        TextView engine = new TextView(context);
        engine.setText("Based on Brave & Chromium");
        engine.setTextColor(Color.rgb(255, 64, 72));
        engine.setTextSize(18);
        column.addView(engine, matchWrap());

        TextView description = new TextView(context);
        description.setText(
                "An unofficial community browser adapted for Google TV and Android TV. "
                        + "Brave and Chromium remain credited to their respective projects.");
        description.setTextColor(Color.rgb(220, 220, 224));
        description.setTextSize(17);
        description.setPadding(0, dp(context, 12), 0, dp(context, 16));
        column.addView(description, matchWrap());

        Button update = new Button(context);
        update.setText("Check for updates");
        update.setOnClickListener(
                v -> {
                    dialog.dismiss();
                    if (onCheckForUpdates != null) onCheckForUpdates.run();
                });
        column.addView(update, matchWrap());

        Button close = new Button(context);
        close.setText("Close");
        close.setOnClickListener(v -> dialog.dismiss());
        column.addView(close, matchWrap());

        dialog.setContentView(column);
        dialog.setOnShowListener(
                ignored -> {
                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setLayout(
                                dp(context, 620), ViewGroup.LayoutParams.WRAP_CONTENT);
                    }
                    update.requestFocus();
                });
        dialog.show();
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
