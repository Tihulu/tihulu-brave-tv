package org.chromium.content_public.browser;

import org.chromium.ui.base.EventForwarder;

public class WebContents {
    private final EventForwarder eventForwarder = new EventForwarder();

    public EventForwarder getEventForwarder() {
        return eventForwarder;
    }
}
