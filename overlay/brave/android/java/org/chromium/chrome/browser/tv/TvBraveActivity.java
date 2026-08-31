/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.app.Dialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.widget.Toast;

import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.chrome.browser.fullscreen.FullscreenOptions;
import org.chromium.chrome.browser.tab.Tab;

/** Chrome/Brave tabbed activity with a TV-first input and browser-control layer. */
public final class TvBraveActivity extends ChromeTabbedActivity
        implements TvControlPanel.Callback, TvBrowserBar.Callback, TvTabPanel.Callback {
    private static final float CURSOR_STEP_DP = 24.0f;
    private static final float CURSOR_REPEAT_ACCELERATION = 0.18f;
    private static final int CURSOR_MAX_ACCEL_REPEAT = 8;
    private static final int CURSOR_SIZE_DP = 28;
    private static final int CURSOR_MARGIN_DP = 8;
    private static final int KEY_BACK = 4;
    private static final int KEY_FORWARD = 125;
    private static final int KEY_R = 46;
    private static final int KEY_TAB = 61;
    private static final int KEY_T = 48;
    private static final int KEY_W = 51;
    private static final int META_SHIFT = 1;

    private final FullscreenManager.Observer mFullscreenObserver =
            new FullscreenManager.Observer() {
                @Override
                public void onEnterFullscreen(Tab tab, FullscreenOptions options) {
                    setTvFullscreenState(true);
                }

                @Override
                public void onExitFullscreen(Tab tab) {
                    setTvFullscreenState(false);
                }
            };

    private TvNavigationMode mNavigationMode = TvNavigationMode.DPAD;
    private TvCursorState mCursorState;
    private TvCursorOverlay mCursorOverlay;
    private Dialog mBrowserBarDialog;
    private ViewGroup mRoot;
    private boolean mSelectLongPressConsumed;
    private boolean mUpLongPressConsumed;
    private boolean mTvUiInitialized;
    private boolean mFullscreenObserverRegistered;
    private boolean mCursorLayoutListenerInstalled;
    private boolean mHtmlFullscreen;

    /**
     * Chromium owns startup. Keep the TV hook deliberately inert here: no added views, no
     * listeners, no dialogs and no fullscreen-manager access. Low-memory TV boxes can spend
     * several seconds in Chromium/Brave startup work; adding our UI to that critical path caused
     * focus-event ANRs on real Android TV hardware. Everything Tihulu-specific is lazy and starts
     * only after the user deliberately invokes a TV feature.
     */
    @Override
    public void performPostInflationStartup() {
        super.performPostInflationStartup();
        if (mTvUiInitialized || isFinishing() || !isTelevision()) return;
        mTvUiInitialized = true;
        mRoot = (ViewGroup) getWindow().getDecorView();
    }

    @Override
    public void onDestroyInternal() {
        dismissBrowserBar();
        if (mFullscreenObserverRegistered) {
            getFullscreenManager().removeObserver(mFullscreenObserver);
            mFullscreenObserverRegistered = false;
        }
        super.onDestroyInternal();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!isTelevision()) return super.dispatchKeyEvent(event);
        if (mHtmlFullscreen) return super.dispatchKeyEvent(event);

        // MENU/INFO/GUIDE is a direct top-bar toggle when the remote provides one. Resolve
        // Chromium fullscreen state only for this deliberate TV-chrome action, never for normal
        // page D-pad events.
        if (isControlsShortcut(event)) {
            ensureFullscreenObserverRegistered();
            if (mHtmlFullscreen) return super.dispatchKeyEvent(event);
            if (event.getAction() == KeyEvent.ACTION_UP) toggleBrowserBar();
            return true;
        }

        // A six-key remote must always be able to reach browser chrome. Hold UP from the page to
        // open the top command bar; DPAD_DOWN or Back from the dialog returns to page content.
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() > 0
                && !mUpLongPressConsumed) {
            mUpLongPressConsumed = true;
            showBrowserBar();
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && event.getAction() == KeyEvent.ACTION_DOWN
                && mUpLongPressConsumed) {
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && event.getAction() == KeyEvent.ACTION_UP
                && mUpLongPressConsumed) {
            mUpLongPressConsumed = false;
            return true;
        }

        // Match common TV-browser behaviour: long OK toggles page navigation mode. Short OK
        // remains a normal page activation/click. Only the first repeated ACTION_DOWN toggles;
        // later repeats are consumed until key-up so one hold cannot oscillate the mode.
        if (isSelectKey(event.getKeyCode())
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() > 0
                && !mSelectLongPressConsumed) {
            mSelectLongPressConsumed = true;
            toggleNavigationMode();
            return true;
        }
        if (isSelectKey(event.getKeyCode())
                && event.getAction() == KeyEvent.ACTION_DOWN
                && mSelectLongPressConsumed) {
            return true;
        }
        if (isSelectKey(event.getKeyCode())
                && event.getAction() == KeyEvent.ACTION_UP
                && mSelectLongPressConsumed) {
            mSelectLongPressConsumed = false;
            return true;
        }

        if (mNavigationMode == TvNavigationMode.CURSOR) {
            ensureCursorInitialized();
            int keyCode = event.getKeyCode();
            if (isDirectionKey(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && isCursorAtTopEdge()) {
                        showBrowserBar();
                    } else {
                        moveCursorForKey(keyCode, event.getRepeatCount());
                    }
                }
                return true;
            }
            if (isSelectKey(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_UP && mCursorState != null && mRoot != null) {
                    TvMouseDispatcher.primaryClick(mRoot, mCursorState.x(), mCursorState.y());
                }
                return true;
            }
        }

        // Plain D-pad navigation remains Chromium-native. No Tihulu view creation, fullscreen
        // manager access or Toast is inserted into this hot path.
        return super.dispatchKeyEvent(event);
    }

    @Override
    public TvNavigationMode navigationMode() {
        return mNavigationMode;
    }

    @Override
    public void setNavigationMode(TvNavigationMode mode) {
        mNavigationMode = mode == null ? TvNavigationMode.DPAD : mode;
        if (mNavigationMode == TvNavigationMode.CURSOR) ensureCursorInitialized();
        refreshTvOverlayVisibility();
        if (!mHtmlFullscreen && mNavigationMode == TvNavigationMode.CURSOR) updateCursorOverlay();
    }

    @Override
    public void toggleNavigationMode() {
        setNavigationMode(mNavigationMode.toggle());
        Toast.makeText(
                        this,
                        mNavigationMode == TvNavigationMode.CURSOR
                                ? "Cursor mode · D-pad moves pointer · OK clicks · Hold ↑ for bar"
                                : "D-pad mode · arrows move page focus · OK activates · Hold ↑ for bar",
                        Toast.LENGTH_LONG)
                .show();
    }

    @Override
    public void focusAddressBar() {
        if (mHtmlFullscreen) return;
        dispatchShortcut(KeyEvent.KEYCODE_L, KeyEvent.META_CTRL_ON);
    }

    @Override
    public void centerCursor() {
        if (mHtmlFullscreen) return;
        ensureCursorInitialized();
        if (mCursorState == null) return;
        mCursorState.center();
        updateCursorOverlay();
    }

    @Override
    public void checkForUpdates() {
        if (mRoot == null || mHtmlFullscreen) return;
        TvGitHubUpdater.checkAndInstall(this, mRoot);
    }

    public void checkBraveUpstream() {
        if (mRoot == null || mHtmlFullscreen) return;
        TvBraveUpstream.check(this, mRoot);
    }

    @Override
    public void showAbout() {
        if (mHtmlFullscreen) return;
        dismissBrowserBar();
        TvAboutPanel.show(this, this::checkForUpdates, this::checkBraveUpstream);
    }

    @Override
    public void goBack() {
        dispatchShortcut(KEY_BACK, 0);
    }

    @Override
    public void goForward() {
        dispatchShortcut(KEY_FORWARD, 0);
    }

    @Override
    public void reloadPage() {
        dispatchShortcut(KEY_R, KeyEvent.META_CTRL_ON);
    }

    @Override
    public void previousTab() {
        dispatchShortcut(KEY_TAB, KeyEvent.META_CTRL_ON | META_SHIFT);
    }

    @Override
    public void nextTab() {
        dispatchShortcut(KEY_TAB, KeyEvent.META_CTRL_ON);
    }

    @Override
    public void newTab() {
        dispatchShortcut(KEY_T, KeyEvent.META_CTRL_ON);
    }

    @Override
    public void closeCurrentTab() {
        dispatchShortcut(KEY_W, KeyEvent.META_CTRL_ON);
    }

    @Override
    public void showTabs() {
        if (mHtmlFullscreen) return;
        dismissBrowserBar();
        TvTabPanel.show(this, this);
    }

    @Override
    public void showTvControls() {
        if (mHtmlFullscreen || isFinishing()) return;
        dismissBrowserBar();
        TvControlPanel.show(this, this);
    }

    private void showBrowserBar() {
        ensureFullscreenObserverRegistered();
        if (mHtmlFullscreen || isFinishing()) return;
        if (mBrowserBarDialog != null && mBrowserBarDialog.isShowing()) return;
        mBrowserBarDialog = TvBrowserBar.show(this, this);
        mBrowserBarDialog.setOnDismissListener(ignored -> mBrowserBarDialog = null);
    }

    private void toggleBrowserBar() {
        if (mBrowserBarDialog != null && mBrowserBarDialog.isShowing()) {
            dismissBrowserBar();
        } else {
            showBrowserBar();
        }
    }

    private void dismissBrowserBar() {
        if (mBrowserBarDialog == null) return;
        Dialog dialog = mBrowserBarDialog;
        mBrowserBarDialog = null;
        if (dialog.isShowing()) dialog.dismiss();
    }

    private void ensureFullscreenObserverRegistered() {
        if (mFullscreenObserverRegistered || !mTvUiInitialized || isFinishing()) return;
        FullscreenManager fullscreenManager = getFullscreenManager();
        fullscreenManager.addObserver(mFullscreenObserver);
        mFullscreenObserverRegistered = true;
        setTvFullscreenState(fullscreenManager.getPersistentFullscreenMode());
    }

    private void setTvFullscreenState(boolean fullscreen) {
        mHtmlFullscreen = fullscreen;
        if (fullscreen) {
            mSelectLongPressConsumed = false;
            mUpLongPressConsumed = false;
            dismissBrowserBar();
        }
        refreshTvOverlayVisibility();
    }

    private void refreshTvOverlayVisibility() {
        if (mCursorOverlay != null) {
            boolean showCursor = !mHtmlFullscreen && mNavigationMode == TvNavigationMode.CURSOR;
            mCursorOverlay.setVisibility(showCursor ? View.VISIBLE : View.GONE);
        }
    }

    private void ensureCursorInitialized() {
        if (mRoot == null || mCursorState != null) return;
        ensureFullscreenObserverRegistered();

        float density = getResources().getDisplayMetrics().density;
        mCursorState =
                new TvCursorState(
                        mRoot.getWidth(), mRoot.getHeight(), CURSOR_MARGIN_DP * density);
        mCursorOverlay = new TvCursorOverlay(this);
        int size = Math.round(CURSOR_SIZE_DP * density);
        mCursorOverlay.layout(0, 0, size, size);
        ViewGroupOverlay overlay = mRoot.getOverlay();
        overlay.add(mCursorOverlay);
        installCursorLayoutListener();
        refreshTvOverlayVisibility();
        updateCursorOverlay();
    }

    private void installCursorLayoutListener() {
        if (mRoot == null || mCursorLayoutListenerInstalled) return;
        mCursorLayoutListenerInstalled = true;
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

    private boolean isCursorAtTopEdge() {
        if (mCursorState == null) return false;
        float density = getResources().getDisplayMetrics().density;
        float threshold = (CURSOR_MARGIN_DP + CURSOR_STEP_DP * 0.5f) * density;
        return mCursorState.y() <= threshold;
    }

    private void moveCursorForKey(int keyCode, int repeatCount) {
        if (mCursorState == null) return;
        int boundedRepeat = Math.min(Math.max(repeatCount, 0), CURSOR_MAX_ACCEL_REPEAT);
        float multiplier = 1.0f + boundedRepeat * CURSOR_REPEAT_ACCELERATION;
        float step = CURSOR_STEP_DP * multiplier * getResources().getDisplayMetrics().density;
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
        if (mRoot != null) TvMouseDispatcher.hover(mRoot, mCursorState.x(), mCursorState.y());
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
