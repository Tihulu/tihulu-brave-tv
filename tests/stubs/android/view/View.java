package android.view;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
public class View {
    public static final int VISIBLE=0,GONE=8;
    protected Context c;
    public View(Context c){this.c=c;}
    public interface OnClickListener { void onClick(View v); }
    public interface OnFocusChangeListener { void onFocusChange(View v, boolean hasFocus); }
    public interface OnLayoutChangeListener { void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int orr,int ob); }
    public void setBackground(android.graphics.drawable.GradientDrawable d){}
    public void setMinHeight(int h){}
    public View findFocus(){return null;}
    public void setFocusable(boolean v){}
    public void setClickable(boolean v){}
    public void setPadding(int l,int t,int r,int b){}
    public void setBackgroundColor(int color){}
    public void setScaleX(float value){}
    public void setScaleY(float value){}
    protected void onDraw(Canvas c){}
    public int getWidth(){return 100;}
    public int getHeight(){return 100;}
    public void measure(int w,int h){}
    public int getMeasuredHeight(){return 100;}
    public static class MeasureSpec { public static final int EXACTLY=1,AT_MOST=2; public static int makeMeasureSpec(int size,int mode){return size;} }
    public void setVisibility(int v){}
    public void setTranslationX(float x){}
    public void setTranslationY(float y){}
    public void invalidate(){}
    public void layout(int l,int t,int r,int b){}
    public void post(Runnable r){r.run();}
    public void addOnLayoutChangeListener(OnLayoutChangeListener l){}
    public ViewOverlay getOverlay(){return new ViewOverlay();}
    public boolean dispatchGenericMotionEvent(MotionEvent e){return false;}
    public boolean dispatchTouchEvent(MotionEvent e){return false;}
    public Resources getResources(){return c==null?new Resources():c.getResources();}
    public void setOnClickListener(OnClickListener l){}
    public OnFocusChangeListener focusListener;
    public void setOnFocusChangeListener(OnFocusChangeListener l){focusListener=l;}
    public boolean requestFocus(){return true;}
}
