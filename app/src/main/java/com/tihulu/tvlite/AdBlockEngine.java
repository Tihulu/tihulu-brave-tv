package com.tihulu.tvlite;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class AdBlockEngine {
    private final Set<String> blockedDomains = new HashSet<>();
    private final Set<String> blockedFragments = new HashSet<>();
    private final AtomicInteger blockedCount = new AtomicInteger();

    private volatile boolean enabled = true;
    private volatile boolean braveReady = false;

    AdBlockEngine(Context context) {
        loadFallbackRules(context);

        Context appContext = context.getApplicationContext();
        Thread initializer = new Thread(() -> initializeBraveEngine(appContext), "tihulu-adblock-init");
        initializer.setDaemon(true);
        initializer.start();
    }

    boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean value) {
        enabled = value;
    }

    boolean isBraveReady() {
        return braveReady;
    }

    int blockedCount() {
        return blockedCount.get();
    }

    void resetCounter() {
        blockedCount.set(0);
    }

    boolean shouldBlock(
            Uri uri,
            boolean isMainFrame,
            String sourceUrl,
            String requestType,
            String method) {
        if (!enabled || isMainFrame || uri == null) return false;

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }

        String url = uri.toString();

        if (braveReady
                && BraveAdblock.shouldBlock(
                        url,
                        sourceUrl == null || sourceUrl.isEmpty() ? url : sourceUrl,
                        requestType == null ? "other" : requestType,
                        method == null ? "get" : method)) {
            blockedCount.incrementAndGet();
            return true;
        }

        // Keep the tiny Java matcher as a fail-open fallback if native initialization
        // is unavailable on a particular TV ROM.
        String host = uri.getHost();
        if (host != null && matchesFallbackDomain(host)) {
            blockedCount.incrementAndGet();
            return true;
        }

        String lowerUrl = url.toLowerCase(Locale.US);
        for (String fragment : blockedFragments) {
            if (lowerUrl.contains(fragment)) {
                blockedCount.incrementAndGet();
                return true;
            }
        }

        return false;
    }

    void injectCosmeticFiltering(WebView webView, String url) {
        if (!enabled || webView == null) return;

        injectFallbackCosmetics(webView);

        if (!braveReady || url == null || url.isEmpty()) return;

        String json = BraveAdblock.cosmeticResources(url);
        if (json == null || json.isEmpty()) return;

        try {
            JSONObject resources = new JSONObject(json);
            JSONArray hideSelectors = resources.optJSONArray("hide_selectors");
            StringBuilder css = new StringBuilder();

            if (hideSelectors != null) {
                for (int i = 0; i < hideSelectors.length(); i++) {
                    String selector = hideSelectors.optString(i, "");
                    if (selector.isEmpty()) continue;
                    if (css.length() > 0) css.append(',');
                    css.append(selector);
                }
            }

            if (css.length() > 0) {
                String cssText = css + "{display:none!important;}";
                String cssScript =
                        "(function(){"
                                + "let s=document.getElementById('__tihulu_brave_css');"
                                + "if(!s){s=document.createElement('style');"
                                + "s.id='__tihulu_brave_css';"
                                + "(document.head||document.documentElement).appendChild(s);}"
                                + "s.textContent=" + JSONObject.quote(cssText) + ";"
                                + "})();";
                webView.evaluateJavascript(cssScript, null);
            }

            String injectedScript = resources.optString("injected_script", "");
            if (!injectedScript.isEmpty()) {
                String script =
                        "(function(){try{"
                                + injectedScript
                                + "}catch(e){console.debug('Tihulu adblock scriptlet error',e);}})();";
                webView.evaluateJavascript(script, null);
            }
        } catch (Throwable ignored) {
            // Fail open if a filter/resource cannot be represented safely in WebView.
        }
    }

    private void initializeBraveEngine(Context context) {
        if (!BraveAdblock.isNativeAvailable()) return;

        try {
            String community = readAsset(context, "brave/community-filters.txt");
            String brave = readAsset(context, "brave/brave-filters.txt");
            String resources = readAsset(context, "brave/resources.json");

            if (community.isEmpty() || brave.isEmpty() || resources.isEmpty()) return;
            braveReady = BraveAdblock.init(community, brave, resources);
        } catch (Throwable ignored) {
            braveReady = false;
        }
    }

    private static String readAsset(Context context, String path) throws IOException {
        try (InputStream input = context.getAssets().open(path);
                InputStreamReader reader =
                        new InputStreamReader(input, StandardCharsets.UTF_8);
                BufferedReader buffered = new BufferedReader(reader)) {
            StringBuilder text = new StringBuilder();
            char[] chunk = new char[16 * 1024];
            int count;
            while ((count = buffered.read(chunk)) >= 0) {
                text.append(chunk, 0, count);
            }
            return text.toString();
        }
    }

    private boolean matchesFallbackDomain(String host) {
        String candidate = host.toLowerCase(Locale.US);
        while (true) {
            if (blockedDomains.contains(candidate)) return true;
            int dot = candidate.indexOf('.');
            if (dot < 0) return false;
            candidate = candidate.substring(dot + 1);
        }
    }

    private void injectFallbackCosmetics(WebView webView) {
        String script =
                "(function(){"
                + "if(document.getElementById('__tihulu_filter_css'))return;"
                + "const s=document.createElement('style');s.id='__tihulu_filter_css';"
                + "s.textContent='"
                + "iframe[src*=\"doubleclick\"],iframe[src*=\"adservice\"],"
                + "[id^=\"google_ads\"],[id^=\"div-gpt-ad\"],[data-ad-slot],"
                + "[aria-label=\"Advertisement\"],[aria-label=\"Ads\"],"
                + ".adsbygoogle,.ad-container,.advertisement,.sponsored-ad,"
                + ".taboola,.OUTBRAIN{display:none!important;visibility:hidden!important}'"
                + ";(document.head||document.documentElement).appendChild(s);"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private void loadFallbackRules(Context context) {
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                context.getAssets().open("adblock_rules.txt"),
                                StandardCharsets.UTF_8))) {
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
        } catch (IOException ignored) {
            // Fail open.
        }
    }
}
