package app.morphe.extension.mapsautomotive.patches;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.WeakHashMap;

/** Experimental non-interactive overlay; native navigation remains untouched. */
final class NatNavigationCard {
    private static final boolean VERIFIED_NAVIGATION_HOOK = false; // Fail closed until route identity and safe layout are proven.
    private static final WeakHashMap<View, Card> CARDS = new WeakHashMap<>();
    static void clear(View root) {
        Card c = CARDS.remove(root);
        if (c != null) root.getOverlay().remove(c);
    }
    private static void find(View v, java.util.List<TextView> matches, int depth) {
        if (depth > 40 || !v.isShown() || matches.size() > 1) return;
        if (v instanceof TextView && !(v instanceof android.widget.EditText)) {
            if (NatNavigationSummary.parse(((TextView)v).getText()) != null) matches.add((TextView)v);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup)v;
            for (int i=0;i<g.getChildCount();i++) find(g.getChildAt(i),matches,depth+1);
        }
    }
    static void refresh(View root) {
        if (!VERIFIED_NAVIGATION_HOOK) { clear(root); return; }
        java.util.ArrayList<TextView> matches = new java.util.ArrayList<>();
        find(root,matches,0);
        if (matches.size()!=1) { clear(root); return; }
        TextView tv=matches.get(0);
        Integer soc=NatNavigationSummary.parse(tv.getText());
        if (soc==null) { clear(root); return; }
        Rect visible=new Rect();
        if (!tv.getGlobalVisibleRect(visible)) { clear(root); return; }
        int[] origin=new int[2]; root.getLocationOnScreen(origin);
        int[] pos=new int[2]; tv.getLocationOnScreen(pos);
        float d=root.getResources().getDisplayMetrics().density;
        int w=Math.round(156*d), h=Math.round(84*d), gap=Math.round(8*d);
        int x=pos[0]-origin[0]+tv.getWidth()-w, y=pos[1]-origin[1]-h-gap;
        // Avoid drawing outside the root or over the native summary itself.
        if (x<0 || y<0 || x+w>root.getWidth() || y+h>root.getHeight()) { clear(root); return; }
        boolean thai=tv.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("th") || tv.getText().toString().matches("(?s).*[\\u0E00-\\u0E7F].*");
        boolean dark=ColorScheme.nightColors(tv.getContext());
        Card c=CARDS.get(root);
        if(c==null) { c=new Card(); CARDS.put(root,c); root.getOverlay().add(c); }
        c.soc=soc; c.thai=thai; c.dark=dark; c.setBounds(x,y,x+w,y+h); c.invalidateSelf();
    }
    private static final class Card extends Drawable {
        int soc; boolean thai,dark;
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c) {
            Rect b=getBounds(); float u=b.width()/156f;
            p.setColor(dark?0xff172b40:0xffe5f1f7);
            c.drawRoundRect(new RectF(b),10*u,10*u,p);
            p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT); p.setTextSize(12*u);
            p.setColor(dark?Color.WHITE:0xff172b40);
            c.drawText(thai?"แบตตามเส้นทาง":"Route battery",b.exactCenterX(),b.top+16*u,p);
            p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(32*u);
            if(soc<0) p.setColor(dark?0xffff8888:0xffb91c1c);
            c.drawText(soc+"%",b.exactCenterX(),b.top+49*u,p);
            p.setTypeface(Typeface.DEFAULT); p.setTextSize(11*u);
            if(soc<0) c.drawText(thai?"ต้องชาร์จ":"Charge required",b.exactCenterX(),b.top+63*u,p);
            float l=b.left+12*u,r=b.right-12*u,t=b.bottom-12*u;
            p.setColor(dark?0xff485461:0xffc2ced8); c.drawRoundRect(l,t,r,t+5*u,2*u,2*u,p);
            p.setColor(soc<20?0xffef4444:soc<35?0xffd97706:0xff179b64);
            if(soc>0)c.drawRoundRect(l,t,l+(r-l)*NatSignedSoc.bar(soc)/100f,t+5*u,2*u,2*u,p);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
