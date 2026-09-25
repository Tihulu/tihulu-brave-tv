package com.tihulu.tvlite;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.text.InputType;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

public final class MainActivity extends Activity
        implements BlockingWebViewClient.Listener,
                TvBrowserBar.Callback,
                TvControlPanel.Callback,
                TvTabPanel.Callback {
    private static final String HOME_URL = "https://www.google.com/";
    private static final int DPAD_REPEAT_DIVISOR = 3;
    private static final float CURSOR_STEP_DP = 24f;
    private static final float CURSOR_REPEAT_ACCELERATION = 0.16f;
    private static final int CURSOR_MAX_ACCEL_REPEAT = 6;

    private final ArrayList<TabState> tabs = new ArrayList<>();

    private FrameLayout root;
    private FrameLayout webContainer;
    private WebView webView;
    private CursorOverlay cursorOverlay;
    private TextView status;
    private AdBlockEngine adBlockEngine;

    private NavigationMode navigationMode = NavigationMode.DPAD;
    private Dialog browserBarDialog;
    private boolean upLongPressConsumed;
    private int currentTabIndex;
    private volatile boolean webTextEditing;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private static final class TabState {
        String url;
        String title;

        TabState(String url, String title) {
            this.url = url;
            this.title = title;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        adBlockEngine = new AdBlockEngine(this);
        buildUi();
        configureWebView();
        restoreTabs(savedInstanceState);

        boolean restoredWebView = savedInstanceState != null
                && webView.restoreState(savedInstanceState) != null;
        if (!restoredWebView) {
            webView.loadUrl(currentTab().url);
        }

        cursorOverlay.post(cursorOverlay::center);
        applyNavigationMode();
        updateStatus();
        webView.requestFocus();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        webContainer = new FrameLayout(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        cursorOverlay = new CursorOverlay(this);
        cursorOverlay.setVisibility(View.GONE);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(12f);
        status.setPadding(TvUi.dp(this, 12), TvUi.dp(this, 7),
                TvUi.dp(this, 12), TvUi.dp(this, 7));
        status.setBackground(TvUi.rounded(0xCC18181C, TvUi.dp(this, 18),
                Color.TRANSPARENT, 0));

        FrameLayout.LayoutParams fill = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        webContainer.addView(webView, fill);
        webContainer.addView(cursorOverlay, fill);

        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        statusParams.setMargins(0, 0, TvUi.dp(this, 14), TvUi.dp(this, 14));
        webContainer.addView(status, statusParams);

        root.addView(webContainer, fill);
        setContentView(root);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " TihuluTVLite/0.2");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.addJavascriptInterface(new WebInputBridge(), "__TihuluInput");
        webView.setWebViewClient(new BlockingWebViewClient(adBlockEngine, this));
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title != null && !title.trim().isEmpty()) {
                    currentTab().title = title;
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                enterFullscreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });
    }

    private void restoreTabs(Bundle state) {
        tabs.clear();
        if (state != null) {
            ArrayList<String> urls = state.getStringArrayList("tihulu_tab_urls");
            ArrayList<String> titles = state.getStringArrayList("tihulu_tab_titles");
            if (urls != null) {
                for (int i = 0; i < urls.size(); i++) {
                    String title = titles != null && i < titles.size() ? titles.get(i) : "Tab";
                    tabs.add(new TabState(urls.get(i), title));
                }
            }
            currentTabIndex = state.getInt("tihulu_tab_index", 0);
            String mode = state.getString("tihulu_nav_mode", NavigationMode.DPAD.name());
            try {
                navigationMode = NavigationMode.valueOf(mode);
            } catch (IllegalArgumentException ignored) {
                navigationMode = NavigationMode.DPAD;
            }
        }

        if (tabs.isEmpty()) tabs.add(new TabState(HOME_URL, "Home"));
        currentTabIndex = Math.max(0, Math.min(currentTabIndex, tabs.size() - 1));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        // When a web text field owns focus or Android TV's IME is active, the keyboard
        // owns remote navigation. Do not reinterpret D-pad as page focus/cursor movement.
        if ((webTextEditing || isSystemImeActive())
                && (isDirectionalKey(keyCode)
                        || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_BACK)) {
            return super.dispatchKeyEvent(event);
        }

        if (event.getAction() == KeyEvent.ACTION_UP
                && keyCode == KeyEvent.KEYCODE_DPAD_UP
                && upLongPressConsumed) {
            upLongPressConsumed = false;
            postShowBrowserBar();
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0
                && (keyCode == KeyEvent.KEYCODE_MENU
                        || keyCode == KeyEvent.KEYCODE_INFO
                        || keyCode == KeyEvent.KEYCODE_GUIDE)) {
            toggleBrowserBar();
            return true;
        }

        if (event.getAction() != KeyEvent.ACTION_DOWN
                || webView == null
                || !webView.hasFocus()
                || customView != null) {
            return super.dispatchKeyEvent(event);
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.getRepeatCount() > 0) {
            upLongPressConsumed = true;
        }

        if (navigationMode == NavigationMode.DPAD) {
            if (isDirectionalKey(keyCode)) {
                if (event.getRepeatCount() > 0
                        && event.getRepeatCount() % DPAD_REPEAT_DIVISOR != 0) {
                    return true;
                }
                movePageFocus(keyCode);
                return true;
            }
            if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                    && event.getRepeatCount() == 0) {
                activatePageFocus();
                return true;
            }
        } else {
            if (isDirectionalKey(keyCode)) {
                moveCursorForKey(keyCode, event.getRepeatCount());
                return true;
            }
            if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                    && event.getRepeatCount() == 0) {
                clickCursor();
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private final class WebInputBridge {
        @JavascriptInterface
        public void setTextEditing(boolean editing) {
            webTextEditing = editing;
            runOnUiThread(MainActivity.this::updateStatus);
        }
    }

    private void injectWebTextInputTracking() {
        String script =
                "(function(){"
                + "if(window.__tihuluInputTrackingInstalled){"
                + "if(window.__tihuluReportTextEditing)window.__tihuluReportTextEditing();"
                + "return;}"
                + "window.__tihuluInputTrackingInstalled=true;"
                + "const editable=(n)=>{"
                + "if(!n||n.nodeType!==1)return false;"
                + "const tag=(n.tagName||'').toUpperCase();"
                + "const type=(n.getAttribute&&n.getAttribute('type')||'').toLowerCase();"
                + "if(tag==='TEXTAREA')return true;"
                + "if(tag==='INPUT'&&!['button','submit','reset','checkbox','radio','file','range','color','image','hidden'].includes(type))return true;"
                + "if(n.isContentEditable)return true;"
                + "const role=(n.getAttribute&&n.getAttribute('role')||'').toLowerCase();"
                + "return role==='textbox'||role==='searchbox'||role==='combobox';"
                + "};"
                + "const report=(ev)=>{"
                + "let editing=false;"
                + "try{"
                + "const path=ev&&ev.composedPath?ev.composedPath():[];"
                + "editing=path.some(editable)||editable(document.activeElement);"
                + "}catch(e){}"
                + "try{window.__TihuluInput.setTextEditing(!!editing);}catch(e){}"
                + "};"
                + "window.__tihuluReportTextEditing=()=>report(null);"
                + "document.addEventListener('focusin',report,true);"
                + "document.addEventListener('focusout',()=>setTimeout(()=>report(null),0),true);"
                + "document.addEventListener('pointerdown',()=>setTimeout(()=>report(null),0),true);"
                + "report(null);"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private boolean isSystemImeActive() {
        if (root == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets insets = root.getRootWindowInsets();
            if (insets != null && insets.isVisible(WindowInsets.Type.ime())) {
                return true;
            }
        }

        Rect visible = new Rect();
        root.getWindowVisibleDisplayFrame(visible);
        int rootHeight = root.getRootView().getHeight();
        if (rootHeight > 0 && rootHeight - visible.bottom > TvUi.dp(this, 120)) {
            return true;
        }

        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        return imm != null && imm.isActive() && imm.isAcceptingText();
    }

    private static boolean isDirectionalKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private void movePageFocus(int keyCode) {
        int dx = 0;
        int dy = 0;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) dx = -1;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) dx = 1;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) dy = -1;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) dy = 1;

        String script =
                "(function(){"
                + "const q='a[href],button,input,select,textarea,summary,[role=button],[tabindex]:not([tabindex=\"-1\"])';"
                + "const els=[...document.querySelectorAll(q)].filter(e=>{"
                + "const r=e.getBoundingClientRect(),s=getComputedStyle(e);"
                + "return r.width>2&&r.height>2&&s.visibility!==\"hidden\"&&s.display!==\"none\";});"
                + "if(!els.length){window.scrollBy(0," + (dy * 420) + ");return;}"
                + "let cur=document.activeElement;"
                + "if(!els.includes(cur)){els[0].focus();els[0].scrollIntoView({block:'center',inline:'center'});return;}"
                + "const a=cur.getBoundingClientRect(),ax=a.left+a.width/2,ay=a.top+a.height/2;"
                + "let best=null,score=1e30;"
                + "for(const e of els){if(e===cur)continue;const r=e.getBoundingClientRect();"
                + "const x=r.left+r.width/2,y=r.top+r.height/2,ddx=x-ax,ddy=y-ay;"
                + "if((" + dx + "!==0&&Math.sign(ddx)!==" + dx + ")||(" + dy + "!==0&&Math.sign(ddy)!==" + dy + "))continue;"
                + "const primary=" + (dx != 0 ? "Math.abs(ddx)" : "Math.abs(ddy)") + ";"
                + "const secondary=" + (dx != 0 ? "Math.abs(ddy)" : "Math.abs(ddx)") + ";"
                + "const s=primary*1000+secondary;"
                + "if(s<score){score=s;best=e;}}"
                + "if(best){best.focus();best.scrollIntoView({block:'center',inline:'center'});}"
                + "else{window.scrollBy(0," + (dy * 420) + ");}"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private void activatePageFocus() {
        webView.evaluateJavascript(
                "(function(){const e=document.activeElement;"
                        + "if(e&&e!==document.body&&e!==document.documentElement){e.click();return true;}"
                        + "return false;})();",
                null);
    }

    private void injectTvFocusRing() {
        String script =
                "(function(){if(document.getElementById('__tihulu_tv_focus'))return;"
                + "const s=document.createElement('style');s.id='__tihulu_tv_focus';"
                + "s.textContent='*:focus{outline:4px solid #da2028!important;"
                + "outline-offset:3px!important;}';"
                + "(document.head||document.documentElement).appendChild(s);})();";
        webView.evaluateJavascript(script, null);
    }

    private void moveCursorForKey(int keyCode, int repeatCount) {
        int boundedRepeat = Math.min(Math.max(repeatCount, 0), CURSOR_MAX_ACCEL_REPEAT);
        float multiplier = 1.0f + boundedRepeat * CURSOR_REPEAT_ACCELERATION;
        float step = TvUi.dp(this, Math.round(CURSOR_STEP_DP * multiplier));
        float dx = 0;
        float dy = 0;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) dx = -step;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) dx = step;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) dy = -step;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) dy = step;
        cursorOverlay.move(dx, dy);
    }

    private void clickCursor() {
        long now = SystemClock.uptimeMillis();
        float x = cursorOverlay.cursorX();
        float y = cursorOverlay.cursorY();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 32, MotionEvent.ACTION_UP, x, y, 0);
        webView.dispatchTouchEvent(down);
        webView.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private void applyNavigationMode() {
        boolean cursor = navigationMode == NavigationMode.CURSOR;
        cursorOverlay.setVisibility(cursor ? View.VISIBLE : View.GONE);
        updateStatus();
    }

    @Override
    public NavigationMode mode() {
        return navigationMode;
    }

    @Override
    public void toggleMode() {
        navigationMode = navigationMode.toggle();
        applyNavigationMode();
        webView.requestFocus();
    }

    private void toggleBrowserBar() {
        if (browserBarDialog != null && browserBarDialog.isShowing()) {
            browserBarDialog.dismiss();
            return;
        }
        showBrowserBar();
    }

    private void postShowBrowserBar() {
        root.post(this::showBrowserBar);
    }

    private void showBrowserBar() {
        if (isFinishing() || customView != null) return;
        if (browserBarDialog != null && browserBarDialog.isShowing()) return;
        browserBarDialog = TvBrowserBar.show(this, this);
        browserBarDialog.setOnDismissListener(d -> {
            browserBarDialog = null;
            webView.requestFocus();
        });
    }

    @Override
    public void goBack() {
        if (webView.canGoBack()) webView.goBack();
    }

    @Override
    public void goForward() {
        if (webView.canGoForward()) webView.goForward();
    }

    @Override
    public void reload() {
        adBlockEngine.resetCounter();
        webView.reload();
    }

    @Override
    public void openAddress() {
        showAddressDialog();
    }

    private void showAddressDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = TvUi.dp(this, 22);
        column.setPadding(pad, pad, pad, pad);
        column.setBackground(TvUi.rounded(TvUi.PANEL, TvUi.dp(this, 24), Color.TRANSPARENT, 0));

        TextView title = TvUi.title(this, "Search or enter address", 24);
        column.addView(title);

        EditText address = new EditText(this);
        address.setSingleLine(true);
        address.setText(webView.getUrl() == null ? "" : webView.getUrl());
        address.setSelectAllOnFocus(true);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(TvUi.MUTED);
        address.setHint("Search or URL");
        address.setTextSize(19f);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setPadding(TvUi.dp(this, 16), 0, TvUi.dp(this, 16), 0);
        address.setBackground(TvUi.rounded(TvUi.NORMAL, TvUi.dp(this, 14),
                Color.TRANSPARENT, 0));
        address.setOnFocusChangeListener((v, focused) ->
                address.setBackground(TvUi.rounded(
                        TvUi.NORMAL,
                        TvUi.dp(this, 14),
                        focused ? TvUi.FOCUSED : Color.TRANSPARENT,
                        focused ? TvUi.dp(this, 3) : 0)));

        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, TvUi.dp(this, 62));
        addressParams.setMargins(0, TvUi.dp(this, 14), 0, TvUi.dp(this, 12));
        column.addView(address, addressParams);

        final Runnable[] submitHolder = new Runnable[1];
        submitHolder[0] = () -> {
            String value = address.getText().toString();
            dialog.dismiss();
            navigate(value);
        };

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button go = TvUi.button(this, "Go");
        Button cancel = TvUi.button(this, "Cancel");
        go.setOnClickListener(v -> submitHolder[0].run());
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(go, new LinearLayout.LayoutParams(0, TvUi.dp(this, 56), 1f));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, TvUi.dp(this, 56), 1f));
        column.addView(actions);

        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                submitHolder[0].run();
                return true;
            }
            return false;
        });

        dialog.setContentView(column);
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setLayout(TvUi.dp(this, 820), ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }

            address.requestFocus();
            address.selectAll();
            address.postDelayed(() -> {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(address, InputMethodManager.SHOW_IMPLICIT);
            }, 120);
        });
        dialog.show();
    }

    private void navigate(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return;

        String url;
        if (text.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            url = text;
        } else if (text.contains(".") && !text.contains(" ")) {
            url = "https://" + text;
        } else {
            try {
                url = "https://www.google.com/search?q=" + URLEncoder.encode(text, "UTF-8");
            } catch (UnsupportedEncodingException impossible) {
                url = "https://www.google.com/search?q=" + text.replace(" ", "+");
            }
        }

        adBlockEngine.resetCounter();
        currentTab().url = url;
        webView.loadUrl(url);
        webView.requestFocus();
        updateStatus();
    }

    @Override
    public void showTabs() {
        TvTabPanel.show(this, this);
    }

    @Override
    public void showControls() {
        TvControlPanel.show(this, this);
    }

    @Override
    public int currentTabNumber() {
        return currentTabIndex + 1;
    }

    @Override
    public int tabCount() {
        return tabs.size();
    }

    @Override
    public String currentTabTitle() {
        String title = currentTab().title;
        return title == null || title.trim().isEmpty() ? currentTab().url : title;
    }

    @Override
    public void previousTab() {
        if (tabs.size() < 2) return;
        saveCurrentTabUrl();
        currentTabIndex = (currentTabIndex - 1 + tabs.size()) % tabs.size();
        loadCurrentTab();
    }

    @Override
    public void nextTab() {
        if (tabs.size() < 2) return;
        saveCurrentTabUrl();
        currentTabIndex = (currentTabIndex + 1) % tabs.size();
        loadCurrentTab();
    }

    @Override
    public void newTab() {
        saveCurrentTabUrl();
        tabs.add(new TabState(HOME_URL, "New tab"));
        currentTabIndex = tabs.size() - 1;
        loadCurrentTab();
    }

    @Override
    public void closeCurrentTab() {
        if (tabs.size() == 1) {
            tabs.set(0, new TabState(HOME_URL, "Home"));
            currentTabIndex = 0;
        } else {
            tabs.remove(currentTabIndex);
            if (currentTabIndex >= tabs.size()) currentTabIndex = tabs.size() - 1;
        }
        loadCurrentTab();
    }

    private void loadCurrentTab() {
        adBlockEngine.resetCounter();
        webView.clearHistory();
        webView.loadUrl(currentTab().url);
        webView.requestFocus();
        updateStatus();
    }

    private void saveCurrentTabUrl() {
        String url = webView.getUrl();
        if (url != null && !url.trim().isEmpty()) currentTab().url = url;
    }

    private TabState currentTab() {
        return tabs.get(currentTabIndex);
    }

    @Override
    public boolean adBlockEnabled() {
        return adBlockEngine.isEnabled();
    }

    @Override
    public void toggleAdBlock() {
        adBlockEngine.setEnabled(!adBlockEngine.isEnabled());
        adBlockEngine.resetCounter();
        webView.reload();
        updateStatus();
    }

    @Override
    public int blockedCount() {
        return adBlockEngine.blockedCount();
    }

    @Override
    public void centerCursor() {
        cursorOverlay.center();
    }

    @Override
    public void goHome() {
        navigate(HOME_URL);
    }

    private void updateStatus() {
        if (status == null || adBlockEngine == null || tabs.isEmpty()) return;
        String shield = adBlockEngine.isEnabled()
                ? "Shield " + adBlockEngine.blockedCount()
                : "Shield off";
        String mode = webTextEditing
                ? "Typing"
                : (navigationMode == NavigationMode.CURSOR ? "Cursor" : "D-pad");
        status.setText(shield + "  ·  " + mode + "  ·  " + (currentTabIndex + 1) + "/" + tabs.size());
    }

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        webContainer.setVisibility(View.GONE);
        root.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void exitFullscreen() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        webContainer.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        webView.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            exitFullscreen();
        } else if (browserBarDialog != null && browserBarDialog.isShowing()) {
            browserBarDialog.dismiss();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveCurrentTabUrl();
        ArrayList<String> urls = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        for (TabState tab : tabs) {
            urls.add(tab.url);
            titles.add(tab.title);
        }
        outState.putStringArrayList("tihulu_tab_urls", urls);
        outState.putStringArrayList("tihulu_tab_titles", titles);
        outState.putInt("tihulu_tab_index", currentTabIndex);
        outState.putString("tihulu_nav_mode", navigationMode.name());
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (browserBarDialog != null) browserBarDialog.dismiss();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBlockedRequest(int total) {
        updateStatus();
    }

    @Override
    public void onPageStarted(String url) {
        webTextEditing = false;
        if (url != null) currentTab().url = url;
        updateStatus();
    }

    @Override
    public void onPageCommitVisible(String url) {
        if (url != null) currentTab().url = url;
        injectWebTextInputTracking();
        updateStatus();
    }

    @Override
    public void onPageFinished(String url) {
        if (url != null) currentTab().url = url;
        injectTvFocusRing();
        injectWebTextInputTracking();
        updateStatus();
    }
}
