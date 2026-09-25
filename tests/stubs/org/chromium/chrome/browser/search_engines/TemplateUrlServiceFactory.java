package org.chromium.chrome.browser.search_engines;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.search_engines.TemplateUrlService;
public class TemplateUrlServiceFactory {
    public static final TemplateUrlService service = new TemplateUrlService();
    public static Profile lastProfile;
    public static TemplateUrlService getForProfile(Profile profile){lastProfile=profile;return service;}
}
