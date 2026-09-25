package com.tihulu.tvlite;

import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

final class BlockingWebViewClient extends WebViewClient {
    interface Listener {
        void onBlockedRequest(int total);
        void onPageStarted(String url);
        void onPageCommitVisible(String url);
        void onPageFinished(String url);
    }

    private final AdBlockEngine adBlockEngine;
    private final Listener listener;
    private volatile String currentPageUrl = "";

    BlockingWebViewClient(AdBlockEngine adBlockEngine, Listener listener) {
        this.adBlockEngine = adBlockEngine;
        this.listener = listener;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (request != null && request.getUrl() != null) {
            String sourceUrl = currentPageUrl;
            String type = inferRequestType(request);
            String method = request.getMethod();

            if (adBlockEngine.shouldBlock(
                    request.getUrl(),
                    request.isForMainFrame(),
                    sourceUrl,
                    type,
                    method)) {
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
        currentPageUrl = url == null ? "" : url;
        listener.onPageStarted(url);
    }

    @Override
    public void onPageCommitVisible(WebView view, String url) {
        if (url != null) currentPageUrl = url;
        adBlockEngine.injectCosmeticFiltering(view, currentPageUrl);
        YouTubeAdGuard.injectFallback(view);
        listener.onPageCommitVisible(url);
        super.onPageCommitVisible(view, url);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (url != null) currentPageUrl = url;
        adBlockEngine.injectCosmeticFiltering(view, currentPageUrl);
        YouTubeAdGuard.injectFallback(view);
        listener.onPageFinished(url);
    }

    private static String inferRequestType(WebResourceRequest request) {
        if (request.isForMainFrame()) return "document";

        Map<String, String> headers = request.getRequestHeaders();
        String destination = getHeader(headers, "sec-fetch-dest");
        if (destination != null) {
            switch (destination.toLowerCase(Locale.US)) {
                case "script":
                    return "script";
                case "style":
                    return "stylesheet";
                case "image":
                    return "image";
                case "font":
                    return "font";
                case "video":
                case "audio":
                    return "media";
                case "iframe":
                case "frame":
                    return "subdocument";
                case "empty":
                    return "xmlhttprequest";
                default:
                    break;
            }
        }

        String accept = getHeader(headers, "accept");
        if (accept != null) {
            String lower = accept.toLowerCase(Locale.US);
            if (lower.contains("text/css")) return "stylesheet";
            if (lower.contains("javascript")) return "script";
            if (lower.startsWith("image/") || lower.contains("image/")) return "image";
            if (lower.startsWith("video/") || lower.startsWith("audio/")) return "media";
            if (lower.contains("font/")) return "font";
            if (lower.contains("application/json")) return "xmlhttprequest";
        }

        Uri uri = request.getUrl();
        String path = uri.getPath();
        if (path != null) {
            String lower = path.toLowerCase(Locale.US);
            if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "script";
            if (lower.endsWith(".css")) return "stylesheet";
            if (lower.matches(".*\\.(png|jpe?g|gif|webp|svg|avif)$")) return "image";
            if (lower.matches(".*\\.(mp4|webm|m4a|mp3|ogg|m3u8)$")) return "media";
            if (lower.matches(".*\\.(woff2?|ttf|otf)$")) return "font";
        }

        return "other";
    }

    private static String getHeader(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) return null;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        }
        return null;
    }
}
