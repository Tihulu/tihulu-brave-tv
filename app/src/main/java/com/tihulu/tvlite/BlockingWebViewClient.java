package com.tihulu.tvlite;

import android.graphics.Bitmap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class BlockingWebViewClient extends WebViewClient {
    interface Listener {
        void onBlockedRequest(int total);
        void onPageStarted(String url);
        void onPageFinished(String url);
    }

    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
            "doubleclick.net",
            "googleadservices.com",
            "googlesyndication.com",
            "adservice.google.com",
            "googletagmanager.com",
            "google-analytics.com",
            "analytics.google.com",
            "scorecardresearch.com",
            "quantserve.com",
            "adsrvr.org",
            "adnxs.com",
            "criteo.com",
            "criteo.net",
            "taboola.com",
            "outbrain.com",
            "zedo.com",
            "rubiconproject.com",
            "pubmatic.com",
            "openx.net",
            "openx.com",
            "casalemedia.com",
            "moatads.com",
            "amazon-adsystem.com",
            "media.net",
            "smartadserver.com",
            "serving-sys.com",
            "bidswitch.net",
            "contextweb.com",
            "bluekai.com",
            "demdex.net",
            "mathtag.com",
            "lijit.com",
            "yieldmo.com",
            "teads.tv",
            "adsafeprotected.com",
            "branch.io",
            "appsflyer.com",
            "adjust.com",
            "segment.com",
            "mixpanel.com"
    ));

    private final Listener listener;
    private int blockedCount;

    BlockingWebViewClient(Listener listener) {
        this.listener = listener;
    }

    private static boolean shouldBlockHost(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.US);
        for (String blocked : BLOCKED_HOSTS) {
            if (normalized.equals(blocked) || normalized.endsWith("." + blocked)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (request != null && request.getUrl() != null
                && shouldBlockHost(request.getUrl().getHost())) {
            blockedCount++;
            int total = blockedCount;
            view.post(() -> listener.onBlockedRequest(total));
            return new WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    new ByteArrayInputStream(new byte[0])
            );
        }
        return super.shouldInterceptRequest(view, request);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return true;
        String scheme = request.getUrl().getScheme();
        return !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        listener.onPageStarted(url);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        listener.onPageFinished(url);
    }
}
