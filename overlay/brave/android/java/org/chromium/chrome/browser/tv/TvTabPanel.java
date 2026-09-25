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
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModel;

/** Bounded, text-only tab overview; no thumbnails or background renderer work. */
final class TvTabPanel {
    private static final int PAGE_SIZE = 8;

    interface Callback {
        TabModel tabModel();
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

        TabModel model = callback.tabModel();
        int initialPage = model == null ? 0 : Math.max(0, model.index()) / PAGE_SIZE;
        populate(context, dialog, column, callback, initialPage);

        TvUi.setPanelContent(context, dialog, column);
        dialog.setOnShowListener(
                ignored -> {
                    TvUi.sizePanel(context, dialog, 640);

                });
        dialog.show();
        return dialog;
    }

    private static void populate(Context context, Dialog dialog, LinearLayout column,
            Callback callback, int page) {
        column.removeAllViews();
        TabModel model = callback.tabModel();
        int count = model == null ? 0 : model.getCount();
        int lastPage = Math.max(0, (count - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, lastPage));
        TextView title = new TextView(context);
        title.setText("Tabs · " + count + (count > PAGE_SIZE ? " · " + (currentPage + 1) + "/" + (lastPage + 1) : ""));
        title.setTextColor(TvUi.TEXT);
        title.setTextSize(26);
        column.addView(title, matchWrap(context));
        Button initialFocus = null;
        for (int index = currentPage * PAGE_SIZE; index < Math.min(count, (currentPage + 1) * PAGE_SIZE); index++) {
            Tab tab = model.getTabAt(index);
            if (tab == null || tab.isDestroyed()) continue;
            int id = tab.getId();
            String name = tab.getTitle();
            if (name == null || name.isEmpty()) name = "Untitled tab";
            if (name.length() > 120) name = name.substring(0, 120) + "…";
            boolean selected = index == model.index();
            Button item = button(context, (selected ? "Current · " : "") + name,
                    () -> selectTab(callback.tabModel(), model, id), dialog);
            item.setMaxLines(2);
            item.setEllipsize(android.text.TextUtils.TruncateAt.END);
            column.addView(item, matchWrap(context));
            if (initialFocus == null || selected) initialFocus = item;
        }
        if (currentPage > 0) {
            column.addView(TvUi.button(context, "Earlier tabs", () ->
                    populate(context, dialog, column, callback, currentPage - 1)), matchWrap(context));
        }
        if (currentPage < lastPage) {
            column.addView(TvUi.button(context, "More tabs", () ->
                    populate(context, dialog, column, callback, currentPage + 1)), matchWrap(context));
        }
        Button create = button(context, "New tab", callback::newTab, dialog);
        column.addView(create, matchWrap(context));
        if (count > 0) {
            column.addView(button(context, "Close current tab", callback::closeCurrentTab, dialog), matchWrap(context));
        }
        column.addView(button(context, "Previous tab", callback::previousTab, dialog), matchWrap(context));
        column.addView(button(context, "Next tab", callback::nextTab, dialog), matchWrap(context));
        column.addView(TvUi.button(context, "Back", dialog::dismiss), matchWrap(context));
        (initialFocus == null ? create : initialFocus).requestFocus();
    }

    static void selectTab(TabModel active, TabModel captured, int id) {
        // Indices can change while the panel is open. Resolve the stable ID, and never switch
        // a stale regular/private model after the active profile changed.
        if (active == null || active != captured) return;
        for (int index = 0; index < active.getCount(); index++) {
            Tab tab = active.getTabAt(index);
            if (tab != null && !tab.isDestroyed() && tab.getId() == id) {
                active.setIndex(index, TabSelectionType.FROM_USER);
                return;
            }
        }
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
