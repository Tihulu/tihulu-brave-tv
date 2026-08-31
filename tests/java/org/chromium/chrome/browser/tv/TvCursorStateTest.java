package org.chromium.chrome.browser.tv;

public final class TvCursorStateTest {
    public static void main(String[] args) {
        TvCursorState cursor = new TvCursorState(1920, 1080, 8);
        assertNear(cursor.x(), 960);
        assertNear(cursor.y(), 540);

        cursor.move(-5000, -5000);
        assertNear(cursor.x(), 8);
        assertNear(cursor.y(), 8);

        cursor.move(5000, 5000);
        assertNear(cursor.x(), 1912);
        assertNear(cursor.y(), 1072);

        cursor.resize(100, 40);
        assertNear(cursor.x(), 92);
        assertNear(cursor.y(), 32);

        cursor.center();
        assertNear(cursor.x(), 50);
        assertNear(cursor.y(), 20);

        TvCursorState tiny = new TvCursorState(10, 6, 20);
        tiny.move(-100, -100);
        assertNear(tiny.x(), 5);
        assertNear(tiny.y(), 3);

        boolean threw = false;
        try {
            new TvCursorState(-1, 2, 0);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        assert threw;

        System.out.println("TvCursorStateTest passed");
    }

    private static void assertNear(float actual, float expected) {
        assert Math.abs(actual - expected) < 0.001f
                : "expected " + expected + ", got " + actual;
    }
}
