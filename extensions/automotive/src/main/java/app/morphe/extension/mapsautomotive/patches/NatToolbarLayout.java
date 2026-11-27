package app.morphe.extension.mapsautomotive.patches;

import java.util.function.Predicate;

/** Pure geometry shared by the Android toolbar and regression tests. */
final class NatToolbarLayout {
    static final int LEGACY_COUNT=4, FULL_COUNT=5;
    private NatToolbarLayout(){}
    static final class Slot {
        final int x,y,w,h,count;final boolean vertical;
        Slot(int x,int y,int w,int h,int count,boolean vertical){this.x=x;this.y=y;this.w=w;this.h=h;this.count=count;this.vertical=vertical;}
    }
    /** Prefer the full toolbar, preserve all four original actions if it does not fit.
     * With no verified route or owned search, offer only the read-only diagnostics shortcut. */
    static Slot choose(boolean mapControls,boolean routeControls,int left,int top,int right,int bottom,int size,int gap,int edge,Predicate<Slot> free){
        if(!mapControls||right<=left||bottom<=top||size<=0||gap<0||edge<0||free==null)return null;
        for(int count:routeControls?new int[]{FULL_COUNT,LEGACY_COUNT}:new int[]{1}){
            long len=(long)size*count+(long)gap*(count-1);
            if(len>Integer.MAX_VALUE)continue;
            int length=(int)len;
            for(int orientation=0;orientation<(count==1?1:2);orientation++)
                for(int side=0;side<2;side++)
                    for(float fy:new float[]{.22f,.34f,.10f,.46f}){
                        int w=orientation==0?size:length,h=orientation==0?length:size;
                        int x=side==0?right-edge-w:left+edge;
                        int y=top+(int)((bottom-top)*fy);
                        if(x<left||y<top||(long)x+w>right||(long)y+h>bottom)continue;
                        Slot s=new Slot(x,y,w,h,count,orientation==0);
                        if(free.test(s))return s;
                    }
        }
        return null;
    }
    static boolean hasDiagnostics(int count){return count==1||count==FULL_COUNT;}
    static int diagnosticIndex(int count){return count==1?0:count==FULL_COUNT?LEGACY_COUNT:-1;}
}
