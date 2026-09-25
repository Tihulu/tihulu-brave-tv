package android.widget;
import android.content.Context;
public class EditText extends TextView {
    public EditText(Context c){super(c);}
    public void setSingleLine(boolean v){}
    public void setHintTextColor(int color){}
    public void setHint(String hint){}
    public void setInputType(int type){}
    public void setImeOptions(int options){}
    public void setSelectAllOnFocus(boolean value){}
    public interface OnEditorActionListener { boolean onEditorAction(TextView v, int action, android.view.KeyEvent event); }
    public OnEditorActionListener editorListener;
    public void setOnEditorActionListener(OnEditorActionListener l){editorListener=l;}
}
