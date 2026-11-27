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
 static void toggle(Activity a){
  if(a==null||a.isFinishing()||a.isDestroyed()||!a.hasWindowFocus())return;
  State s=states.get(a);if(s==null){s=new State();states.put(a,s);}
  boolean on=!s.requested;restore(s);s.requested=on;s.blocked=null;s.retryAt=0;
  s.reason=on?"Split requested":"Full Map requested";
  if(on){
   refresh(a,a.getWindow().peekDecorView());
   if(!active(a)){s.requested=false;restore(s);Toast.makeText(a,"Split 75:25 unavailable — Full Map",Toast.LENGTH_SHORT).show();}
  }
 }
 private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 private static final class State {
  FrameLayout host;View nativeView;FrameLayout.LayoutParams original,applied;
  LinearLayout panel,footer;TextView soc,arrival,arrivalLabel,speed,power,remaining,rate,distance,eta,gps,byd,heading;
  Road road;LocationManager lm;LocationListener listener;
  WeakReference<View> blocked;int blockedW,blockedH;
  long bearingAt=-1,fixAt=-1,started,lastEvidence=-1,retryAt;
  double bearing=Double.NaN,turn=Double.NaN;
  boolean dark,requested;String reason="Full Map (default)";
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
  if(s.road!=null)s.road.running=false;
  remove(s.panel);remove(s.footer);s.panel=s.footer=null;s.road=null;
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
 static void suspend(Activity a){State s=states.get(a);if(s!=null){stopGps(s);if(s.road!=null){s.road.running=false;s.road.invalidate();}}}
 static void clear(Activity a){State s=states.remove(a);if(s!=null)restore(s);}
 static void refresh(Activity a,View root){
  if(a==null)return;if(a.isDestroyed()||a.isFinishing()){clear(a);return;}
  State s=states.get(a);if(s==null){s=new State();states.put(a,s);}
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
   update(a,s,root);
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
  add(cell,value,-2);if(arrival)s.arrivalLabel=label;return value;
 }
 private static void action(Activity a,State s,LinearLayout parent,String name,int symbol,Runnable callback){
  Button b=new Button(a);b.setText(name);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(fg(s));b.setGravity(Gravity.CENTER);b.setMinHeight(dp(a,48));b.setMinimumHeight(dp(a,48));
  b.setPadding(dp(a,4),0,dp(a,4),0);b.setSingleLine(true);b.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);
  b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(s.dark?0x33ffffff:0x22000000),bg(a,card(s),0,8),null));
  Icon icon=new Icon(symbol,fg(s));icon.setBounds(0,0,dp(a,20),dp(a,20));b.setCompoundDrawablesRelative(icon,null,null,null);b.setCompoundDrawablePadding(dp(a,3));
  b.setContentDescription(name);b.setTooltipText(name);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(a,3),dp(a,3),dp(a,3),dp(a,3));parent.addView(b,lp);
  b.setOnClickListener(v->{try{callback.run();}catch(RuntimeException e){ReportLog.w("NatSplitScreen","Action unavailable: "+name,e);Toast.makeText(a,"NAT action unavailable",Toast.LENGTH_SHORT).show();}});
 }
 private static void build(Activity a,State s,int width,int mapH,int footerH){
  s.dark=ColorScheme.nightColors(a);
  LinearLayout panel=vertical(a);panel.setTag(OWN);panel.setBackgroundColor(surface(s));panel.setPadding(dp(a,6),dp(a,5),dp(a,6),dp(a,4));s.panel=panel;
  add(panel,text(a,"BYD SEAL  •  NAT V3.2",14,fg(s),true),dp(a,24));
  s.road=new Road(a);s.road.dark=s.dark;
  int roadH=Math.min(dp(a,144),Math.max(dp(a,56),mapH/4));add(panel,s.road,roadH);
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
  LinearLayout body=vertical(a);details.addView(body,new ScrollView.LayoutParams(-1,-2));
  int metricsH=dp(a,52);
  LinearLayout m1=row(a);add(body,m1,metricsH);s.speed=metric(a,s,m1,"ความเร็ว",fg(s),false,false);s.power=metric(a,s,m1,"กำลังไฟหน้าปัด BYD",fg(s),false,false);
  LinearLayout m2=row(a);add(body,m2,metricsH);s.remaining=metric(a,s,m2,"พลังงานที่ต้องใช้อีก",fg(s),false,false);s.rate=metric(a,s,m2,"อัตราสิ้นเปลือง",fg(s),false,false);
  LinearLayout m3=row(a);add(body,m3,metricsH);s.distance=metric(a,s,m3,"ระยะทาง (Maps)",fg(s),false,false);s.eta=metric(a,s,m3,"ถึงเวลา (Maps)",fg(s),false,false);
  LinearLayout m4=row(a);add(body,m4,metricsH);s.gps=metric(a,s,m4,"GPS",fg(s),false,false);s.byd=metric(a,s,m4,"ข้อมูล BYD",fg(s),false,false);
  s.host.addView(panel,new FrameLayout.LayoutParams(width,mapH,Gravity.RIGHT|Gravity.TOP));
  LinearLayout footer=row(a);footer.setTag(OWN);footer.setBackgroundColor(surface(s));footer.setPadding(dp(a,3),0,dp(a,3),0);s.footer=footer;
  action(a,s,footer,"EV Charging",0,()->NatMinimalEv.splitAction(a,0));
  action(a,s,footer,"Energy",1,()->NatMinimalEv.splitAction(a,1));
  action(a,s,footer,"Charging Plan",2,()->NatMinimalEv.splitAction(a,2));
  action(a,s,footer,"Clean",3,()->NatMinimalEv.splitAction(a,3));
  action(a,s,footer,"Diagnostics",4,()->NatMinimalEv.splitAction(a,4));
  action(a,s,footer,"Full Map 100%",5,()->toggle(a));
  s.host.addView(footer,new FrameLayout.LayoutParams(-1,footerH,Gravity.BOTTOM|Gravity.LEFT));
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
 private static void update(Activity a,State s,View root){
  long now=SystemClock.elapsedRealtime();Motion m=sample;
  double speed=m==null||!BydBatteryPatch.isActive()?Double.NaN:NatSplitMath.speed(m.speed,now,m.at,15000);
  double turn=NatSplitMath.fresh(now,s.bearingAt,3000)&&Double.isFinite(speed)&&speed>=5?s.turn:Double.NaN;
  NatLiveEnergy.Reading live=NatPrediction.liveReading();NatPrediction.Snapshot f=NatPrediction.freshSnapshot();boolean match=NatNavigationCard.matchesForecast(root,f);
  Integer current=NatSplitMath.current(live.soc),nativeSoc=NatNavigationCard.verifiedSoc(root);
  Integer arrival=NatSplitMath.arrival(match,match?Integer.valueOf(f.google):null,nativeSoc);
  set(s.soc,NatSplitMath.percent(current));set(s.arrival,NatSplitMath.percent(arrival));
  ((View)s.arrival.getParent()).setVisibility(NatNavigationCard.hasVisibleCard(root)?View.GONE:View.VISIBLE);
  set(s.arrivalLabel,match?"แบตเมื่อถึงปลายทาง":"แบตเมื่อถึง (Maps)");
  s.arrival.setTextColor(arrival!=null&&arrival<20?(s.dark?0xffff9b9b:0xffb91c1c):blue(s));
  set(s.speed,fmt(speed,"km/h"));
  String motion=!Double.isFinite(speed)?"ข้อมูลความเร็ว —":speed<=1?"จอดนิ่ง  •  PARKED":!Double.isFinite(turn)?"เคลื่อนที่  •  ทิศทาง —":turn<-3?"แนวโน้มเลี้ยวซ้าย":turn>3?"แนวโน้มเลี้ยวขวา":"แนวโน้มขับตรง";
  int motionColor=!Double.isFinite(speed)?muted(s):speed<=1?green(s):Double.isFinite(turn)&&Math.abs(turn)>3?(s.dark?0xffffc881:0xff945313):blue(s);
  if(!motion.contentEquals(s.heading.getText())||s.heading.getCurrentTextColor()!=motionColor||s.heading.getBackground()==null){
   set(s.heading,motion);s.heading.setTextColor(motionColor);
   s.heading.setBackground(bg(a,s.dark?0xff1a2c3b:0xfff8fbfc,s.dark?0xff344a5d:0xffcddbe4,10));
  }
  set(s.power,"—"); // No validated dashboard power signal.
  set(s.remaining,fmt(match?f.remainingKwh:Double.NaN,"kWh"));set(s.rate,fmt(match?live.rate:Double.NaN,"kWh/100km"));
  set(s.distance,fmt(NatNavigationCard.verifiedKm(root),"km"));
  String eta=NatNavigationCard.verifiedEta(root);set(s.eta,eta==null?"—":eta);
  String gps;
  if(a.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED)gps="No permission";
  else if(s.lm==null||!s.lm.isProviderEnabled(LocationManager.GPS_PROVIDER))gps="Unavailable";
  else gps=NatSplitMath.fresh(now,s.fixAt,3000)?"FIX":"NO FIX";
  set(s.gps,gps);s.gps.setTextColor("FIX".equals(gps)?green(s):muted(s));
  String byd="—";try{org.json.JSONObject v=BydBatteryPatch.diagnosticSnapshot();String state=v.optString("status","unknown");byd="fresh".equals(state)?"Fresh":state.equals("inactive")?"Inactive":"No fresh data";}catch(Exception ignored){}
  set(s.byd,byd);s.byd.setTextColor("Fresh".equals(byd)?green(s):muted(s));
  s.road.speed=speed;s.road.turn=turn;s.road.lastData=now;s.road.running=true;s.road.invalidate();
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
    default:c.drawRoundRect(-11,-9,11,9,2,2,p);c.drawLine(3,-9,3,9,p);
   }c.restore();}
  public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
 private static final class Road extends View {
  // Resolution-independent, original stylized Seal side profile. No tiny bitmap,
  // network dependency, camera feed or simulated ADAS information.
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  final Path body=new Path(),glass=new Path(),hood=new Path(),lower=new Path(),belt=new Path(),frontLamp=new Path(),rearLamp=new Path(),sill=new Path(),mirror=new Path();
  final Shader bodyPaint=new LinearGradient(0,27,0,91,new int[]{0xffa2d0fa,0xff477eae,0xff244d78,0xff172e4c},new float[]{0,.32f,.68f,1},Shader.TileMode.CLAMP);
  final Shader glassPaint=new LinearGradient(0,27,0,57,0xff39536d,0xff101b2b,Shader.TileMode.CLAMP);
  final Shader chrome=new LinearGradient(0,-16,0,16,0xffc4d5e4,0xff506174,Shader.TileMode.CLAMP);
  final Shader shadow=new RadialGradient(0,0,1,new int[]{0x99000000,0x00000000},null,Shader.TileMode.CLAMP);
  final Shader nightStudio=new LinearGradient(0,0,0,112,new int[]{0xff132235,0xff21394d,0xff101b29},null,Shader.TileMode.CLAMP);
  final Shader dayStudio=new LinearGradient(0,0,0,112,new int[]{0xffe7f0f5,0xffcbdde8,0xffb6cbd7},null,Shader.TileMode.CLAMP);
  double speed=Double.NaN,turn=Double.NaN;boolean running,dark;long last,lastData;float phase;
  Road(Context c){
   super(c);setContentDescription("Stylized BYD Seal side profile on decorative road; GPS heading trend only, not ADAS");
   // Low bonnet, long wheelbase, fastback roof, short rear deck.
   body.moveTo(12,76);body.cubicTo(11,66,16,57,24,54);body.cubicTo(32,49,47,47,59,43);
   body.cubicTo(76,28,95,22,121,22);body.cubicTo(147,22,164,32,184,46);
   body.cubicTo(197,51,217,52,228,60);body.cubicTo(237,66,238,75,233,84);
   body.lineTo(221,88);body.cubicTo(220,68,191,65,184,88);body.lineTo(65,88);
   body.cubicTo(61,66,31,66,27,88);body.lineTo(16,85);body.close();
   glass.moveTo(64,44);glass.cubicTo(80,32,99,27,121,27);glass.cubicTo(144,27,160,36,174,46);
   glass.lineTo(161,49);glass.lineTo(78,49);glass.close();
   hood.moveTo(16,62);hood.cubicTo(56,51,107,53,164,53);hood.cubicTo(195,52,220,57,232,65);
   lower.moveTo(16,77);lower.lineTo(27,80);lower.cubicTo(35,62,59,65,67,83);lower.lineTo(183,83);
   lower.cubicTo(192,64,215,66,223,80);lower.lineTo(235,76);
   belt.moveTo(65,60);belt.cubicTo(105,56,145,56,184,60);
   sill.moveTo(69,82);sill.cubicTo(110,85,148,85,181,81);
   frontLamp.moveTo(217,59);frontLamp.lineTo(231,64);frontLamp.lineTo(230,67);frontLamp.lineTo(215,64);frontLamp.close();
   rearLamp.moveTo(15,59);rearLamp.lineTo(33,56);rearLamp.lineTo(32,60);rearLamp.lineTo(15,63);rearLamp.close();
   mirror.moveTo(165,47);mirror.cubicTo(171,44,181,44,181,48);mirror.lineTo(174,52);mirror.lineTo(166,52);mirror.close();
  }
  void color(int c){p.setShader(null);p.setColor(c);p.setStyle(Paint.Style.FILL);}
  void fill(Canvas c,Path path,Shader shader){color(0xffffffff);p.setShader(shader);c.drawPath(path,p);p.setShader(null);}
  void stroke(Canvas c,Path path,int col,float width){color(col);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
  void wheel(Canvas c,float x,float y,float spin){
   c.save();c.translate(x,y);color(0xff0a1019);c.drawCircle(0,0,18,p);
   color(0xff273342);c.drawCircle(0,0,14.5f,p);
   color(0xffffffff);p.setShader(chrome);c.drawCircle(0,0,12.4f,p);p.setShader(null);
   color(0xff142233);c.drawCircle(0,0,10.5f,p);
   c.rotate(spin);
   for(int i=0;i<5;i++){c.save();c.rotate(i*72);color(0xffb4c6d6);c.drawRoundRect(-1.8f,-11,1.8f,-3,1.3f,1.3f,p);c.restore();}
   color(0xff92a8bd);c.drawCircle(0,0,3.7f,p);color(0xff314357);c.drawCircle(0,0,1.7f,p);c.restore();
  }
  protected void onDraw(Canvas c){
   super.onDraw(c);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
   long now=SystemClock.elapsedRealtime();boolean move=running&&NatSplitMath.fresh(now,lastData,2000)&&NatSplitMath.moving(speed)&&hasWindowFocus();
   if(move)phase=(phase+Math.min(100,Math.max(0,now-last))*((float)speed/100))%360;last=now;
   float k=Math.min(w/260f,h/112f);
   c.save();c.translate((w-260*k)/2,(h-112*k)/2);c.scale(k,k);
   // Paint the studio background across the full view, even at unusual aspect ratios.
   color(0xffffffff);p.setShader(dark?nightStudio:dayStudio);c.drawRect(-w/k, -h/k,260+w/k,112+h/k,p);p.setShader(null);
   float angle=move&&Double.isFinite(turn)?(float)turn:0;
   // Animated dashed line represents motion only; it is not a detected lane.
   c.save();c.translate(130,99);c.rotate(angle*.12f);
   color(dark?0xff263d50:0xff9fb6c5);c.drawRoundRect(-160,-5,160,17,4,4,p);
   color(dark?0xff5b829c:0xffeaf4f8);for(float xx=-180-(phase%44);xx<180;xx+=44)c.drawRoundRect(xx,9,xx+23,10.6f,1,1,p);
   c.restore();
   c.save();c.translate(130,91);c.scale(104,9);color(0xffffffff);p.setShader(shadow);c.drawCircle(0,0,1,p);p.setShader(null);c.restore();
   // Subtle body tilt, clamped by the existing GPS trend math; no motion when parked.
   c.save();c.translate(130,61);c.rotate(angle*.075f);c.translate(-130,-61);c.translate(8,-1);
   fill(c,body,bodyPaint);stroke(c,body,dark?0xff7d9ebd:0xff375778,1.05f);
   fill(c,glass,glassPaint);stroke(c,glass,0xff92a8ba,1.0f);
   color(0xff192d42);c.drawLine(112,27,114,48,p);c.drawLine(147,34,143,48,p);
   stroke(c,hood,0x88d4eaff,1.1f);stroke(c,lower,0xff233e59,2.1f);stroke(c,belt,0xff95b9d7,.75f);stroke(c,sill,0xff82a8cb,1.15f);
   color(0xff243e5a);c.drawRoundRect(92,58,108,60,1,1,p);c.drawRoundRect(143,58,159,60,1,1,p);
   fill(c,mirror,bodyPaint);stroke(c,mirror,0xff344e69,.9f);
   color(0xffff6974);c.drawPath(rearLamp,p);color(0xffe6f7ff);c.drawPath(frontLamp,p);
   float spin=move?phase*3:0;wheel(c,46,86,spin);wheel(c,202,86,spin);
   c.restore();c.restore();
   if(move&&isShown())postInvalidateDelayed(100);
  }
 }
}
