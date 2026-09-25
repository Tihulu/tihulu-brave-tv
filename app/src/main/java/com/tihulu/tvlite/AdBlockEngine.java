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
                + "if(!document.getElementById('__tihulu_filter_css')){"
                + "const s=document.createElement('style');s.id='__tihulu_filter_css';"
                + "s.textContent='"
                + "iframe[src*=\"doubleclick\"],iframe[src*=\"adservice\"],"
                + "[id^=\"google_ads\"],[id^=\"div-gpt-ad\"],[data-ad-slot],"
                + "[aria-label=\"Advertisement\"],[aria-label=\"Ads\"],"
                + ".adsbygoogle,.ad-container,.advertisement,.sponsored-ad,"
                + ".taboola,.OUTBRAIN{display:none!important;visibility:hidden!important}'"
                + ";(document.head||document.documentElement).appendChild(s);"
                + "}"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    void injectYouTubeFiltering(WebView webView) {
        if (!enabled) return;

        String script =
                "(function(){"
                + "const h=(location.hostname||'').toLowerCase();"
                + "if(!(h==='youtube.com'||h.endsWith('.youtube.com')))return;"
                + "if(document.getElementById('__tihulu_youtube_css')==null){"
                + "const s=document.createElement('style');s.id='__tihulu_youtube_css';"
                + "s.textContent='"
                + ".ytp-ad-module,.ytp-ad-overlay-container,.ytp-ad-player-overlay,"
                + ".video-ads,ytd-ad-slot-renderer,ytd-display-ad-renderer,"
                + "ytd-promoted-video-renderer,ytd-promoted-sparkles-web-renderer,"
                + "ytd-in-feed-ad-layout-renderer,ytd-companion-slot-renderer,"
                + "ytd-banner-promo-renderer,ytd-statement-banner-renderer{"
                + "display:none!important;visibility:hidden!important;}'"
                + ";(document.head||document.documentElement).appendChild(s);"
                + "}"
                + "if(window.__tihuluYouTubeAdGuard)return;"
                + "window.__tihuluYouTubeAdGuard=true;"
                + "let lastRun=0;"
                + "const run=()=>{"
                + "const now=Date.now();if(now-lastRun<250)return;lastRun=now;"
                + "const selectors=["
                + "'.ytp-skip-ad-button',"
                + "'.ytp-ad-skip-button',"
                + "'.ytp-ad-skip-button-modern',"
                + "'button.ytp-ad-skip-button-modern',"
                + "'.ytp-skip-ad-button__text',"
                + "'[id*=\"skip-button\"] button'"
                + "];"
                + "for(const sel of selectors){"
                + "const b=document.querySelector(sel);"
                + "if(b&&b.offsetParent!==null){try{b.click();}catch(e){}}"
                + "}"
                + "document.querySelectorAll('ytd-ad-slot-renderer,ytd-display-ad-renderer,"
                + "ytd-promoted-video-renderer,ytd-promoted-sparkles-web-renderer,"
                + "ytd-in-feed-ad-layout-renderer,ytd-companion-slot-renderer').forEach(e=>{"
                + "try{e.remove();}catch(x){}"
                + "});"
                + "const player=document.querySelector('.html5-video-player.ad-showing');"
                + "if(player){"
                + "const v=player.querySelector('video');"
                + "if(v&&Number.isFinite(v.duration)&&v.duration>0&&v.duration<180){"
                + "try{v.muted=true;v.playbackRate=16;"
                + "if(v.currentTime<v.duration-0.25)v.currentTime=Math.max(0,v.duration-0.15);"
                + "}catch(e){}"
                + "}"
                + "}"
                + "};"
                + "run();"
                + "const root=document.documentElement||document.body;"
                + "if(root){new MutationObserver(run).observe(root,{childList:true,subtree:true,attributes:true,"
                + "attributeFilter:['class','style','aria-hidden']});}"
                + "setInterval(run,900);"
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
