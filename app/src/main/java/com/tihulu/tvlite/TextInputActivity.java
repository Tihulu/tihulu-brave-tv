package com.tihulu.tvlite;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class TextInputActivity extends Activity {
    static final String EXTRA_TOKEN = "token";
    static final String EXTRA_VALUE = "value";
    static final String EXTRA_HINT = "hint";
    static final String EXTRA_INPUT_TYPE = "input_type";
    static final String EXTRA_SUBMIT = "submit";

    private EditText editor;
    private TextView diagnostics;
    private boolean finishedWithResult;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        Intent intent = getIntent();
        String value = intent.getStringExtra(EXTRA_VALUE);
        String hint = intent.getStringExtra(EXTRA_HINT);
        String inputType = intent.getStringExtra(EXTRA_INPUT_TYPE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(
                TvUi.dp(this, 64),
                TvUi.dp(this, 40),
                TvUi.dp(this, 64),
                TvUi.dp(this, 40));
        root.setBackgroundColor(Color.rgb(18, 18, 20));

        TextView title = TvUi.title(this, "Type with Android TV keyboard", 26);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(
                title,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = TvUi.title(this, "D-pad should control the system keyboard", 14);
        subtitle.setTextColor(TvUi.MUTED);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, TvUi.dp(this, 6), 0, TvUi.dp(this, 18));
        root.addView(subtitle, subtitleParams);

        editor = new EditText(this);
        editor.setSingleLine(true);
        editor.setText(value == null ? "" : value);
        editor.setSelection(editor.length());
        editor.setHint(hint == null || hint.isEmpty() ? "Type…" : hint);
        editor.setTextColor(Color.WHITE);
        editor.setHintTextColor(TvUi.MUTED);
        editor.setTextSize(22f);
        editor.setPrivateImeOptions("horizontalAlignment=center,fullWidthKeyboard");
        editor.setPadding(TvUi.dp(this, 18), 0, TvUi.dp(this, 18), 0);
        editor.setBackground(
                TvUi.rounded(
                        TvUi.NORMAL,
                        TvUi.dp(this, 16),
                        TvUi.FOCUSED,
                        TvUi.dp(this, 3)));

        int type = android.text.InputType.TYPE_CLASS_TEXT;
        String normalized = inputType == null ? "" : inputType.toLowerCase();
        if ("url".equals(normalized)) {
            type |= android.text.InputType.TYPE_TEXT_VARIATION_URI;
            editor.setImeOptions(EditorInfo.IME_ACTION_GO);
        } else if ("email".equals(normalized)) {
            type |= android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
            editor.setImeOptions(EditorInfo.IME_ACTION_DONE);
        } else {
            type |= android.text.InputType.TYPE_TEXT_VARIATION_NORMAL;
            editor.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        }
        editor.setInputType(type);

        LinearLayout.LayoutParams editorParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        TvUi.dp(this, 68));
        root.addView(editor, editorParams);

        diagnostics = TvUi.title(this, "", 12);
        diagnostics.setTextColor(TvUi.MUTED);
        diagnostics.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams diagnosticsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        diagnosticsParams.setMargins(0, TvUi.dp(this, 12), 0, 0);
        root.addView(diagnostics, diagnosticsParams);
        updateDiagnostics("waiting for D-pad");

        editor.setOnEditorActionListener(
                (v, actionId, event) -> {
                    boolean submit =
                            actionId == EditorInfo.IME_ACTION_SEARCH
                                    || actionId == EditorInfo.IME_ACTION_GO
                                    || actionId == EditorInfo.IME_ACTION_DONE
                                    || (event != null
                                            && event.getAction() == KeyEvent.ACTION_UP
                                            && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
                    if (!submit) return false;
                    finishWithResult(true);
                    return true;
                });

        setContentView(root);
        editor.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureImeConnected();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) ensureImeConnected();
    }

    private void ensureImeConnected() {
        if (editor == null) return;
        editor.requestFocus();
        editor.postDelayed(
                () -> {
                    if (isFinishing() || editor == null || !editor.hasWindowFocus()) return;
                    InputMethodManager imm =
                            (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.restartInput(editor);
                        imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
                    }
                    updateDiagnostics("IME reconnect requested");
                },
                180);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_LEFT
                || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP
                || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER) {
            String action =
                    event.getAction() == KeyEvent.ACTION_DOWN ? "DOWN"
                            : (event.getAction() == KeyEvent.ACTION_UP ? "UP" : "OTHER");
            updateDiagnostics(
                    KeyEvent.keyCodeToString(code)
                            + " "
                            + action
                            + "  source=0x"
                            + Integer.toHexString(event.getSource())
                            + "  device="
                            + event.getDeviceId()
                            + "  scan="
                            + event.getScanCode());
        }
        return super.dispatchKeyEvent(event);
    }

    private void updateDiagnostics(String eventLine) {
        if (diagnostics == null) return;

        String ime =
                Settings.Secure.getString(
                        getContentResolver(),
                        Settings.Secure.DEFAULT_INPUT_METHOD);
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        boolean active = imm != null && editor != null && imm.isActive(editor);
        boolean accepting = imm != null && imm.isAcceptingText();

        diagnostics.setText(
                "IME: "
                        + (ime == null ? "unknown" : ime)
                        + "\nactive="
                        + active
                        + " acceptingText="
                        + accepting
                        + "\n"
                        + eventLine);
    }

    private void finishWithResult(boolean submit) {
        if (finishedWithResult) return;
        finishedWithResult = true;

        Intent result = new Intent();
        result.putExtra(EXTRA_TOKEN, getIntent().getStringExtra(EXTRA_TOKEN));
        result.putExtra(EXTRA_VALUE, editor == null ? "" : editor.getText().toString());
        result.putExtra(EXTRA_SUBMIT, submit);
        setResult(RESULT_OK, result);

        if (editor != null) {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        }

        finish();
    }

    @Override
    public void onBackPressed() {
        finishWithResult(false);
    }
}
