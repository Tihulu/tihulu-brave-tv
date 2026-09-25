package org.chromium.chrome.browser;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import org.chromium.chrome.browser.fullscreen.FullscreenManager;

public class ChromeTabbedActivity extends Context {
    private final FullscreenManager fullscreenManager = new FullscreenManager();

    protected final void onCreate(Bundle b) {}

    public void performPostInflationStartup() {}

    public void onDestroyInternal() {}

    public FullscreenManager getFullscreenManager() {
        return fullscreenManager;
    }

    public org.chromium.chrome.browser.tab.Tab activeTab;
    public boolean tabModelsReady;
    public int lastMenuAction;
    public org.chromium.chrome.browser.tab.Tab getActivityTab(){return activeTab;}
    public org.chromium.chrome.browser.tabmodel.TabModelSelector getTabModelSelector(){return new org.chromium.chrome.browser.tabmodel.TabModelSelector();}
    public boolean areTabModelsInitialized(){return tabModelsReady;}
    public boolean onMenuOrKeyboardAction(int id, boolean fromMenu, Bundle data, Object motion){lastMenuAction=id;return true;}
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
