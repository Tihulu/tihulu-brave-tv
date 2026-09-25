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
import org.chromium.chrome.R;
import org.chromium.chrome.browser.fullscreen.FullscreenManager;
import org.chromium.chrome.browser.fullscreen.FullscreenOptions;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.search_engines.TemplateUrlServiceFactory;
import org.chromium.components.search_engines.TemplateUrlService;
import org.chromium.components.url_formatter.UrlFormatter;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.url.GURL;

/** Chrome/Brave tabbed activity with a TV-first input and browser-control layer. */
public final class TvBraveActivity extends ChromeTabbedActivity
        implements TvControlPanel.Callback, TvBrowserBar.Callback, TvTabPanel.Callback,
                TvHomePanel.Callback, TvAddressPanel.Callback {
    private static final float CURSOR_STEP_DP = 24.0f;
    private static final float CURSOR_REPEAT_ACCELERATION = 0.16f;
    private static final int CURSOR_MAX_ACCEL_REPEAT = 6;
    private static final int CURSOR_SIZE_DP = 28;
    private static final int CURSOR_MARGIN_DP = 8;
    private static final int DPAD_REPEAT_DIVISOR = 3;
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
    private Dialog mPanelDialog;
    private ViewGroup mRoot;
    private boolean mUpLongPressConsumed;
    private boolean mTvUiInitialized;
    private boolean mTvRuntimeEnabled;
    private boolean mFullscreenObserverRegistered;
    private boolean mCursorLayoutListenerInstalled;
    private boolean mHtmlFullscreen;
    private boolean mNavigationPreferenceLoaded;
    private long mLastScrollTime = -1;
    private long mAddressRequest;
    private boolean mDestroyed;

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
        mDestroyed = true;
        mAddressRequest++;
        dismissBrowserBar();
        dismissPanel();
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
        if (mDestroyed) return super.dispatchKeyEvent(event);
        // Observe fullscreen before the first remote event, even if no TV panel was opened yet.
        ensureFullscreenObserverRegistered();
        restoreNavigationPreference();
        // Let text editing and the TV IME own arrows/OK when the omnibox has focus.
        if (mRoot != null && mRoot.findFocus() instanceof android.widget.EditText) {
            mUpLongPressConsumed = false;
            return super.dispatchKeyEvent(event);
        }
        if (mHtmlFullscreen) return super.dispatchKeyEvent(event);

        // MENU/INFO/GUIDE is a direct top-bar toggle when the remote provides one. Defer Dialog
        // creation until the current key dispatch has completed to avoid re-entrant UI work.
        if (isControlsShortcut(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) postToggleBrowserBar();
            return true;
        }

        if (mNavigationMode == TvNavigationMode.SCROLL) {
            ensureCursorInitialized();
            int keyCode = event.getKeyCode();
            if (isDirectionKey(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    long now = SystemClock.uptimeMillis();
                    if (event.getRepeatCount() == 0 || mLastScrollTime < 0 || now - mLastScrollTime >= 80) {
                        mLastScrollTime = now;
                        float horizontal = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -2 : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 2 : 0;
                        float vertical = keyCode == KeyEvent.KEYCODE_DPAD_UP ? 2 : keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? -2 : 0;
                        if (mCursorState != null && mRoot != null) {
                            TvMouseDispatcher.scroll(mRoot, mCursorState.x(), mCursorState.y(), horizontal, vertical);
                        }
                    }
                }
                return true;
            }
            if (isSelectKey(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) postShowBrowserBar();
                return true;
            }
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
            if (mNavigationMode == TvNavigationMode.DPAD) super.dispatchKeyEvent(event);
            if (!event.isCanceled()) postShowBrowserBar();
            return true;
        }

        if (mNavigationMode == TvNavigationMode.CURSOR) {
            ensureCursorInitialized();
            int keyCode = event.getKeyCode();
            if (isDirectionKey(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && isCursorAtTopEdge()) {
                        // Open only after release, so the new window never receives an orphan
                        // key-up and immediately changes focus or activates a control.
                        mUpLongPressConsumed = true;
                    } else {
                        moveCursorForKey(keyCode, event.getRepeatCount());
                    }
                }
                return true;
            }
            if (isSelectKey(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()
                        && mCursorState != null && mRoot != null) {
                    TvMouseDispatcher.primaryClick(mRoot, mCursorState.x(), mCursorState.y());
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
        restoreNavigationPreference();
        return mNavigationMode;
    }

    @Override
    public void setNavigationMode(TvNavigationMode mode) {
        mNavigationMode = mode == null ? TvNavigationMode.DPAD : mode;
        mNavigationPreferenceLoaded = true;
        getSharedPreferences("tihulu_tv", Context.MODE_PRIVATE).edit()
                .putString("navigation_mode", mNavigationMode.name()).apply();
        mUpLongPressConsumed = false;
        mLastScrollTime = -1;
        if (mNavigationMode != TvNavigationMode.DPAD) ensureCursorInitialized();
        refreshTvOverlayVisibility();
        if (!mHtmlFullscreen && mNavigationMode != TvNavigationMode.DPAD) updateCursorOverlay();
    }

    private void restoreNavigationPreference() {
        if (mNavigationPreferenceLoaded) return;
        mNavigationPreferenceLoaded = true;
        mNavigationMode = TvNavigationMode.fromPreference(
                getSharedPreferences("tihulu_tv", Context.MODE_PRIVATE)
                        .getString("navigation_mode", "DPAD"));
    }

    @Override
    public void toggleNavigationMode() {
        setNavigationMode(navigationMode().toggle());
    }

    @Override
    public void focusAddressBar() {
        if (mHtmlFullscreen || isFinishing() || mDestroyed) return;
        dismissBrowserBar();
        dismissPanel();
        Tab tab = areTabModelsInitialized() ? getActivityTab() : null;
        String url = tab == null || tab.isDestroyed() ? "" : tab.getUrl().getSpec();
        if (!url.startsWith("https://") && !url.startsWith("http://")) url = "";
        mPanelDialog = TvAddressPanel.show(this, url, this);
    }

    @Override
    public boolean openAddress(String input) {
        if (isFinishing() || mDestroyed || mHtmlFullscreen || !areTabModelsInitialized()) return false;
        Tab tab = getActivityTab();
        if (tab == null || tab.isDestroyed()) return false;
        String candidate = TvAddressInput.urlCandidate(input);
        long request = ++mAddressRequest;
        if (!candidate.isEmpty()) {
            GURL url = UrlFormatter.fixupUrl(candidate);
            if (!url.isValid() || (!"https".equals(url.getScheme()) && !"http".equals(url.getScheme()))) {
                throw new IllegalArgumentException("Check the web address and try again.");
            }
            tab.loadUrl(new LoadUrlParams(url.getSpec()));
            return true;
        }
        // Use the active profile's chosen engine, including private mode. Never silently
        // switch providers or navigate another tab if engine loading completes later.
        String originalUrl = tab.getUrl().getSpec();
        TemplateUrlService service = TemplateUrlServiceFactory.getForProfile(tab.getProfile());
        service.runWhenLoaded(() -> {
            if (mDestroyed || isFinishing() || request != mAddressRequest || tab.isDestroyed()
                    || getActivityTab() != tab || !originalUrl.equals(tab.getUrl().getSpec())) return;
            String searchUrl = service.getUrlForSearchQuery(input.trim());
            if (searchUrl == null || searchUrl.isEmpty()) {
                android.widget.Toast.makeText(this, "Choose a default search engine in Brave settings.",
                        android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            tab.loadUrl(new LoadUrlParams(searchUrl));
        });
        return true;
    }

    @Override
    public void showHome() {
        if (mHtmlFullscreen || isFinishing() || mDestroyed) return;
        dismissBrowserBar();
        dismissPanel();
        mPanelDialog = TvHomePanel.show(this, this);
    }

    @Override
    public void showBookmarks() {
        openBrowserSection(R.id.all_bookmarks_menu_id);
    }

    @Override
    public void showDownloads() {
        openBrowserSection(R.id.downloads_menu_id);
    }

    private void openBrowserSection(int id) {
        if (mRoot == null || mHtmlFullscreen || isFinishing() || mDestroyed) return;
        dismissBrowserBar();
        dismissPanel();
        mRoot.post(() -> {
            if (!mDestroyed && !isFinishing() && areTabModelsInitialized()) {
                onMenuOrKeyboardAction(id, false, null, null);
            }
        });
    }

    @Override
    public String pageTitle() {
        Tab tab = areTabModelsInitialized() ? getActivityTab() : null;
        return tab == null || tab.isDestroyed() ? "Tihulu TV Browser" : tab.getTitle();
    }

    @Override
    public String pageOrigin() {
        Tab tab = areTabModelsInitialized() ? getActivityTab() : null;
        if (tab == null || tab.isDestroyed()) return "Ready to explore";
        String url = tab.getUrl().getSpec();
        return url.startsWith("https://") || url.startsWith("http://")
                ? UrlFormatter.formatUrlForSecurityDisplay(url) : "Tihulu TV Browser";
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
        if (mHtmlFullscreen || isFinishing() || mDestroyed) return;
        dismissBrowserBar();
        dismissPanel();
        mPanelDialog = TvAboutPanel.show(this, this::checkForUpdates, this::checkBraveUpstream);
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
        if (mRoot != null) mRoot.post(this::showHome);
    }

    @Override
    public void closeCurrentTab() {
        dispatchShortcut(KEY_W, KeyEvent.META_CTRL_ON);
    }

    @Override
    public TabModel tabModel() {
        return areTabModelsInitialized() ? getTabModelSelector().getCurrentModel() : null;
    }

    @Override
    public void showTabs() {
        if (mHtmlFullscreen || isFinishing() || mDestroyed) return;
        dismissBrowserBar();
        dismissPanel();
        mPanelDialog = TvTabPanel.show(this, this);
    }

    @Override
    public void showTvControls() {
        if (mHtmlFullscreen || isFinishing() || mDestroyed) return;
        dismissBrowserBar();
        dismissPanel();
        mPanelDialog = TvControlPanel.show(this, this);
    }

    @Override
    public void showShields() {
        if (mHtmlFullscreen || isFinishing() || mDestroyed) return;
        dismissBrowserBar();
        dismissPanel();
        mPanelDialog = TvShieldsPanel.show(this, getActivityTab());
    }

    private void dismissPanel() {
        if (mPanelDialog != null) {
            mPanelDialog.dismiss();
            mPanelDialog = null;
        }
    }

    private void postShowBrowserBar() {
        if (mRoot != null) mRoot.post(this::showBrowserBar);
    }

    private void postToggleBrowserBar() {
        if (mRoot != null) mRoot.post(this::toggleBrowserBar);
    }

    private void showBrowserBar() {
        ensureFullscreenObserverRegistered();
        if (mHtmlFullscreen || isFinishing() || mDestroyed) return;
        dismissPanel();
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
        if (mFullscreenObserverRegistered || !mTvRuntimeEnabled || isFinishing() || mDestroyed) return;
        FullscreenManager fullscreenManager = getFullscreenManager();
        if (fullscreenManager == null) return;
        fullscreenManager.addObserver(mFullscreenObserver);
        mFullscreenObserverRegistered = true;
        setTvFullscreenState(fullscreenManager.getPersistentFullscreenMode());
    }

    private void setTvFullscreenState(boolean fullscreen) {
        mHtmlFullscreen = fullscreen;
        if (fullscreen) {
            mUpLongPressConsumed = false;
            dismissBrowserBar();
            dismissPanel();
        }
        refreshTvOverlayVisibility();
    }

    private void refreshTvOverlayVisibility() {
        if (mCursorOverlay != null) {
            boolean showCursor = !mHtmlFullscreen && mNavigationMode != TvNavigationMode.DPAD;
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
        // Moving the visual pointer is enough. Synthetic HOVER_MOVE on every key-repeat made
        // complex pages do unnecessary hit-testing/style work on low-memory TV hardware. Mouse
        // events are generated only when the user actually clicks.
        updateCursorOverlay();
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
                    if (mDestroyed || isFinishing()) return;
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
