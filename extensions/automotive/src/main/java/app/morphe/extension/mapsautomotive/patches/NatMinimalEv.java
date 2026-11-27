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
        WeakReference<android.widget.LinearLayout> toolbar,panel,current,remaining,arrival;
        String currentKey="",remainingKey="",arrivalKey="",uiStatus="Waiting for map";boolean collapsed;
        long categoryUntil=-1;
        boolean opened,confirmed,previousFresh;
        long previousSession=-1;
        int previousStops=-1,mode=0;
        long openedAt=-1;
        String rendered="",diagnostic="";
        int left=-1,top=-1;
        boolean vertical,explicitPanelRequest;
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
    private static void act(Activity a,State state,boolean clean){
        if(a.isFinishing()||!a.hasWindowFocus()||!enabled(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY))return;
        View root=a.getWindow().peekDecorView();if(root==null)return;
        Controls c=controls(root);updateSearch(state,c);boolean route=NatNavigationCard.hasVerifiedRoute(root);
        state.mode=0;removePanel(state);
        if(clean){
            if((ownedSearch(state)||unique(c.hide)!=null)&&!hideNative(state,c,false)&&c.results)toast(a,"ปิดผลค้นหา EV ด้วยปุ่มของ Maps");
            state.categoryUntil=-1;
            report(state,"Clean mode: NAT panels closed; native route retained");return;
        }
        if(hideNative(state,c,false))return;
        if(ownedSearch(state)&&c.results){toast(a,"ใช้ปุ่มปิดผลค้นหาของ Maps");return;}
        View open=unique(c.open),along=unique(c.along);
        boolean pending=android.os.SystemClock.elapsedRealtime()<=state.categoryUntil&&c.mapControls;
        if((route||pending)&&open!=null&&!c.results){if(open.performClick()){state.opened=true;state.confirmed=false;state.categoryUntil=-1;state.openedAt=android.os.SystemClock.elapsedRealtime();report(state,"Native EV category requested");}return;}
        if(route&&along!=null&&!c.results){if(along.performClick()){state.categoryUntil=android.os.SystemClock.elapsedRealtime()+30000;toast(a,"เลือกหมวดการชาร์จ EV ใน Maps");}return;}
        toast(a,"เปิดค้นหาตามเส้นทางใน Maps แล้วเลือก EV");report(state,"Native EV action unavailable; no route change");
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
        State s=STATES.get(a);if(s==null)return;remove(s.host());removePanel(s);removeMini(s,true);removeMini(s,false);remove(s.arrival==null?null:s.arrival.get());s.arrival=null;s.arrivalKey="";s.toolbar=null;s.left=s.top=-1;s.mode=0;
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
        t.setTooltipText("System Diagnostics — CPU / RAM / GPS");
        return t;
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
            if(bounds.contains(q)&&!Rect.intersects(toolbarSlot,q)&&!hits(q,s.current)&&!hits(q,s.arrival)&&free(views,q,host)){slot=q;break;}
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
            content.addView(line(a,"LIVE POWER • ค่าเฉลี่ยช่วงตัวอย่าง",12,muted));
            content.addView(line(a,power+" · "+powerState,20,fg));
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
            message="LIVE POWER • ค่าเฉลี่ยช่วงตัวอย่าง\n"+number(f==null?Double.NaN:live.power,"kW")
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
        PowerGauge(Context c,double kw,boolean dark){super(c);this.kw=kw;this.dark=dark;setContentDescription("Average battery power "+number(kw,"kW"));}
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
            if(bounds.contains(q)&&!Rect.intersects(toolbar,q)&&(panel==null||!Rect.intersects(panel,q))&&(other==null||!Rect.intersects(other,q))&&!hits(q,s.arrival)&&free(views,q,host))slot=q;
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
    private static boolean hits(Rect q,WeakReference<android.widget.LinearLayout> ref){Rect x=global(ref==null?null:ref.get());return x!=null&&Rect.intersects(q,x);}
    private static Rect rect(NatToolbarLayout.Slot q){return q==null?null:new Rect(q.x,q.y,q.x+q.w,q.y+q.h);}
    private static boolean overlaps(Rect a,Rect b){return a!=null&&b!=null&&Rect.intersects(a,b);}
    static String uiStatus(Activity a){State s=STATES.get(a);return s==null?"No UI session":s.uiStatus;}
    private static void battery(Activity a,FrameLayout host,State s,Rect slot,boolean dark,boolean current,String title,String value){
        WeakReference<android.widget.LinearLayout> ref=current?s.current:s.arrival;
        View old=ref==null?null:ref.get();
        String key=slot==null?"":dark+"|"+title+"|"+value+"|"+slot.toShortString();
        if(slot!=null&&key.equals(current?s.currentKey:s.arrivalKey)&&old!=null&&old.getParent()==host&&old.getVisibility()==View.VISIBLE)return;
        remove(old);
        if(current){s.current=null;s.currentKey="";}else{s.arrival=null;s.arrivalKey="";}
        if(slot==null)return;
        android.widget.LinearLayout box=column(a);box.setGravity(Gravity.CENTER);box.setBackground(background(dark?0xff20303a:Color.WHITE,dp(a,10)));box.setElevation(dp(a,4));
        box.setClickable(false);box.setFocusable(false);
        TextView caption=line(a,title,12,dark?0xffbacbd3:0xff546a74);caption.setGravity(Gravity.CENTER);caption.setSingleLine(true);
        TextView metric=line(a,value,36,value.startsWith("-")?(dark?0xffff8585:0xffb91c1c):dark?0xff74dba8:0xff137548);
        metric.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);metric.setGravity(Gravity.CENTER);metric.setSingleLine(true);
        box.addView(caption);box.addView(metric);put(host,box,slot,true);
        if(current){s.current=new WeakReference<>(box);s.currentKey=key;}else{s.arrival=new WeakReference<>(box);s.arrivalKey=key;}
    }
    private static void menuAction(Activity a,State s,int which,int tries){
        if(a.isFinishing()||a.isDestroyed()||!enabled(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY)||STATES.get(a)!=s)return;
        View root=a.getWindow().peekDecorView();if(root==null)return;
        if(!a.hasWindowFocus()){if(tries>0)root.postDelayed(()->menuAction(a,s,which,tries-1),150);else toast(a,"NAT: เปิดเมนูอีกครั้ง");return;}
        if(!controls(root).mapControls){toast(a,"NAT: กลับหน้าจอแผนที่ก่อน");return;}
        if(which==0||which==3)act(a,s,which==3);
        else if(which==1||which==2)switchPanel(a,s,which);
        else if(which==4)NatSystemDiagnostics.show(a);
        else if(which==5){s.collapsed=false;refresh(a,root);}
    }
    private static TextView launcher(Activity a,State s){
        return toolbarButton(a,"☷","NAT controls — all five actions",()->{
            if(!a.hasWindowFocus())return;
            final int[] selection={-1};
            android.app.AlertDialog d=new android.app.AlertDialog.Builder(a).setTitle("NAT Controls • จอดรถก่อนใช้งาน")
                .setItems(new String[]{"EV Charging","Energy Prediction","Charging Plan","Clean Mode","System Diagnostics","Expand toolbar"},(dialog,which)->selection[0]=which)
                .setNegativeButton("Close",null).create();
            d.setOnDismissListener(dialog->{View root=a.getWindow().peekDecorView();if(selection[0]>=0&&root!=null)root.postDelayed(()->menuAction(a,s,selection[0],6),150);});
            d.show();
        });
    }
    static void refresh(Activity a,View root){
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
        updateSearch(state,c);
        if(!c.mapControls){state.uiStatus="Map controls not recognized; views released";clear(a);return;}
        Rect bounds=new Rect();if(!host.getGlobalVisibleRect(bounds)){state.uiStatus="Map viewport unavailable";clear(a);return;}
        int size=dp(a,48),gap=dp(a,7),edge=dp(a,12);
        boolean dark=ColorScheme.nightColors(a);
        NatLiveEnergy.Reading live=NatPrediction.liveReading();
        NatPrediction.Snapshot matched=NatNavigationCard.matchesForecast(root,forecast)?forecast:null;
        Integer nativeSoc=NatNavigationCard.verifiedSoc(root);
        Integer arrival=matched!=null?Integer.valueOf(matched.google):nativeSoc;
        String arrivalLabel=matched!=null?"แบตปลายทาง":nativeSoc!=null?"แบตเมื่อถึง (Maps)":"แบตปลายทาง";
        Integer current=Double.isFinite(live.soc)&&live.soc>=0&&live.soc<=100?(int)Math.round(live.soc):null;
        // Independent full-size cards: neither the toolbar nor the native summary owns their lifecycle.
        Rect currentSlot=rect(NatPersistentLayout.battery(bounds.left,bounds.top,bounds.right,bounds.bottom,dp(a,164),dp(a,78),edge,true,q->free(views,rect(q),host)));
        Rect arrivalSlot=rect(NatPersistentLayout.battery(bounds.left,bounds.top,bounds.right,bounds.bottom,dp(a,198),dp(a,94),edge,false,q->!overlaps(currentSlot,rect(q))&&free(views,rect(q),host)));
        battery(a,host,state,currentSlot,dark,true,"แบตเตอรี่ปัจจุบัน",NatPersistentLayout.soc(current));
        battery(a,host,state,arrivalSlot,dark,false,arrivalLabel,NatPersistentLayout.soc(arrival));
        NatToolbarLayout.Slot chosen=NatPersistentLayout.toolbar(bounds.left,bounds.top,bounds.right,bounds.bottom,size,gap,edge,state.collapsed,
                q->{Rect r=rect(q);return !overlaps(currentSlot,r)&&!overlaps(arrivalSlot,r)&&free(views,r,host);});
        state.uiStatus="Current: "+(currentSlot==null?"no safe slot":current==null?"visible / no fresh SOC":"visible")+" | Arrival: "+(arrivalSlot==null?"no safe slot":arrival==null?"visible / no verified SOC":"visible")+" | Toolbar: "+(chosen==null?"no safe slot":chosen.count==1?"launcher":"5 buttons")+" | Route summary: "+(route?"verified":"not verified");
        if(chosen==null){remove(state.host());state.toolbar=null;state.left=state.top=-1;removeMini(state,false);if(state.explicitPanelRequest&&state.mode!=0)panelUnavailable(a,state,matched);else{state.mode=0;removePanel(state);}return;}
        Rect slot=rect(chosen);boolean vertical=chosen.vertical;
        boolean active=unique(c.hide)!=null||(ownedSearch(state)&&c.evEvidence);
        android.widget.LinearLayout bar=state.toolbar==null?null:state.toolbar.get();
        if(bar==null||bar.getParent()!=host||state.vertical!=vertical||bar.getChildCount()!=chosen.count){
            remove(bar);bar=column(a);bar.setOrientation(vertical?1:0);bar.setElevation(dp(a,5));
            TextView[] keys;
            if(chosen.count==1)keys=new TextView[]{launcher(a,owner)};
            else {
                keys=new TextView[]{toolbarButton(a,"⚡","EV Charging",()->act(a,owner,false)),toolbarButton(a,"↗","Energy Prediction",()->switchPanel(a,owner,1)),toolbarButton(a,"☷","Charging Plan",()->switchPanel(a,owner,2)),toolbarButton(a,"⊘","Clean Mode — hold to collapse",()->{act(a,owner,true);refresh(a,a.getWindow().peekDecorView());}),diagnosticsButton(a,owner)};
                keys[3].setOnLongClickListener(v->{owner.collapsed=true;owner.mode=0;removePanel(owner);refresh(a,a.getWindow().peekDecorView());return true;});
            }
            for(int i=0;i<keys.length;i++){android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(size,size);if(i>0){if(vertical)lp.topMargin=gap;else lp.leftMargin=gap;}bar.addView(keys[i],lp);}
            put(host,bar,slot,true);state.toolbar=new WeakReference<>(bar);state.vertical=vertical;
        }else if(state.left!=slot.left||state.top!=slot.top)put(host,bar,slot,false);
        state.left=slot.left;state.top=slot.top;
        boolean[] activeKeys=chosen.count==1?new boolean[]{false}:new boolean[]{active,state.mode==1,state.mode==2,false,false};
        for(int i=0;i<chosen.count;i++){TextView t=(TextView)bar.getChildAt(i);if(t.getCurrentTextColor()!=(activeKeys[i]||dark?Color.WHITE:0xff263238)||t.isSelected()!=activeKeys[i]||t.getBackground()==null){style(t,activeKeys[i],dark,size/2);t.setSelected(activeKeys[i]);}}
        showPanel(a,host,state,views,bounds,slot,dark,matched);
        if(route)mini(a,host,state,views,bounds,slot,dark,false,matched==null?"—":number(matched.remainingKwh,"kWh"));
        else removeMini(state,false);
    }
}
