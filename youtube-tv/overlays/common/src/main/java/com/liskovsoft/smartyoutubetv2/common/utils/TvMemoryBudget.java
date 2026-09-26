package com.liskovsoft.smartyoutubetv2.common.utils;

/** Explicit allocation budgets. These are NOT a prediction of total process PSS. */
public final class TvMemoryBudget {
    public static final int MIB = 1024 * 1024;
    // OEMs reserve part of RAM for graphics. Include nominal 2 GB devices with rounding.
    public static final long LOW_RAM_CEILING = 2304L * MIB;
    public final boolean lite;
    public final int videoBytes;
    public final int imageBytes;
    public final int bitmapPoolBytes;
    public final int arrayPoolBytes;
    public final int diskCacheBytes;

    private TvMemoryBudget(boolean lite) {
        this.lite = lite;
        videoBytes = (lite ? 24 : 64) * MIB;
        imageBytes = (lite ? 12 : 32) * MIB;
        bitmapPoolBytes = (lite ? 4 : 8) * MIB;
        arrayPoolBytes = (lite ? 2 : 4) * MIB;
        diskCacheBytes = (lite ? 48 : 96) * MIB;
    }

    public static TvMemoryBudget select(boolean process64Bit, boolean lowRam, long totalRam) {
        // Unknown RAM and pre-23 Android fall back conservatively.
        return new TvMemoryBudget(!process64Bit || lowRam || totalRam <= LOW_RAM_CEILING);
    }
}
