package app.morphe.extension.mapsautomotive.patches;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import java.util.*;
import java.lang.ref.WeakReference;

/** Dedicated measured child in a native vertical summary container; never covers controls. */
final class NatDedicatedFrame {
    private static final WeakHashMap<View, WeakReference<BatteryView>> frames = new WeakHashMap<>();
    static boolean owns(View v) { return v instanceof BatteryView; }
    static void clear(View root) {
        WeakReference<BatteryView> ref=frames.remove(root);
        BatteryView card=ref==null?null:ref.get();
        if(card!=null && card.getParent() instanceof ViewGroup) ((ViewGroup)card.getParent()).removeView(card);
    }
    static void refresh(View root, List<TextView> matches) {
        WeakReference<BatteryView> ref=frames.get(root);
        BatteryView card=ref==null?null:ref.get();
        TextView anchor=null; Integer soc=null; boolean conflict=false;
        for(TextView tv:matches) {
            Integer n=NatNavigationSummary.localSoc(tv.getText());
            if(n==null)n=NatSignedSoc.parse(NatNavigationSummary.normalize(tv.getText()));
            if(anchor==null)anchor=tv;
            if(n!=null){if(soc!=null&&!soc.equals(n))conflict=true; soc=n;}
        }
        if(conflict)soc=null;
        if(anchor==null){if(card!=null && card.isShown())card.update(null); return;}
        LinearLayout host=null; ViewParent parent=anchor.getParent();
        for(int i=0;i<4 && parent instanceof ViewGroup;i++,parent=parent.getParent()) {
            if(parent instanceof LinearLayout && ((LinearLayout)parent).getOrientation()==LinearLayout.VERTICAL){host=(LinearLayout)parent;break;}
        }
        if(host==null){clear(root);return;}
        float d=root.getResources().getDisplayMetrics().density;
        int w=Math.round(220*d),h=Math.round(124*d);
        if(host.getWidth()-host.getPaddingLeft()-host.getPaddingRight()<w)return;
        if(card==null || card.getParent()!=host){clear(root);card=new BatteryView(host.getContext());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(w,h); lp.gravity=Gravity.CENTER_HORIZONTAL;
            host.addView(card,lp);frames.put(root,new WeakReference<>(card));
        }
        card.update(soc);
    }
    private static final class BatteryView extends View {
        Integer soc; boolean dark; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        BatteryView(android.content.Context c){super(c);setClickable(false);setFocusable(false);}
        void update(Integer n){boolean mode=ColorScheme.nightColors(getContext());if(!Objects.equals(soc,n)||mode!=dark){soc=n;dark=mode;invalidate();}setContentDescription("Route battery "+(n==null?"unavailable":n+"%"));}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density;c.save();c.scale(d,d);
            p.setColor(dark?0xff172b40:0xffe5f1f7);c.drawRoundRect(0,0,220,124,10,10,p);
            p.setColor(dark?Color.WHITE:0xff172b40);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT);p.setTextSize(12);c.drawText("Route battery",110,20,p);
            p.setColor(soc!=null&&soc<0?0xffef4444:0xff179b64);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawRoundRect(12,40,44,65,3,3,p);p.setStyle(Paint.Style.FILL);c.drawRect(44,47,48,58,p);
            if(soc!=null&&soc>0)c.drawRect(15,43,15+26*NatSignedSoc.bar(soc)/100f,62,p);
            p.setColor(soc!=null&&soc<0?(dark?0xffff8888:0xffb91c1c):(dark?Color.WHITE:0xff172b40));p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(50);c.drawText(soc==null?"--%":soc+"%",132,79,p);
            p.setColor(dark?0xff485461:0xffc2ced8);c.drawRoundRect(12,106,208,116,3,3,p);if(soc!=null&&soc>0){p.setColor(0xff179b64);c.drawRoundRect(12,106,12+196*NatSignedSoc.bar(soc)/100f,116,3,3,p);}c.restore();
        }
    }
}
