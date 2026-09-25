package com.tihulu.tvlite;

import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Collections;

final class YouTubeAdGuard {
    private YouTubeAdGuard() {}

    static void install(WebView webView) {
        if (webView == null) return;
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;

        WebViewCompat.addDocumentStartJavaScript(
                webView,
                SCRIPT,
                Collections.singleton("*"));
    }

    static void injectFallback(WebView webView) {
        if (webView == null) return;
        webView.evaluateJavascript(SCRIPT, null);
    }

    private static final String SCRIPT =
            "(function(){"
            + "const host=(location.hostname||'').toLowerCase();"
            + "if(!(host==='youtube.com'||host.endsWith('.youtube.com')))return;"
            + "if(window.__tihuluYtResponseGuard)return;"
            + "window.__tihuluYtResponseGuard=true;"
            + ""
            + "const isPlayerUrl=(u)=>{"
            + "try{const s=String(u||'');"
            + "return /\\/youtubei\\/v1\\/(player|get_watch)(?:\\?|$)/.test(s)"
            + "||/\\/player(?:\\?|$)/.test(s)"
            + "||/\\/watch\\?/.test(s)"
            + "||/\\/playlist\\?list=/.test(s);}catch(e){return false;}};"
            + ""
            + "const prune=(v,depth)=>{"
            + "if(!v||typeof v!=='object'||depth>12)return v;"
            + "if(Array.isArray(v)){for(const x of v)prune(x,depth+1);return v;}"
            + "for(const k of Object.keys(v)){"
            + "if(k==='adPlacements'||k==='adSlots'||k==='playerAds'"
            + "||k==='adBreakHeartbeatParams'||k==='adBreakParams'){"
            + "try{delete v[k];}catch(e){try{v[k]=[];}catch(x){}}"
            + "}else{prune(v[k],depth+1);}"
            + "}return v;};"
            + ""
            + "const cleanText=(t)=>{"
            + "if(typeof t!=='string')return t;"
            + "if(t.indexOf('adPlacements')<0&&t.indexOf('adSlots')<0"
            + "&&t.indexOf('playerAds')<0&&t.indexOf('adBreakHeartbeatParams')<0)return t;"
            + "try{const o=JSON.parse(t);prune(o,0);return JSON.stringify(o);}catch(e){return t;}};"
            + ""
            + "try{"
            + "const ofetch=window.fetch;"
            + "if(typeof ofetch==='function'){"
            + "window.fetch=async function(){"
            + "const args=arguments;"
            + "const req=args[0];"
            + "const url=(typeof req==='string')?req:(req&&req.url)||'';"
            + "const res=await ofetch.apply(this,args);"
            + "if(!isPlayerUrl(url))return res;"
            + "try{"
            + "const text=await res.clone().text();"
            + "const cleaned=cleanText(text);"
            + "if(cleaned===text)return res;"
            + "const headers=new Headers(res.headers);"
            + "headers.delete('content-length');"
            + "return new Response(cleaned,{status:res.status,statusText:res.statusText,headers});"
            + "}catch(e){return res;}"
            + "};"
            + "}"
            + "}catch(e){}"
            + ""
            + "try{"
            + "const X=window.XMLHttpRequest;"
            + "if(X&&X.prototype){"
            + "const open=X.prototype.open;"
            + "X.prototype.open=function(method,url){"
            + "try{this.__tihuluYtUrl=String(url||'');}catch(e){}"
            + "return open.apply(this,arguments);};"
            + ""
            + "const rt=Object.getOwnPropertyDescriptor(X.prototype,'responseText');"
            + "if(rt&&rt.get&&rt.configurable!==false){"
            + "Object.defineProperty(X.prototype,'responseText',{"
            + "configurable:true,enumerable:rt.enumerable,"
            + "get:function(){"
            + "const text=rt.get.call(this);"
            + "return isPlayerUrl(this.__tihuluYtUrl)?cleanText(text):text;"
            + "}});"
            + "}"
            + ""
            + "const rr=Object.getOwnPropertyDescriptor(X.prototype,'response');"
            + "if(rr&&rr.get&&rr.configurable!==false){"
            + "Object.defineProperty(X.prototype,'response',{"
            + "configurable:true,enumerable:rr.enumerable,"
            + "get:function(){"
            + "const value=rr.get.call(this);"
            + "if(!isPlayerUrl(this.__tihuluYtUrl))return value;"
            + "try{"
            + "if(this.responseType==='json'&&value&&typeof value==='object'){"
            + "return prune(value,0);}"
            + "if((this.responseType===''||this.responseType==='text')&&typeof value==='string'){"
            + "return cleanText(value);}"
            + "}catch(e){}"
            + "return value;"
            + "}});"
            + "}"
            + "}"
            + "}catch(e){}"
            + ""
            + "try{"
            + "const parse=JSON.parse;"
            + "JSON.parse=function(text,reviver){"
            + "const out=parse.call(this,text,reviver);"
            + "try{"
            + "if(typeof text==='string'&&(text.indexOf('adPlacements')>=0"
            + "||text.indexOf('adSlots')>=0||text.indexOf('playerAds')>=0)){"
            + "prune(out,0);}"
            + "}catch(e){}"
            + "return out;};"
            + "}catch(e){}"
            + ""
            + "try{"
            + "const style=document.createElement('style');"
            + "style.textContent='.ytp-ad-module,.video-ads,.ytp-ad-overlay-container,'"
            + "+'ytd-ad-slot-renderer,ytd-display-ad-renderer,ytd-in-feed-ad-layout-renderer,'"
            + "+'ytd-promoted-video-renderer,ytd-companion-slot-renderer{'"
            + "+'display:none!important;visibility:hidden!important}';"
            + "(document.documentElement||document).appendChild(style);"
            + "}catch(e){}"
            + "})();";
}
