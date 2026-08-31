/*
 * Tihulu TV Browser
 * Copyright (C) 2026 Tihulu contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.chromium.chrome.browser.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;

import org.chromium.base.CommandLine;

/** Applies Chromium's supported low-end profile where TV memory pressure is most likely. */
final class TvMemoryProfile {
    private static final String LOW_END_DEVICE_SWITCH = "enable-low-end-device-mode";

    private TvMemoryProfile() {}

    static void apply(Context context) {
        if (shouldUseLowMemoryProfile(context)) {
            CommandLine.getInstance().appendSwitch(LOW_END_DEVICE_SWITCH);
        }
    }

    static boolean shouldUseLowMemoryProfile(Context context) {
        // A 32-bit browser process has a substantially tighter virtual address space even on a
        // device whose physical RAM is above Chromium's normal low-end threshold. Prefer
        // Chromium's own low-end behavior rather than weakening the process model.
        if (!Process.is64Bit()) return true;

        if (context == null) return false;
        try {
            ActivityManager manager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            return manager != null && manager.isLowRamDevice();
        } catch (RuntimeException ignored) {
            // Some vendor Android builds expose incomplete system services. Failure to query the
            // hint must not prevent browser startup; 64-bit then keeps Chromium's default policy.
            return false;
        }
    }

    static String runtimeLabel(Context context) {
        if (!Process.is64Bit()) return "Runtime: 32-bit · low-memory profile";
        return shouldUseLowMemoryProfile(context)
                ? "Runtime: 64-bit · low-RAM profile"
                : "Runtime: 64-bit · standard profile";
    }
}
