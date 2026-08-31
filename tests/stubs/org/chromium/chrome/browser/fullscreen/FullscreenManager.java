package org.chromium.chrome.browser.fullscreen;

import org.chromium.chrome.browser.tab.Tab;

public class FullscreenManager {
    public interface Observer {
        default void onEnterFullscreen(Tab tab, FullscreenOptions options) {}
        default void onExitFullscreen(Tab tab) {}
    }

    public void addObserver(Observer observer) {}
    public void removeObserver(Observer observer) {}
    public boolean getPersistentFullscreenMode() { return false; }
}
