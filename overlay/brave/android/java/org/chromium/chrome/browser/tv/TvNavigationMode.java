/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

/** Navigation modes exposed by the TV control panel. */
public enum TvNavigationMode {
    DPAD,
    CURSOR;

    public TvNavigationMode toggle() {
        return this == DPAD ? CURSOR : DPAD;
    }
}
