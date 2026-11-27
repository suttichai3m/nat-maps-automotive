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
 private static final String PREF="nat_split_v30", OWN="nat_v201_overlay";
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
 static boolean enabled(Activity a){return a.getSharedPreferences(PREF,0).getBoolean("split",true);}
 static String status(Activity a){State s=states.get(a);return s==null?"Waiting for map":s.reason;}
 static void toggle(Activity a){
  boolean on=!enabled(a);a.getSharedPreferences(PREF,0).edit().putBoolean("split",on).apply();
  State s=states.get(a);if(s!=null){restore(s);s.blocked=null;s.retryAt=0;s.reason=on?"Split requested":"Full Map requested";}
  if(on)refresh(a,a.getWindow().peekDecorView());
 }
 private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 private static final class State {
  FrameLayout host;View nativeView;FrameLayout.LayoutParams original,applied;
  LinearLayout panel,footer;TextView soc,arrival,arrivalLabel,speed,power,remaining,rate,distance,eta,gps,byd,heading;
  Road road;LocationManager lm;LocationListener listener;
  WeakReference<View> blocked;int blockedW,blockedH;
  long bearingAt=-1,fixAt=-1,started,lastEvidence=-1,retryAt;
  double bearing=Double.NaN,turn=Double.NaN;
  boolean dark;String reason="Waiting for map";
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
  restore(s);s.retryAt=SystemClock.elapsedRealtime()+2000;
  if(!reason.equals(s.reason))ReportLog.i("NatSplitScreen",reason);
  s.reason=reason;
 }
 static void suspend(Activity a){State s=states.get(a);if(s!=null){stopGps(s);if(s.road!=null){s.road.running=false;s.road.invalidate();}}}
 static void clear(Activity a){State s=states.remove(a);if(s!=null)restore(s);}
 static void refresh(Activity a,View root){
  if(a==null)return;if(a.isDestroyed()||a.isFinishing()){clear(a);return;}
  State s=states.get(a);if(s==null){s=new State();states.put(a,s);}
  if(!enabled(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY)||!NatMinimalEv.enabled(a)){restore(s);s.reason="Full Map / disabled";return;}
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
 private static int card(State s){return s.dark?0xff1d303d:0xfff8fbfc;}
 private static int surface(State s){return s.dark?0xff101e2a:0xffe8f0f3;}
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
  cell.setBackground(bg(a,card(s),battery?accent:0,9));
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
  s.heading=text(a,"—",11,muted(s),false);add(panel,s.heading,dp(a,18));
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
  action(a,s,footer,"Full Map",5,()->toggle(a));
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
  set(s.heading,!Double.isFinite(speed)?"ข้อมูลความเร็ว —":speed<=1?"จอดนิ่ง":!Double.isFinite(turn)?"ทิศทาง —":turn<-3?"แนวโน้มเลี้ยวซ้าย":turn>3?"แนวโน้มเลี้ยวขวา":"แนวโน้มขับตรง");
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
  final Paint p=new Paint(3);final Bitmap car;
  double speed=Double.NaN,turn=Double.NaN;boolean running,dark;long last,lastData;float phase;
  Road(Context c){super(c);setContentDescription("Owner BYD Seal on illustrative road, not ADAS");Bitmap b=null;
   try{byte[] bytes=android.util.Base64.decode("UklGRgoFAABXRUJQVlA4WAoAAAAQAAAAdwAAKgAAQUxQSA4CAAABkARJtmlb69m2vm3btm3btm1jZNu2bdu2ru/6uBvv7L3mETEBYMyAiLS5ilVt2K1nV9etarmuni8SNO2WWGb0mu0nrt99+ubzbztyWlyb3907OKdDsbRhenEPLTxw8yM05LsLm8Y2zR2kB988HZbcN6ORHaZTHcOUC6ow/8onVPHWwJRKRbU/4UBln07MpEzWMQ9Q7Y/zC6rgV275e1T/y7pKUcbyS2xz2IF6dF5bXCPCzRj+eVrOPPIBdep4tKRhUrJl7b365ifUsOPFpq75IpIj9j5q/PeVle2zBUtym426/3V5QdNsHhLK/dbe/x/OTisotAup/DIjJZ//NTIQH/UI4Am7QwjiySoerNRPSEHLhjyMrC9pQfw8KclFkY/UID7tFw0AFX/Sg3ijvT/UsVKE2AvaO2laDv2Q5lMe04h6GDeVqI+FphFlazSMKBzTiao1Xala2IaqqUFriBoGETtp6gwQecxmd9LTFgBiitdo2mPUZ1pMlYA9l5Jfuyv5ceT/LsMkaP7x6qXgsyuXBC/u27adf9t9OabVZT2A122HhHHpM/FnThchGhYoGuDrLZrltIzDJTxAMPNRoW0+oH7ifrGHqUA8ZKKJ72Ea0GHkpB8CtjogtfYDjjdnq4AmSxzmW+QuB1J17tFz0rSZk9oUSxMC2gzq8ZTxaXujICA1c+fuE2eu2TirCPACVlA4INYCAAAwEgCdASp4ACsAPyV4s1SuJqQjMBVbUcAkiUAYti/ycgldEFcjGu+WTzjrYEVQldNwHpEnqyJuBi+J1mqYo/CYO0rkaGZPaEdjrDCvJGfQhayHCKOHrn0QyW6Y2vKL458uqUnSo+mO5qDh/XnGzVdsH/hjj7nBv1QObMtOZ/NyKjIgmOEgavXSI2O/ixxTg/68EP62PlW1bksAAP70I8srI93QU5FgHJlPI0DMnw1vA+aKkJStJJW76HskIXOabl3KlV8N3nCuIi47oINGbCLzEBaBT36kqCRtyS99YeVJOc9IZ0ptY2lep4weUyn3j8ogl7K34ALBoYj19Gsj5D0YDBcGdWc1p3tApgb546GeTg/kNdUxZq6Fsz4PUv0DrytC5MXjRv0qymOvIlZI85ynUbB2aiYEmu8qpnzHAMu5JWngET8cWUVfXyN52qhd/LIk/erR2i4e7PMSX4hW318+avtS3oz6ep4mZvx7Yay0KnY2DgAJOinuM6KuDzKyxcp6PknWTgZc3DH9YdWCUjFfgP5GgiJGszmarBoESSpdbAcDC3A15MFOvF20kYwcOK6TiL8ZguKjqqzN9ppmbLy/5fnC7xQ79oVojn/nb6k43NvxY5k0VoV7ctYi/TAMLCgKCiJnAjaEhU6F4bNvS1UTrllVWWkXV3ouB07rrWmQmrfUhztSQ1oQjmYrpca6ECfL+03mnlvrfyo3DwZG52PglEhvIbxr6CMpstb5gpdsTZVpQawQKhRyDdrAdhilh2DlNHFQI5s07sjbmdxEiGPq9i/WHvilzvGFkGFMti/PxF05oQeHEv75ofJcp0RUVMFLAEVBB6LO9fokk1bSjrOcHyDfvL+/xiWCHOdXifYdGqQDTNq39vnNyjxghOU4OZ6dlvJdKZEwLDK5MCwpYnGmsZNZ30JqxTb6zplcm4sonEiB/2hCuUUVK8Our5OtxUPmPFQAAAA=",android.util.Base64.DEFAULT);b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);}catch(RuntimeException ignored){}car=b;}
  void color(int c){p.setColor(c);p.setStyle(Paint.Style.FILL);}
  protected void onDraw(Canvas c){
   super.onDraw(c);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
   long now=SystemClock.elapsedRealtime();boolean move=running&&NatSplitMath.fresh(now,lastData,2000)&&NatSplitMath.moving(speed)&&hasWindowFocus();
   if(move)phase=(phase+Math.min(100,Math.max(0,now-last))*((float)speed/100))%42;last=now;
   c.drawColor(dark?0xff152533:0xffdbe8ed);
   float x=w/2,y=h*.7f,angle=move&&Double.isFinite(turn)?(float)turn:0;
   // Decorative side-profile road: curvature indicates measured heading trend, not lanes or ADAS.
   c.save();c.translate(x,y);c.rotate(angle*.16f);color(dark?0xff293f4d:0xff9eafb8);c.drawRect(-w,-h*.06f,w,h*.55f,p);
   color(dark?0xff7295aa:0xfff3f7f8);for(float xx=-w-phase;xx<w;xx+=42)c.drawRoundRect(xx,h*.12f,xx+22,h*.14f,1,1,p);
   if(car!=null){float cw=Math.min(w*.88f,h*2.2f),ch=cw*car.getHeight()/car.getWidth();if(ch>h*.72f){ch=h*.72f;cw=ch*car.getWidth()/car.getHeight();}p.setFilterBitmap(true);p.setAlpha(255);c.drawBitmap(car,null,new RectF(-cw/2,-ch*.88f,cw/2,ch*.12f),p);}
   else{float k=Math.min(w/180,h/84);c.scale(k,k);color(0xff637fae);c.drawRoundRect(-70,-25,70,5,13,13,p);c.drawRoundRect(-38,-40,42,-10,12,12,p);color(0xff14232b);c.drawCircle(-45,5,11,p);c.drawCircle(45,5,11,p);}
   c.restore();
   if(move&&isShown())postInvalidateDelayed(100);
  }
 }
}
