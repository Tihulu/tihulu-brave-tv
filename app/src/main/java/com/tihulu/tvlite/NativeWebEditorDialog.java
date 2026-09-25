package com.tihulu.tvlite;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

final class NativeWebEditorDialog {
    interface Callback {
        void onTextChanged(String value);
        void onSubmit(String value);
        void onClosed(String value);
    }

    private NativeWebEditorDialog() {}

    static Dialog show(
            Context context,
            String initialValue,
            String hintText,
            String inputType,
            Callback callback) {
        Dialog dialog = new Dialog(context);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(
                TvUi.dp(context, 22),
                TvUi.dp(context, 18),
                TvUi.dp(context, 22),
                TvUi.dp(context, 18));
        column.setBackground(
                TvUi.rounded(TvUi.PANEL, TvUi.dp(context, 24), Color.TRANSPARENT, 0));

        TextView title = TvUi.title(context, "Type with Android TV keyboard", 22);
        title.setTextColor(Color.WHITE);
        column.addView(title);

        TextView subtitle =
                TvUi.title(context, "D-pad now belongs to the system keyboard", 13);
        subtitle.setTextColor(TvUi.MUTED);
        subtitle.setPadding(0, 0, 0, TvUi.dp(context, 10));
        column.addView(subtitle);

        EditText editor = new EditText(context);
        editor.setSingleLine(true);
        editor.setText(initialValue == null ? "" : initialValue);
        editor.setSelectAllOnFocus(false);
        editor.setSelection(editor.length());
        editor.setTextColor(Color.WHITE);
        editor.setHintTextColor(TvUi.MUTED);
        editor.setHint(hintText == null || hintText.isEmpty() ? "Type…" : hintText);
        editor.setTextSize(20f);
        editor.setFocusable(true);
        editor.setFocusableInTouchMode(true);
        editor.setPadding(
                TvUi.dp(context, 16),
                0,
                TvUi.dp(context, 16),
                0);
        editor.setBackground(
                TvUi.rounded(
                        TvUi.NORMAL,
                        TvUi.dp(context, 14),
                        TvUi.FOCUSED,
                        TvUi.dp(context, 2)));

        int androidInputType = InputType.TYPE_CLASS_TEXT;
        String normalized = inputType == null ? "" : inputType.toLowerCase();
        if ("url".equals(normalized)) {
            androidInputType |= InputType.TYPE_TEXT_VARIATION_URI;
        } else if ("email".equals(normalized)) {
            androidInputType |= InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        } else {
            androidInputType |= InputType.TYPE_TEXT_VARIATION_NORMAL;
        }
        editor.setInputType(androidInputType);
        editor.setImeOptions(EditorInfo.IME_ACTION_SEARCH);

        LinearLayout.LayoutParams editorParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        TvUi.dp(context, 64));
        column.addView(editor, editorParams);

        final boolean[] closing = {false};
        editor.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(
                            CharSequence s, int start, int before, int count) {
                        if (!closing[0]) callback.onTextChanged(s.toString());
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

        editor.setOnEditorActionListener(
                (v, actionId, event) -> {
                    boolean submit =
                            actionId == EditorInfo.IME_ACTION_SEARCH
                                    || actionId == EditorInfo.IME_ACTION_GO
                                    || actionId == EditorInfo.IME_ACTION_DONE
                                    || (event != null
                                            && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
                    if (!submit) return false;

                    String value = editor.getText().toString();
                    closing[0] = true;
                    callback.onSubmit(value);
                    dialog.dismiss();
                    return true;
                });

        dialog.setContentView(column);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(
                ignored -> {
                    String value = editor.getText().toString();
                    closing[0] = true;
                    callback.onClosed(value);
                });

        dialog.setOnShowListener(
                ignored -> {
                    Window window = dialog.getWindow();
                    if (window != null) {
                        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                        window.setGravity(Gravity.TOP);
                        window.setLayout(
                                TvUi.dp(context, 860),
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        window.setSoftInputMode(
                                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    }

                    editor.requestFocus();
                    editor.postDelayed(
                            () -> {
                                InputMethodManager imm =
                                        (InputMethodManager)
                                                context.getSystemService(
                                                        Context.INPUT_METHOD_SERVICE);
                                if (imm != null) {
                                    imm.restartInput(editor);
                                    imm.showSoftInput(
                                            editor,
                                            InputMethodManager.SHOW_IMPLICIT);
                                }
                            },
                            150);
                });

        dialog.show();
        return dialog;
    }
}
