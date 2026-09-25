package com.tihulu.tvlite;

final class BraveAdblock {
    private static final boolean NATIVE_AVAILABLE;

    static {
        boolean available;
        try {
            System.loadLibrary("tihulu_adblock");
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
        NATIVE_AVAILABLE = available;
    }

    private BraveAdblock() {}

    static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    static boolean init(String untrustedRules, String braveRules, String resourcesJson) {
        if (!NATIVE_AVAILABLE) return false;
        try {
            return nativeInit(untrustedRules, braveRules, resourcesJson);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean shouldBlock(
            String url, String sourceUrl, String requestType, String method) {
        if (!NATIVE_AVAILABLE) return false;
        try {
            return nativeShouldBlock(url, sourceUrl, requestType, method);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String cosmeticResources(String url) {
        if (!NATIVE_AVAILABLE) return null;
        try {
            return nativeCosmeticResources(url);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static native boolean nativeInit(
            String untrustedRules, String braveRules, String resourcesJson);

    private static native boolean nativeShouldBlock(
            String url, String sourceUrl, String requestType, String method);

    private static native String nativeCosmeticResources(String url);
}
