package app.morphe.extension.mapsautomotive.patches;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry; coordinates use the same space as the root's visible rectangle. */
final class NatBatteryPlacement {
    static final class Box {
        final int x,y,w,h;
        Box(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;}
        boolean contains(Box b){return b.x>=x && b.y>=y && b.x+b.w<=x+w && b.y+b.h<=y+h;}
        boolean overlaps(Box b){return x<b.x+b.w && x+w>b.x && y<b.y+b.h && y+h>b.y;}
    }
    private static void add(List<Box> out, Box viewport, int x,int y,int w,int h){
        Box b=new Box(x,y,w,h); if(viewport.contains(b)) out.add(b);
    }
    static List<Box> candidates(Box v, Box a, int w, int h, int gap) {
        List<Box> out=new ArrayList<>();
        if(w<=0 || h<=0 || v.w<w || v.h<h) return out;
        int left=v.x+gap, right=v.x+v.w-w-gap, top=v.y+gap, bottom=v.y+v.h-h-gap;
        int x1=Math.max(v.x,Math.min(a.x+a.w-w,v.x+v.w-w));
        int x2=Math.max(v.x,Math.min(a.x,v.x+v.w-w));
        // Prefer next to native summary, then bounded edges. Never place across the anchor itself.
        add(out,v,x1,a.y-h-gap,w,h); add(out,v,x2,a.y-h-gap,w,h);
        add(out,v,a.x-w-gap,a.y+a.h-h,w,h); add(out,v,a.x+a.w+gap,a.y+a.h-h,w,h);
        add(out,v,x1,a.y+a.h+gap,w,h); add(out,v,x2,a.y+a.h+gap,w,h);
        add(out,v,left,bottom,w,h); add(out,v,right,bottom,w,h);
        add(out,v,left,top,w,h); add(out,v,right,top,w,h);
        add(out,v,left,v.y+(v.h-h)/2,w,h); add(out,v,right,v.y+(v.h-h)/2,w,h);
        out.removeIf(b->b.overlaps(a));
        return out;
    }
    private static void unique(List<Box> out,Box v,int x,int y,int w,int h) {
        if(w<=0 || h<=0) return;
        Box b=new Box(x,y,w,h);
        if(!v.contains(b)) return;
        for(Box old:out) if(old.x==x && old.y==y && old.w==w && old.h==h) return;
        out.add(b);
    }
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(value,max));}
    // Within native panel: align to the right and to the SOC's vertical center first.
    static List<Box> integrated(Box panel,Box soc,int w,int h,int gap) {
        List<Box> out=new ArrayList<>();
        if(w<=0 || h<=0 || w>panel.w || h>panel.h) return out;
        int right=panel.x+panel.w-w, bottom=panel.y+panel.h-h;
        int cy=clamp(soc.y+soc.h/2-h/2,panel.y,bottom);
        int cx=clamp(soc.x+soc.w/2-w/2,panel.x,right);
        int[] xs={Math.max(panel.x,right-gap),right,cx,clamp(soc.x,panel.x,right),clamp(soc.x+soc.w-w,panel.x,right)};
        int[] ys={cy,clamp(soc.y,panel.y,bottom),clamp(soc.y+soc.h-h,panel.y,bottom),Math.min(bottom,panel.y+gap),panel.y,bottom};
        for(int y:ys) for(int x:xs) unique(out,panel,x,y,w,h);
        return out;
    }
    // Fixed right edge, aligned with the summary SOC row. No floating positions.
    static List<Box> rightCell(Box panel, Box soc, int w, int h, int gap) {
        List<Box> out = new ArrayList<>();
        if (w <= 0 || h <= 0 || w > panel.w || h > panel.h) return out;
        int x = panel.x + panel.w - w - Math.min(gap, panel.w - w);
        int bottom = panel.y + panel.h - h;
        int y = clamp(soc.y + soc.h / 2 - h / 2, panel.y, bottom);
        unique(out, panel, x, y, w, h);
        unique(out, panel, x, panel.y, w, h);
        unique(out, panel, x, clamp(soc.y + soc.h - h, panel.y, bottom), w, h);
        return out;
    }
    // Adjacent to the summary only: no arbitrary map-corner placements.
    static List<Box> adjacent(Box viewport,Box panel,Box soc,int w,int h,int gap) {
        List<Box> out=new ArrayList<>();
        if(w<=0 || h<=0 || w>viewport.w || h>viewport.h) return out;
        int x=clamp(panel.x+panel.w-w,viewport.x,viewport.x+viewport.w-w);
        int left=clamp(panel.x,viewport.x,viewport.x+viewport.w-w);
        int cy=clamp(soc.y+soc.h/2-h/2,viewport.y,viewport.y+viewport.h-h);
        unique(out,viewport,x,panel.y-h-gap,w,h);
        unique(out,viewport,left,panel.y-h-gap,w,h);
        unique(out,viewport,panel.x+panel.w+gap,cy,w,h);
        unique(out,viewport,panel.x-w-gap,cy,w,h);
        unique(out,viewport,x,panel.y+panel.h+gap,w,h);
        out.removeIf(b->b.overlaps(panel)); return out;
    }
}
