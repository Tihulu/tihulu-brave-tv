package org.chromium.chrome.browser.tv;

import android.app.Dialog;
import android.content.Context;
import java.lang.reflect.Method;
import org.chromium.chrome.browser.preferences.website.BraveShieldsContentSettings;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.url.GURL;

public final class TvShieldsPanelTest {
    private static final class TestTab extends Tab {
        final Profile profile = new Profile();
        String url = "https://example.com/";
        boolean destroyed;
        int reloads;
        @Override public Profile getProfile() { return profile; }
        @Override public GURL getUrl() { return new GURL() {
            @Override public String getSpec() { return url; }
        }; }
        @Override public boolean isDestroyed() { return destroyed; }
        @Override public void reload() { reloads++; }
    }
    public static void main(String[] args) throws Exception {
        Method apply = TvShieldsPanel.class.getDeclaredMethod("apply", Tab.class,
                String.class, Dialog.class, boolean.class, boolean.class);
        apply.setAccessible(true);
        TestTab tab = new TestTab();
        Dialog dialog = new Dialog(new Context());
        String capturedUrl = tab.url;
        apply.invoke(null, tab, capturedUrl, dialog, true, true);
        assert BraveShieldsContentSettings.lastProfile == tab.profile : "use active tab profile, including private mode";
        assert capturedUrl.equals(BraveShieldsContentSettings.lastUrl);
        assert BraveShieldsContentSettings.enabled;
        assert "aggressive".equals(BraveShieldsContentSettings.lastLevel);
        assert tab.reloads == 1;
        apply.invoke(null, tab, capturedUrl, dialog, false, true);
        assert "default".equals(BraveShieldsContentSettings.lastLevel);
        int writes = BraveShieldsContentSettings.writes;
        apply.invoke(null, tab, capturedUrl, dialog, false, false);
        assert !BraveShieldsContentSettings.enabled;
        assert BraveShieldsContentSettings.writes == writes + 1 : "off must preserve filter level";
        tab.url = "https://other.example/";
        int reloads = tab.reloads;
        apply.invoke(null, tab, capturedUrl, dialog, true, true);
        assert tab.reloads == reloads : "do not reload a page navigated in the background";
        assert capturedUrl.equals(BraveShieldsContentSettings.lastUrl) : "only modify captured site";
        tab.destroyed = true;
        writes = BraveShieldsContentSettings.writes;
        apply.invoke(null, tab, capturedUrl, dialog, true, true);
        assert BraveShieldsContentSettings.writes == writes : "ignore closed tabs";
        assert TvShieldsPanel.show(new Context(), tab) == null;
        tab.destroyed = false;
        tab.url = "chrome://newtab/";
        assert TvShieldsPanel.show(new Context(), tab) == null;
        System.out.println("TV Shields action regression tests passed.");
    }
}
