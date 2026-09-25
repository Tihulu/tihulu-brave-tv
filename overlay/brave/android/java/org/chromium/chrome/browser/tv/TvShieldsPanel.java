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
import android.widget.TextView;
import android.widget.Toast;

import org.chromium.chrome.browser.preferences.website.BraveShieldsContentSettings;
import org.chromium.chrome.browser.tab.Tab;

/** Per-site controls backed by Brave's native network and cosmetic filtering settings. */
final class TvShieldsPanel {
    private TvShieldsPanel() {}

    static Dialog show(Context context, Tab tab) {
        if (tab == null || tab.isDestroyed()) return null;
        String url = tab.getUrl().getSpec();
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            Toast.makeText(context, "Open a website to manage its Shields.", Toast.LENGTH_LONG).show();
            return null;
        }
        Dialog dialog = new Dialog(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int padding = TvUi.dp(context, 24);
        column.setPadding(padding, padding, padding, padding);
        TextView title = new TextView(context);
        title.setText("Shields · " + tab.getUrl().getHost());
        title.setTextSize(26);
        title.setTextColor(TvUi.TEXT);
        column.addView(title, TvUi.row(context));
        TextView help = new TextView(context);
        help.setText("Ad & tracker blocking for this site. Changes reload the page. "
                + "Aggressive blocks more ads but can break some sites. "
                + "Video ads embedded in a stream may still appear.");
        help.setTextSize(18);
        help.setTextColor(TvUi.TEXT);
        column.addView(help, TvUi.row(context));
        boolean enabled = BraveShieldsContentSettings.getShields(tab.getProfile(), url,
                BraveShieldsContentSettings.RESOURCE_IDENTIFIER_BRAVE_SHIELDS);
        String level = BraveShieldsContentSettings.getShieldsValue(tab.getProfile(), url,
                BraveShieldsContentSettings.RESOURCE_IDENTIFIER_TRACKERS);
        TextView status = new TextView(context);
        status.setText("Current: " + (enabled ? "Shields on · " + level : "Shields off"));
        status.setTextSize(18);
        status.setTextColor(TvUi.ACCENT);
        column.addView(status, TvUi.row(context));
        Button standard = TvUi.button(context, "Standard blocking", () -> apply(tab, url, dialog, false, true));
        column.addView(standard, TvUi.row(context));
        column.addView(TvUi.button(context, "Aggressive blocking", () -> apply(tab, url, dialog, true, true)), TvUi.row(context));
        column.addView(TvUi.button(context, "Turn off for this site", () -> apply(tab, url, dialog, false, false)), TvUi.row(context));
        column.addView(TvUi.button(context, "Use global defaults", () -> {
            if (!tab.isDestroyed()) {
                BraveShieldsContentSettings.resetSiteToDefaults(tab.getProfile(), url);
                if (url.equals(tab.getUrl().getSpec())) tab.reload();
            }
            dialog.dismiss();
        }), TvUi.row(context));
        column.addView(TvUi.button(context, "Back", dialog::dismiss), TvUi.row(context));
        TvUi.setPanelContent(context, dialog, column);
        dialog.setOnShowListener(ignored -> {
            TvUi.sizePanel(context, dialog, 640);
            standard.requestFocus();
        });
        dialog.show();
        return dialog;
    }

    private static void apply(Tab tab, String url, Dialog dialog, boolean aggressive, boolean enabled) {
        if (!tab.isDestroyed()) {
            BraveShieldsContentSettings.setShields(tab.getProfile(), url,
                    BraveShieldsContentSettings.RESOURCE_IDENTIFIER_BRAVE_SHIELDS, enabled, true);
            if (enabled) {
                BraveShieldsContentSettings.setShieldsValue(tab.getProfile(), url,
                        BraveShieldsContentSettings.RESOURCE_IDENTIFIER_TRACKERS,
                        aggressive ? BraveShieldsContentSettings.AGGRESSIVE : BraveShieldsContentSettings.DEFAULT, false);
            }
            if (url.equals(tab.getUrl().getSpec())) tab.reload();
        }
        dialog.dismiss();
    }
}
