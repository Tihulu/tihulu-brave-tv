package org.chromium.components.search_engines;
public class TemplateUrlService {
    public boolean delay;
    public Runnable pending;
    public void runWhenLoaded(Runnable action){if(delay) pending=action; else action.run();}
    public String getUrlForSearchQuery(String query){return "https://chosen-engine.test/search?q="+query;}
}
