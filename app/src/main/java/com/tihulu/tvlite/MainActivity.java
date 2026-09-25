package com.tihulu.tvlite;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

public final class MainActivity extends Activity implements BlockingWebViewClient.Listener {
    private WebView webView;
    private EditText addressBar;
    private TextView status;
    private Button backButton;
    private Button forwardButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(17, 17, 17));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(10), dp(8), dp(10), dp(8));
        toolbar.setBackgroundColor(Color.rgb(28, 28, 28));

        backButton = button("‹");
        forwardButton = button("›");
        Button reloadButton = button("↻");
        Button goButton = button("Go");

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextColor(Color.WHITE);
        addressBar.setHintTextColor(Color.LTGRAY);
        addressBar.setHint("Search or enter address");
        addressBar.setTextSize(18f);
        addressBar.setSelectAllOnFocus(true);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setBackgroundColor(Color.rgb(48, 48, 48));
        addressBar.setPadding(dp(14), 0, dp(14), 0);

        LinearLayout.LayoutParams addressParams =
                new LinearLayout.LayoutParams(0, dp(52), 1f);
        addressParams.setMargins(dp(8), 0, dp(8), 0);

        toolbar.addView(backButton);
        toolbar.addView(forwardButton);
        toolbar.addView(reloadButton);
        toolbar.addView(addressBar, addressParams);
        toolbar.addView(goButton);

        FrameLayout webContainer = new FrameLayout(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        webContainer.addView(webView, webParams);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(12f);
        status.setBackgroundColor(0xAA111111);
        status.setPadding(dp(10), dp(6), dp(10), dp(6));
        status.setText("Lite · blocker on");

        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END
        );
        statusParams.setMargins(0, 0, dp(10), dp(10));
        webContainer.addView(status, statusParams);

        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));
        root.addView(webContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        configureWebView();

        backButton.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });
        forwardButton.setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });
        reloadButton.setOnClickListener(v -> webView.reload());
        goButton.setOnClickListener(v -> navigate(addressBar.getText().toString()));

        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate(addressBar.getText().toString());
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            navigate("https://www.google.com/");
        } else {
            webView.restoreState(savedInstanceState);
        }

        webView.requestFocus();
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
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new BlockingWebViewClient(this));
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                setTitle(title == null ? "Tihulu TV Browser Lite" : title);
            }
        });
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(18f);
        button.setAllCaps(false);
        button.setMinWidth(dp(58));
        button.setMinHeight(dp(52));
        button.setFocusable(true);
        return button;
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
                url = "https://www.google.com/search?q="
                        + URLEncoder.encode(text, "UTF-8");
            } catch (UnsupportedEncodingException impossible) {
                url = "https://www.google.com/search?q=" + text.replace(" ", "+");
            }
        }

        webView.loadUrl(url);
        webView.requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && webView != null && webView.hasFocus()) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    movePageFocus(0, -1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    movePageFocus(0, 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    movePageFocus(-1, 0);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    movePageFocus(1, 0);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    activatePageFocus();
                    return true;
                case KeyEvent.KEYCODE_MENU:
                    addressBar.requestFocus();
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void movePageFocus(int dx, int dy) {
        String script =
                "(function(){"
                + "const q='a[href],button,input,select,textarea,[tabindex]:not([tabindex=\"-1\"])';"
                + "const els=[...document.querySelectorAll(q)].filter(e=>{"
                + "const r=e.getBoundingClientRect();const s=getComputedStyle(e);"
                + "return r.width>2&&r.height>2&&s.visibility!=='hidden'&&s.display!=='none';});"
                + "if(!els.length)return;"
                + "let cur=document.activeElement;"
                + "if(!els.includes(cur)){els[0].focus();els[0].scrollIntoView({block:'center',inline:'center'});return;}"
                + "const a=cur.getBoundingClientRect();const ax=a.left+a.width/2, ay=a.top+a.height/2;"
                + "let best=null,score=1e30;"
                + "for(const e of els){if(e===cur)continue;const r=e.getBoundingClientRect();"
                + "const x=r.left+r.width/2,y=r.top+r.height/2,ddx=x-ax,ddy=y-ay;"
                + "if((" + dx + "!==0&&Math.sign(ddx)!==" + dx + ")||(" + dy + "!==0&&Math.sign(ddy)!==" + dy + "))continue;"
                + "const primary=" + (dx != 0 ? "Math.abs(ddx)" : "Math.abs(ddy)") + ";"
                + "const secondary=" + (dx != 0 ? "Math.abs(ddy)" : "Math.abs(ddx)") + ";"
                + "const s=primary*1000+secondary;"
                + "if(s<score){score=s;best=e;}}"
                + "if(best){best.focus();best.scrollIntoView({block:'center',inline:'center',behavior:'smooth'});}"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private void activatePageFocus() {
        webView.evaluateJavascript(
                "(function(){const e=document.activeElement;"
                        + "if(e&&e!==document.body&&e!==document.documentElement){e.click();return true;}return false;})();",
                null
        );
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBlockedRequest(int total) {
        status.setText("Lite · blocked " + total);
    }

    @Override
    public void onPageStarted(String url) {
        addressBar.setText(url);
        updateNavButtons();
    }

    @Override
    public void onPageFinished(String url) {
        addressBar.setText(url);
        updateNavButtons();
    }

    private void updateNavButtons() {
        backButton.setEnabled(webView.canGoBack());
        forwardButton.setEnabled(webView.canGoForward());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
