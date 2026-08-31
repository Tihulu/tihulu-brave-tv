package org.chromium.components.embedder_support.view;

import android.content.Context;
import android.view.View;

import org.chromium.content_public.browser.WebContents;

public class ContentView extends View {
    private final WebContents webContents = new WebContents();

    public ContentView(Context context) {
        super(context);
    }

    public WebContents getWebContents() {
        return webContents;
    }
}
