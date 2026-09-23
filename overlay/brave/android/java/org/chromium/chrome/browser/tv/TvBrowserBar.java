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
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Remote-first browser chrome presented in its own top-anchored Dialog window.
 *
 * <p>It deliberately never attaches to Chromium's DecorView. That keeps Chrome's toolbar and
 * compositor hierarchy untouched while giving TV remotes a deterministic, highly visible focus
 * surface.
 */
final class TvBrowserBar extends LinearLayout {
    interface Callback {
        void goBack();
        void goForward();
        void reloadPage();
        void focusAddressBar();
        void showTabs();
        void showShields();
        void showTvControls();
        void toggleNavigationMode();
        TvNavigationMode navigationMode();
    }

    private final Button mSearchButton;
    private final Button mModeButton;

    private TvBrowserBar(Context context, Dialog dialog, Callback callback) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 8);
        setPadding(pad, pad, pad, pad);
        setBackgroundColor(TvUi.BACKGROUND);
        setFocusable(false);

        Button back = actionButton(context, "← Back", () -> runAndDismiss(dialog, callback::goBack));
        Button forward =
                actionButton(context, "→ Forward", () -> runAndDismiss(dialog, callback::goForward));
        Button reload =
                actionButton(context, "↻ Reload", () -> runAndDismiss(dialog, callback::reloadPage));
        mSearchButton =
                actionButton(
                        context,
                        "Search / Address",
                        () -> runAndDismiss(dialog, callback::focusAddressBar));
        Button tabs =
                actionButton(context, "Tabs", () -> runAndDismiss(dialog, callback::showTabs));

        mModeButton = actionButton(context, modeLabel(callback.navigationMode()), () -> {});
        mModeButton.setOnClickListener(
                v -> {
                    callback.toggleNavigationMode();
                    refreshMode(callback.navigationMode());
                    mModeButton.requestFocus();
                });

        Button menu =
                actionButton(
                        context,
                        "Menu",
                        () -> runAndDismiss(dialog, callback::showTvControls));
        Button close = actionButton(context, "✕ Close", dialog::dismiss);

        LinearLayout primary = new LinearLayout(context);
        primary.addView(mSearchButton, buttonLayout(context, 2));
        primary.addView(actionButton(context, "Shields", () -> runAndDismiss(dialog, callback::showShields)), buttonLayout(context, 1));
        primary.addView(tabs, buttonLayout(context, 1));
        primary.addView(menu, buttonLayout(context, 1));
        addView(primary, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout secondary = new LinearLayout(context);
        secondary.addView(back, buttonLayout(context, 1));
        secondary.addView(forward, buttonLayout(context, 1));
        secondary.addView(reload, buttonLayout(context, 1));
        secondary.addView(mModeButton, buttonLayout(context, 1.4f));
        secondary.addView(close, buttonLayout(context, 1));
        addView(secondary, new LinearLayout.LayoutParams(-1, -2));
    }

    static Dialog show(Context context, Callback callback) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(TvUi.BACKGROUND);

        TvBrowserBar bar = new TvBrowserBar(context, dialog, callback);
        shell.addView(
                bar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(context);
        hint.setText(
                "OK: select   ·   Back: return to page   ·   Hold ↑ on page: open controls");
        hint.setTextColor(Color.rgb(205, 205, 210));
        hint.setTextSize(14);
        int hPad = dp(context, 14);
        hint.setPadding(hPad, 0, hPad, dp(context, 8));
        shell.addView(
                hint,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(shell);
        dialog.setOnKeyListener(
                (ignored, keyCode, event) -> {
                    if (keyCode == KeyEvent.KEYCODE_BACK
                            || keyCode == KeyEvent.KEYCODE_MENU
                            || keyCode == KeyEvent.KEYCODE_INFO
                            || keyCode == KeyEvent.KEYCODE_GUIDE) {
                        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                            dialog.dismiss();
                        }
                        return true;
                    }
                    return false;
                });
        dialog.setOnShowListener(
                ignored -> {
                    Window window = dialog.getWindow();
                    if (window != null) {
                        window.setGravity(Gravity.TOP);
                        window.setDimAmount(0.0f);
                        window.setLayout(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                    }
                    bar.focusPrimaryAction();
                });
        dialog.show();
        return dialog;
    }

    void focusPrimaryAction() {
        mSearchButton.requestFocus();
    }

    void refreshMode(TvNavigationMode mode) {
        mModeButton.setText(modeLabel(mode));
    }

    private static String modeLabel(TvNavigationMode mode) {
        return mode == TvNavigationMode.CURSOR ? "Mode: Cursor" : "Mode: D-pad";
    }

    private static Button actionButton(Context context, String label, Runnable action) {
        return TvUi.button(context, label, action);
    }

    private static void runAndDismiss(Dialog dialog, Runnable action) {
        dialog.dismiss();
        action.run();
    }

    private static LinearLayout.LayoutParams buttonLayout(Context context, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(context, 64), weight);
        params.setMargins(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));
        return params;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
