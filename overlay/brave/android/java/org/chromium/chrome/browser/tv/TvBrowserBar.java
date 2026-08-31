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
        void showTvControls();
        void toggleNavigationMode();
        TvNavigationMode navigationMode();
    }

    private static final int NORMAL_BG = Color.rgb(42, 42, 46);
    private static final int FOCUSED_BG = Color.rgb(190, 24, 32);
    private static final int NORMAL_TEXT = Color.rgb(232, 232, 236);
    private static final int FOCUSED_TEXT = Color.WHITE;

    private final Button mSearchButton;
    private final Button mModeButton;

    private TvBrowserBar(Context context, Dialog dialog, Callback callback) {
        super(context);
        setOrientation(0);
        int pad = dp(context, 8);
        setPadding(pad, pad, pad, pad);
        setBackgroundColor(Color.rgb(18, 18, 20));
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

        addView(back, buttonLayout(context, 0.85f));
        addView(forward, buttonLayout(context, 0.95f));
        addView(reload, buttonLayout(context, 0.9f));
        addView(mSearchButton, buttonLayout(context, 1.45f));
        addView(tabs, buttonLayout(context, 0.8f));
        addView(mModeButton, buttonLayout(context, 1.15f));
        addView(menu, buttonLayout(context, 0.8f));
        addView(close, buttonLayout(context, 0.8f));
    }

    static Dialog show(Context context, Callback callback) {
        Dialog dialog = new Dialog(context);

        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(18, 18, 20));

        TvBrowserBar bar = new TvBrowserBar(context, dialog, callback);
        shell.addView(
                bar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(context);
        hint.setText("D-pad: move focus   ·   OK: select   ·   ↓/Back: close   ·   Hold OK: Cursor/D-pad   ·   Hold ↑: open bar");
        hint.setTextColor(Color.rgb(190, 190, 196));
        hint.setTextSize(13);
        int hPad = dp(context, 14);
        hint.setPadding(hPad, 0, hPad, dp(context, 8));
        shell.addView(
                hint,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(shell);
        dialog.setOnKeyListener(
                (ignored, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                            || keyCode == KeyEvent.KEYCODE_BACK
                            || keyCode == KeyEvent.KEYCODE_MENU
                            || keyCode == KeyEvent.KEYCODE_INFO
                            || keyCode == KeyEvent.KEYCODE_GUIDE) {
                        dialog.dismiss();
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
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(17);
        button.setTextColor(NORMAL_TEXT);
        button.setBackgroundColor(NORMAL_BG);
        button.setFocusable(true);
        button.setClickable(true);
        button.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        button.setOnFocusChangeListener(
                (view, focused) -> {
                    button.setBackgroundColor(focused ? FOCUSED_BG : NORMAL_BG);
                    button.setTextColor(focused ? FOCUSED_TEXT : NORMAL_TEXT);
                    button.setScaleX(focused ? 1.06f : 1.0f);
                    button.setScaleY(focused ? 1.06f : 1.0f);
                });
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private static void runAndDismiss(Dialog dialog, Runnable action) {
        dialog.dismiss();
        action.run();
    }

    private static LinearLayout.LayoutParams buttonLayout(Context context, float weight) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, dp(context, 64), weight);
        return params;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
