package com.liskovsoft.smartyoutubetv2.common.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

/** Uses process bitness, not the CPU model: many 64-bit TVs run a 32-bit OS. */
public final class TvDeviceProfile {
    private static volatile TvMemoryBudget budget;

    public static TvMemoryBudget get(Context context) {
        TvMemoryBudget result = budget;
        if (result != null) return result;
        synchronized (TvDeviceProfile.class) {
            if (budget == null) {
                ActivityManager manager = (ActivityManager) context.getApplicationContext()
                        .getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                if (manager != null) manager.getMemoryInfo(info);
                boolean lowRam = manager == null || manager.isLowRamDevice();
                boolean is64Bit = Build.VERSION.SDK_INT >= 23 && Process.is64Bit();
                budget = TvMemoryBudget.select(is64Bit, lowRam, info.totalMem);
            }
            return budget;
        }
    }

    private TvDeviceProfile() {}
}
