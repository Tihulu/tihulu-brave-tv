package com.tihulu.tvlite;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class AdBlockEngine {
    private final Set<String> blockedDomains = new HashSet<>();
    private final Set<String> blockedFragments = new HashSet<>();
    private final AtomicInteger blockedCount = new AtomicInteger();
    private volatile boolean enabled = true;

    AdBlockEngine(Context context) {
        loadRules(context);
    }

    boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean value) {
        enabled = value;
    }

    int blockedCount() {
        return blockedCount.get();
    }

    void resetCounter() {
        blockedCount.set(0);
    }

    boolean shouldBlock(Uri uri, boolean isMainFrame) {
        if (!enabled || isMainFrame || uri == null) return false;
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;

        String host = uri.getHost();
        if (host != null && matchesDomain(host)) {
            blockedCount.incrementAndGet();
            return true;
        }

        String url = uri.toString().toLowerCase(Locale.US);
        for (String fragment : blockedFragments) {
            if (url.contains(fragment)) {
                blockedCount.incrementAndGet();
                return true;
            }
        }
        return false;
    }

    private boolean matchesDomain(String host) {
        String candidate = host.toLowerCase(Locale.US);
        while (true) {
            if (blockedDomains.contains(candidate)) return true;
            int dot = candidate.indexOf('.');
            if (dot < 0) return false;
            candidate = candidate.substring(dot + 1);
        }
    }

    void injectCosmeticFiltering(WebView webView) {
        if (!enabled) return;
        String script =
                "(function(){"
                + "if(document.getElementById('__tihulu_filter_css'))return;"
                + "const s=document.createElement('style');s.id='__tihulu_filter_css';"
                + "s.textContent='"
                + "iframe[src*="doubleclick"],iframe[src*="adservice"],"
                + "[id^="google_ads"],[id^="div-gpt-ad"],[data-ad-slot],"
                + "[aria-label="Advertisement"],[aria-label="Ads"],"
                + ".adsbygoogle,.ad-container,.advertisement,.sponsored-ad,"
                + ".taboola,.OUTBRAIN{display:none!important;visibility:hidden!important}'"
                + ";(document.head||document.documentElement).appendChild(s);"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private void loadRules(Context context) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("adblock_rules.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase(Locale.US);
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
                if (line.startsWith("||") && line.endsWith("^") && line.length() > 3) {
                    blockedDomains.add(line.substring(2, line.length() - 1));
                } else if (line.startsWith("domain:")) {
                    blockedDomains.add(line.substring("domain:".length()).trim());
                } else if (line.startsWith("contains:")) {
                    String fragment = line.substring("contains:".length()).trim();
                    if (!fragment.isEmpty()) blockedFragments.add(fragment);
                }
            }
        } catch (IOException e) {
            // Fail open: browsing must still work if a packaged filter asset is unavailable.
        }
    }
}
