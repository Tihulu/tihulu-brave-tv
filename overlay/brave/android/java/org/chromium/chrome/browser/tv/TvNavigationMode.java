/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

/** Navigation modes exposed by the TV control panel. */
public enum TvNavigationMode {
    DPAD,
    CURSOR,
    SCROLL;

    public static TvNavigationMode fromPreference(String value) {
        if ("CURSOR".equals(value)) return CURSOR;
        if ("SCROLL".equals(value)) return SCROLL;
        return DPAD;
    }

    public String label() {
        return this == DPAD ? "D-pad" : this == CURSOR ? "Cursor" : "Scroll";
    }

    public TvNavigationMode toggle() {
        return this == DPAD ? CURSOR : this == CURSOR ? SCROLL : DPAD;
    }
}
