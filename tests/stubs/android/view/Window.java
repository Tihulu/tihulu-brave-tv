package android.view;

public class Window {
    public View getDecorView() { return new View(null); }
    public void setLayout(int w, int h) {}
    public void setGravity(int gravity) {}
    public void setDimAmount(float amount) {}
}
