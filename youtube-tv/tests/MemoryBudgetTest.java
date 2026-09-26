import com.liskovsoft.smartyoutubetv2.common.utils.TvMemoryBudget;

/** Run with java -ea. Checks classification boundaries and allocation budgets. */
public final class MemoryBudgetTest {
    public static void main(String[] args) {
        long gib = 1024L * 1024 * 1024;
        assert TvMemoryBudget.select(false, false, 8 * gib).lite : "32-bit process must be Lite";
        assert TvMemoryBudget.select(true, false, 2 * gib).lite : "2 GB ARM64 must also be protected";
        assert TvMemoryBudget.select(true, true, 8 * gib).lite : "Honor OEM low-RAM flag";
        assert TvMemoryBudget.select(true, false, 0).lite : "Unknown RAM uses safe profile";
        assert TvMemoryBudget.select(true, false, -1).lite : "Invalid RAM uses safe profile";
        assert TvMemoryBudget.select(true, false, TvMemoryBudget.LOW_RAM_CEILING).lite;
        assert !TvMemoryBudget.select(true, false, TvMemoryBudget.LOW_RAM_CEILING + 1).lite;
        TvMemoryBudget lite = TvMemoryBudget.select(false, false, 2 * gib);
        assert lite.videoBytes == 24 * TvMemoryBudget.MIB;
        assert lite.imageBytes + lite.bitmapPoolBytes + lite.arrayPoolBytes == 18 * TvMemoryBudget.MIB;
        assert !TvMemoryBudget.select(true, false, 4 * gib).lite;
        System.out.println("10 memory profile checks passed");
    }
}
