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

import java.util.IdentityHashMap;
import java.util.Map;

final class TvKeyboard {
    interface Callback {
        void submit();
    }

    static final class Result {
        final LinearLayout root;
        final Button firstKey;
        private final Button[][] keys;
        private final Map<View, Position> positions = new IdentityHashMap<>();
        private final EditText target;

        Result(LinearLayout root, Button firstKey, Button[][] keys, EditText target) {
            this.root = root;
            this.firstKey = firstKey;
            this.keys = keys;
            this.target = target;

            for (int row = 0; row < keys.length; row++) {
                for (int col = 0; col < keys[row].length; col++) {
                    positions.put(keys[row][col], new Position(row, col));
                }
            }
        }

        boolean owns(View view) {
            return positions.containsKey(view);
        }

        boolean handleDirectional(View current, int keyCode) {
            Position position = positions.get(current);
            if (position == null) return false;

            int row = position.row;
            int col = position.col;

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                keys[row][Math.max(0, col - 1)].requestFocus();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                keys[row][Math.min(keys[row].length - 1, col + 1)].requestFocus();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (row == 0) {
                    target.requestFocus();
                    target.selectAll();
                } else {
                    int mapped = mapColumn(col, keys[row].length, keys[row - 1].length);
                    keys[row - 1][mapped].requestFocus();
                }
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (row < keys.length - 1) {
                    int mapped = mapColumn(col, keys[row].length, keys[row + 1].length);
                    keys[row + 1][mapped].requestFocus();
                }
                return true;
            }

            return false;
        }

        private static int mapColumn(int col, int sourceSize, int targetSize) {
            if (targetSize <= 1 || sourceSize <= 1) return 0;
            float fraction = (float) col / (float) (sourceSize - 1);
            return Math.max(0, Math.min(targetSize - 1, Math.round(fraction * (targetSize - 1))));
        }
    }

    private static final class Position {
        final int row;
        final int col;

        Position(int row, int col) {
            this.row = row;
            this.col = col;
        }
    }

    private TvKeyboard() {}

    static Result create(Context context, EditText target, Callback callback) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, TvUi.dp(context, 8), 0, 0);
        root.setFocusable(false);

        TextView hint = TvUi.title(
                context,
                "↓ Keyboard  ·  D-pad: move  ·  OK: type  ·  ↑ from top row: address",
                13);
        hint.setTextColor(TvUi.MUTED);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        hint.setFocusable(false);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        String[][] labels = {
                {"1","2","3","4","5","6","7","8","9","0"},
                {"q","w","e","r","t","y","u","ı","o","p"},
                {"a","s","d","f","g","h","j","k","l","ş"},
                {"z","x","c","v","b","n","m","ö","ç","ğ"},
                {"ü",".","/",":","-","_","@","⌫"},
                {"Clear","Space","Go"}
        };

        Button[][] keys = new Button[labels.length][];
        Button firstKey = null;

        for (int rowIndex = 0; rowIndex < labels.length; rowIndex++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setFocusable(false);

            keys[rowIndex] = new Button[labels[rowIndex].length];

            for (int colIndex = 0; colIndex < labels[rowIndex].length; colIndex++) {
                String label = labels[rowIndex][colIndex];
                Button key = TvUi.button(context, label);
                key.setId(View.generateViewId());
                key.setTextSize(rowIndex == labels.length - 1 ? 15f : 18f);
                key.setMinWidth(0);
                key.setMinimumWidth(0);
                key.setFocusable(true);
                key.setFocusableInTouchMode(true);

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(0, TvUi.dp(context, 54), 1f);
                params.setMargins(
                        TvUi.dp(context, 3),
                        TvUi.dp(context, 3),
                        TvUi.dp(context, 3),
                        TvUi.dp(context, 3));
                row.addView(key, params);

                keys[rowIndex][colIndex] = key;
                if (firstKey == null) firstKey = key;
                installAction(key, label, target, callback);
            }

            root.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        if (firstKey == null) throw new IllegalStateException("TV keyboard has no keys");

        target.setId(View.generateViewId());
        target.setFocusable(true);
        target.setFocusableInTouchMode(true);
        target.setNextFocusDownId(firstKey.getId());

        // Also provide an explicit Android focus graph as a fallback. The dialog-level
        // dispatcher is authoritative, but these links make focus deterministic even
        // on remotes/ROMs that bypass the OnKeyListener path.
        for (int row = 0; row < keys.length; row++) {
            for (int col = 0; col < keys[row].length; col++) {
                Button key = keys[row][col];

                key.setNextFocusLeftId(keys[row][Math.max(0, col - 1)].getId());
                key.setNextFocusRightId(keys[row][Math.min(keys[row].length - 1, col + 1)].getId());

                if (row == 0) {
                    key.setNextFocusUpId(target.getId());
                } else {
                    int up = Result.mapColumn(col, keys[row].length, keys[row - 1].length);
                    key.setNextFocusUpId(keys[row - 1][up].getId());
                }

                if (row < keys.length - 1) {
                    int down = Result.mapColumn(col, keys[row].length, keys[row + 1].length);
                    key.setNextFocusDownId(keys[row + 1][down].getId());
                } else {
                    key.setNextFocusDownId(key.getId());
                }
            }
        }

        return new Result(root, firstKey, keys, target);
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
