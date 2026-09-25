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

final class TvControlPanel {
    interface Callback {
        NavigationMode mode();
        void toggleMode();
        void openAddress();
        void showTabs();
        void centerCursor();
        boolean adBlockEnabled();
        void toggleAdBlock();
        int blockedCount();
        void goHome();
    }

    private TvControlPanel() {}

    static void show(Context context, Callback callback) {
        Dialog dialog = new Dialog(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = TvUi.dp(context, 24);
        column.setPadding(pad, pad, pad, pad);
        column.setBackground(TvUi.rounded(TvUi.PANEL, TvUi.dp(context, 24), Color.TRANSPARENT, 0));

        TextView title = TvUi.title(context, "Tihulu TV Browser Lite", 26);
        column.addView(title);

        TextView subtitle = TvUi.title(context,
                "WebView engine · TV controls · lightweight ad blocking", 15);
        subtitle.setTextColor(TvUi.MUTED);
        subtitle.setPadding(0, 0, 0, TvUi.dp(context, 12));
        column.addView(subtitle);

        Button mode = TvUi.button(context, modeLabel(callback.mode()));
        mode.setOnClickListener(v -> {
            callback.toggleMode();
            mode.setText(modeLabel(callback.mode()));
        });
        column.addView(mode, TvUi.fullRow(context));

        Button address = TvUi.button(context, "Search / Address / Keyboard");
        address.setOnClickListener(v -> {
            dialog.dismiss();
            callback.openAddress();
        });
        column.addView(address, TvUi.fullRow(context));

        Button tabs = TvUi.button(context, "Tabs");
        tabs.setOnClickListener(v -> {
            dialog.dismiss();
            callback.showTabs();
        });
        column.addView(tabs, TvUi.fullRow(context));

        Button blocker = TvUi.button(context, blockerLabel(callback));
        blocker.setOnClickListener(v -> {
            callback.toggleAdBlock();
            blocker.setText(blockerLabel(callback));
        });
        column.addView(blocker, TvUi.fullRow(context));

        Button home = TvUi.button(context, "Home");
        home.setOnClickListener(v -> {
            dialog.dismiss();
            callback.goHome();
        });
        column.addView(home, TvUi.fullRow(context));

        Button center = TvUi.button(context, "Center cursor");
        center.setOnClickListener(v -> callback.centerCursor());
        column.addView(center, TvUi.fullRow(context));

        Button close = TvUi.button(context, "Close");
        close.setOnClickListener(v -> dialog.dismiss());
        column.addView(close, TvUi.fullRow(context));

        dialog.setContentView(column);
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setGravity(Gravity.CENTER);
                window.setLayout(TvUi.dp(context, 620), ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            mode.requestFocus();
        });
        dialog.show();
    }

    private static String modeLabel(NavigationMode mode) {
        return mode == NavigationMode.CURSOR ? "Navigation: Cursor" : "Navigation: D-pad";
    }

    private static String blockerLabel(Callback callback) {
        return (callback.adBlockEnabled() ? "Ad blocker: On" : "Ad blocker: Off")
                + "  ·  blocked " + callback.blockedCount();
    }
}
