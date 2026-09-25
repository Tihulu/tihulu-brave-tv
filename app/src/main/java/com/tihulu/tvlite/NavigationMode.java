package com.tihulu.tvlite;

enum NavigationMode {
    DPAD,
    CURSOR;

    NavigationMode toggle() {
        return this == DPAD ? CURSOR : DPAD;
    }
}
