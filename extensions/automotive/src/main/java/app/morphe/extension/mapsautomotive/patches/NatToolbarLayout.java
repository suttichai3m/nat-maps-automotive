package app.morphe.extension.mapsautomotive.patches;

import java.util.function.Predicate;

/** Pure geometry shared by the Android toolbar and regression tests. */
final class NatToolbarLayout {
    static final int LEGACY_COUNT=4, FULL_COUNT=5, SPLIT_COUNT=6, IDLE_SPLIT_COUNT=2;
    private NatToolbarLayout(){}
    static final class Slot {
        final int x,y,w,h,count;final boolean vertical;
        Slot(int x,int y,int w,int h,int count,boolean vertical){this.x=x;this.y=y;this.w=w;this.h=h;this.count=count;this.vertical=vertical;}
    }
    /** Prefer the full toolbar, preserve all four original actions if it does not fit.
     * With no verified route or owned search, offer only the read-only diagnostics shortcut. */
    static Slot choose(boolean mapControls,boolean routeControls,int left,int top,int right,int bottom,int size,int gap,int edge,Predicate<Slot> free){
        return choosePreferred(mapControls,routeControls,left,top,right,bottom,size,gap,edge,false,free);
    }
    static Slot choosePreferred(boolean mapControls,boolean routeControls,int left,int top,int right,int bottom,int size,int gap,int edge,boolean leftFirst,Predicate<Slot> free){
        return chooseCounts(mapControls,left,top,right,bottom,size,gap,edge,leftFirst,free,routeControls?new int[]{FULL_COUNT,LEGACY_COUNT}:new int[]{1});
    }
    /** Six keys in navigation, Diagnostics + Split when idle. Prefer vertical placement; never cover native controls. */
    static Slot chooseWithSplit(boolean mapControls,boolean routeControls,int left,int top,int right,int bottom,int size,int gap,int edge,boolean leftFirst,Predicate<Slot> free){
        return chooseCounts(mapControls,left,top,right,bottom,size,gap,edge,leftFirst,free,routeControls?new int[]{SPLIT_COUNT,FULL_COUNT,LEGACY_COUNT}:new int[]{IDLE_SPLIT_COUNT,1});
    }
    static Slot chooseV36(boolean mapControls,boolean routeControls,int left,int top,int right,int bottom,int size,int gap,int edge,boolean leftFirst,Predicate<Slot> free){
        return chooseCounts(mapControls,left,top,right,bottom,size,gap,edge,leftFirst,free,routeControls?new int[]{7,6,5,4}:new int[]{3,2,1});
    }
    static Slot chooseV37Ev(boolean mapControls,boolean routeControls,int left,int top,int right,int bottom,int size,int gap,int edge,boolean leftFirst,Predicate<Slot> free){
        return chooseCounts(mapControls,left,top,right,bottom,size,gap,edge,leftFirst,free,routeControls?new int[]{7,6,5,4}:new int[]{4,3,2,1});
    }
    static Slot chooseV37Mode(boolean map,boolean route,boolean ev,int left,int top,int right,int bottom,int size,int gap,int edge,boolean leftFirst,Predicate<Slot> free){
        return chooseCounts(map,left,top,right,bottom,size,gap,edge,leftFirst,free,route?(ev?new int[]{7,6,5,4,1}:new int[]{7,6,5,4}):new int[]{4,3,2,1});
    }
    private static Slot chooseCounts(boolean mapControls,int left,int top,int right,int bottom,int size,int gap,int edge,boolean leftFirst,Predicate<Slot> free,int[] counts){
        if(!mapControls||right<=left||bottom<=top||size<=0||gap<0||edge<0||free==null)return null;
        for(int count:counts){
            long len=(long)size*count+(long)gap*(count-1);
            if(len>Integer.MAX_VALUE)continue;
            int length=(int)len;
            for(int orientation=0;orientation<(count==1?1:2);orientation++)
                for(int side=0;side<2;side++)
                    for(float fy:new float[]{.22f,.34f,.10f,.46f}){
                        int w=orientation==0?size:length,h=orientation==0?length:size;
                        int x=(side==0)==leftFirst?left+edge:right-edge-w;
                        int y=top+(int)((bottom-top)*fy);
                        if(x<left||y<top||(long)x+w>right||(long)y+h>bottom)continue;
                        Slot s=new Slot(x,y,w,h,count,orientation==0);
                        if(free.test(s))return s;
                    }
        }
        return null;
    }
    static Slot currentBattery(int left,int top,int right,int bottom,int width,int height,int edge,Predicate<Slot> free){
        if(width<=0||height<=0||edge<0||free==null)return null;
        long x=(long)left+edge,y=(long)top+edge;
        if(x+width>right||y+height>bottom)return null;
        Slot slot=new Slot((int)x,(int)y,width,height,0,false);
        return free.test(slot)?slot:null;
    }
    static boolean hasDiagnostics(int count){return diagnosticIndex(count)>=0;}
    static int diagnosticIndex(int count){return count==1||count==IDLE_SPLIT_COUNT?0:count==FULL_COUNT||count==SPLIT_COUNT?LEGACY_COUNT:-1;}
    static int splitIndex(int count){return count==IDLE_SPLIT_COUNT?1:count==SPLIT_COUNT?5:-1;}
}
