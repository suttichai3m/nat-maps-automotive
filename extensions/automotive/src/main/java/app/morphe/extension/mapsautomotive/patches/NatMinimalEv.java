package app.morphe.extension.mapsautomotive.patches;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** Experimental bridge to the installed Maps native controls. No network calls, no overlay data, no Back/route cancellation. */
final class NatMinimalEv {
    private static final String PREF="nat_minimal_ev";
    private static final WeakHashMap<Activity,State> STATES=new WeakHashMap<>();
    private static final class State {
        WeakReference<TextView> button;
        boolean opened;
        int left=-1,top=-1;
    }
    static boolean enabled(Context c){return c.getSharedPreferences(PREF,0).getBoolean("shortcut",true);}
    static void setEnabled(Activity a,boolean on){a.getSharedPreferences(PREF,0).edit().putBoolean("shortcut",on).apply();if(!on)clear(a);}
    private static String id(View v){try{return v.getId()==View.NO_ID?"":v.getResources().getResourceEntryName(v.getId());}catch(RuntimeException e){return "";}}
    private static boolean in(View v,String name){for(View p=v;p!=null;){if(id(p).equals(name))return true;ViewParent par=p.getParent();p=par instanceof View?(View)par:null;}return false;}
    private static boolean visible(View v){return v.isShown()&&v.getAlpha()>0f&&v.getGlobalVisibleRect(new Rect());}
    private static void collect(View v,List<View> out,int depth,View own){
        if(v==own||depth>40||!visible(v))return;out.add(v);
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),out,depth+1,own);}
    }
    private static CharSequence label(View v){return v instanceof TextView?((TextView)v).getText():v.getContentDescription();}
    private static boolean localized(View v,CharSequence label,String key){
        try{int res=v.getResources().getIdentifier(key,"string",v.getContext().getPackageName());return res!=0&&!NatMinimalEvRules.norm(label).isEmpty()&&NatMinimalEvRules.norm(label).equals(NatMinimalEvRules.norm(v.getResources().getString(res)));}catch(RuntimeException e){return false;}
    }
    private static boolean ev(View v,CharSequence x){return NatMinimalEvRules.ev(x)||localized(v,x,"CAR_SEARCH_CATEGORY_CHARGING_STATION")||localized(v,x,"CAR_SEARCH_CATEGORY_CHARGING_STATION_TITLE");}
    private static boolean along(View v,CharSequence x){return NatMinimalEvRules.along(x)||localized(v,x,"CAR_SEARCH_ALONG_YOUR_ROUTE_HINT");}
    private static View clickable(View v){
        for(int i=0;i<3&&v!=null;i++){
            float d=v.getResources().getDisplayMetrics().density;
            if(v.isEnabled()&&v.isClickable()&&visible(v)&&v.getWidth()<=480*d&&v.getHeight()<=100*d)return v;
            ViewParent p=v.getParent();v=p instanceof View?(View)p:null;
        }return null;
    }
    private static void add(List<View> l,View v){if(v!=null&&!l.contains(v))l.add(v);}
    private static View unique(List<View> list){return list.size()==1?list.get(0):null;}
    private static boolean checked(View v){if(v==null)return false;if(v.isSelected()||(v instanceof android.widget.Checkable&&((android.widget.Checkable)v).isChecked()))return true;AccessibilityNodeInfo n=v.createAccessibilityNodeInfo();try{return n!=null&&(n.isSelected()||(n.isCheckable()&&n.isChecked()));}finally{if(n!=null)n.recycle();}}
    private static final class Controls {
        final List<View> open=new ArrayList<>(),along=new ArrayList<>(),hide=new ArrayList<>(),close=new ArrayList<>();
        boolean results,evEvidence,mapControls;
    }
    private static Controls inspect(List<View> views){
        Controls c=new Controls();
        for(View v:views){
            String name=id(v);CharSequence a=label(v),b=v.getContentDescription();
            if(name.equals("navigation_search_results_list"))c.results=true;
            if(name.equals("map_buttons_view_container"))c.mapControls=true;
            boolean e=ev(v,a)||ev(v,b);
            if(e){
                View target=clickable(v);
                boolean category=in(v,"destination_input_search_category_list_view")||in(v,"destination_input_search_category_list_frame_view");
                if(category){if(checked(v)||checked(target))add(c.hide,target);else add(c.open,target);}
                if(v instanceof TextView && !(v instanceof android.widget.EditText) && !in(v,"navigation_search_results_list")&&!category)c.evEvidence=true;
                if(v instanceof android.widget.EditText && e)c.evEvidence=true;
            }
            if((along(v,a)||along(v,b))&&!in(v,"navigation_search_results_list"))add(c.along,clickable(v));
            if(NatMinimalEvRules.explicitHide(a)||NatMinimalEvRules.explicitHide(b))add(c.hide,clickable(v));
            if(name.equals("search_close_btn")&&(NatMinimalEvRules.closeSearch(a)||NatMinimalEvRules.closeSearch(b)))add(c.close,clickable(v));
        }
        return c;
    }
    private static void toast(Activity a,String s){Toast.makeText(a,s,Toast.LENGTH_SHORT).show();}
    private static void act(Activity a,State state){
        if(a.isFinishing()||!a.hasWindowFocus()||!enabled(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY))return;
        View root=a.getWindow().peekDecorView();if(root==null)return;
        List<View> views=new ArrayList<>();collect(root,views,0,state.button==null?null:state.button.get());
        Controls c=inspect(views);View h=unique(c.hide),close=unique(c.close),open=unique(c.open),along=unique(c.along);
        boolean route=NatNavigationCard.hasVerifiedRoute(root);
        if(h!=null&&(route||state.opened)){if(h.performClick())state.opened=false;return;}
        if(NatMinimalEvRules.allowClose(state.opened,c.results,c.evEvidence,close!=null)){if(close.performClick())state.opened=false;return;}
        if(state.opened&&c.results){toast(a,"ใช้ปุ่มปิดผลค้นหาของ Maps / Use native close search");return;}
        if(route&&open!=null){if(open.performClick())state.opened=true;return;}
        if(route&&along!=null){if(along.performClick())toast(a,"เลือกหมวดการชาร์จ EV / Select EV charging");return;}
        toast(a,"เปิดค้นหาตามเส้นทางใน Maps แล้วเลือก EV / Use native along-route search");
    }
    /** No overlay if it could cover another visible native control or label. */
    private static boolean free(List<View> views,Rect slot,View host){
        for(View v:views){
            if(v==host)continue;
            Rect r=new Rect();if(!v.getGlobalVisibleRect(r)||!Rect.intersects(r,slot))continue;
            if(v instanceof android.view.SurfaceView||v instanceof android.view.TextureView)continue;
            if(v instanceof ViewGroup){
                if(v.isClickable()&&r.width()<=slot.width()*3&&r.height()<=slot.height()*3)return false;
                continue;
            }
            // Protect labels, icons, touch targets and unknown small rendered elements.
            if(v instanceof TextView||v instanceof android.widget.ImageView||v.isClickable()||v.isFocusable()||r.width()<=slot.width()*3||r.height()<=slot.height()*3)return false;
        }
        return true;
    }
    static void clear(Activity a){
        State s=STATES.get(a);if(s==null)return;TextView b=s.button==null?null:s.button.get();
        if(b!=null&&b.getParent() instanceof ViewGroup)((ViewGroup)b.getParent()).removeView(b);s.button=null;s.left=s.top=-1;
    }
    static void refresh(Activity a,View root){
        if(!enabled(a)||!root.hasWindowFocus()||!SafeMode.on(a,SafeMode.BYD_BATTERY)){clear(a);return;}
        View content=a.findViewById(android.R.id.content);if(!(content instanceof FrameLayout)){clear(a);return;}
        FrameLayout host=(FrameLayout)content;
        State state=STATES.get(a);if(state==null){state=new State();STATES.put(a,state);}
        TextView button=state.button==null?null:state.button.get();
        List<View> views=new ArrayList<>();collect(root,views,0,button);Controls c=inspect(views);
        boolean route=NatNavigationCard.hasVerifiedRoute(root);
        if(!c.mapControls||(!route&&!state.opened)){clear(a);return;}
        // Show only when a native action was verified; do not launch a generic search over an existing route.
        boolean actionable=unique(c.hide)!=null||NatMinimalEvRules.allowClose(state.opened,c.results,c.evEvidence,unique(c.close)!=null)||(route&&(unique(c.open)!=null||unique(c.along)!=null));
        if(!actionable){clear(a);return;}
        Rect bounds=new Rect();if(!host.getGlobalVisibleRect(bounds)){clear(a);return;}
        float d=host.getResources().getDisplayMetrics().density;int size=Math.round(48*d),gap=Math.round(12*d);
        Rect slot=null;
        for(int edge=0;edge<2&&slot==null;edge++)for(float fy:new float[]{.35f,.48f,.60f,.23f}){
            int x=edge==0?bounds.right-gap-size:bounds.left+gap;
            int y=bounds.top+(int)(bounds.height()*fy);Rect r=new Rect(x,y,x+size,y+size);
            if(bounds.contains(r)&&free(views,r,host)){slot=r;break;}
        }
        if(slot==null){clear(a);return;}
        android.graphics.Point origin=new android.graphics.Point();Rect ignored=new Rect();host.getGlobalVisibleRect(ignored,origin);
        int x=slot.left-origin.x,y=slot.top-origin.y;
        boolean active=unique(c.hide)!=null||(state.opened&&c.results&&c.evEvidence);
        boolean dark=ColorScheme.nightColors(a);
        if(button==null){
            button=new TextView(a);button.setText("⚡");button.setTextSize(23);button.setGravity(Gravity.CENTER);button.setElevation(5*d);
            final State owner=state;button.setOnClickListener(v->act(a,owner));state.button=new WeakReference<>(button);
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(size,size,Gravity.TOP|Gravity.LEFT);lp.leftMargin=x;lp.topMargin=y;host.addView(button,lp);
        }else if(state.left!=x||state.top!=y){
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)button.getLayoutParams();lp.leftMargin=x;lp.topMargin=y;button.setLayoutParams(lp);
        }
        state.left=x;state.top=y;
        GradientDrawable bg=new GradientDrawable();bg.setShape(GradientDrawable.OVAL);bg.setColor(active?0xff16805c:dark?0xff20303a:0xffffffff);button.setBackground(bg);button.setTextColor(active||dark?Color.WHITE:0xff263238);
        button.setContentDescription(active?"ซ่อนสถานีชาร์จ / Hide EV charging":"การชาร์จ EV / EV charging");
    }
}
