package com.tihulu.tvlite;

import android.content.Context;
import android.text.Editable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

final class TvKeyboard {
    interface Callback {
        void submit();
    }

    static final class Result {
        final LinearLayout root;
        final Button firstKey;

        Result(LinearLayout root, Button firstKey) {
            this.root = root;
            this.firstKey = firstKey;
        }
    }

    private TvKeyboard() {}

    static Result create(Context context, EditText target, Callback callback) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, TvUi.dp(context, 8), 0, 0);

        TextView hint = TvUi.title(
                context,
                "↓ Keyboard  ·  D-pad: move  ·  OK: type  ·  ↑ from top row: address",
                13);
        hint.setTextColor(TvUi.MUTED);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        String[][] rows = {
                {"1","2","3","4","5","6","7","8","9","0"},
                {"q","w","e","r","t","y","u","ı","o","p"},
                {"a","s","d","f","g","h","j","k","l","ş"},
                {"z","x","c","v","b","n","m","ö","ç","ğ"},
                {"ü",".","/",":","-","_","@","⌫"},
                {"Clear","Space","Go"}
        };

        Button firstKey = null;
        for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);

            for (String label : rows[rowIndex]) {
                Button key = TvUi.button(context, label);
                key.setId(View.generateViewId());
                key.setTextSize(rowIndex == rows.length - 1 ? 15f : 18f);
                key.setMinWidth(0);
                key.setMinimumWidth(0);

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(0, TvUi.dp(context, 54), 1f);
                params.setMargins(
                        TvUi.dp(context, 3),
                        TvUi.dp(context, 3),
                        TvUi.dp(context, 3),
                        TvUi.dp(context, 3));
                row.addView(key, params);

                if (firstKey == null) firstKey = key;
                installAction(key, label, target, callback);
            }

            root.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        if (firstKey == null) throw new IllegalStateException("TV keyboard has no keys");

        Button topLeft = (Button) ((LinearLayout) root.getChildAt(1)).getChildAt(0);
        Button[] topRow = new Button[((LinearLayout) root.getChildAt(1)).getChildCount()];
        LinearLayout topRowLayout = (LinearLayout) root.getChildAt(1);
        for (int i = 0; i < topRowLayout.getChildCount(); i++) {
            topRow[i] = (Button) topRowLayout.getChildAt(i);
        }

        target.setId(View.generateViewId());
        target.setNextFocusDownId(firstKey.getId());
        for (Button key : topRow) {
            key.setNextFocusUpId(target.getId());
        }

        target.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                topLeft.requestFocus();
                return true;
            }
            return false;
        });

        return new Result(root, firstKey);
    }

    private static void installAction(
            Button key, String label, EditText target, Callback callback) {
        key.setOnClickListener(v -> {
            switch (label) {
                case "⌫":
                    backspace(target);
                    break;
                case "Clear":
                    target.setText("");
                    target.setSelection(0);
                    break;
                case "Space":
                    insert(target, " ");
                    break;
                case "Go":
                    callback.submit();
                    break;
                default:
                    insert(target, label);
                    break;
            }
        });
    }

    private static void insert(EditText target, String text) {
        Editable editable = target.getText();
        int start = Math.max(0, target.getSelectionStart());
        int end = Math.max(0, target.getSelectionEnd());
        int lo = Math.min(start, end);
        int hi = Math.max(start, end);
        editable.replace(lo, hi, text);
        target.setSelection(lo + text.length());
    }

    private static void backspace(EditText target) {
        Editable editable = target.getText();
        int start = Math.max(0, target.getSelectionStart());
        int end = Math.max(0, target.getSelectionEnd());
        int lo = Math.min(start, end);
        int hi = Math.max(start, end);

        if (lo != hi) {
            editable.delete(lo, hi);
            target.setSelection(lo);
        } else if (lo > 0) {
            int previous = Character.offsetByCodePoints(editable, lo, -1);
            editable.delete(previous, lo);
            target.setSelection(previous);
        }
    }
}
