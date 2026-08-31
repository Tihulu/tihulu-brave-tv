package android.app;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.Window;
public class Dialog implements DialogInterface {
    private boolean showing;
    public Dialog(Context c) {}
    public void setContentView(View v) {}
    public Window getWindow() { return new Window(); }
    public void setOnShowListener(DialogInterface.OnShowListener l) {}
    public void setOnDismissListener(DialogInterface.OnDismissListener l) {}
    public void setOnKeyListener(DialogInterface.OnKeyListener l) {}
    public void show() { showing = true; }
    public void dismiss() { showing = false; }
    public boolean isShowing() { return showing; }
}
