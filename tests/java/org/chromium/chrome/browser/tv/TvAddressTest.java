package org.chromium.chrome.browser.tv;

import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.search_engines.TemplateUrlServiceFactory;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.components.search_engines.TemplateUrlService;
import org.chromium.url.GURL;

/** Behavioral regressions for remote address routing and delayed native search callbacks. */
public final class TvAddressTest {
    private static final class Page extends Tab {
        final Profile profile = new Profile();
        String url = "https://example.com/";
        boolean destroyed;
        @Override public Profile getProfile() { return profile; }
        @Override public GURL getUrl() { return new GURL(url); }
        @Override public boolean isDestroyed() { return destroyed; }
    }

    private static void rejected(String input) {
        boolean rejected = false;
        try { TvAddressInput.urlCandidate(input); }
        catch (IllegalArgumentException expected) { rejected = true; }
        assert rejected : "must reject " + input;
    }

    public static void main(String[] args) {
        assert TvAddressInput.urlCandidate(" example.com/a?q=1 ").equals("https://example.com/a?q=1");
        assert TvAddressInput.urlCandidate("http://192.168.1.8:8080/").equals("http://192.168.1.8:8080/");
        assert TvAddressInput.urlCandidate("localhost:8080").equals("https://localhost:8080");
        assert TvAddressInput.urlCandidate("[::1]:8443/").equals("https://[::1]:8443/");
        assert TvAddressInput.urlCandidate("hello world").isEmpty();
        assert TvAddressInput.urlCandidate("weather").isEmpty();
        assert TvAddressInput.urlCandidate("site:example.com").isEmpty();
        assert TvAddressInput.urlCandidate("topic: browser controls").isEmpty();
        assert TvAddressInput.urlCandidate("a@b.com").isEmpty();
        for (String value : new String[] {null, " ", "javascript:alert(1)", "intent://scan", "file:///tmp/a", "data:text/html,x"}) {
            rejected(value);
        }

        TvBraveActivity activity = new TvBraveActivity();
        assert !activity.openAddress("example.com") : "ignore input before Chromium initialization";
        Page page = new Page();
        activity.activeTab = page;
        activity.tabModelsReady = true;
        assert activity.openAddress("example.com");
        assert page.loadedUrl.equals("https://example.com");
        assert activity.openAddress("cats on TV");
        assert page.loadedUrl.equals("https://chosen-engine.test/search?q=cats on TV");
        assert TemplateUrlServiceFactory.lastProfile == page.profile : "use the active profile";

        TemplateUrlService service = TemplateUrlServiceFactory.service;
        service.delay = true;
        page.loadedUrl = null;
        activity.openAddress("delayed query");
        activity.activeTab = new Page();
        service.pending.run();
        assert page.loadedUrl == null : "a delayed search must not navigate an inactive tab";

        activity.activeTab = page;
        activity.openAddress("delayed query");
        page.url = "https://different-site.test/";
        service.pending.run();
        assert page.loadedUrl == null : "do not replace a newer navigation";

        activity.openAddress("older query");
        Runnable stale = service.pending;
        activity.openAddress("newer query");
        stale.run();
        assert page.loadedUrl == null : "only the most recent address request can navigate";
        service.pending.run();
        assert page.loadedUrl.endsWith("newer query");

        page.loadedUrl = null;
        activity.openAddress("closed tab query");
        page.destroyed = true;
        service.pending.run();
        assert page.loadedUrl == null;
        assert !activity.openAddress("example.com");

        page.destroyed = false;
        activity.openAddress("closing activity query");
        activity.onDestroyInternal();
        service.pending.run();
        assert page.loadedUrl == null : "no native calls after activity destruction";
        assert !activity.openAddress("example.com");
        System.out.println("TV address routing, active-profile search and lifecycle tests passed.");
    }
}
