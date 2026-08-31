package android.view;

public class KeyEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1, META_CTRL_ON = 4096;
    public static final int KEYCODE_BACK = 4, KEYCODE_DPAD_LEFT = 21, KEYCODE_DPAD_RIGHT = 22,
            KEYCODE_DPAD_UP = 19, KEYCODE_DPAD_DOWN = 20, KEYCODE_DPAD_CENTER = 23,
            KEYCODE_ENTER = 66, KEYCODE_NUMPAD_ENTER = 160, KEYCODE_BUTTON_SELECT = 109,
            KEYCODE_MENU = 82, KEYCODE_INFO = 165, KEYCODE_GUIDE = 172, KEYCODE_M = 41,
            KEYCODE_L = 40;

    private int action, key, repeat, meta;

    public KeyEvent(long a, long b, int action, int key, int repeat, int meta) {
        this.action = action;
        this.key = key;
        this.repeat = repeat;
        this.meta = meta;
    }

    public KeyEvent(KeyEvent other) {
        this.action = other.action;
        this.key = other.key;
        this.repeat = other.repeat;
        this.meta = other.meta;
    }

    public int getAction() { return action; }
    public int getKeyCode() { return key; }
    public int getRepeatCount() { return repeat; }
    public boolean isCtrlPressed() { return (meta & META_CTRL_ON) != 0; }
    public boolean isShiftPressed() { return false; }
}
