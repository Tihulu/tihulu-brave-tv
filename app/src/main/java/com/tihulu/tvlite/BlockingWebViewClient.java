package com.tihulu.tvlite;

import android.graphics.Bitmap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.util.Collections;

final class BlockingWebViewClient extends WebViewClient {
    interface Listener {
        void onBlockedRequest(int total);
        void onPageStarted(String url);
        void onPageFinished(String url);
    }

    private final AdBlockEngine adBlockEngine;
    private final Listener listener;

    BlockingWebViewClient(AdBlockEngine adBlockEngine, Listener listener) {
        this.adBlockEngine = adBlockEngine;
        this.listener = listener;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (request != null
                && request.getUrl() != null
                && adBlockEngine.shouldBlock(request.getUrl(), request.isForMainFrame())) {
            int total = adBlockEngine.blockedCount();
            view.post(() -> listener.onBlockedRequest(total));
            return new WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    204,
                    "No Content",
                    Collections.emptyMap(),
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
        adBlockEngine.injectCosmeticFiltering(view);
        listener.onPageFinished(url);
    }
}
