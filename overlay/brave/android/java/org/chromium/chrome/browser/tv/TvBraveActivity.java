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

import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.chrome.browser.fullscreen.FullscreenOptions;
import org.chromium.chrome.browser.tab.Tab;

/** Chrome/Brave tabbed activity with a TV-first input and browser-control layer. */
public final class TvBraveActivity extends ChromeTabbedActivity
        implements TvControlPanel.Callback, TvBrowserBar.Callback, TvTabPanel.Callback {
    private static final float CURSOR_STEP_DP = 24.0f;
    private static final float CURSOR_REPEAT_ACCELERATION = 0.16f;
    private static final int CURSOR_MAX_ACCEL_REPEAT = 6;
    private static final int CURSOR_SIZE_DP = 28;
    private static final int CURSOR_MARGIN_DP = 8;
    private static final int DPAD_REPEAT_DIVISOR = 3;
    private static final long CURSOR_HOVER_MIN_INTERVAL_MS = 50L;
    private static final long SELECT_LONG_PRESS_MS = 550L;
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

    private final Runnable mSelectLongPressRunnable = this::handleSelectLongPress;
    private final Runnable mCursorHoverRunnable = this::runScheduledCursorHover;

    private TvNavigationMode mNavigationMode = TvNavigationMode.DPAD;
    private TvCursorState mCursorState;
    private TvCursorOverlay mCursorOverlay;
    private Dialog mBrowserBarDialog;
    private ViewGroup mRoot;
    private KeyEvent mPendingSelectDownEvent;
    private boolean mSelectLongPressConsumed;
    private boolean mUpLongPressConsumed;
    private boolean mTvUiInitialized;
    private boolean mTvRuntimeEnabled;
    private boolean mFullscreenObserverRegistered;
    private boolean mCursorLayoutListenerInstalled;
    private boolean mCursorHoverPosted;
    private boolean mHtmlFullscreen;
    private long mLastCursorHoverUptimeMs;
    private int[] mRootLocationInWindow;
    private int[] mPointerTargetLocationInWindow;
    private float mPointerTargetX;
    private float mPointerTargetY;

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
        if (mTvUiInitialized || isFinishing()) return;
        mTvUiInitialized = true;
        mTvRuntimeEnabled = isTelevision();
        if (!mTvRuntimeEnabled) return;
        mRoot = (ViewGroup) getWindow().getDecorView();
    }

    @Override
    public void onDestroyInternal() {
        cancelSelectTracking();
        cancelCursorHover();
        dismissBrowserBar();
        if (mFullscreenObserverRegistered) {
            getFullscreenManager().removeObserver(mFullscreenObserver);
            mFullscreenObserverRegistered = false;
        }
        super.onDestroyInternal();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Do not call UiModeManager for every remote event. The TV decision is cached once after
        // Chromium finishes inflating the activity.
        if (!mTvRuntimeEnabled) return super.dispatchKeyEvent(event);
        if (mHtmlFullscreen) return super.dispatchKeyEvent(event);

        // MENU/INFO/GUIDE is a direct top-bar toggle when the remote provides one. Defer Dialog
        // creation until the current key dispatch has completed to avoid re-entrant UI work.
        if (isControlsShortcut(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP) postToggleBrowserBar();
            return true;
        }

        // Hold UP to request browser chrome. We only mark the hold while repeat events are arriving;
        // the actual Dialog is opened after key-up. This keeps expensive UI creation out of the
        // key-repeat storm. The real ACTION_UP is still forwarded to Chromium so its initial
        // ACTION_DOWN cannot remain logically stuck.
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() > 0) {
            mUpLongPressConsumed = true;
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                && event.getAction() == KeyEvent.ACTION_UP
                && mUpLongPressConsumed) {
            mUpLongPressConsumed = false;
            super.dispatchKeyEvent(event);
            postShowBrowserBar();
            return true;
        }

        // OK is the only guaranteed spare control on a six-key remote. Delay only this key until
        // release so a long hold can switch D-pad/Cursor exactly once without also activating the
        // focused page element. Fullscreen bypasses this entire TV layer above, so video players
        // still receive their native D-pad/OK stream while fullscreen.
        if (isSelectKey(event.getKeyCode()) && handleSelectKeyEvent(event)) return true;

        if (mNavigationMode == TvNavigationMode.CURSOR) {
            ensureCursorInitialized();
            int keyCode = event.getKeyCode();
            if (isDirectionKey(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && isCursorAtTopEdge()) {
                        postShowBrowserBar();
                    } else {
                        moveCursorForKey(keyCode, event.getRepeatCount());
                    }
                }
                return true;
            }
        }

        // Chromium's spatial-navigation search can be relatively expensive on large modern pages,
        // especially in a 32-bit 2 GB process. Let the first D-pad press through immediately, but
        // thin Android's high-rate repeat stream instead of asking Blink to recompute focus dozens
        // of times per second. ACTION_UP is never throttled.
        if (mNavigationMode == TvNavigationMode.DPAD
                && isDirectionKey(event.getKeyCode())
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() > 0
                && event.getKeyCode() != KeyEvent.KEYCODE_DPAD_UP
                && event.getRepeatCount() % DPAD_REPEAT_DIVISOR != 0) {
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public TvNavigationMode navigationMode() {
        return mNavigationMode;
    }

    @Override
    public void setNavigationMode(TvNavigationMode mode) {
        mNavigationMode = mode == null ? TvNavigationMode.DPAD : mode;
        if (mNavigationMode == TvNavigationMode.CURSOR) {
            ensureCursorInitialized();
        } else {
            cancelCursorHover();
        }
        refreshTvOverlayVisibility();
        if (!mHtmlFullscreen && mNavigationMode == TvNavigationMode.CURSOR) {
            updateCursorOverlay();
            scheduleCursorHover();
        }
    }

    @Override
    public void toggleNavigationMode() {
        setNavigationMode(mNavigationMode.toggle());
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
        scheduleCursorHover();
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

    private boolean handleSelectKeyEvent(KeyEvent event) {
        if (mRoot == null) return false;

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() > 0) {
                return mPendingSelectDownEvent != null;
            }
            cancelSelectTracking();
            mPendingSelectDownEvent = new KeyEvent(event);
            mSelectLongPressConsumed = false;
            mRoot.postDelayed(mSelectLongPressRunnable, SELECT_LONG_PRESS_MS);
            return true;
        }

        if (event.getAction() != KeyEvent.ACTION_UP || mPendingSelectDownEvent == null) {
            return false;
        }

        mRoot.removeCallbacks(mSelectLongPressRunnable);
        KeyEvent downEvent = mPendingSelectDownEvent;
        mPendingSelectDownEvent = null;
        boolean wasLongPress = mSelectLongPressConsumed;
        mSelectLongPressConsumed = false;
        if (wasLongPress) return true;

        if (mNavigationMode == TvNavigationMode.CURSOR) {
            performCursorClick();
            return true;
        }

        dispatchToBrowser(downEvent);
        dispatchToBrowser(event);
        return true;
    }

    private void handleSelectLongPress() {
        if (mPendingSelectDownEvent == null || mSelectLongPressConsumed || mHtmlFullscreen) return;
        mSelectLongPressConsumed = true;
        toggleNavigationMode();
    }

    private void cancelSelectTracking() {
        if (mRoot != null) mRoot.removeCallbacks(mSelectLongPressRunnable);
        mPendingSelectDownEvent = null;
        mSelectLongPressConsumed = false;
    }

    private void postShowBrowserBar() {
        if (mRoot != null) mRoot.post(this::showBrowserBar);
    }

    private void postToggleBrowserBar() {
        if (mRoot != null) mRoot.post(this::toggleBrowserBar);
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
        if (mFullscreenObserverRegistered || !mTvRuntimeEnabled || isFinishing()) return;
        FullscreenManager fullscreenManager = getFullscreenManager();
        fullscreenManager.addObserver(mFullscreenObserver);
        mFullscreenObserverRegistered = true;
        setTvFullscreenState(fullscreenManager.getPersistentFullscreenMode());
    }

    private void setTvFullscreenState(boolean fullscreen) {
        mHtmlFullscreen = fullscreen;
        if (fullscreen) {
            mUpLongPressConsumed = false;
            cancelSelectTracking();
            cancelCursorHover();
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
                    scheduleCursorHover();
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
        scheduleCursorHover();
    }

    private void updateCursorOverlay() {
        if (mCursorState == null || mCursorOverlay == null) return;
        float halfWidth = mCursorOverlay.getWidth() / 2.0f;
        float halfHeight = mCursorOverlay.getHeight() / 2.0f;
        mCursorOverlay.setTranslationX(mCursorState.x() - halfWidth);
        mCursorOverlay.setTranslationY(mCursorState.y() - halfHeight);
        mCursorOverlay.invalidate();
    }

    /**
     * Chromium/Blink needs real mouse hover to reveal HTML5 player controls, but sending one event
     * for every Android key-repeat caused unnecessary hit-testing and style work. Coalesce to at
     * most 20 Hz while preserving the latest pointer position.
     */
    private void scheduleCursorHover() {
        if (mRoot == null
                || mCursorState == null
                || mHtmlFullscreen
                || mNavigationMode != TvNavigationMode.CURSOR) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long elapsed = now - mLastCursorHoverUptimeMs;
        if (elapsed >= CURSOR_HOVER_MIN_INTERVAL_MS) {
            cancelCursorHover();
            dispatchCursorHover();
            return;
        }
        if (mCursorHoverPosted) return;
        mCursorHoverPosted = true;
        mRoot.postDelayed(mCursorHoverRunnable, CURSOR_HOVER_MIN_INTERVAL_MS - elapsed);
    }

    private void runScheduledCursorHover() {
        mCursorHoverPosted = false;
        if (mHtmlFullscreen || mNavigationMode != TvNavigationMode.CURSOR) return;
        dispatchCursorHover();
    }

    private void cancelCursorHover() {
        if (mRoot != null && mCursorHoverPosted) mRoot.removeCallbacks(mCursorHoverRunnable);
        mCursorHoverPosted = false;
    }

    private void dispatchCursorHover() {
        mLastCursorHoverUptimeMs = SystemClock.uptimeMillis();
        View target = getPointerTarget();
        if (target == null || !mapCursorToTarget(target)) return;
        TvMouseDispatcher.hover(target, mPointerTargetX, mPointerTargetY);
    }

    private void performCursorClick() {
        if (mCursorState == null) ensureCursorInitialized();
        View target = getPointerTarget();
        if (target == null || !mapCursorToTarget(target)) return;
        cancelCursorHover();
        TvMouseDispatcher.primaryClick(target, mPointerTargetX, mPointerTargetY);
        mLastCursorHoverUptimeMs = SystemClock.uptimeMillis();
    }

    private View getPointerTarget() {
        Tab tab = getActivityTab();
        if (tab == null) return null;
        View contentView = tab.getContentView();
        return contentView != null ? contentView : tab.getView();
    }

    /** Converts the visual cursor's DecorView coordinates into active-content local coordinates. */
    private boolean mapCursorToTarget(View target) {
        if (mRoot == null || mCursorState == null || target.getWidth() <= 0 || target.getHeight() <= 0) {
            return false;
        }
        if (mRootLocationInWindow == null) {
            mRootLocationInWindow = new int[2];
            mPointerTargetLocationInWindow = new int[2];
        }
        mRoot.getLocationInWindow(mRootLocationInWindow);
        target.getLocationInWindow(mPointerTargetLocationInWindow);
        mPointerTargetX =
                mCursorState.x() + mRootLocationInWindow[0] - mPointerTargetLocationInWindow[0];
        mPointerTargetY =
                mCursorState.y() + mRootLocationInWindow[1] - mPointerTargetLocationInWindow[1];
        return mPointerTargetX >= 0
                && mPointerTargetY >= 0
                && mPointerTargetX < target.getWidth()
                && mPointerTargetY < target.getHeight();
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
