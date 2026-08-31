package android.view;

public class Window {
    public static final int FEATURE_NO_TITLE = 1;
    public View getDecorView() { return new View(null); }
    public void setLayout(int w, int h) {}
    public void setGravity(int gravity) {}
    public void setDimAmount(float amount) {}
    public void addFlags(int flags) {}
    public void clearFlags(int flags) {}
}
