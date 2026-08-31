/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;

import org.chromium.chrome.browser.ChromeTabbedActivity;

/** Chrome/Brave tabbed activity with a TV-first input and browser-control layer. */
public final class TvBraveActivity extends ChromeTabbedActivity
        implements TvControlPanel.Callback, TvBrowserBar.Callback, TvTabPanel.Callback {
    private static final float CURSOR_STEP_DP = 32.0f;
    private static final int CURSOR_SIZE_DP = 28;
    private static final int CURSOR_MARGIN_DP = 8;
    private static final int KEY_BACK = 4;
    private static final int KEY_FORWARD = 125;
    private static final int KEY_R = 46;
    private static final int KEY_TAB = 61;
    private static final int KEY_T = 48;
    private static final int KEY_W = 51;
    private static final int META_SHIFT = 1;

    private TvNavigationMode mNavigationMode = TvNavigationMode.DPAD;
    private TvCursorState mCursorState;
    private TvCursorOverlay mCursorOverlay;
    private TvBrowserBar mTvBrowserBar;
    private ViewGroup mRoot;
    private boolean mSelectLongPressConsumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isTelevision()) return;

        mRoot = (ViewGroup) getWindow().getDecorView();
        mRoot.post(
                () -> {
                    initializeCursor();
                    installTvBrowserBar();
                });
        mRoot.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (mCursorState == null) return;
                    int oldWidth = oldRight - oldLeft;
                    int oldHeight = oldBottom - oldTop;
                    int width = right - left;
                    int height = bottom - top;
                    mCursorState.resize(width, height);
                    if (Math.abs(width - oldWidth) > width / 3
                            || Math.abs(height - oldHeight) > height / 3) {
                        mCursorState.center();
                    }
                    updateCursorOverlay();
                });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!isTelevision()) return super.dispatchKeyEvent(event);

        if (isControlsShortcut(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP) showTvControls();
            return true;
        }

        if (isSelectKey(event.getKeyCode())
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() > 0) {
            mSelectLongPressConsumed = true;
            focusTvBrowserBar();
            return true;
        }
        if (isSelectKey(event.getKeyCode())
                && event.getAction() == KeyEvent.ACTION_UP
                && mSelectLongPressConsumed) {
            mSelectLongPressConsumed = false;
            return true;
        }

        if (mNavigationMode == TvNavigationMode.CURSOR) {
            int keyCode = event.getKeyCode();
            if (isDirectionKey(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) moveCursorForKey(keyCode);
                return true;
            }
            if (isSelectKey(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_UP && mCursorState != null && mRoot != null) {
                    TvMouseDispatcher.primaryClick(mRoot, mCursorState.x(), mCursorState.y());
                }
                return true;
            }
        }

        // In DPAD mode, arrows/select deliberately continue into Chromium. Blink spatial
        // navigation is enabled by the startup integration applied by this project.
        return super.dispatchKeyEvent(event);
    }

    @Override
    public TvNavigationMode navigationMode() {
        return mNavigationMode;
    }

    @Override
    public void setNavigationMode(TvNavigationMode mode) {
        mNavigationMode = mode == null ? TvNavigationMode.DPAD : mode;
        if (mCursorOverlay != null) {
            mCursorOverlay.setVisibility(
                    mNavigationMode == TvNavigationMode.CURSOR ? View.VISIBLE : View.GONE);
        }
        if (mNavigationMode == TvNavigationMode.CURSOR) {
            updateCursorOverlay();
        }
        if (mTvBrowserBar != null) {
            mTvBrowserBar.refreshMode(mNavigationMode);
        }
    }

    @Override
    public void focusAddressBar() {
        dispatchShortcut(KeyEvent.KEYCODE_L, KeyEvent.META_CTRL_ON);
    }

    @Override
    public void centerCursor() {
        if (mCursorState == null) return;
        mCursorState.center();
        updateCursorOverlay();
    }

    public void goBack() {
        dispatchShortcut(KEY_BACK, 0);
    }

    public void goForward() {
        dispatchShortcut(KEY_FORWARD, 0);
    }

    public void reloadPage() {
        dispatchShortcut(KEY_R, KeyEvent.META_CTRL_ON);
    }

    public void previousTab() {
        dispatchShortcut(KEY_TAB, KeyEvent.META_CTRL_ON | META_SHIFT);
    }

    public void nextTab() {
        dispatchShortcut(KEY_TAB, KeyEvent.META_CTRL_ON);
    }

    public void newTab() {
        dispatchShortcut(KEY_T, KeyEvent.META_CTRL_ON);
    }

    public void closeCurrentTab() {
        dispatchShortcut(KEY_W, KeyEvent.META_CTRL_ON);
    }

    @Override
    public void showTabs() {
        TvTabPanel.show(this, this);
    }

    public void showTvControls() {
        TvControlPanel.show(this, this);
    }

    private void installTvBrowserBar() {
        if (mTvBrowserBar != null) return;
        mTvBrowserBar = new TvBrowserBar(this, this);
        addContentView(
                mTvBrowserBar,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mTvBrowserBar.refreshMode(mNavigationMode);
    }

    private void focusTvBrowserBar() {
        if (mTvBrowserBar == null) {
            installTvBrowserBar();
        }
        if (mTvBrowserBar != null) {
            mTvBrowserBar.focusPrimaryAction();
        }
    }

    private void initializeCursor() {
        if (mRoot == null || mCursorState != null) return;
        float density = getResources().getDisplayMetrics().density;
        mCursorState =
                new TvCursorState(
                        mRoot.getWidth(), mRoot.getHeight(), CURSOR_MARGIN_DP * density);
        mCursorOverlay = new TvCursorOverlay(this);
        int size = Math.round(CURSOR_SIZE_DP * density);
        mCursorOverlay.layout(0, 0, size, size);
        mCursorOverlay.setVisibility(View.GONE);
        ViewGroupOverlay overlay = mRoot.getOverlay();
        overlay.add(mCursorOverlay);
        updateCursorOverlay();
    }

    private void moveCursorForKey(int keyCode) {
        if (mCursorState == null) return;
        float step = CURSOR_STEP_DP * getResources().getDisplayMetrics().density;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                mCursorState.move(-step, 0);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mCursorState.move(step, 0);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                mCursorState.move(0, -step);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                mCursorState.move(0, step);
                break;
            default:
                return;
        }
        updateCursorOverlay();
        if (mRoot != null) {
            TvMouseDispatcher.hover(mRoot, mCursorState.x(), mCursorState.y());
        }
    }

    private void updateCursorOverlay() {
        if (mCursorState == null || mCursorOverlay == null) return;
        float halfWidth = mCursorOverlay.getWidth() / 2.0f;
        float halfHeight = mCursorOverlay.getHeight() / 2.0f;
        mCursorOverlay.setTranslationX(mCursorState.x() - halfWidth);
        mCursorOverlay.setTranslationY(mCursorState.y() - halfHeight);
        mCursorOverlay.invalidate();
    }

    private void dispatchShortcut(int keyCode, int metaState) {
        if (mRoot == null) return;
        mRoot.post(
                () -> {
                    long now = SystemClock.uptimeMillis();
                    dispatchToBrowser(
                            new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState));
                    dispatchToBrowser(
                            new KeyEvent(
                                    now,
                                    SystemClock.uptimeMillis(),
                                    KeyEvent.ACTION_UP,
                                    keyCode,
                                    0,
                                    metaState));
                });
    }

    private boolean dispatchToBrowser(KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    private boolean isTelevision() {
        UiModeManager manager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        return manager != null
                && manager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static boolean isDirectionKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private static boolean isSelectKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT;
    }

    private static boolean isControlsShortcut(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_INFO
                || keyCode == KeyEvent.KEYCODE_GUIDE) {
            return true;
        }
        return keyCode == KeyEvent.KEYCODE_M
                && event.isCtrlPressed()
                && event.isShiftPressed();
    }
}
