package com.tihulu.tvlite;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class TvTabPanel {
    interface Callback {
        int currentTabNumber();
        int tabCount();
        String currentTabTitle();
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
        int pad = TvUi.dp(context, 24);
        column.setPadding(pad, pad, pad, pad);
        column.setBackground(TvUi.rounded(TvUi.PANEL, TvUi.dp(context, 24), Color.TRANSPARENT, 0));

        TextView title = TvUi.title(context, "Tabs", 28);
        column.addView(title);

        TextView current = TvUi.title(context,
                "Tab " + callback.currentTabNumber() + " of " + callback.tabCount()
                        + "  ·  " + callback.currentTabTitle(), 15);
        current.setTextColor(TvUi.MUTED);
        current.setPadding(0, 0, 0, TvUi.dp(context, 12));
        column.addView(current);

        Button previous = action(context, "Previous tab", callback::previousTab, dialog);
        Button next = action(context, "Next tab", callback::nextTab, dialog);
        Button create = action(context, "New tab", callback::newTab, dialog);
        Button closeTab = action(context, "Close current tab", callback::closeCurrentTab, dialog);
        Button close = TvUi.button(context, "Close");
        close.setOnClickListener(v -> dialog.dismiss());

        column.addView(previous, TvUi.fullRow(context));
        column.addView(next, TvUi.fullRow(context));
        column.addView(create, TvUi.fullRow(context));
        column.addView(closeTab, TvUi.fullRow(context));
        column.addView(close, TvUi.fullRow(context));

        dialog.setContentView(column);
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setGravity(Gravity.CENTER);
                window.setLayout(TvUi.dp(context, 640), ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            next.requestFocus();
        });
        dialog.show();
    }

    private static Button action(Context context, String label, Runnable action, Dialog dialog) {
        Button button = TvUi.button(context, label);
        button.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        return button;
    }
}
