package android.view;

public class MotionEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_HOVER_MOVE = 7;
    public static final int ACTION_BUTTON_PRESS = 11;
    public static final int ACTION_BUTTON_RELEASE = 12;
    public static final int BUTTON_PRIMARY = 1;
    public static final int TOOL_TYPE_MOUSE = 3;

    public static class PointerProperties {
        public int id;
        public int toolType;
    }

    public static class PointerCoords {
        public float x;
        public float y;
        public float pressure;
    }

    public static MotionEvent obtain(long a, long b, int c, float d, float e, int f) {
        return new MotionEvent();
    }

    public static MotionEvent obtain(
            long downTime,
            long eventTime,
            int action,
            int pointerCount,
            PointerProperties[] pointerProperties,
            PointerCoords[] pointerCoords,
            int metaState,
            int buttonState,
            float xPrecision,
            float yPrecision,
            int deviceId,
            int edgeFlags,
            int source,
            int flags) {
        return new MotionEvent();
    }

    public void setSource(int source) {}

    public void recycle() {}
}
