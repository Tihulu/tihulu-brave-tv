package org.chromium.chrome.browser.tab;
public class Tab {
    public int getId(){return 0;}
    public String getTitle(){return "Example";}
    public boolean isDestroyed(){return false;}
    public org.chromium.url.GURL getUrl(){return new org.chromium.url.GURL();}
    public org.chromium.chrome.browser.profiles.Profile getProfile(){return new org.chromium.chrome.browser.profiles.Profile();}
    public String loadedUrl;
    public void loadUrl(org.chromium.content_public.browser.LoadUrlParams params){loadedUrl=params.url;}
    public void reload(){}
}
