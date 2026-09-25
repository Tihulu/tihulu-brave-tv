package com.tihulu.tvlite;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class TvBrowserBar {
    interface Callback {
        void goBack();
        void goForward();
        void reload();
        void openAddress();
        void showTabs();
        void showControls();
        void toggleMode();
        NavigationMode mode();
    }

    private TvBrowserBar() {}

    static Dialog show(Context context, Callback callback) {
        Dialog dialog = new Dialog(context);
        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(TvUi.dp(context, 12), TvUi.dp(context, 10),
                TvUi.dp(context, 12), TvUi.dp(context, 10));
        shell.setBackground(TvUi.rounded(TvUi.BG, TvUi.dp(context, 18), Color.TRANSPARENT, 0));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Button back = action(context, "← Back", () -> closeAndRun(dialog, callback::goBack));
        Button forward = action(context, "→ Forward", () -> closeAndRun(dialog, callback::goForward));
        Button reload = action(context, "↻ Reload", () -> closeAndRun(dialog, callback::reload));
        Button address = action(context, "Search / Address", () -> closeAndRun(dialog, callback::openAddress));
        Button tabs = action(context, "Tabs", () -> closeAndRun(dialog, callback::showTabs));
        Button mode = action(context, modeLabel(callback.mode()), () -> {});
        mode.setOnClickListener(v -> {
            callback.toggleMode();
            installModeFocus(context, mode, callback.mode());
            mode.requestFocus();
        });
        Button menu = action(context, "Menu", () -> closeAndRun(dialog, callback::showControls));
        Button close = action(context, "✕ Close", dialog::dismiss);

        row.addView(back, weighted(context, .9f));
        row.addView(forward, weighted(context, 1.0f));
        row.addView(reload, weighted(context, .95f));
        row.addView(address, weighted(context, 1.5f));
        row.addView(tabs, weighted(context, .8f));
        row.addView(mode, weighted(context, 1.15f));
        row.addView(menu, weighted(context, .8f));
        row.addView(close, weighted(context, .85f));
        shell.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = TvUi.title(context,
                "D-pad: move  ·  OK: select  ·  ↓ / Back: close  ·  Hold ↑: open bar", 13);
        hint.setTextColor(TvUi.MUTED);
        hint.setPadding(TvUi.dp(context, 10), TvUi.dp(context, 5), 0, 0);
        shell.addView(hint);

        dialog.setContentView(shell);
        dialog.setOnKeyListener((d, keyCode, event) -> {
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
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setGravity(Gravity.TOP);
                window.setDimAmount(0f);
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            address.requestFocus();
        });
        dialog.show();
        return dialog;
    }

    private static Button action(Context context, String label, Runnable action) {
        Button button = TvUi.button(context, label);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private static void installModeFocus(Context context, Button button, NavigationMode mode) {
        String label = modeLabel(mode);
        button.setText("▶  " + label + "  ◀");
        button.setBackground(TvUi.rounded(TvUi.FOCUSED, TvUi.dp(context, 14),
                Color.WHITE, TvUi.dp(context, 2)));
        button.setOnFocusChangeListener((v, focused) -> {
            button.setBackground(TvUi.rounded(
                    focused ? TvUi.FOCUSED : TvUi.NORMAL,
                    TvUi.dp(context, 14),
                    focused ? Color.WHITE : Color.TRANSPARENT,
                    focused ? TvUi.dp(context, 2) : 0));
            button.setText(focused ? "▶  " + label + "  ◀" : label);
        });
    }

    private static String modeLabel(NavigationMode mode) {
        return mode == NavigationMode.CURSOR ? "Mode: Cursor" : "Mode: D-pad";
    }

    private static void closeAndRun(Dialog dialog, Runnable action) {
        dialog.dismiss();
        action.run();
    }

    private static LinearLayout.LayoutParams weighted(Context context, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, TvUi.dp(context, 62), weight);
        params.setMargins(TvUi.dp(context, 3), 0, TvUi.dp(context, 3), 0);
        return params;
    }
}
