package app.morphe.extension.mapsautomotive.patches;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.WeakHashMap;

/** Experimental non-interactive overlay; native navigation remains untouched. */
final class NatNavigationCard {
    private static final WeakHashMap<View, Card> CARDS = new WeakHashMap<>();
    static void clear(View root) {
        Card c = CARDS.remove(root);
        if (c != null) root.getOverlay().remove(c);
    }
    static void invalidateForecast(View root) {
        Card c = CARDS.get(root);
        if (c != null && c.soc != null) { c.soc = null; c.invalidateSelf(); }
    }
    static void invalidateIfChanged(View root, Integer soc) {
        Card c = CARDS.get(root);
        if (c != null && !java.util.Objects.equals(c.soc, soc)) invalidateForecast(root);
    }
    static void refresh(View root, java.util.List<TextView> matches) {
        if (matches.isEmpty()) { pending(root); return; }
        TextView tv=matches.get(0);
        Integer soc=NatNavigationSummary.parse(tv.getText());

        for (TextView match : matches) {
            if (!java.util.Objects.equals(soc,NatNavigationSummary.parse(match.getText()))) soc=null;
        }
        Rect visible=new Rect();
        if (!tv.getGlobalVisibleRect(visible)) { pending(root); return; }
        int[] origin=new int[2]; root.getLocationOnScreen(origin);
        int[] pos=new int[2]; tv.getLocationOnScreen(pos);
        float d=root.getResources().getDisplayMetrics().density;
        int w=Math.round(220*d), h=Math.round(124*d), gap=Math.round(8*d);
        int ax=pos[0]-origin[0], ay=pos[1]-origin[1];
        Rect anchor=new Rect(visible); anchor.offset(-origin[0],-origin[1]);
        Rect chosen=null;
        for (int[] xy : NatCardPlacement.candidates(root.getWidth(),root.getHeight(),w,h,gap,ax,ay,tv.getWidth(),tv.getHeight())) {
            Rect local=new Rect(xy[0],xy[1],xy[0]+w,xy[1]+h);
            if (Rect.intersects(local,anchor)) continue;
            Rect target=new Rect(local); target.offset(origin[0],origin[1]);
            if (!obstructs(root,target,tv,0)) { chosen=local; break; }
        }
        if (chosen==null) { pending(root); return; }
        int x=chosen.left,y=chosen.top;
        boolean thai=tv.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("th") || tv.getText().toString().matches("(?s).*[\\u0E00-\\u0E7F].*");
        boolean dark=ColorScheme.nightColors(tv.getContext());
        Card c=CARDS.get(root);
        if(c==null) { c=new Card(); CARDS.put(root,c); root.getOverlay().add(c); }
        c.lastSeen=android.os.SystemClock.elapsedRealtime();
        c.expiryQueued=false;
        boolean changed=!java.util.Objects.equals(c.soc,soc) || c.thai!=thai || c.dark!=dark;
        c.soc=soc; c.thai=thai; c.dark=dark;
        if (!c.getBounds().equals(chosen)) c.setBounds(chosen);
        if (changed) c.invalidateSelf();
    }
    // Brief layout transitions may hide the native summary. Never retain stale SOC.
    private static void pending(View root) {
        Card c=CARDS.get(root);
        if(c==null) return;
        if(!NatCardContinuity.keep(android.os.SystemClock.elapsedRealtime()-c.lastSeen)
                || c.getBounds().right>root.getWidth() || c.getBounds().bottom>root.getHeight()) {
            clear(root); return;
        }
        int[] origin=new int[2]; root.getLocationOnScreen(origin);
        Rect target=new Rect(c.getBounds()); target.offset(origin[0],origin[1]);
        if(obstructs(root,target,null,0)) { clear(root); return; }
        if (!c.expiryQueued) {
            c.expiryQueued = true;
            java.lang.ref.WeakReference<View> ref = new java.lang.ref.WeakReference<>(root);
            long seen = c.lastSeen;
            root.postDelayed(() -> {
                View v = ref.get();
                if (v != null && CARDS.get(v) == c && c.lastSeen == seen
                        && !NatCardContinuity.keep(android.os.SystemClock.elapsedRealtime()-seen)) clear(v);
            }, Math.max(1, NatCardContinuity.MAX_MS+1-(android.os.SystemClock.elapsedRealtime()-seen)));
        }
        boolean dark=ColorScheme.nightColors(root.getContext());
        if(c.soc!=null || c.dark!=dark) { c.soc=null; c.dark=dark; c.invalidateSelf(); }
    }
    // Never cover visible text or actionable leaf controls.
    private static boolean obstructs(View v, Rect target, View anchor, int depth) {
        if (!v.isShown() || v == anchor) return false;
        if (depth > 40) return true;
        Rect r = new Rect();
        if (!v.getGlobalVisibleRect(r) || !Rect.intersects(r, target)) return false;
        if (v instanceof TextView || (v.isClickable() && !(v instanceof ViewGroup))) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) if(obstructs(g.getChildAt(i),target,anchor,depth+1)) return true;
        }
        return false;
    }
    private static final class Card extends Drawable {
        Integer soc; boolean thai,dark,expiryQueued; long lastSeen;
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c) {
            Rect b=getBounds(); float u=b.width()/220f;
            p.setColor(dark?0xff172b40:0xffe5f1f7);
            c.drawRoundRect(new RectF(b),10*u,10*u,p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f*u);
            p.setColor(dark?0xff367cb3:0xff93c5fd);
            c.drawRoundRect(new RectF(b.left+u,b.top+u,b.right-u,b.bottom-u),10*u,10*u,p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT); p.setTextSize(12*u);
            p.setColor(dark?Color.WHITE:0xff172b40);
            c.drawText(thai?"แบตตามเส้นทาง":"Route battery",b.exactCenterX(),b.top+21*u,p);
            p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(50*u);
            if(soc!=null && soc<0) p.setColor(dark?0xffff8888:0xffb91c1c);
            c.drawText(soc==null?"--%":soc+"%",b.exactCenterX(),b.top+79*u,p);
            p.setTypeface(Typeface.DEFAULT); p.setTextSize(11*u);
            if(soc!=null && soc<0) c.drawText(thai?"ต้องชาร์จ":"Charge required",b.exactCenterX(),b.top+94*u,p);
            float l=b.left+12*u,r=b.right-12*u,t=b.bottom-18*u;
            p.setColor(dark?0xff485461:0xffc2ced8); c.drawRoundRect(l,t,r,t+10*u,2*u,2*u,p);
            p.setColor(soc==null?0xff64748b:soc<20?0xffef4444:soc<35?0xffd97706:0xff179b64);
            if(soc!=null && soc>0)c.drawRoundRect(l,t,l+(r-l)*NatSignedSoc.bar(soc)/100f,t+10*u,2*u,2*u,p);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
