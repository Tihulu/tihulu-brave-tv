/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.app.Dialog;
import android.content.Context;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** TV-sized address entry in its own focus/IME window, independent of pointer mode. */
final class TvAddressPanel {
    interface Callback {
        boolean openAddress(String input);
    }

    private TvAddressPanel() {}

    static Dialog show(Context context, String currentUrl, Callback callback) {
        Dialog dialog = new Dialog(context);
        LinearLayout column = TvUi.column(context);
        column.addView(TvUi.text(context, "Search / Address", 28, TvUi.TEXT), TvUi.row(context));
        column.addView(TvUi.text(context, "Search with your browser's default search engine", 16,
                TvUi.MUTED), TvUi.row(context));
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setTextSize(22);
        input.setTextColor(TvUi.TEXT);
        input.setHintTextColor(TvUi.MUTED);
        input.setHint("Search or enter a website");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setSelectAllOnFocus(true);
        input.setMinHeight(TvUi.dp(context, 64));
        input.setPadding(TvUi.dp(context, 16), TvUi.dp(context, 12),
                TvUi.dp(context, 16), TvUi.dp(context, 12));
        input.setBackground(TvUi.background(context, false));
        input.setText(currentUrl);
        column.addView(input, TvUi.row(context));
        TextView error = TvUi.text(context, "", 16, TvUi.ACCENT);
        column.addView(error, TvUi.row(context));
        Runnable submit = () -> {
            String value = input.getText().toString().trim();
            try {
                TvAddressInput.urlCandidate(value);
                if (callback.openAddress(value)) dialog.dismiss();
                else error.setText("The browser is still starting. Please try again.");
            } catch (IllegalArgumentException invalid) {
                error.setText(invalid.getMessage());
            }
        };
        input.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER);
            if (actionId != EditorInfo.IME_ACTION_GO && !enter) return false;
            // Consume both halves of a physical Enter, submit exactly once on release.
            if (event == null || (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled())) {
                submit.run();
            }
            return true;
        });
        TvUi.addPair(context, column, TvUi.button(context, "Go", submit),
                TvUi.button(context, "Cancel", dialog::dismiss));
        TvUi.setPanelContent(context, dialog, column);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.setOnShowListener(ignored -> {
            TvUi.sizePanel(context, dialog, 760);
            input.requestFocus();
        });
        dialog.show();
        return dialog;
    }
}
