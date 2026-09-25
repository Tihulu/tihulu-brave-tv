package android.view;
public class MotionEvent {
 public static final int ACTION_HOVER_MOVE=7,ACTION_DOWN=0,ACTION_UP=1,ACTION_SCROLL=8,TOOL_TYPE_MOUSE=3,AXIS_HSCROLL=10,AXIS_VSCROLL=9;
 public static class PointerProperties { public int id,toolType; }
 public static class PointerCoords { public float x,y; private float h,v; public void setAxisValue(int a,float value){if(a==AXIS_HSCROLL)h=value;else v=value;} }
 public int action,source; public float x,y,h,v; public boolean recycled;
 public static MotionEvent obtain(long a,long b,int c,float d,float e,int f){MotionEvent m=new MotionEvent();m.action=c;m.x=d;m.y=e;return m;}
 public static MotionEvent obtain(long a,long b,int c,int count,PointerProperties[] p,PointerCoords[] q,int meta,int buttons,float xp,float yp,int device,int edge,int source,int flags){
 MotionEvent m=obtain(a,b,c,q[0].x,q[0].y,meta);m.h=q[0].h;m.v=q[0].v;m.source=source;return m;}
 public void setSource(int s){source=s;} public void recycle(){recycled=true;}
}