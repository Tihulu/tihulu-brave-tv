/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.app.Dialog;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;

/** Lightweight TV start dashboard. Sites load only after an explicit selection. */
final class TvHomePanel {
    interface Callback {
        void focusAddressBar();
        boolean openAddress(String input);
        void showTabs();
        void showShields();
        void showBookmarks();
        void showDownloads();
        void showTvControls();
    }

    private TvHomePanel() {}

    static Dialog show(Context context, Callback callback) {
        Dialog dialog = new Dialog(context);
        LinearLayout column = TvUi.column(context);
        column.addView(TvUi.text(context, "TIHULU  /  TV BROWSER", 14, TvUi.ACCENT), TvUi.row(context));
        column.addView(TvUi.text(context, "Your web. On the big screen.", 32, TvUi.TEXT), TvUi.row(context));
        column.addView(TvUi.text(context, "Choose a website, or search for something new.", 18,
                TvUi.MUTED), TvUi.row(context));
        Button search = action(context, dialog, "Search or enter a website", callback::focusAddressBar);
        search.setMinHeight(TvUi.dp(context, 72));
        column.addView(search, TvUi.row(context));
        column.addView(TvUi.text(context, "QUICK LINKS", 14, TvUi.MUTED), TvUi.row(context));
        TvUi.addPair(context, column,
                site(context, dialog, callback, "YouTube", "https://www.youtube.com/"),
                site(context, dialog, callback, "Twitch", "https://www.twitch.tv/"));
        TvUi.addPair(context, column,
                site(context, dialog, callback, "Wikipedia", "https://www.wikipedia.org/"),
                action(context, dialog, "Bookmarks", callback::showBookmarks));
        column.addView(TvUi.text(context, "BROWSER", 14, TvUi.MUTED), TvUi.row(context));
        TvUi.addPair(context, column,
                action(context, dialog, "Tabs", callback::showTabs),
                action(context, dialog, "Downloads", callback::showDownloads));
        TvUi.addPair(context, column,
                action(context, dialog, "Shields for this site", callback::showShields),
                action(context, dialog, "TV settings", callback::showTvControls));
        column.addView(TvUi.button(context, "Return to page", dialog::dismiss), TvUi.row(context));
        column.addView(TvUi.text(context, "Arrows to move  ·  OK to choose  ·  Back to return", 14,
                TvUi.MUTED), TvUi.row(context));
        TvUi.setPanelContent(context, dialog, column);
        dialog.setOnShowListener(ignored -> {
            TvUi.sizePanel(context, dialog, 880);
            search.requestFocus();
        });
        dialog.show();
        return dialog;
    }

    private static Button action(Context context, Dialog dialog, String label, Runnable action) {
        return TvUi.button(context, label, () -> {
            dialog.dismiss();
            action.run();
        });
    }

    private static Button site(Context context, Dialog dialog, Callback callback,
            String label, String url) {
        return TvUi.button(context, label, () -> {
            if (callback.openAddress(url)) dialog.dismiss();
        });
    }
}
