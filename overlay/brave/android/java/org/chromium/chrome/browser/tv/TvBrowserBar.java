/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

/** Always-visible, focusable browser controls sized for a TV viewing distance. */
final class TvBrowserBar extends LinearLayout {
    interface Callback {
        void goBack();
        void goForward();
        void reloadPage();
        void focusAddressBar();
        void showTabs();
        void showTvControls();
        TvNavigationMode navigationMode();
    }

    private final Button mSearchButton;
    private final Button mModeButton;

    TvBrowserBar(Context context, Callback callback) {
        super(context);
        setOrientation(0);
        int pad = dp(context, 10);
        setPadding(pad, pad, pad, pad);
        setBackgroundColor(Color.rgb(20, 20, 20));
        setFocusable(false);

        Button back = actionButton(context, "Back", callback::goBack);
        Button forward = actionButton(context, "Forward", callback::goForward);
        Button reload = actionButton(context, "Reload", callback::reloadPage);
        mSearchButton = actionButton(context, "Search / Address", callback::focusAddressBar);
        Button tabs = actionButton(context, "Tabs", callback::showTabs);
        mModeButton = actionButton(context, "TV Controls", callback::showTvControls);

        addView(back, buttonLayout(context));
        addView(forward, buttonLayout(context));
        addView(reload, buttonLayout(context));
        addView(mSearchButton, buttonLayout(context));
        addView(tabs, buttonLayout(context));
        addView(mModeButton, buttonLayout(context));
        refreshMode(callback.navigationMode());
    }

    void focusPrimaryAction() {
        mSearchButton.requestFocus();
    }

    void refreshMode(TvNavigationMode mode) {
        mModeButton.setText(mode == TvNavigationMode.CURSOR ? "TV: Cursor" : "TV: D-pad");
    }

    private static Button actionButton(Context context, String label, Runnable action) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(18);
        button.setFocusable(true);
        button.setClickable(true);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private static LinearLayout.LayoutParams buttonLayout(Context context) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 64));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
