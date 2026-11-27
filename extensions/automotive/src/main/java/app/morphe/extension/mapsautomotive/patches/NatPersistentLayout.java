package app.morphe.extension.mapsautomotive.patches;
import java.util.function.Predicate;
final class NatPersistentLayout {
    private NatPersistentLayout(){}
    static NatToolbarLayout.Slot battery(int left,int top,int right,int bottom,int w,int h,int edge,boolean current,Predicate<NatToolbarLayout.Slot> free){
        if(w<=0||h<=0||edge<0||right<=left||bottom<=top||free==null)return null;
        int[] xs=current?new int[]{left+edge,right-edge-w,left+(right-left-w)/2}:new int[]{right-edge-w,left+(right-left-w)/2,left+edge};
        float[] ys=current?new float[]{0,.12f,.24f,.36f,.48f,.60f,.72f}:new float[]{.50f,.36f,.24f,.12f,0,.62f,.72f};
        for(float fy:ys)for(int x:xs){
            int y=top+edge+(int)((bottom-top)*fy);
            if(x<left||y<top||(long)x+w>right-edge||(long)y+h>bottom-edge)continue;
            NatToolbarLayout.Slot q=new NatToolbarLayout.Slot(x,y,w,h,0,false);if(free.test(q))return q;
        }
        return null;
    }
    static NatToolbarLayout.Slot toolbar(int l,int t,int r,int b,int size,int gap,int edge,boolean collapsed,Predicate<NatToolbarLayout.Slot> free){
        if(!collapsed){NatToolbarLayout.Slot full=NatToolbarLayout.choosePreferred(true,true,l,t,r,b,size,gap,edge,true,q->q.count==5&&free.test(q));if(full!=null)return full;}
        return NatToolbarLayout.choosePreferred(true,false,l,t,r,b,size,gap,edge,true,free);
    }
    static String soc(Integer value){return value==null?"—":value+"%";}
    static boolean intersects(NatToolbarLayout.Slot a,NatToolbarLayout.Slot b){return a!=null&&b!=null&&a.x<(long)b.x+b.w&&b.x<(long)a.x+a.w&&a.y<(long)b.y+b.h&&b.y<(long)a.y+a.h;}
}
