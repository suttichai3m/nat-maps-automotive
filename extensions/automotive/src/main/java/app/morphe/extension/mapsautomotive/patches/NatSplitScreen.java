package app.morphe.extension.mapsautomotive.patches;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.location.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.lang.ref.WeakReference;

/** Dedicated native viewport, with a separately owned dashboard and footer.
 * Never reparent the renderer. Restore native layout on unverified geometry.
 */
final class NatSplitScreen {
 private static final String OWN="nat_v201_overlay";
 private static final WeakHashMap<Activity,State> states=new WeakHashMap<>();
 private static volatile Motion sample;
 private static final class Motion {final long at;final double speed;Motion(long t,double s){at=t;speed=s;}}
 static void observe(long at,double speed){sample=new Motion(at,speed);}
 static boolean active(Activity a){State s=states.get(a);return s!=null&&s.panel!=null&&s.footer!=null&&s.nativeView!=null&&s.panel.getParent()==s.host&&s.footer.getParent()==s.host&&s.nativeView.getParent()==s.host;}
 static boolean ownsRoot(View root){
  if(root==null)return false;
  for(State s:states.values())if(s!=null&&s.host!=null&&s.panel!=null&&s.panel.getParent()==s.host&&s.host.getRootView()==root)return true;
  return false;
 }
 static Rect mapBounds(View root){
  for(State s:states.values())if(s!=null&&s.host!=null&&s.nativeView!=null&&s.panel!=null&&s.host.getRootView()==root){Rect b=new Rect();if(s.nativeView.getGlobalVisibleRect(b))return b;}
  return null;
 }
 static boolean enabled(Activity a){State s=states.get(a);return s!=null&&s.requested;}
 static String status(Activity a){State s=states.get(a);return s==null?"Waiting for map":s.reason;}
 static void toggle(Activity a){toggle(a,true);}
 static void temporaryToggle(Activity a){toggle(a,false);}
 private static void toggle(Activity a,boolean remember){
  if(a==null||a.isFinishing()||a.isDestroyed()||!a.hasWindowFocus())return;
  State s=states.get(a);if(s==null){s=new State();states.put(a,s);}
  View root=a.getWindow().peekDecorView();if(root!=null)NatNavigationCard.invalidateSplitRead(root);
  boolean on=!s.requested;restore(s);s.requested=on;s.blocked=null;s.retryAt=0;
  s.resumeChecked=true;
  if(remember)customPrefs(a).edit().putBoolean("last_split",on).apply();
  s.reason=on?"Split requested":"Full Map requested";
  if(on){
   refresh(a,a.getWindow().peekDecorView());
   if(active(a)){NatNavigationCard.refresh(root);refreshDashboard(a,root);}
   if(!active(a)){s.requested=false;restore(s);Toast.makeText(a,"Split 75:25 unavailable — Full Map",Toast.LENGTH_SHORT).show();}
  }
 }
 private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 private static final class State {
  FrameLayout host;View nativeView;FrameLayout.LayoutParams original,applied;
  LinearLayout panel,footer,detailsBody;TextView driverSummary;Button evButton,smartButton;TextView soc,arrival,arrivalLabel,speed,power,remaining,rate,distance,eta,gps,byd,heading;
  ImageView hero;FrameLayout heroFrame;LinearLayout zoomCard;TextView zoomTitle,zoomValue,zoomHint,zoomSource,zoomLabel;TextView todayDuration,todayRate,todayPerKm;TextView range,odo,acDriver,acPassenger,todayKm,todayEnergy,tariff,todayCost,dayNote,outside,battTemp,soh,tyresFront,tyresRear;LocationManager lm;LocationListener listener;
  WeakReference<View> blocked;int blockedW,blockedH;
  long bearingAt=-1,fixAt=-1,started,lastEvidence=-1,retryAt;
  double bearing=Double.NaN,turn=Double.NaN;
  boolean dark,requested,resumeChecked;String reason="Full Map (default)";
 }
 private static boolean shown(View v){return v!=null&&v.isShown()&&v.getAlpha()>0&&v.getWidth()>0&&v.getHeight()>0;}
 private static String id(View v){try{return v.getId()==View.NO_ID?"":v.getResources().getResourceEntryName(v.getId());}catch(RuntimeException ignored){return "";}}
 private static boolean evidence(View v,int depth,boolean renderer){
  if(v==null||depth>40||!shown(v)||NatMinimalEv.owned(v))return false;
  if("map_buttons_view_container".equals(id(v))||(renderer&&(v instanceof SurfaceView||v instanceof TextureView)))return true;
  if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(evidence(g.getChildAt(i),depth+1,renderer))return true;}return false;
 }
 private static boolean withinViewport(View v,View frame,int depth){
  if(depth>40||!shown(v)||NatMinimalEv.owned(v))return true;
  boolean target=v instanceof SurfaceView||v instanceof TextureView||"map_buttons_view_container".equals(id(v));
  if(target){
   int[] p=new int[2],q=new int[2];v.getLocationOnScreen(p);frame.getLocationOnScreen(q);
   if(p[0]<q[0]-2||p[0]+v.getWidth()>q[0]+frame.getWidth()+2||p[1]<q[1]-2||p[1]+v.getHeight()>q[1]+frame.getHeight()+2)return false;
  }
  if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(!withinViewport(g.getChildAt(i),frame,depth+1))return false;}
  return true;
 }
 private static void remove(View v){if(v!=null&&v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);}
 private static void stopGps(State s){
  if(s.listener!=null&&s.lm!=null)try{s.lm.removeUpdates(s.listener);}catch(RuntimeException ignored){}
  s.listener=null;s.lm=null;s.bearingAt=s.fixAt=-1;s.bearing=s.turn=Double.NaN;
 }
 private static void removeUi(State s){
  NatV33Telemetry.stop();
  remove(s.panel);remove(s.footer);s.panel=s.footer=s.detailsBody=null;s.hero=null;s.heroFrame=null;s.zoomCard=null;s.zoomTitle=s.zoomValue=s.zoomHint=s.zoomSource=s.zoomLabel=null;s.todayDuration=s.todayRate=s.todayPerKm=null;s.evButton=null;s.smartButton=null;s.driverSummary=null;
  s.range=s.odo=s.acDriver=s.acPassenger=s.todayKm=s.todayEnergy=s.tariff=s.todayCost=s.dayNote=s.outside=s.battTemp=s.soh=s.tyresFront=s.tyresRear=null;
  s.soc=s.arrival=s.arrivalLabel=s.speed=s.power=s.remaining=s.rate=s.distance=s.eta=s.gps=s.byd=s.heading=null;
 }
 private static void restore(State s){
  stopGps(s);removeUi(s);
  try{
   if(s.nativeView!=null&&s.nativeView.getParent()==s.host&&s.original!=null&&s.applied!=null){
    ViewGroup.LayoutParams raw=s.nativeView.getLayoutParams();
    if(raw==s.applied)s.nativeView.setLayoutParams(s.original);
    else if(raw instanceof FrameLayout.LayoutParams){
     FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)raw;
     if(NatSplitMath.ownsGeometry(lp.width,lp.height,lp.gravity,s.applied.width,s.applied.height,s.applied.gravity)){
      FrameLayout.LayoutParams back=new FrameLayout.LayoutParams(lp);
      back.width=s.original.width;back.height=s.original.height;back.gravity=s.original.gravity;
      s.nativeView.setLayoutParams(back);
     }
    }
   }
  }catch(RuntimeException error){ReportLog.w("NatSplitScreen","Native restore failed",error);}
  finally{s.host=null;s.nativeView=null;s.original=s.applied=null;s.lastEvidence=-1;}
 }
 private static void fail(State s,String reason,boolean block,int w,int h){
  if(block&&s.nativeView!=null){s.blocked=new WeakReference<>(s.nativeView);s.blockedW=w;s.blockedH=h;}
  restore(s);s.requested=false;s.retryAt=0;
  if(!reason.equals(s.reason))ReportLog.i("NatSplitScreen",reason);
  s.reason=reason;
 }
 static void suspend(Activity a){View root=a.getWindow().peekDecorView();if(root!=null)NatNavigationCard.invalidateSplitRead(root);State s=states.get(a);if(s!=null){stopGps(s);NatV33Telemetry.stop();}}
 static void clear(Activity a){View root=a.getWindow().peekDecorView();if(root!=null)NatNavigationCard.invalidateSplitRead(root);State s=states.remove(a);if(s!=null)restore(s);}
 static void refresh(Activity a,View root){
  if(a==null)return;if(a.isDestroyed()||a.isFinishing()){clear(a);return;}
  State s=states.get(a);if(s==null){s=new State();states.put(a,s);}
  if(!s.resumeChecked&&a.hasWindowFocus()){s.resumeChecked=true;s.requested=customPrefs(a).getBoolean("resume",false)&&customPrefs(a).getBoolean("last_split",false)&&!NatMinimalEv.evMode(a);}
  if(!enabled(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY)||!NatMinimalEv.enabled(a)){if(s.nativeView!=null)restore(s);s.requested=false;return;}
  if(root==null||!a.hasWindowFocus()){suspend(a);return;}
  try{
   View content=a.findViewById(android.R.id.content);
   if(!(content instanceof FrameLayout)){fail(s,"Full Map: content is not FrameLayout",false,0,0);return;}
   FrameLayout h=(FrameLayout)content;int w=h.getWidth(),height=h.getHeight();
   int side=NatSplitMath.side(w,height,dp(a,540),dp(a,200)),footer=dp(a,56),mapH=NatSplitMath.viewportHeight(height,footer,dp(a,260));
   if(side==0||mapH==0){fail(s,"Full Map: viewport too small for safe split",false,w,height);return;}
   long now=SystemClock.elapsedRealtime();
   if(s.nativeView!=null){
    if(s.host!=h||s.nativeView.getParent()!=h||s.panel==null||s.panel.getParent()!=h||s.footer==null||s.footer.getParent()!=h||s.nativeView.getLayoutParams()!=s.applied){
     fail(s,"Native view changed; recovering on next map scan",false,w,height);return;
    }
    if(s.applied.width!=w-side||s.applied.height!=mapH){fail(s,"Viewport resized; rebuilding",false,w,height);return;}
    boolean seen=evidence(s.nativeView,0,true);
    if(seen)s.lastEvidence=now;
    if(!NatSplitMath.keepViewport(seen,now,s.lastEvidence)){fail(s,"Full Map: map renderer no longer visible",false,w,height);return;}
    if(now-s.started>2000){
     if(!NatSplitMath.geometry(s.nativeView.getWidth(),s.nativeView.getHeight(),w-side,mapH,2)||
        !NatSplitMath.geometry(s.panel.getWidth(),s.panel.getHeight(),side,mapH,2)||
        !NatSplitMath.geometry(s.footer.getWidth(),s.footer.getHeight(),w,footer,2)||
        !withinViewport(s.nativeView,s.nativeView,0)||!disjoint(s)){
      fail(s,"Full Map: renderer / native controls failed geometry check; toggle Split to retry",true,w,height);return;
     }
    }
    // Rebuild only our views for a theme change; keep the renderer and battery source intact.
    if(s.dark!=ColorScheme.nightColors(a)){removeUi(s);build(a,s,side,mapH,footer);s.started=now;}
   }else{
    if(now<s.retryAt)return;
    View child=null;
    for(int i=0;i<h.getChildCount();i++){View v=h.getChildAt(i);if(NatMinimalEv.owned(v))continue;if(child!=null){s.reason="Full Map: multiple native children";return;}child=v;}
    if(child==null||!evidence(child,0,false)||!(child.getLayoutParams() instanceof FrameLayout.LayoutParams)){s.reason="Full Map: waiting for recognized native map";return;}
    if(s.blocked!=null&&s.blocked.get()==child&&s.blockedW==w&&s.blockedH==height)return;
    FrameLayout.LayoutParams old=(FrameLayout.LayoutParams)child.getLayoutParams();
    if(old.width!=-1||old.height!=-1||old.leftMargin!=0||old.rightMargin!=0||old.topMargin!=0||old.bottomMargin!=0||
       h.getPaddingLeft()!=0||h.getPaddingRight()!=0||h.getPaddingTop()!=0||h.getPaddingBottom()!=0){s.reason="Full Map: unsupported native layout params";return;}
    NatMinimalEv.clear(a);
    s.host=h;s.nativeView=child;s.original=old;s.applied=new FrameLayout.LayoutParams(old);
    s.applied.width=w-side;s.applied.height=mapH;s.applied.gravity=Gravity.TOP|Gravity.LEFT;
    s.started=s.lastEvidence=now;child.setLayoutParams(s.applied);
    build(a,s,side,mapH,footer);
    s.reason="Split attached; awaiting geometry check";
   }
   if(s.listener==null)listen(a,s);
   if(now-s.started>2000)s.reason="Split 75/25 | native geometry checked | dashboard attached | "+(NatNavigationCard.recentlyVerified(root)?"route verified":"route unverified")+" | Current "+s.soc.getText()+" | Arrival "+s.arrival.getText();
  }catch(RuntimeException e){fail(s,"Full Map: split exception; restored",true,s.host==null?0:s.host.getWidth(),s.host==null?0:s.host.getHeight());ReportLog.w("NatSplitScreen","Split layout restored",e);}
 }
 private static boolean disjoint(State s){
  Rect m=new Rect(),p=new Rect(),f=new Rect();
  return s.nativeView.getGlobalVisibleRect(m)&&s.panel.getGlobalVisibleRect(p)&&s.footer.getGlobalVisibleRect(f)&&
         !Rect.intersects(m,p)&&!Rect.intersects(m,f)&&!Rect.intersects(p,f);
 }
 private static int fg(State s){return s.dark?0xffedf4f7:0xff263238;}
 private static int muted(State s){return s.dark?0xffb4c6d1:0xff546a74;}
 private static int card(State s){return s.dark?0xff192b3b:0xfff8fbfc;}
 private static int surface(State s){return s.dark?0xff101b29:0xffe8f0f3;}
 private static int green(State s){return s.dark?0xff83e5b4:0xff136b45;}
 private static int blue(State s){return s.dark?0xffa3ccff:0xff174c90;}
 private static GradientDrawable bg(Context c,int color,int edge,int radius){
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));if(edge!=0)d.setStroke(dp(c,1),edge);return d;
 }
 private static TextView text(Context c,String value,int sp,int color,boolean bold){
  TextView v=new TextView(c);v.setText(value);v.setTextSize(sp);v.setTextColor(color);v.setGravity(Gravity.CENTER);v.setIncludeFontPadding(false);
  if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;
 }
 private static LinearLayout vertical(Context c){LinearLayout x=new LinearLayout(c);x.setOrientation(LinearLayout.VERTICAL);return x;}
 private static LinearLayout row(Context c){LinearLayout x=new LinearLayout(c);x.setOrientation(LinearLayout.HORIZONTAL);return x;}
 private static void add(LinearLayout host,View child,int height){host.addView(child,new LinearLayout.LayoutParams(-1,height));}
 private static TextView metric(Activity a,State s,LinearLayout row,String name,int accent,boolean battery,boolean arrival){
  LinearLayout cell=vertical(a);cell.setGravity(Gravity.CENTER);cell.setPadding(dp(a,4),dp(a,5),dp(a,4),dp(a,5));
  cell.setBackground(bg(a,card(s),battery?accent:(s.dark?0xff2c4154:0xffd4dfe6),11));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(a,2),dp(a,2),dp(a,2),dp(a,2));row.addView(cell,lp);
  TextView label=text(a,name,battery?12:11,muted(s),false);label.setMaxLines(2);add(cell,label,-2);
  TextView value=text(a,"—",battery?30:17,accent,true);value.setSingleLine(true);value.setAutoSizeTextTypeUniformWithConfiguration(battery?24:12,battery?34:19,1,android.util.TypedValue.COMPLEX_UNIT_SP);
  add(cell,value,-2);if(arrival)s.arrivalLabel=label;
  cell.setClickable(true);cell.setFocusable(true);cell.setTooltipText("แตะเพื่อขยาย "+name);cell.setOnClickListener(v->zoom(a,s,label,value));return value;
 }
 private static void zoom(Activity a,State s,TextView label,TextView value){
  if(!active(a)||s.zoomCard==null||!a.hasWindowFocus())return;
  if(s.zoomSource==value){closeZoom(s);return;}
  s.zoomLabel=label;s.zoomSource=value;s.hero.setVisibility(View.INVISIBLE);s.zoomCard.setVisibility(View.VISIBLE);refreshZoom(s);
 }
 private static void closeZoom(State s){s.zoomSource=s.zoomLabel=null;if(s.zoomCard!=null)s.zoomCard.setVisibility(View.GONE);if(s.hero!=null)s.hero.setVisibility(View.VISIBLE);}
 private static void refreshZoom(State s){
  if(s.zoomSource==null||s.zoomLabel==null||s.zoomCard==null)return;
  if(!s.zoomSource.isShown()){closeZoom(s);return;}
  set(s.zoomTitle,s.zoomLabel.getText().toString());set(s.zoomValue,s.zoomSource.getText().toString());s.zoomValue.setTextColor(s.zoomSource.getCurrentTextColor());
  s.zoomCard.setContentDescription(s.zoomTitle.getText()+" "+s.zoomValue.getText()+" แตะเพื่อปิด");
 }
 private static Button action(Activity a,State s,LinearLayout parent,String name,int symbol,Runnable callback){
  Button b=new Button(a);b.setText(name);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(fg(s));b.setGravity(Gravity.CENTER);b.setMinHeight(dp(a,48));b.setMinimumHeight(dp(a,48));
  b.setPadding(dp(a,4),0,dp(a,4),0);b.setSingleLine(true);b.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);
  b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(s.dark?0x33ffffff:0x22000000),bg(a,card(s),0,8),null));
  Icon icon=new Icon(symbol,fg(s));icon.setBounds(0,0,dp(a,20),dp(a,20));b.setCompoundDrawablesRelative(icon,null,null,null);b.setCompoundDrawablePadding(dp(a,3));
  b.setContentDescription(name);b.setTooltipText(name);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(a,3),dp(a,3),dp(a,3),dp(a,3));parent.addView(b,lp);
  b.setOnClickListener(v->{try{callback.run();}catch(RuntimeException e){ReportLog.w("NatSplitScreen","Action unavailable: "+name,e);Toast.makeText(a,"NAT action unavailable",Toast.LENGTH_SHORT).show();}});
  return b;
 }
 private static void build(Activity a,State s,int width,int mapH,int footerH){
  s.dark=ColorScheme.nightColors(a);
  LinearLayout panel=vertical(a);panel.setTag(OWN);panel.setBackgroundColor(surface(s));panel.setPadding(dp(a,6),dp(a,5),dp(a,6),dp(a,4));s.panel=panel;
  add(panel,text(a,"BYD SEAL  •  NAT V4.0",14,fg(s),true),dp(a,24));
  s.hero=new ImageView(a);s.hero.setScaleType(ImageView.ScaleType.FIT_CENTER);
  s.hero.setContentDescription("BYD Seal blue, static studio image");s.hero.setImageBitmap(NatSealHero.bitmap());
  s.hero.setBackground(bg(a,0xff071522,0,8));s.hero.setClipToOutline(true);
  int heroH=Math.min(dp(a,118),Math.max(dp(a,48),mapH/5));
  s.heroFrame=new FrameLayout(a);add(panel,s.heroFrame,heroH);s.heroFrame.addView(s.hero,new FrameLayout.LayoutParams(-1,-1));
  s.zoomCard=vertical(a);s.zoomCard.setGravity(Gravity.CENTER);s.zoomCard.setPadding(dp(a,6),dp(a,3),dp(a,6),dp(a,3));s.zoomCard.setBackground(bg(a,card(s),blue(s),10));
  s.zoomTitle=text(a,"",16,fg(s),true);s.zoomTitle.setSingleLine(true);s.zoomTitle.setAutoSizeTextTypeUniformWithConfiguration(11,18,1,android.util.TypedValue.COMPLEX_UNIT_SP);add(s.zoomCard,s.zoomTitle,dp(a,23));
  s.zoomValue=text(a,"—",48,fg(s),true);s.zoomValue.setMaxLines(2);s.zoomValue.setAutoSizeTextTypeUniformWithConfiguration(16,58,1,android.util.TypedValue.COMPLEX_UNIT_SP);s.zoomCard.addView(s.zoomValue,new LinearLayout.LayoutParams(-1,0,1));
  s.zoomHint=text(a,"แตะเพื่อปิด ×",12,muted(s),false);add(s.zoomCard,s.zoomHint,dp(a,18));
  s.heroFrame.addView(s.zoomCard,new FrameLayout.LayoutParams(-1,-1));s.zoomCard.setVisibility(View.GONE);s.zoomCard.setClickable(true);s.zoomCard.setFocusable(true);s.zoomCard.setOnClickListener(v->closeZoom(s));
  
  s.heading=text(a,"—",11,muted(s),true);
  s.heading.setSingleLine(true);
  s.heading.setAutoSizeTextTypeUniformWithConfiguration(9,11,1,android.util.TypedValue.COMPLEX_UNIT_SP);
  LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(-1,dp(a,20));
  headingParams.setMargins(dp(a,4),dp(a,1),dp(a,4),dp(a,2));
  panel.addView(s.heading,headingParams);
  LinearLayout batteries=row(a);add(panel,batteries,dp(a,84));
  s.soc=metric(a,s,batteries,"แบตเตอรี่ปัจจุบัน",green(s),true,false);
  s.arrival=metric(a,s,batteries,"แบตเมื่อถึงปลายทาง",blue(s),true,true);
  // Battery cards remain outside the scrollable details, even on smaller displays.
  ScrollView details=new ScrollView(a);details.setFillViewport(true);panel.addView(details,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout body=vertical(a);s.detailsBody=body;details.addView(body,new ScrollView.LayoutParams(-1,-2));
  s.driverSummary=text(a,"",14,fg(s),true);s.driverSummary.setGravity(Gravity.START);s.driverSummary.setPadding(dp(a,6),dp(a,6),dp(a,6),dp(a,6));add(body,s.driverSummary,-2);s.driverSummary.setVisibility(View.GONE);
  int metricsH=dp(a,46);
  LinearLayout m0=row(a);m0.setTag("nat_custom_range");add(body,m0,metricsH);s.range=metric(a,s,m0,"ระยะที่ขับได้ (ประมาณ)",green(s),false,false);s.odo=metric(a,s,m0,"เลขไมล์สะสม ODO",fg(s),false,false);
  LinearLayout m1=row(a);m1.setTag("nat_custom_power");add(body,m1,metricsH);s.speed=metric(a,s,m1,"ความเร็ว",fg(s),false,false);s.power=metric(a,s,m1,"POWER / REGEN",fg(s),false,false);
  LinearLayout m2=row(a);m2.setTag("nat_custom_forecast");add(body,m2,metricsH);s.remaining=metric(a,s,m2,"พลังงานที่ต้องใช้อีก",fg(s),false,false);s.rate=metric(a,s,m2,"อัตราสิ้นเปลืองคาดการณ์",fg(s),false,false);
  LinearLayout m3=row(a);m3.setTag("nat_custom_route");add(body,m3,metricsH);s.distance=metric(a,s,m3,"ระยะทาง (Maps)",fg(s),false,false);s.eta=metric(a,s,m3,"ถึงเวลา (Maps)",fg(s),false,false);
  LinearLayout ac=row(a);ac.setTag("nat_custom_climate");add(body,ac,metricsH);s.acDriver=metric(a,s,ac,"แอร์คนขับ",blue(s),false,false);s.acPassenger=metric(a,s,ac,"แอร์ผู้โดยสาร",blue(s),false,false);
  add(body,text(a,"TODAY  •  สรุปการขับวันนี้",12,fg(s),true),dp(a,22));
  LinearLayout daily=row(a);daily.setTag("nat_custom_daily");add(body,daily,metricsH);s.todayKm=metric(a,s,daily,"ระยะทางวันนี้",fg(s),false,false);s.todayEnergy=metric(a,s,daily,"พลังงานสุทธิที่บันทึก",fg(s),false,false);
  LinearLayout costs=row(a);costs.setTag("nat_custom_cost");add(body,costs,metricsH);s.tariff=metric(a,s,costs,"ค่าไฟ / kWh  ⚙",blue(s),false,false);s.todayCost=metric(a,s,costs,"ค่าไฟวันนี้ (ประมาณ)",green(s),false,false);
  View tariffCell=(View)s.tariff.getParent();tariffCell.setTooltipText("แตะขยาย • กดค้างเพื่อตั้งค่าไฟเมื่อจอด");tariffCell.setOnLongClickListener(v->{NatV33Telemetry.Fast f=NatV33Telemetry.fast();if(f!=null&&Double.isFinite(f.speed)&&f.speed<=1)editTariff(a,s);else Toast.makeText(a,"ตั้งค่าเมื่อจอดรถ",Toast.LENGTH_SHORT).show();return true;});
  LinearLayout todayMore=row(a);todayMore.setTag("nat_custom_daily");add(body,todayMore,metricsH);s.todayDuration=metric(a,s,todayMore,"เวลาขับที่บันทึก",fg(s),false,false);s.todayRate=metric(a,s,todayMore,"สิ้นเปลืองช่วงที่วัด",fg(s),false,false);
  LinearLayout perKm=row(a);perKm.setTag("nat_custom_cost");add(body,perKm,metricsH);s.todayPerKm=metric(a,s,perKm,"ค่าไฟช่วงที่วัด / km",green(s),false,false);
  s.dayNote=text(a,"ข้อมูลตามช่วงที่แอปบันทึก",10,muted(s),false);add(body,s.dayNote,dp(a,20));
  LinearLayout v1=row(a);v1.setTag("nat_custom_temperature");add(body,v1,metricsH);s.outside=metric(a,s,v1,"อุณหภูมิภายนอก",fg(s),false,false);s.battTemp=metric(a,s,v1,"อุณหภูมิแบตเฉลี่ย",fg(s),false,false);
  LinearLayout v2=row(a);v2.setTag("nat_custom_tyres");add(body,v2,metricsH);s.tyresFront=metric(a,s,v2,"ยางหน้า L / R (PSI)",fg(s),false,false);s.tyresRear=metric(a,s,v2,"ยางหลัง L / R (PSI)",fg(s),false,false);
  LinearLayout v3=row(a);v3.setTag("nat_custom_health");add(body,v3,metricsH);s.soh=metric(a,s,v3,"SOH จาก BMS",green(s),false,false);s.byd=metric(a,s,v3,"ข้อมูล BYD",fg(s),false,false);
  LinearLayout m4=row(a);m4.setTag("nat_custom_gps");add(body,m4,metricsH);s.gps=metric(a,s,m4,"GPS",fg(s),false,false);
  s.host.addView(panel,new FrameLayout.LayoutParams(width,mapH,Gravity.RIGHT|Gravity.TOP));
  applyCustom(a,s);
  LinearLayout footer=row(a);footer.setTag(OWN);footer.setBackgroundColor(surface(s));footer.setPadding(dp(a,3),0,dp(a,3),0);s.footer=footer;
  action(a,s,footer,"Snow AI",8,()->NatV36Shortcuts.snow(a));
  s.smartButton=action(a,s,footer,"Smart EV",6,()->NatSmartEvMenu.show(a));
  s.smartButton.setTooltipText("Smart EV • hold: Driver Mode");s.smartButton.setOnLongClickListener(v->{NatDriverWork.show(a);return true;});
  s.evButton=action(a,s,footer,"EV Charging",0,()->NatMinimalEv.splitAction(a,0));
  s.evButton.setTextColor(green(s));s.evButton.setTooltipText("EV Charging • กดค้างเพื่อค้นหาสถานีใน Maps เมื่อจอด");s.evButton.setOnLongClickListener(v->{NatMinimalEv.nearby(a);return true;});
  action(a,s,footer,"OverDrive",7,()->NatV36Shortcuts.control(a));
  action(a,s,footer,"Favorites",2,()->NatFavorites.show(a));
  action(a,s,footer,"Clean",3,()->NatMinimalEv.splitAction(a,3));
  action(a,s,footer,"Diagnostics",4,()->NatMinimalEv.splitAction(a,4));
  action(a,s,footer,"Full Map 100%",5,()->toggle(a));
  s.host.addView(footer,new FrameLayout.LayoutParams(-1,footerH,Gravity.BOTTOM|Gravity.LEFT));
 }
 private static final String[] CUSTOM_KEYS={"range","power","forecast","route","climate","daily","cost","temperature","tyres","health","gps"};
 private static final String[] CUSTOM_NAMES={"ระยะขับได้ / ODO","ความเร็ว / POWER","พลังงานที่ต้องใช้ / อัตราสิ้นเปลือง","ระยะทาง / ETA","อุณหภูมิแอร์","ระยะทาง / พลังงานวันนี้","ค่าไฟต่อหน่วย / ค่าไฟวันนี้","อุณหภูมิภายนอก / แบต","ลมยางหน้า / หลัง","SOH / BYD","GPS"};
 private static android.content.SharedPreferences customPrefs(Activity a){return a.getSharedPreferences("nat_v36_dashboard",0);}
 private static void applyCustom(Activity a,State s){
  if(s.detailsBody==null)return;
  boolean driver=NatDriverWork.dashboard(a),clean=customPrefs(a).getBoolean("minimal_drive",false);
  for(int i=0;i<s.detailsBody.getChildCount();i++){View v=s.detailsBody.getChildAt(i);Object tag=v.getTag();boolean show=!driver;if(v==s.driverSummary)show=driver;else if(tag instanceof String&&((String)tag).startsWith("nat_custom_")){String key=((String)tag).substring(11);show=!driver&&customPrefs(a).getBoolean(key,true)&&(!clean||key.equals("power")||key.equals("route"));}else if(clean)show=false;v.setVisibility(show?View.VISIBLE:View.GONE);}
  if(s.driverSummary!=null&&driver)set(s.driverSummary,NatDriverWork.summary(a));
 }
 static void personalize(Activity a){
  if(!active(a)||!a.hasWindowFocus())return;
  String[] names={"จำหน้าจอ Split เมื่อเปิดใหม่","Smart Driving • ซ่อนรายละเอียดเสริม","Driver Dashboard"};
  boolean[] values={customPrefs(a).getBoolean("resume",false),customPrefs(a).getBoolean("minimal_drive",false),NatDriverWork.dashboard(a)};
  new android.app.AlertDialog.Builder(a).setTitle("Personalization • ตั้งค่าขณะจอด").setMultiChoiceItems(names,values,(d,i,b)->values[i]=b).setNegativeButton("ยกเลิก",null).setPositiveButton("บันทึก",(d,w)->{customPrefs(a).edit().putBoolean("resume",values[0]).putBoolean("last_split",enabled(a)).putBoolean("minimal_drive",values[1]).apply();NatDriverWork.dashboard(a,values[2]);State s=states.get(a);if(s!=null)applyCustom(a,s);}).show();
 }
 static void customize(Activity a){
  if(!active(a)||!a.hasWindowFocus())return;
  boolean[] selected=new boolean[CUSTOM_KEYS.length];for(int i=0;i<selected.length;i++)selected[i]=customPrefs(a).getBoolean(CUSTOM_KEYS[i],true);
  new android.app.AlertDialog.Builder(a).setTitle("Custom Dashboard").setMultiChoiceItems(CUSTOM_NAMES,selected,(d,i,b)->selected[i]=b)
   .setNegativeButton("ยกเลิก",null).setNeutralButton("แสดงทั้งหมด",(d,w)->{android.content.SharedPreferences.Editor editor=customPrefs(a).edit();for(String k:CUSTOM_KEYS)editor.remove(k);editor.apply();State s=states.get(a);if(s!=null)applyCustom(a,s);})
   .setPositiveButton("บันทึก",(d,w)->{android.content.SharedPreferences.Editor e=customPrefs(a).edit();for(int i=0;i<selected.length;i++)e.putBoolean(CUSTOM_KEYS[i],selected[i]);e.apply();State s=states.get(a);if(s!=null)applyCustom(a,s);}).show();
 }
 private static void listen(Activity a,State s){
  if(s.listener!=null)return;
  try{
   if(a.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
   s.lm=a.getSystemService(LocationManager.class);if(s.lm==null||!s.lm.isProviderEnabled(LocationManager.GPS_PROVIDER)){s.lm=null;return;}
   s.listener=new LocationListener(){public void onLocationChanged(Location l){
    if(s.listener!=this||s.panel==null||!a.hasWindowFocus())return;
    long t=l.getElapsedRealtimeNanos()/1000000,now=SystemClock.elapsedRealtime();
    if(!NatSplitMath.fresh(now,t,3000)||!l.hasAccuracy()||!Float.isFinite(l.getAccuracy())||l.getAccuracy()<0){s.fixAt=-1;s.bearingAt=-1;s.turn=s.bearing=Double.NaN;return;}
    s.fixAt=t;
    if(!l.hasBearing()||!l.hasSpeed()||l.getAccuracy()>25||(l.hasBearingAccuracy()&&l.getBearingAccuracyDegrees()>30)){s.turn=s.bearing=Double.NaN;s.bearingAt=-1;return;}
    s.turn=NatSplitMath.turn(s.bearing,l.getBearing(),l.getSpeed()*3.6,t-s.bearingAt);s.bearing=l.getBearing();s.bearingAt=t;
   }public void onProviderDisabled(String x){s.fixAt=s.bearingAt=-1;s.turn=s.bearing=Double.NaN;}
    public void onProviderEnabled(String x){}public void onStatusChanged(String x,int y,Bundle z){}};
   s.lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,500,0,s.listener,Looper.getMainLooper());
  }catch(RuntimeException e){stopGps(s);}
 }
 private static String fmt(double v,String u){return Double.isFinite(v)?String.format(Locale.US,"%.1f %s",v,u):"—";}
 private static void set(TextView v,String value){if(!value.contentEquals(v.getText()))v.setText(value);}
 // Called after the native summary has been refreshed in this same UI tick.
 static void refreshDashboard(Activity a,View root){
  State s=states.get(a);if(s!=null&&active(a)&&a.hasWindowFocus()){if(s.evButton!=null){String label=NatMinimalEv.layerButtonLabel(a);if(!label.contentEquals(s.evButton.getText())){s.evButton.setText(label);s.evButton.setContentDescription(label);}}update(a,s,root);}
 }
 private static void editTariff(Activity a,State s){
  if(!active(a))return;
  EditText input=new EditText(a);input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
  input.setText(String.format(Locale.US,"%.2f",NatV33Telemetry.tariff(a)));input.selectAll();
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(a).setTitle("ตั้งราคาค่าไฟ (บาท/kWh)").setMessage("ค่าเริ่มต้น 7.00 บาท/kWh").setView(input).setNegativeButton("ยกเลิก",null).setPositiveButton("บันทึก",null).create();
  dialog.setOnShowListener(x->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   double rate;try{rate=Double.parseDouble(input.getText().toString().trim());}catch(NumberFormatException e){rate=Double.NaN;}
   if(!NatDailyMath.validRate(rate)){input.setError("กรอกมากกว่า 0 และไม่เกิน 100");return;}
   NatV33Telemetry.tariff(a,rate);updateDaily(a,s);dialog.dismiss();
  }));dialog.show();
 }
 private static void updateDaily(Activity a,State s){
  if(s.tariff==null)return;
  double price=NatV33Telemetry.tariff(a);NatV33Telemetry.Today t=NatV33Telemetry.today();
  set(s.tariff,String.format(Locale.US,"฿%.2f",price));
  set(s.todayKm,fmt(t==null?Double.NaN:t.km,"km"));set(s.todayEnergy,fmt(t==null?Double.NaN:t.kwh,"kWh"));
  double cost=t==null?Double.NaN:NatDailyMath.cost(t.kwh,price);
  set(s.todayCost,Double.isFinite(cost)?String.format(Locale.US,"฿%.2f",cost):"—");
  NatV36Daily.Summary times=NatV36Daily.read(a);set(s.todayDuration,times==null?"—":NatV36Daily.duration(times.moving));
  double observed=t!=null&&t.detailPairs>0?Math.max(0,t.observedNet):Double.NaN,covered=t!=null&&t.detailPairs>0?t.observedKm:Double.NaN;
  set(s.todayRate,fmt(NatDailyMath.forecastRate(observed,covered),"kWh/100km"));double per=NatDailyMath.perKm(NatDailyMath.cost(observed,price),covered);set(s.todayPerKm,Double.isFinite(per)?String.format(Locale.US,"฿%.2f/km",per):"—");
  set(s.dayNote,t==null?"รอข้อมูลวันนี้":"เฉพาะช่วงที่บันทึก • ไม่รวมการชาร์จ/ช่วงขาดข้อมูล");
 }
 static void refreshFast(Activity a){
  State s=states.get(a);if(s==null||!active(a)||!a.hasWindowFocus()||!BydBatteryPatch.isActive())return;
  NatV33Telemetry.request();NatV33Telemetry.Fast f=NatV33Telemetry.fast();
  updatePower(s);
  if(f==null){refreshZoom(s);return;}
  Integer soc=NatSplitMath.current(f.soc);if(soc!=null)set(s.soc,NatSplitMath.percent(soc));
  if(Double.isFinite(f.speed))set(s.speed,fmt(f.speed,"km/h"));
  refreshZoom(s);
 }
 private static void metricLabel(TextView value,String label){if(value!=null&&value.getParent() instanceof LinearLayout){View v=((LinearLayout)value.getParent()).getChildAt(0);if(v instanceof TextView)set((TextView)v,label);}}
 private static void batteryStatus(State s,TextView value,Integer percent,double reserve){
  boolean low=percent!=null&&percent<reserve;
  value.setTextColor(percent==null?fg(s):low?(s.dark?0xffff9b9b:0xffb91c1c):green(s));
 }
 private static void updatePower(State s){
  if(s.power==null)return;
  NatDrivePower.Reading direct=BydBatteryPatch.isActive()?NatDrivePower.fresh():null;
  double kw=direct!=null?direct.kw:Double.NaN;
  String label=direct!=null?NatDrivePowerRules.label(kw,direct.speed):"POWER / REGEN • ไม่มีข้อมูล";
  set(s.power,Double.isFinite(kw)?String.format(Locale.US,kw==0?"%.0f kW":"%+.0f kW",kw):"—");
  if(s.power.getParent() instanceof LinearLayout){View v=((LinearLayout)s.power.getParent()).getChildAt(0);if(v instanceof TextView)set((TextView)v,label);}
  s.power.setTextColor(direct!=null&&NatDrivePowerRules.regen(kw,direct.speed)?green(s):direct!=null&&kw>0?blue(s):fg(s));
 }
 private static void update(Activity a,State s,View root){
  long now=SystemClock.elapsedRealtime();Motion m=sample;
  NatV33Telemetry.request();NatV33Telemetry.Fast fast=NatV33Telemetry.fast();
  double speed=fast!=null&&Double.isFinite(fast.speed)?fast.speed:m==null||!BydBatteryPatch.isActive()?Double.NaN:NatSplitMath.speed(m.speed,now,m.at,15000);
  double turn=NatSplitMath.fresh(now,s.bearingAt,3000)&&Double.isFinite(speed)&&speed>=5?s.turn:Double.NaN;
  NatLiveEnergy.Reading live=NatPrediction.liveReading();NatPrediction.Snapshot f=NatPrediction.freshSnapshot();
  NatNavigationCard.RouteRead route=NatNavigationCard.splitRead(root,f);
  boolean match=route!=null&&f!=null&&(NatStabilityMath.matches(route.soc,route.km,f.google,f.km)||(f.charges>0&&NatStabilityMath.matches(route.soc,route.km,f.next,f.nextKm)));
  Integer current=NatSplitMath.current(fast!=null&&Double.isFinite(fast.soc)?fast.soc:live.soc),nativeSoc=route==null?null:route.soc;
  Integer arrival=NatSplitMath.arrival(match,match?Integer.valueOf(f.google):null,nativeSoc);
  set(s.soc,NatSplitMath.percent(current));set(s.arrival,NatSplitMath.percent(arrival));
  ((View)s.arrival.getParent()).setVisibility(NatNavigationCard.hasVisibleCard(root)&&(route==null||!route.held)?View.GONE:View.VISIBLE);
  set(s.arrivalLabel,route!=null&&route.held?"แบตถึง • ล่าสุด":match?"แบตเมื่อถึงปลายทาง":"แบตเมื่อถึง (Maps)");
  double reserve=a.getSharedPreferences("nat_v34_smart_ev",0).getFloat("reserve",15f);
  if(!Double.isFinite(reserve)||reserve<0||reserve>100)reserve=15;
  if(arrival!=null&&route!=null&&!route.held&&arrival<reserve)set(s.arrivalLabel,arrival<0?"พลังงานไม่พอ":"ต่ำกว่าแบตสำรอง "+Math.round(reserve)+"%");
  metricLabel(s.soc,current!=null&&current<reserve?"แบตปัจจุบัน • เหลือน้อย":"แบตเตอรี่ปัจจุบัน");
  batteryStatus(s,s.soc,current,reserve);
  batteryStatus(s,s.arrival,arrival,reserve);
  set(s.speed,fmt(speed,"km/h"));
  String motion=!Double.isFinite(speed)?"ข้อมูลความเร็ว —":speed<=1?"จอดนิ่ง  •  PARKED":!Double.isFinite(turn)?"เคลื่อนที่  •  ทิศทาง —":turn<-3?"แนวโน้มเลี้ยวซ้าย":turn>3?"แนวโน้มเลี้ยวขวา":"แนวโน้มขับตรง";
  int motionColor=!Double.isFinite(speed)?muted(s):speed<=1?green(s):Double.isFinite(turn)&&Math.abs(turn)>3?(s.dark?0xffffc881:0xff945313):blue(s);
  if(!motion.contentEquals(s.heading.getText())||s.heading.getCurrentTextColor()!=motionColor||s.heading.getBackground()==null){
   set(s.heading,motion);s.heading.setTextColor(motionColor);
   s.heading.setBackground(bg(a,s.dark?0xff1a2c3b:0xfff8fbfc,s.dark?0xff344a5d:0xffcddbe4,10));
  }
  NatV33Telemetry.Slow tele=BydBatteryPatch.isActive()?NatV33Telemetry.slow():null;
  updatePower(s);
  set(s.range,fmt(tele==null?Double.NaN:tele.range,"km"));
  set(s.odo,tele!=null&&Double.isFinite(tele.odo)?String.format(Locale.US,"%,.1f km",tele.odo):"—");
  NatVehicleExtras.Values extra=NatVehicleExtras.read();
  set(s.acDriver,extra==null?"—":extra.driver);set(s.acPassenger,extra==null?"—":extra.passenger);
  int outside=BydBatteryPatch.natFreshOutsideC();set(s.outside,outside==BydOutsideTemperature.UNAVAILABLE?"—":outside+"°C");
  set(s.tyresFront,extra==null?"—":extra.front);set(s.tyresRear,extra==null?"—":extra.rear);
  String temp="—",health="—";try{
   org.json.JSONObject j=BydBatteryPatch.natHealthSnapshot();
   if(j!=null){long age=j.optLong("soh_read_age_s",-1),ta=j.optLong("temp_read_age_s",-1);
    org.json.JSONObject t=j.optJSONObject("temp_c");
    if(age>=0&&age<=3600&&!j.isNull("soh_percent"))health=j.optString("soh_percent")+"%";
    if(ta>=0&&ta<=180&&t!=null&&!t.isNull("average"))temp=t.optString("average")+"°C";
   }
  }catch(Exception ignored){}
  set(s.battTemp,temp);set(s.soh,health);updateDaily(a,s);
  boolean finalMatch=route!=null&&!route.held&&f!=null&&NatStabilityMath.matches(route.soc,route.km,f.google,f.km);
  double energy=finalMatch&&Double.isFinite(f.remainingKwh)&&f.remainingKwh>=0?f.remainingKwh:Double.NaN;
  boolean estimated=false;
  if(!Double.isFinite(energy)&&route!=null&&!route.held&&f!=null&&f.charges==0){
   // Same destination evidence, fresh no-charge snapshot and live SOC only; never estimate from a next charging stop.
   double freshSoc=fast!=null&&Double.isFinite(fast.soc)?fast.soc:live.soc;
   energy=NatReadableRules.estimate(f.soc,f.google,f.km,f.capacity,route.soc,route.km,freshSoc);
   estimated=Double.isFinite(energy);
  }
  set(s.remaining,fmt(energy,"kWh"));set(s.rate,fmt(NatDailyMath.forecastRate(energy,route==null?Double.NaN:route.km),"kWh/100km"));
  metricLabel(s.remaining,estimated?"พลังงานที่ต้องใช้อีก ≈ kWh":"พลังงานที่ต้องใช้อีก (kWh)");
  metricLabel(s.rate,estimated?"สิ้นเปลืองคาดการณ์ ≈ kWh/100 km":"สิ้นเปลืองคาดการณ์ (kWh/100 km)");
  set(s.distance,fmt(route==null?Double.NaN:route.km,"km"));
  String eta=route==null?null:route.eta;set(s.eta,eta==null?"—":eta);
  String gps;
  if(a.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED)gps="No permission";
  else if(s.lm==null||!s.lm.isProviderEnabled(LocationManager.GPS_PROVIDER))gps="Unavailable";
  else gps=NatSplitMath.fresh(now,s.fixAt,3000)?"FIX":"NO FIX";
  set(s.gps,gps);s.gps.setTextColor("FIX".equals(gps)?green(s):muted(s));
  String byd="—";try{org.json.JSONObject v=BydBatteryPatch.diagnosticSnapshot();String state=v.optString("status","unknown");byd="fresh".equals(state)?"Fresh":state.equals("inactive")?"Inactive":"No fresh data";}catch(Exception ignored){}
  NatReadOnlyVehicle.Values vehicle=NatReadOnlyVehicle.read();if(vehicle!=null&&!vehicle.gear.equals("—"))byd+=" • "+vehicle.gear;
  set(s.byd,byd);s.byd.setTextColor(byd.startsWith("Fresh")?green(s):muted(s));
  applyCustom(a,s);refreshZoom(s);

 }
 private static final class Icon extends Drawable {
  final int kind,color;final Paint p=new Paint(3);
  Icon(int kind,int color){this.kind=kind;this.color=color;}
  public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.centerX(),b.centerY());c.scale(b.width()/28f,b.height()/28f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
   Path path=new Path();switch(kind){
    case 0:path.moveTo(2,-12);path.lineTo(-9,1);path.lineTo(-1,1);path.lineTo(-3,12);path.lineTo(9,-3);path.lineTo(1,-3);path.close();c.drawPath(path,p);break;
    case 1:c.drawLine(-11,10,11,10,p);path.moveTo(-10,4);path.lineTo(-4,-3);path.lineTo(2,1);path.lineTo(10,-9);c.drawPath(path,p);c.drawLine(4,-9,10,-9,p);c.drawLine(10,-9,10,-3,p);break;
    case 2:for(int y=-8;y<=8;y+=8){c.drawCircle(-9,y,1,p);c.drawLine(-4,y,10,y,p);}break;
    case 3:c.drawCircle(0,0,10,p);c.drawLine(-7,7,7,-7,p);break;
    case 4:c.drawCircle(0,0,11,p);c.drawCircle(0,-5,1,p);c.drawLine(0,-1,0,6,p);break;
    case 7:c.drawRoundRect(-12,-6,12,7,3,3,p);c.drawLine(-8,-6,-5,-11,p);c.drawLine(-5,-11,5,-11,p);c.drawLine(5,-11,8,-6,p);c.drawCircle(-7,8,2,p);c.drawCircle(7,8,2,p);break;
    case 8:for(int k=0;k<6;k++){c.drawLine(0,0,0,-11,p);c.drawLine(0,-6,-3,-9,p);c.drawLine(0,-6,3,-9,p);c.rotate(60);}break;
    case 6:c.drawRoundRect(-11,-10,11,10,3,3,p);path.moveTo(2,-8);path.lineTo(-5,1);path.lineTo(0,1);path.lineTo(-2,8);path.lineTo(5,-1);path.lineTo(0,-1);path.close();c.drawPath(path,p);break;
    default:c.drawRoundRect(-11,-9,11,9,2,2,p);c.drawLine(3,-9,3,9,p);
   }c.restore();}
  public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
