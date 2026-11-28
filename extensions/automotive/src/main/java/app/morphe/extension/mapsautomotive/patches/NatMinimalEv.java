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
        WeakReference<android.widget.LinearLayout> toolbar,panel,current,remaining;
        String currentKey="",remainingKey="";
        long categoryUntil=-1;
        boolean opened,confirmed,previousFresh;
        long previousSession=-1;
        int previousStops=-1,mode=0;
        long openedAt=-1;
        String rendered="",diagnostic="";
        int left=-1,top=-1;
        boolean vertical,explicitPanelRequest,toolbarRoute,evMode,restoreSplit,toolbarEv;
        String layerStatus="Not observed";
        View host(){return toolbar==null?null:toolbar.get();}
        View sheet(){return panel==null?null:panel.get();}
    }
    static boolean enabled(Context c){return c.getSharedPreferences(PREF,0).getBoolean("shortcut",true);}
    static void setEnabled(Activity a,boolean on){a.getSharedPreferences(PREF,0).edit().putBoolean("shortcut",on).apply();if(!on){clear(a);STATES.remove(a);}}
    private static String id(View v){try{return v.getId()==View.NO_ID?"":v.getResources().getResourceEntryName(v.getId());}catch(RuntimeException e){return "";}}
    private static boolean in(View v,String name){for(View p=v;p!=null;){if(id(p).equals(name))return true;ViewParent par=p.getParent();p=par instanceof View?(View)par:null;}return false;}
    private static boolean visible(View v){return v.isShown()&&v.getAlpha()>0f&&v.getGlobalVisibleRect(new Rect());}
    private static void collect(View v,List<View> out,int depth,View own){
        if(v==own||owned(v)||depth>40||!visible(v))return;out.add(v);
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
        final List<View> open=new ArrayList<>(),along=new ArrayList<>(),hide=new ArrayList<>(),close=new ArrayList<>(),showLayer=new ArrayList<>(),hideLayer=new ArrayList<>();
        boolean results,evEvidence,mapControls,layerFilter;
    }
    private static Controls inspect(List<View> views){
        Controls c=new Controls();
        for(View v:views){
            String name=id(v);CharSequence a=label(v),b=v.getContentDescription();
            if(name.equals("navigation_search_results_list"))c.results=true;
            if(name.equals("map_buttons_view_container"))c.mapControls=true;
            if(name.equals("map_buttons_view_evcs_layer_map_filters_screen_container"))c.layerFilter=true;
            if(in(v,"map_buttons_view_container")&&!in(v,"navigation_search_results_list")){
                View target=clickable(v);
                // Map-owned action only, never a place-details or generic search button.
                if(target!=null&&in(target,"map_buttons_view_container")){
                    if(NatMinimalEvRules.explicitShow(a)||NatMinimalEvRules.explicitShow(b))add(c.showLayer,target);
                    if(NatMinimalEvRules.explicitHide(a)||NatMinimalEvRules.explicitHide(b))add(c.hideLayer,target);
                }
            }
            boolean e=ev(v,a)||ev(v,b);
            if(e){
                View target=clickable(v);
                boolean category=in(v,"destination_input_search_category_list_view")||in(v,"destination_input_search_category_list_frame_view");
                if(category&&!checked(v)&&!checked(target))add(c.open,target);
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
    private static final String OWN="nat_v201_overlay";
    static boolean owned(View v){return OWN.equals(v.getTag());}
    private static void report(State s,String msg){if(!msg.equals(s.diagnostic)){s.diagnostic=msg;ReportLog.i("NatMinimalEv",msg);}}
    private static boolean ownedSearch(State s){return s.opened&&(s.confirmed||(s.openedAt>=0&&android.os.SystemClock.elapsedRealtime()-s.openedAt<=15000));}
    private static void resetSearch(State s){s.opened=false;s.confirmed=false;s.openedAt=-1;}
    private static void updateSearch(State s,Controls c){
        long age=s.openedAt<0?-1:android.os.SystemClock.elapsedRealtime()-s.openedAt;
        if(!NatMinimalEvRules.keepSearch(s.opened,s.confirmed,c.results,c.evEvidence,c.mapControls,age)){resetSearch(s);return;}
        if(NatMinimalEvRules.confirmedSearch(s.opened,c.results,c.evEvidence,c.mapControls))s.confirmed=true;
    }
    private static boolean hideNative(State s,Controls c,boolean explicitOnly){
        if(!c.mapControls)return false;
        updateSearch(s,c);
        View h=unique(c.hide),close=unique(c.close);
        if(h!=null&&c.mapControls){if(h.performClick()){resetSearch(s);report(s,"Native EV hide requested");return true;}}
        if(!explicitOnly&&NatMinimalEvRules.allowClose(s.confirmed,c.results,c.evEvidence,close!=null)){
            if(close.performClick()){resetSearch(s);report(s,"Native close search requested");return true;}
        }
        return false;
    }
    private static Controls controls(View root){List<View> all=new ArrayList<>();collect(root,all,0,null);return inspect(all);}
    private static void layerEvidence(State s,Controls c){
        s.layerStatus="Native EV layer: show="+c.showLayer.size()+" hide="+c.hideLayer.size()+" filter="+c.layerFilter+" map="+c.mapControls+" results="+c.results;
    }
    static String layerStatus(Activity a){State s=STATES.get(a);return NatNativeEvLayer.status()+"\n"+(s==null?"Native EV controls: not observed":s.layerStatus);}
    static String layerButtonLabel(Activity a){State s=STATES.get(a);return s!=null&&s.evMode?"ซ่อนสถานีชาร์จ":"แสดงสถานีชาร์จ";}
    static boolean evMode(Activity a){State s=STATES.get(a);return s!=null&&s.evMode;}
    private static boolean setLayer(Activity a,State s,boolean show){
        View root=a.getWindow().peekDecorView();if(root==null)return false;
        Controls c=controls(root);layerEvidence(s,c);
        if(NatNativeEvLayer.request(a,show)){report(s,"Native producer state requested: "+show);return true;}
        int action=NatMinimalEvRules.layerAction(c.mapControls,c.results,c.showLayer.size(),c.hideLayer.size());
        if((show&&action==-1)||(!show&&action==1))return true;
        View target=show&&action==1?unique(c.showLayer):!show&&action==-1?unique(c.hideLayer):null;
        if(target!=null&&visible(target)&&target.isEnabled()&&target.performClick()){
            report(s,"Native EV layer "+(show?"show":"hide")+" requested; awaiting Maps evidence");return true;
        }
        report(s,s.layerStatus+" | layer state unavailable; route retained");return false;
    }
    private static void exitEv(Activity a,State s){
        boolean split=s.restoreSplit;s.evMode=false;s.restoreSplit=false;
        if(!setLayer(a,s,false))toast(a,"ยังยืนยันการซ่อนหมุดไม่ได้ • ใช้ปุ่มของ Maps");
        if(split&&!NatSplitScreen.enabled(a))NatSplitScreen.temporaryToggle(a);
    }
    private static void layer(Activity a,State s){
        if(a.isFinishing()||a.isDestroyed()||!a.hasWindowFocus()||!enabled(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY))return;
        if(s.evMode){exitEv(a,s);return;}
        if(!setLayer(a,s,true)){
            // Some Maps builds expose charging search but not the MAIN EVCS layer.
            // The user requested stations: open the native category when parked,
            // leaving place names and map pins entirely to Maps.
            NatV36Daily.Energy e=NatV36Daily.energy();
            if(e!=null&&Double.isFinite(e.speed)&&e.speed>=0&&e.speed<=1){nearby(a);return;}
            toast(a,"Maps ยังเปิดชั้นหมุด EV ไม่ได้ • จอดรถแล้วกดค้างเพื่อค้นหาสถานี");
            return;
        }
        s.restoreSplit=NatSplitScreen.active(a);s.evMode=true;
        if(s.restoreSplit)NatSplitScreen.temporaryToggle(a);
        report(s,"EV Full Map requested; native markers not yet visually verified");
    }
    static void nearby(Activity a){
        if(a==null||a.isFinishing()||a.isDestroyed()||!a.hasWindowFocus()||!enabled(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY))return;
        NatV36Daily.Energy e=NatV36Daily.energy();
        if(e==null||!Double.isFinite(e.speed)||e.speed<0||e.speed>1){toast(a,"ค้นหาสถานีเมื่อจอดรถและมีข้อมูลความเร็ว");return;}
        View root=a.getWindow().peekDecorView();if(root==null)return;
        State s=STATES.get(a);if(s==null){s=new State();STATES.put(a,s);}
        Controls c=controls(root);layerEvidence(s,c);
        if(c.results){toast(a,"ใช้รายการสถานีของ Maps ที่เปิดอยู่");return;}
        View open=unique(c.open);
        if(c.mapControls&&open!=null&&visible(open)&&open.isEnabled()&&open.performClick()){
            s.opened=true;s.confirmed=false;s.openedAt=android.os.SystemClock.elapsedRealtime();report(s,"Native EV search requested by user; ranking supplied by Maps");return;
        }
        toast(a,"เปิดค้นหาใน Maps แล้วเลือกสถานีชาร์จ EV • ไม่พบปุ่มค้นหาที่รองรับ");
        report(s,"Nearby charging: native category unavailable; no synthetic results");
    }
    private static TextView evKey(Activity a,State s,String description){
        TextView t=toolbarButton(a,"⚡",description,()->layer(a,s));
        t.setTooltipText(description+" • กดค้างค้นหาสถานีเมื่อจอด");
        t.setOnLongClickListener(v->{nearby(a);return true;});return t;
    }
    private static void act(Activity a,State state,boolean clean){
        if(a.isFinishing()||!a.hasWindowFocus()||!enabled(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY))return;
        View root=a.getWindow().peekDecorView();if(root==null)return;
        Controls c=controls(root);updateSearch(state,c);boolean route=NatNavigationCard.hasVerifiedRoute(root);
        state.mode=0;removePanel(state);
        if(clean){
            if(state.evMode){exitEv(a,state);return;}
            if((ownedSearch(state)||unique(c.hide)!=null)&&!hideNative(state,c,false)&&c.results)toast(a,"ปิดผลค้นหา EV ด้วยปุ่มของ Maps");
            state.categoryUntil=-1;
            report(state,"Clean mode: NAT panels closed; native route retained");return;
        }
        if(hideNative(state,c,false))return;
        if(ownedSearch(state)&&c.results){toast(a,"ใช้ปุ่มปิดผลค้นหาของ Maps");return;}
        View open=unique(c.open);
        if(route&&c.mapControls&&open!=null&&!c.results){if(open.performClick()){state.opened=true;state.confirmed=false;state.categoryUntil=-1;state.openedAt=android.os.SystemClock.elapsedRealtime();report(state,"Native EV category requested");}return;}
        state.categoryUntil=-1;
        toast(a,"ยังไม่พบตัวควบคุมสถานีชาร์จบนแผนที่");report(state,"EV direct control unavailable; generic Search not opened; route retained");
    }
    /** No overlay if it could cover another visible native control or label. */
    private static boolean free(List<View> views,Rect slot,View host){
        if(NatNavigationCard.reserves(host.getRootView(),slot))return false;
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
    private static void remove(View v){if(v!=null&&v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);}
    private static void removePanel(State s){remove(s.sheet());s.panel=null;s.rendered="";}
    static void clear(Activity a){
        State s=STATES.get(a);if(s==null)return;remove(s.host());removePanel(s);removeMini(s,true);removeMini(s,false);s.toolbar=null;s.left=s.top=-1;s.mode=0;
    }
    private static android.widget.LinearLayout column(Context a){android.widget.LinearLayout l=new android.widget.LinearLayout(a);l.setTag(OWN);l.setOrientation(android.widget.LinearLayout.VERTICAL);return l;}
    private static int dp(Context a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
    private static GradientDrawable background(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g;}
    private static TextView button(Activity a,String symbol,String description,Runnable action){
        TextView t=new TextView(a);t.setTag(OWN);t.setText(symbol);t.setTextSize(23);t.setGravity(Gravity.CENTER);t.setContentDescription(description);t.setTooltipText(description);t.setOnClickListener(v->{try{action.run();}catch(RuntimeException ex){clear(a);ReportLog.w("NatMinimalEv","Optional control failed",ex);}});return t;
    }
    private static void style(TextView t,boolean active,boolean dark,int radius){
        int fg=active||dark?Color.WHITE:0xff263238;
        t.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(dark?0x33ffffff:0x22000000),
                background(active?0xff16805c:dark?0xff20303a:0xffffffff,radius),null));
        t.setTextColor(fg);
        if(t instanceof ToolbarIcon)((ToolbarIcon)t).invalidate();
    }
    /** Uniform vector strokes, independent of font and emoji rendering on the head unit. */
    private static final class ToolbarIcon extends TextView {
        final String symbol;final android.graphics.Paint pen=new android.graphics.Paint(3);
        ToolbarIcon(Context c,String symbol){super(c);this.symbol=symbol;}
        @Override protected void onDraw(android.graphics.Canvas canvas){
            float k=Math.min(getWidth(),getHeight())/48f;
            canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f);canvas.scale(k,k);
            pen.setColor(getCurrentTextColor());pen.setStrokeWidth(2.1f);pen.setStrokeCap(android.graphics.Paint.Cap.ROUND);pen.setStrokeJoin(android.graphics.Paint.Join.ROUND);pen.setStyle(android.graphics.Paint.Style.STROKE);
            android.graphics.Path path=new android.graphics.Path();
            switch(symbol){
                case "⚡":path.moveTo(2,-13);path.lineTo(-10,1);path.lineTo(-1,1);path.lineTo(-3,13);path.lineTo(10,-3);path.lineTo(1,-3);path.close();canvas.drawPath(path,pen);break;
                case "↗":canvas.drawLine(-12,10,12,10,pen);path.moveTo(-11,4);path.lineTo(-4,-3);path.lineTo(2,1);path.lineTo(11,-10);canvas.drawPath(path,pen);canvas.drawLine(4,-10,11,-10,pen);canvas.drawLine(11,-10,11,-3,pen);break;
                case "☷":for(int y=-9;y<=9;y+=9){canvas.drawCircle(-10,y,1,pen);canvas.drawLine(-4,y,11,y,pen);}break;
                case "⊘":canvas.drawCircle(0,0,11,pen);canvas.drawLine(-8,8,8,-8,pen);break;
                case "ⓘ":canvas.drawCircle(0,0,12,pen);canvas.drawCircle(0,-6,1,pen);canvas.drawLine(0,-1,0,7,pen);break;
                case "▥":canvas.drawRoundRect(-13,-10,13,10,2,2,pen);canvas.drawLine(6.5f,-10,6.5f,10,pen);break;
                case "◇":canvas.drawRoundRect(-12,-6,12,7,3,3,pen);canvas.drawLine(-8,-6,-5,-11,pen);canvas.drawLine(-5,-11,5,-11,pen);canvas.drawLine(5,-11,8,-6,pen);canvas.drawCircle(-7,8,2,pen);canvas.drawCircle(7,8,2,pen);break;
                case "❄":for(int k2=0;k2<6;k2++){canvas.drawLine(0,0,0,-12,pen);canvas.drawLine(0,-7,-3,-10,pen);canvas.drawLine(0,-7,3,-10,pen);canvas.rotate(60);}break;
                default:break;
            }
            canvas.restore();
        }
    }
    private static TextView toolbarButton(Activity a,String symbol,String description,Runnable action){
        ToolbarIcon t=new ToolbarIcon(a,symbol);t.setTag(OWN);t.setContentDescription(description);t.setTooltipText(description);t.setFocusable(true);
        t.setOnClickListener(v->{try{action.run();}catch(RuntimeException ex){clear(a);ReportLog.w("NatMinimalEv","Optional control failed",ex);toast(a,"NAT control unavailable");}});
        return t;
    }
    private static TextView diagnosticsButton(Activity a,State s){
        TextView t=toolbarButton(a,"ⓘ","System Diagnostics — CPU / RAM / GPS",()->{
            View root=a.getWindow().peekDecorView();
            if(a.isFinishing()||a.isDestroyed()||!a.hasWindowFocus()||root==null
                    ||!enabled(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY)||!controls(root).mapControls)return;
            s.mode=0;removePanel(s);
            // No native search, Back, navigation or vehicle action here.
            NatSystemDiagnostics.show(a);
        });
        t.setTooltipText("System Diagnostics — CPU / RAM / GPS (hold: Split / Full Map)");
        t.setOnLongClickListener(v->{if(evMode(a)){State st=STATES.get(a);if(st!=null)exitEv(a,st);}else NatSplitScreen.toggle(a);return true;});
        return t;
    }
    private static TextView snowButton(Activity a){return toolbarButton(a,"❄","Snow AI",()->NatV36Shortcuts.snow(a));}
    private static TextView splitButton(Activity a){
        TextView button=toolbarButton(a,"▥","Split Screen 75:25",()->{
            View root=a.getWindow().peekDecorView();
            if(a.isFinishing()||a.isDestroyed()||!a.hasWindowFocus()||root==null||!enabled(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY)||!controls(root).mapControls)return;
            if(evMode(a)){State st=STATES.get(a);if(st!=null)exitEv(a,st);return;}
            NatSplitScreen.toggle(a);
        });
        button.setTooltipText("Split Screen 75:25 • hold: Favorite Destinations");
        button.setOnLongClickListener(v->{NatFavorites.show(a);return true;});
        return button;
    }
    private static void switchPanel(Activity a,State s,int which){
        if(a.isFinishing()||a.isDestroyed()||!a.hasWindowFocus()||!enabled(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY))return;
        s.mode=s.mode==which?0:which;removePanel(s);View root=a.getWindow().peekDecorView();if(root!=null){s.explicitPanelRequest=true;try{refresh(a,root);}finally{s.explicitPanelRequest=false;}}
    }
    private static void put(FrameLayout host,View view,Rect slot,boolean newView){
        android.graphics.Point origin=new android.graphics.Point();Rect dummy=new Rect();host.getGlobalVisibleRect(dummy,origin);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(slot.width(),slot.height(),Gravity.TOP|Gravity.LEFT);
        lp.leftMargin=slot.left-origin.x;lp.topMargin=slot.top-origin.y;
        if(newView)host.addView(view,lp);else view.setLayoutParams(lp);
    }
    private static String number(double d,String unit){return Double.isFinite(d)?String.format(java.util.Locale.US,"%.1f %s",d,unit):"—";}
    private static double tariff(Context a){try{return Double.parseDouble(a.getSharedPreferences(PREF,0).getString("tariff",""));}catch(RuntimeException e){return Double.NaN;}}
    private static void editTariff(Activity a,State s){
        android.widget.EditText e=new android.widget.EditText(a);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);double old=tariff(a);if(Double.isFinite(old))e.setText(String.valueOf(old));e.setHint("บาท / kWh");
        new android.app.AlertDialog.Builder(a).setTitle("ค่าไฟอ้างอิง (กรอกเอง)").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{double value=Double.parseDouble(e.getText().toString());if(!Double.isFinite(value)||value<=0||value>100)throw new IllegalArgumentException();a.getSharedPreferences(PREF,0).edit().putString("tariff",Double.toString(value)).apply();}catch(RuntimeException ex){toast(a,"ค่าไฟไม่ถูกต้อง");}}).show();
    }
    private static TextView line(Context a,String text,int size,int color){TextView t=new TextView(a);t.setTag(OWN);t.setText(text);t.setTextSize(size);t.setTextColor(color);return t;}
    private static void showPanel(Activity a,FrameLayout host,State s,List<View> views,Rect bounds,Rect toolbarSlot,boolean dark,NatPrediction.Snapshot f){
        if(s.mode==0){removePanel(s);return;}
        int w=Math.min(dp(a,340),bounds.width()-dp(a,100)),h=Math.min(dp(a,230),bounds.height()-dp(a,100));
        if(w<dp(a,215)||h<dp(a,145)){panelUnavailable(a,s,f);return;}
        Rect slot=null;
        for(int edge=0;edge<2&&slot==null;edge++)for(float fy:new float[]{.25f,.40f,.12f}){
            int x=edge==0?bounds.left+dp(a,12):bounds.right-dp(a,12)-w;
            int y=bounds.top+(int)(bounds.height()*fy);Rect q=new Rect(x,y,x+w,y+h);
            if(bounds.contains(q)&&!Rect.intersects(toolbarSlot,q)&&(global(s.current==null?null:s.current.get())==null||!Rect.intersects(global(s.current.get()),q))&&free(views,q,host)){slot=q;break;}
        }
        if(slot==null){panelUnavailable(a,s,f);return;}
        NatLiveEnergy.Reading live=NatPrediction.liveReading();
        double observed=f==null?Double.NaN:live.net,cost=NatStabilityMath.cost(observed,tariff(a));
        double displayedPower=f==null?Double.NaN:live.power;
        String power=number(displayedPower,"kW"), actualRate=number(f==null?Double.NaN:live.rate,"kWh/100 km"),increase=number(f==null?Double.NaN:live.increase,"kWh"),coverage=number(f==null?Double.NaN:live.km,"km");
        String powerState=!Double.isFinite(displayedPower)?"ไม่มีข้อมูล":displayedPower>0?"แบตจ่ายพลังงาน":displayedPower<0?"พลังงานแบตเพิ่ม":"การเปลี่ยนแปลงสุทธิเป็นศูนย์";
        String remaining=f==null?"—":number(f.remainingKwh,"kWh");
        String rate=f==null?"—":number(NatStabilityMath.rate(f.remainingKwh,f.km),"kWh/100 km");
        String used=number(observed,"kWh"),money=number(cost,"THB");
        String plan=f==null?"ไม่มี forecast สดที่ตรงกับเส้นทาง":f.charges==0?"ยังไม่มีจุดชาร์จใน forecast":("แผนจาก Maps (ไม่มีชื่อสถานี)"+f.plan);
        String arrival=f==null?"—":f.google+"%";
        String signature=s.mode+"|"+dark+"|"+remaining+"|"+rate+"|"+used+"|"+money+"|"+plan+"|"+arrival+"|"+power+"|"+actualRate+"|"+increase+"|"+coverage+"|"+powerState+"|"+live.gaps+"|"+slot.toShortString();
        if(signature.equals(s.rendered)&&s.sheet()!=null)return;
        int oldScroll=0;View previous=s.sheet();
        if(previous instanceof ViewGroup){ViewGroup group=(ViewGroup)previous;if(group.getChildCount()>1&&group.getChildAt(1) instanceof android.widget.ScrollView)oldScroll=group.getChildAt(1).getScrollY();}
        removePanel(s);s.rendered=signature;
        int fg=dark?0xffedf4f7:0xff263238, muted=dark?0xffbacbd3:0xff546a74;
        android.widget.LinearLayout box=column(a);box.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,8));box.setBackground(background(dark?0xff20303a:Color.WHITE,dp(a,12)));box.setElevation(dp(a,6));
        android.widget.LinearLayout header=new android.widget.LinearLayout(a);header.setTag(OWN);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=line(a,s.mode==1?"Energy Prediction":"Charging Plan",18,fg);header.addView(title,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        TextView close=button(a,"×","ปิดหน้าต่าง",()->{s.mode=0;removePanel(s);refresh(a,a.getWindow().peekDecorView());});close.setTextColor(fg);header.addView(close,new android.widget.LinearLayout.LayoutParams(dp(a,44),dp(a,40)));box.addView(header);
        android.widget.ScrollView scroll=new android.widget.ScrollView(a);scroll.setTag(OWN);android.widget.LinearLayout content=column(a);scroll.addView(content);box.addView(scroll,new android.widget.LinearLayout.LayoutParams(-1,0,1));
        if(s.mode==1){
            content.addView(line(a,"กำลังไฟแบตเตอรี่เฉลี่ย (ไม่ใช่ค่าหน้าปัด)",12,muted));
            content.addView(line(a,power+" · "+powerState,20,fg));
             content.addView(line(a,"กำลังไฟจริงจากหน้าปัด BYD: — (ยังไม่มีสัญญาณที่ยืนยัน)",11,muted));
            if(Double.isFinite(displayedPower)){
                PowerGauge gauge=new PowerGauge(a,displayedPower,dark);gauge.setTag(OWN);content.addView(gauge,new android.widget.LinearLayout.LayoutParams(-1,dp(a,18)));
                content.addView(line(a,"− เพิ่มพลังงาน     0     + จ่ายพลังงาน (kW)",10,muted));
            }
            String[] labels={"พลังงานสุทธิที่สังเกตได้","พลังงานที่ต้องใช้อีก","สิ้นเปลืองจากช่วงที่มีเลขไมล์","พลังงานเพิ่มขณะเคลื่อนที่","สิ้นเปลืองตาม forecast","ค่าไฟประมาณการ"};
            String[] values={used,remaining,actualRate,increase,rate,money};
            for(int i=0;i<labels.length;i+=2){
                android.widget.LinearLayout row=new android.widget.LinearLayout(a);row.setTag(OWN);
                for(int j=i;j<i+2;j++){android.widget.LinearLayout cell=column(a);cell.setPadding(dp(a,5),dp(a,3),dp(a,5),dp(a,3));cell.addView(line(a,labels[j],11,muted));cell.addView(line(a,values[j],19,fg));row.addView(cell,new android.widget.LinearLayout.LayoutParams(0,-2,1));}
                content.addView(row);
            }
            content.addView(line(a,f==null?"ไม่มี forecast สดที่ตรงกับเส้นทาง":f.charges>0?"ประมาณจากพลังงานและจุดชาร์จขั้นต่ำใน Maps":"ประมาณจาก Wh ของ BYD และ Maps",11,muted));
            content.addView(line(a,"ช่วงที่มีเลขไมล์: "+coverage+" • อัตราสิ้นเปลืองรออย่างน้อย 2 กม. • ช่วงขาด/ตัดออก: "+live.gaps+" • ไม่ใช่ยอดทั้งเที่ยว • ไม่ใช่ค่า Regen ที่ยืนยันแล้ว",11,muted));
            android.widget.Button set=new android.widget.Button(a);set.setTag(OWN);set.setText("ตั้งค่าไฟ");set.setOnClickListener(v->editTariff(a,s));content.addView(set);
        }else{
            content.addView(line(a,plan,14,fg));content.addView(line(a,"แบตปลายทางตาม Maps: "+arrival,15,fg));
            android.widget.Button search=new android.widget.Button(a);search.setTag(OWN);search.setText("ค้นหาจุดชาร์จตามเส้นทาง");search.setOnClickListener(v->act(a,s,false));content.addView(search);
            content.addView(line(a,"รายการสถานี / กำลังหัวชาร์จ / จำนวนหัวว่าง: ใช้ผลค้นหาและหน้ารายละเอียด Native Maps เท่านั้น เมื่อ Maps มีข้อมูล",11,muted));
            content.addView(line(a,"ให้ Maps คำนวณใหม่หลังเพิ่มจุดแวะ • ยังไม่แนะนำสถานีอัตโนมัติ",11,muted));
        }
        put(host,box,slot,true);s.panel=new WeakReference<>(box);
        if(oldScroll>0){final int position=oldScroll;scroll.post(()->scroll.scrollTo(0,position));}
    }
    /** Explicit user request may open a read-only snapshot dialog when no collision-free panel fits.
     * Never opens spontaneously from the periodic refresh, and never invokes a native navigation action. */
    private static void panelUnavailable(Activity a,State s,NatPrediction.Snapshot f){
        int mode=s.mode;removePanel(s);s.mode=0;
        report(s,"Panel unavailable: native controls protected");
        if(!s.explicitPanelRequest){toast(a,"ไม่พบพื้นที่ปลอดภัย ใช้เมนู BYD Battery Settings");return;}
        if(a.isFinishing()||a.isDestroyed()||!a.hasWindowFocus())return;
        NatLiveEnergy.Reading live=NatPrediction.liveReading();
        String message;
        if(mode==1){
            double used=f==null?Double.NaN:live.net;
            message="กำลังไฟแบตเตอรี่เฉลี่ย (ไม่ใช่ค่าหน้าปัด)\n"+number(f==null?Double.NaN:live.power,"kW")+"\nกำลังไฟจริงจากหน้าปัด BYD: — (ยังไม่มีสัญญาณที่ยืนยัน)"
                +"\n\nพลังงานสุทธิที่สังเกตได้: "+number(used,"kWh")
                +"\nพลังงานที่ต้องใช้อีก: "+number(f==null?Double.NaN:f.remainingKwh,"kWh")
                +"\nสิ้นเปลืองจากช่วงที่มีเลขไมล์: "+number(f==null?Double.NaN:live.rate,"kWh/100 km")
                +"\nพลังงานเพิ่มขณะเคลื่อนที่: "+number(f==null?Double.NaN:live.increase,"kWh")
                +"\nสิ้นเปลืองตาม forecast: "+number(f==null?Double.NaN:NatStabilityMath.rate(f.remainingKwh,f.km),"kWh/100 km")
                +"\nค่าไฟประมาณการ: "+number(NatStabilityMath.cost(used,tariff(a)),"THB")
                +"\n\nช่วงที่มีเลขไมล์: "+number(f==null?Double.NaN:live.km,"km")+" • รออย่างน้อย 2 กม. • ไม่ใช่ยอดทั้งเที่ยวหรือ Regen ที่ยืนยันแล้ว";
        }else{
            message=f==null?"ไม่มี forecast สดที่ตรงกับเส้นทาง":(f.charges==0?"ยังไม่มีจุดชาร์จใน forecast":"แผนจาก Maps (ไม่มีชื่อสถานี)"+f.plan)+"\n\nแบตปลายทางตาม Maps: "+f.google+"%";
            message+="\n\nค้นหาสถานีด้วยปุ่ม EV Charging ของ Maps หลังปิดหน้าต่าง";
        }
        if(f==null&&mode==1)message+="\n\nไม่มี forecast สดที่ตรงกับเส้นทาง";
        new android.app.AlertDialog.Builder(a).setTitle(mode==1?"Energy Prediction":"Charging Plan")
                .setMessage("ข้อมูล ณ เวลาที่เปิด • Snapshot (จอดรถก่อนใช้งาน)\n\n"+message)
                .setPositiveButton("Close",null).show();
    }
    private static final class PowerGauge extends View {
        final double kw;final boolean dark;final android.graphics.Paint paint=new android.graphics.Paint(3);
        PowerGauge(Context c,double kw,boolean dark){super(c);this.kw=kw;this.dark=dark;setContentDescription("Estimated average battery power "+number(kw,"kW"));}
        @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);float x=getWidth()/2f,y=getHeight()/2f,h=dp(getContext(),4),amount=(float)(Math.min(250,Math.abs(kw))/250*x);
            paint.setColor(dark?0xff40535e:0xffdce5e0);canvas.drawRoundRect(0,y-h,getWidth(),y+h,h,h,paint);
            paint.setColor(kw<0?0xff169b65:0xff4285f4);if(amount>0)canvas.drawRoundRect(kw<0?x-amount:x,y-h,kw<0?x:x+amount,y+h,h,h,paint);
            paint.setColor(dark?Color.WHITE:0xff263238);paint.setStrokeWidth(dp(getContext(),2));canvas.drawLine(x,0,x,getHeight(),paint);
        }
    }
    private static void removeMini(State s,boolean current){
        WeakReference<android.widget.LinearLayout> ref=current?s.current:s.remaining;
        if(ref!=null)remove(ref.get());
        if(current){s.current=null;s.currentKey="";}else{s.remaining=null;s.remainingKey="";}
    }
    private static boolean nativeCurrent(List<View> views){
        for(View v:views)if(v instanceof TextView){
            String t=NatMinimalEvRules.norm(((TextView)v).getText()),d=NatMinimalEvRules.norm(v.getContentDescription());
            if(t.startsWith("current battery")||t.startsWith("แบตเตอรี่ปัจจุบัน")||t.startsWith("แบตปัจจุบัน")||d.startsWith("current battery")||d.startsWith("แบตเตอรี่ปัจจุบัน"))return true;
        }
        return false;
    }
    private static Rect global(View v){Rect r=new Rect();return v!=null&&v.getGlobalVisibleRect(r)?r:null;}
    private static void mini(Activity a,FrameLayout host,State s,List<View> views,Rect bounds,Rect toolbar,boolean dark,boolean current,String value){
        if(current&&nativeCurrent(views)){removeMini(s,true);return;}
        if(!current&&s.mode!=1){removeMini(s,false);return;}
        int w=dp(a,current?142:166),h=dp(a,58),edge=dp(a,12);
        Rect slot=null,panel=global(s.sheet()),other=global(current?(s.remaining==null?null:s.remaining.get()):(s.current==null?null:s.current.get()));
        if(current){
            NatToolbarLayout.Slot fixed=NatToolbarLayout.currentBattery(bounds.left,bounds.top,bounds.right,bounds.bottom,w,h,edge,q->{
                Rect r=new Rect(q.x,q.y,q.x+q.w,q.y+q.h);
                return !Rect.intersects(toolbar,r)&&(panel==null||!Rect.intersects(panel,r))&&(other==null||!Rect.intersects(other,r))&&free(views,r,host);
            });
            if(fixed!=null)slot=new Rect(fixed.x,fixed.y,fixed.x+fixed.w,fixed.y+fixed.h);
        }else for(float y:new float[]{.82f,.72f,.64f})for(int side=0;side<3&&slot==null;side++){
            int x=side==0?(current?bounds.right-edge-w:bounds.centerX()-w/2):side==1?bounds.left+edge:bounds.right-edge-w;
            int top=bounds.top+(int)(bounds.height()*y);Rect q=new Rect(x,top,x+w,top+h);
            if(bounds.contains(q)&&!Rect.intersects(toolbar,q)&&(panel==null||!Rect.intersects(panel,q))&&(other==null||!Rect.intersects(other,q))&&free(views,q,host))slot=q;
        }
        if(slot==null){removeMini(s,current);return;}
        String key=dark+"|"+value+"|"+slot.toShortString(),old=current?s.currentKey:s.remainingKey;
        WeakReference<android.widget.LinearLayout> ref=current?s.current:s.remaining;
        if(key.equals(old)&&ref!=null&&ref.get()!=null&&ref.get().getParent()==host)return;
        removeMini(s,current);
        android.widget.LinearLayout box=column(a);box.setGravity(Gravity.CENTER);box.setBackground(background(dark?0xff20303a:Color.WHITE,dp(a,9)));box.setElevation(dp(a,4));
        TextView label=line(a,current?"แบตเตอรี่ปัจจุบัน":"พลังงานที่ต้องใช้อีก",11,dark?0xffbacbd3:0xff546a74);label.setGravity(Gravity.CENTER);
        TextView metric=line(a,value,25,dark?Color.WHITE:0xff263238);metric.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);metric.setGravity(Gravity.CENTER);
        box.addView(label);box.addView(metric);put(host,box,slot,true);
        if(current){s.current=new WeakReference<>(box);s.currentKey=key;}else{s.remaining=new WeakReference<>(box);s.remainingKey=key;}
    }
    static void splitAction(Activity a,int action){
        if(a.isFinishing()||a.isDestroyed()||!a.hasWindowFocus()||!enabled(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY))return;
        View root=a.getWindow().peekDecorView();if(root==null)return;
        State s=STATES.get(a);if(s==null){s=new State();STATES.put(a,s);}
        if(action==0)layer(a,s);
        else if(action==3||action==5)act(a,s,action==3);
        else if(action==1||action==2){s.mode=action;s.explicitPanelRequest=true;try{NatPrediction.Snapshot f=NatPrediction.freshSnapshot();panelUnavailable(a,s,NatNavigationCard.matchesForecast(root,f)?f:null);}finally{s.explicitPanelRequest=false;}}
        else if(action==4)NatSystemDiagnostics.show(a);
    }
    static void refresh(Activity a,View root){
        if(NatSplitScreen.active(a)){
            State s=STATES.get(a);
            if(s!=null&&root!=null){NatPrediction.Snapshot f=NatPrediction.freshSnapshot();if(f!=null&&s.previousSession>=0&&s.previousSession!=f.session)resetSearch(s);if(f!=null)s.previousSession=f.session;Controls c=controls(root);updateSearch(s,c);layerEvidence(s,c);}
            return;
        }
        if(root==null||!enabled(a)||!root.hasWindowFocus()||!SafeMode.on(a,SafeMode.BYD_BATTERY)){clear(a);return;}
        View content=a.findViewById(android.R.id.content);if(!(content instanceof FrameLayout)){clear(a);return;}
        FrameLayout host=(FrameLayout)content;
        State state=STATES.get(a);if(state==null){state=new State();STATES.put(a,state);}
        final State owner=state;
        List<View> views=new ArrayList<>();collect(root,views,0,null);Controls c=inspect(views);
        boolean route=NatNavigationCard.recentlyVerified(root);
        NatPrediction.Snapshot forecast=NatPrediction.freshSnapshot();boolean fresh=forecast!=null;int stops=fresh?forecast.charges:-1;
        if(NatStabilityMath.autoHide(state.previousFresh,state.previousStops,fresh,stops)||(fresh&&state.previousSession!=forecast.session)){
            if(!state.explicitPanelRequest){state.mode=0;removePanel(state);report(state,"Auto-hide: new forecast or changed charging-stop count");}
            // Native results are never cleared automatically through generic close or Back.
        }
        if(fresh&&state.previousSession>=0&&state.previousSession!=forecast.session)resetSearch(state);
        state.previousFresh=fresh;state.previousStops=stops;if(fresh)state.previousSession=forecast.session;
        updateSearch(state,c);layerEvidence(state,c);
        boolean routeControls=route||ownedSearch(state)||android.os.SystemClock.elapsedRealtime()<=state.categoryUntil;
        if(!c.mapControls){clear(a);return;}
        Rect bounds=new Rect();if(!host.getGlobalVisibleRect(bounds)){clear(a);return;}
        int size=dp(a,48),gap=dp(a,7),edge=dp(a,12);
        NatToolbarLayout.Slot reserved=route&&!nativeCurrent(views)?NatToolbarLayout.currentBattery(bounds.left,bounds.top,bounds.right,bounds.bottom,dp(a,142),dp(a,58),edge,
                q->free(views,new Rect(q.x,q.y,q.x+q.w,q.y+q.h),host)):null;
        final Rect batterySlot=reserved==null?null:new Rect(reserved.x,reserved.y,reserved.x+reserved.w,reserved.y+reserved.h);
        NatToolbarLayout.Slot chosen=NatToolbarLayout.chooseV37Mode(c.mapControls,routeControls,state.evMode,
                bounds.left,bounds.top,bounds.right,bounds.bottom,size,gap,edge,true,
                q->{Rect r=new Rect(q.x,q.y,q.x+q.w,q.y+q.h);return (batterySlot==null||!Rect.intersects(batterySlot,r))&&free(views,r,host);});
        if(chosen==null){if(state.evMode&&c.mapControls){exitEv(a,state);toast(a,"ไม่มีพื้นที่ปุ่มออก EV ที่ปลอดภัย");}clear(a);report(state,"Toolbar unavailable: native controls protected");return;}
        Rect slot=new Rect(chosen.x,chosen.y,chosen.x+chosen.w,chosen.y+chosen.h);
        boolean vertical=chosen.vertical;
        if(!routeControls){state.mode=0;removePanel(state);removeMini(state,true);removeMini(state,false);}
        boolean dark=ColorScheme.nightColors(a);
        android.widget.LinearLayout bar=state.toolbar==null?null:state.toolbar.get();
        if(bar==null||bar.getParent()!=host||state.vertical!=vertical||state.toolbarRoute!=routeControls||state.toolbarEv!=state.evMode||bar.getChildCount()!=chosen.count){
            remove(bar);bar=column(a);bar.setOrientation(vertical?1:0);bar.setElevation(dp(a,5));
            TextView[] keys;
            if(state.evMode&&chosen.count==1)keys=new TextView[]{evKey(a,owner,"Exit EV Charging")};
            else if(chosen.count==1)keys=new TextView[]{diagnosticsButton(a,owner)};
            else if(!routeControls&&chosen.count==4)keys=new TextView[]{diagnosticsButton(a,owner),splitButton(a),snowButton(a),evKey(a,owner,state.evMode?"Exit EV Charging":"EV Charging Live Map")};
            else if(chosen.count==3)keys=new TextView[]{diagnosticsButton(a,owner),splitButton(a),state.evMode?evKey(a,owner,"Exit EV Charging"):snowButton(a)};
            else if(chosen.count==NatToolbarLayout.IDLE_SPLIT_COUNT)keys=new TextView[]{diagnosticsButton(a,owner),state.evMode?evKey(a,owner,"Exit EV Charging"):splitButton(a)};
            else {
                TextView[] original={evKey(a,owner,state.evMode?"Exit EV Charging":"EV Charging Live Map"),toolbarButton(a,"◇","OverDrive",()->NatV36Shortcuts.control(a)),toolbarButton(a,"★","Favorite Destinations",()->NatFavorites.show(a)),toolbarButton(a,"⊘","Clean Mode",()->{act(a,owner,true);refresh(a,a.getWindow().peekDecorView());})};
                keys=java.util.Arrays.copyOf(original,chosen.count);
                if(NatToolbarLayout.hasDiagnostics(chosen.count))keys[NatToolbarLayout.diagnosticIndex(chosen.count)]=diagnosticsButton(a,owner);
                if(NatToolbarLayout.splitIndex(chosen.count)>=0)keys[NatToolbarLayout.splitIndex(chosen.count)]=splitButton(a);
                if(chosen.count==7){keys[4]=diagnosticsButton(a,owner);keys[5]=splitButton(a);keys[6]=snowButton(a);}
            }
            for(int i=0;i<keys.length;i++){android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(size,size);if(i>0){if(vertical)lp.topMargin=gap;else lp.leftMargin=gap;}bar.addView(keys[i],lp);}
            put(host,bar,slot,true);state.toolbar=new WeakReference<>(bar);state.vertical=vertical;state.toolbarRoute=routeControls;state.toolbarEv=state.evMode;
        }else if(state.left!=slot.left||state.top!=slot.top)put(host,bar,slot,false);
        state.left=slot.left;state.top=slot.top;
        boolean[] activeKeys=new boolean[chosen.count];
        Boolean nativeLayer=NatNativeEvLayer.effective();
        boolean layerOn=nativeLayer!=null?nativeLayer:c.showLayer.isEmpty()&&c.hideLayer.size()==1&&c.mapControls;
        if(routeControls&&chosen.count>=NatToolbarLayout.LEGACY_COUNT){activeKeys[0]=layerOn;activeKeys[1]=false;activeKeys[2]=false;}
        else if(!routeControls&&chosen.count==4)activeKeys[3]=layerOn;
        if(state.evMode){int k=routeControls&&chosen.count>=4?0:chosen.count-1;activeKeys[k]=true;}
        for(int i=0;i<chosen.count;i++){TextView t=(TextView)bar.getChildAt(i);if(t.getCurrentTextColor()!=(activeKeys[i]||dark?Color.WHITE:0xff263238)||t.isSelected()!=activeKeys[i]||t.getBackground()==null){style(t,activeKeys[i],dark,size/2);t.setSelected(activeKeys[i]);}}
        if(!routeControls)return;
        NatPrediction.Snapshot matched=NatNavigationCard.matchesForecast(root,forecast)?forecast:null;
        NatLiveEnergy.Reading live=NatPrediction.liveReading();
        if(route){mini(a,host,state,views,bounds,slot,dark,true,Double.isFinite(live.soc)?Math.round(live.soc)+"%":"—");}
        else removeMini(state,true);
        showPanel(a,host,state,views,bounds,slot,dark,matched);
        if(route){mini(a,host,state,views,bounds,slot,dark,false,matched==null?"—":number(matched.remainingKwh,"kWh"));}
        else{removeMini(state,true);removeMini(state,false);}
    }
}
