package android.widget;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
public class LinearLayout extends ViewGroup {
    public static final int VERTICAL=1;
    public LinearLayout(Context c){super(c);}
    public void setOrientation(int o){}
    public void setPadding(int a,int b,int c,int d){}
    public void setBackgroundColor(int c){}
    public void addView(View v, LayoutParams p){}
    public static class LayoutParams extends ViewGroup.LayoutParams {
        public float weight;
        public LayoutParams(int w,int h){super(w,h);}
        public LayoutParams(int w,int h,float weight){super(w,h);this.weight=weight;}
    }
}
