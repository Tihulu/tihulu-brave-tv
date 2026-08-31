package org.chromium.chrome.browser;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

public class ChromeTabbedActivity extends Context {
    protected final void onCreate(Bundle b) {}

    public void performPostInflationStartup() {}

    public boolean isFinishing() {
        return false;
    }

    public boolean dispatchKeyEvent(KeyEvent e) {
        return false;
    }

    public void addContentView(View v, ViewGroup.LayoutParams p) {}

    public Window getWindow() {
        return new Window() {
            @Override
            public android.view.View getDecorView() {
                return new ViewGroup(ChromeTabbedActivity.this);
            }
        };
    }
}
