package app.morphe.extension.mapsautomotive.patches;
import java.util.ArrayList;
import java.util.List;
final class NatCardPlacement {
    static List<int[]> candidates(int W,int H,int w,int h,int g,int x,int y,int aw,int ah) {
        List<int[]> out=new ArrayList<>();
        if(w<=0 || h<=0 || W<w || H<h) return out;
        int right=Math.max(0,Math.min(W-w,x+aw-w));
        int left=Math.max(0,Math.min(W-w,x));
        int top=Math.max(0,Math.min(H-h,y));
        int[][] preferred={{right,y-h-g},{left,y-h-g},{x+aw+g,top},{x-w-g,top},{right,y+ah+g},{left,y+ah+g}};
        for(int[] a:preferred) add(out,a[0],a[1],W,H,w,h);
        // Bounded fallback grid; never shrink the card.
        for(int yy=H-h;yy>=0;yy-=Math.max(1,h/2))
            for(int xx=W-w;xx>=0;xx-=Math.max(1,w/2)) add(out,xx,yy,W,H,w,h);
        add(out,0,0,W,H,w,h);
        return out;
    }
    private static void add(List<int[]> out,int x,int y,int W,int H,int w,int h) {
        if(x>=0 && y>=0 && x<=W-w && y<=H-h) out.add(new int[]{x,y});
    }
}
