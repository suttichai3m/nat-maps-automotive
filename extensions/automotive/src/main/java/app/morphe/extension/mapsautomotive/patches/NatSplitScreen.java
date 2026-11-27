package app.morphe.extension.mapsautomotive.patches;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.location.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.lang.ref.WeakReference;

/** Experimental dedicated viewport. Never reparent a native renderer. */
final class NatSplitScreen {
 private static final String PREF="nat_split_v30", OWN="nat_v201_overlay";
 private static final WeakHashMap<Activity,State> states=new WeakHashMap<>();
 private static volatile Motion sample;
 private static final class Motion {final long at;final double speed;Motion(long t,double s){at=t;speed=s;}}
 static void observe(long at,double speed){sample=new Motion(at,speed);}
 static boolean active(Activity a){State s=states.get(a);return s!=null&&s.panel!=null;}
 static boolean enabled(Activity a){return a.getSharedPreferences(PREF,0).getBoolean("split",true);}
 static void toggle(Activity a){boolean on=!enabled(a);a.getSharedPreferences(PREF,0).edit().putBoolean("split",on).apply();State s=states.get(a);if(s!=null){s.failed=false;restore(s);}if(on)refresh(a,a.getWindow().peekDecorView());}
 private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 private static final class State {
  FrameLayout host;View nativeView;FrameLayout.LayoutParams original,applied;ScrollView panel;LinearLayout body;TextView soc,arrival,energy,status;Road road;LocationManager lm;LocationListener listener;long bearingAt=-1,started;double bearing=Double.NaN,turn=Double.NaN;boolean failed,dark;
 }
 private static boolean map(View v,int depth){if(v==null||depth>40||!v.isShown()||NatMinimalEv.owned(v))return false;try{if(v.getId()!=View.NO_ID&&"map_buttons_view_container".equals(v.getResources().getResourceEntryName(v.getId())))return true;}catch(RuntimeException ignored){}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(map(g.getChildAt(i),depth+1))return true;}return false;}
 private static boolean withinViewport(View v,View frame,int depth){
  if(depth>40||!v.isShown()||NatMinimalEv.owned(v))return true;
  boolean target=v instanceof SurfaceView||v instanceof TextureView;
  try{target|=v.getId()!=View.NO_ID&&"map_buttons_view_container".equals(v.getResources().getResourceEntryName(v.getId()));}catch(RuntimeException ignored){}
  if(target&&v.getWidth()>0&&v.getHeight()>0){int[] p=new int[2],q=new int[2];v.getLocationOnScreen(p);frame.getLocationOnScreen(q);if(p[0]<q[0]-2||p[0]+v.getWidth()>q[0]+frame.getWidth()+2||p[1]<q[1]-2||p[1]+v.getHeight()>q[1]+frame.getHeight()+2)return false;}
  if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(!withinViewport(g.getChildAt(i),frame,depth+1))return false;}
  return true;
 }
 private static void restore(State s){
  if(s.listener!=null&&s.lm!=null)try{s.lm.removeUpdates(s.listener);}catch(RuntimeException ignored){}
  s.listener=null;s.lm=null;s.bearingAt=-1;s.bearing=s.turn=Double.NaN;
  if(s.road!=null)s.road.running=false;
  if(s.panel!=null&&s.panel.getParent() instanceof ViewGroup)((ViewGroup)s.panel.getParent()).removeView(s.panel);
  if(s.nativeView!=null&&s.nativeView.getParent()==s.host&&s.nativeView.getLayoutParams()==s.applied)s.nativeView.setLayoutParams(s.original);
  s.panel=null;s.host=null;s.nativeView=null;s.original=s.applied=null;s.road=null;s.body=null;s.soc=s.arrival=s.energy=s.status=null;
 }
 static void suspend(Activity a){State s=states.get(a);if(s!=null&&s.listener!=null){try{s.lm.removeUpdates(s.listener);}catch(RuntimeException ignored){}s.listener=null;s.lm=null;s.bearingAt=-1;s.bearing=s.turn=Double.NaN;}if(s!=null&&s.road!=null){s.road.running=false;s.road.invalidate();}}
 static void clear(Activity a){State s=states.remove(a);if(s!=null)restore(s);}
 static void refresh(Activity a,View root){
  if(a==null||a.isDestroyed()||a.isFinishing())return;
  State s=states.get(a);if(s==null){s=new State();states.put(a,s);}
  if(!enabled(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY)||!NatMinimalEv.enabled(a)){restore(s);return;}
  if(root==null||!a.hasWindowFocus()){suspend(a);return;}
  try{
   View content=a.findViewById(android.R.id.content);
   if(!(content instanceof FrameLayout)||!map(root,0)){restore(s);return;}
   FrameLayout h=(FrameLayout)content;int w=h.getWidth(),height=h.getHeight(),side=NatSplitMath.side(w,height,dp(a,540),dp(a,200));
   if(side==0){restore(s);return;}
   if(s.panel!=null){
    if(s.host!=h||s.nativeView.getParent()!=h||s.panel.getParent()!=h||s.nativeView.getLayoutParams()!=s.applied||s.applied.width!=w-side||s.panel.getLayoutParams().width!=side){restore(s);s.failed=true;return;}
    if(SystemClock.elapsedRealtime()-s.started>2000&&(Math.abs(s.nativeView.getWidth()-(w-side))>2||Math.abs(s.panel.getWidth()-side)>2||!withinViewport(s.nativeView,s.nativeView,0))){restore(s);s.failed=true;return;}
   }else{
    if(s.failed)return;
    NatMinimalEv.clear(a);View child=null;
    for(int i=0;i<h.getChildCount();i++){View v=h.getChildAt(i);if(NatMinimalEv.owned(v))continue;if(child!=null)return;child=v;}
    if(child==null||!map(child,0)||!(child.getLayoutParams() instanceof FrameLayout.LayoutParams))return;
    FrameLayout.LayoutParams old=(FrameLayout.LayoutParams)child.getLayoutParams();
    if(old.width!=-1||old.height!=-1||old.leftMargin!=0||old.rightMargin!=0||old.topMargin!=0||old.bottomMargin!=0||h.getPaddingLeft()!=0||h.getPaddingRight()!=0)return;
    s.host=h;s.nativeView=child;s.original=old;s.applied=new FrameLayout.LayoutParams(old);s.applied.width=w-side;s.applied.gravity=Gravity.TOP|Gravity.LEFT;
    s.started=SystemClock.elapsedRealtime();child.setLayoutParams(s.applied);
    build(a,s,side);
   }
   if(s.listener==null)listen(a,s);
   update(a,s,root);
  }catch(RuntimeException e){restore(s);s.failed=true;ReportLog.w("NatSplitScreen","Split layout restored",e);}
 }
 private static TextView label(LinearLayout p,String text,int size,int color){TextView t=new TextView(p.getContext());t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);t.setPadding(2,5,2,5);p.addView(t);return t;}
 private static void button(LinearLayout p,String text,Runnable action){Button b=new Button(p.getContext());b.setText(text);b.setAllCaps(false);b.setMinHeight(dp(p.getContext(),48));p.addView(b);b.setOnClickListener(v->{try{action.run();}catch(RuntimeException e){Toast.makeText(p.getContext(),"NAT action unavailable",Toast.LENGTH_SHORT).show();}});}
 private static void build(Activity a,State s,int width){
  s.dark=ColorScheme.nightColors(a);int fg=s.dark?0xffedf4f7:0xff263238;
  ScrollView p=new ScrollView(a);p.setTag(OWN);p.setFillViewport(true);p.setBackgroundColor(s.dark?0xff14232b:0xffedf2ef);
  LinearLayout body=new LinearLayout(a);body.setOrientation(1);body.setPadding(dp(a,8),dp(a,8),dp(a,8),dp(a,8));p.addView(body);s.body=body;
  label(body,"NAT • BYD SEAL",16,fg);byte[] image=android.util.Base64.decode("UklGRgoFAABXRUJQVlA4WAoAAAAQAAAAdwAAKgAAQUxQSA4CAAABkARJtmlb69m2vm3btm3btm1jZNu2bdu2ru/6uBvv7L3mETEBYMyAiLS5ilVt2K1nV9etarmuni8SNO2WWGb0mu0nrt99+ubzbztyWlyb3907OKdDsbRhenEPLTxw8yM05LsLm8Y2zR2kB988HZbcN6ORHaZTHcOUC6ow/8onVPHWwJRKRbU/4UBln07MpEzWMQ9Q7Y/zC6rgV275e1T/y7pKUcbyS2xz2IF6dF5bXCPCzRj+eVrOPPIBdep4tKRhUrJl7b365ifUsOPFpq75IpIj9j5q/PeVle2zBUtym426/3V5QdNsHhLK/dbe/x/OTisotAup/DIjJZ//NTIQH/UI4Am7QwjiySoerNRPSEHLhjyMrC9pQfw8KclFkY/UID7tFw0AFX/Sg3ijvT/UsVKE2AvaO2laDv2Q5lMe04h6GDeVqI+FphFlazSMKBzTiao1Xala2IaqqUFriBoGETtp6gwQecxmd9LTFgBiitdo2mPUZ1pMlYA9l5Jfuyv5ceT/LsMkaP7x6qXgsyuXBC/u27adf9t9OabVZT2A122HhHHpM/FnThchGhYoGuDrLZrltIzDJTxAMPNRoW0+oH7ifrGHqUA8ZKKJ72Ea0GHkpB8CtjogtfYDjjdnq4AmSxzmW+QuB1J17tFz0rSZk9oUSxMC2gzq8ZTxaXujICA1c+fuE2eu2TirCPACVlA4INYCAAAwEgCdASp4ACsAPyV4s1SuJqQjMBVbUcAkiUAYti/ycgldEFcjGu+WTzjrYEVQldNwHpEnqyJuBi+J1mqYo/CYO0rkaGZPaEdjrDCvJGfQhayHCKOHrn0QyW6Y2vKL458uqUnSo+mO5qDh/XnGzVdsH/hjj7nBv1QObMtOZ/NyKjIgmOEgavXSI2O/ixxTg/68EP62PlW1bksAAP70I8srI93QU5FgHJlPI0DMnw1vA+aKkJStJJW76HskIXOabl3KlV8N3nCuIi47oINGbCLzEBaBT36kqCRtyS99YeVJOc9IZ0ptY2lep4weUyn3j8ogl7K34ALBoYj19Gsj5D0YDBcGdWc1p3tApgb546GeTg/kNdUxZq6Fsz4PUv0DrytC5MXjRv0qymOvIlZI85ynUbB2aiYEmu8qpnzHAMu5JWngET8cWUVfXyN52qhd/LIk/erR2i4e7PMSX4hW318+avtS3oz6ep4mZvx7Yay0KnY2DgAJOinuM6KuDzKyxcp6PknWTgZc3DH9YdWCUjFfgP5GgiJGszmarBoESSpdbAcDC3A15MFOvF20kYwcOK6TiL8ZguKjqqzN9ppmbLy/5fnC7xQ79oVojn/nb6k43NvxY5k0VoV7ctYi/TAMLCgKCiJnAjaEhU6F4bNvS1UTrllVWWkXV3ouB07rrWmQmrfUhztSQ1oQjmYrpca6ECfL+03mnlvrfyo3DwZG52PglEhvIbxr6CMpstb5gpdsTZVpQawQKhRyDdrAdhilh2DlNHFQI5s07sjbmdxEiGPq9i/WHvilzvGFkGFMti/PxF05oQeHEv75ofJcp0RUVMFLAEVBB6LO9fokk1bSjrOcHyDfvL+/xiWCHOdXifYdGqQDTNq39vnNyjxghOU4OZ6dlvJdKZEwLDK5MCwpYnGmsZNZ30JqxTb6zplcm4sonEiB/2hCuUUVK8Our5OtxUPmPFQAAAA=",android.util.Base64.DEFAULT);Bitmap car=BitmapFactory.decodeByteArray(image,0,image.length);if(car!=null){ImageView photo=new ImageView(a);photo.setImageBitmap(car);photo.setScaleType(ImageView.ScaleType.FIT_CENTER);photo.setContentDescription("Owner BYD Seal photo, cropped");body.addView(photo,new LinearLayout.LayoutParams(-1,dp(a,60)));}s.road=new Road(a);body.addView(s.road,new LinearLayout.LayoutParams(-1,dp(a,150)));
  s.status=label(body,"—",12,fg);s.soc=label(body,"Current —",24,fg);s.arrival=label(body,"Arrival —",24,fg);s.energy=label(body,"—",14,fg);
  button(body,"EV Charging",()->NatMinimalEv.splitAction(a,0));button(body,"Energy Prediction",()->NatMinimalEv.splitAction(a,1));button(body,"Charging Plan",()->NatMinimalEv.splitAction(a,2));button(body,"Clean Mode",()->NatMinimalEv.splitAction(a,3));button(body,"Diagnostics",()->NatMinimalEv.splitAction(a,4));button(body,"Trip Timeline",()->NatTripTimeline.show(a));button(body,"Full Map",()->toggle(a));
  s.panel=p;s.host.addView(p,new FrameLayout.LayoutParams(width,-1,Gravity.RIGHT|Gravity.TOP));
 }
 private static void listen(Activity a,State s){
  if(s.listener!=null)return;
  try{if(a.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
   s.lm=a.getSystemService(LocationManager.class);if(s.lm==null)return;
   s.listener=new LocationListener(){public void onLocationChanged(Location l){
    if(s.listener!=this||s.panel==null||!a.hasWindowFocus())return;long t=l.getElapsedRealtimeNanos()/1000000,now=SystemClock.elapsedRealtime();if(!NatSplitMath.fresh(now,t,3000)||!l.hasBearing()||!l.hasSpeed()||!l.hasAccuracy()||l.getAccuracy()>25||(l.hasBearingAccuracy()&&l.getBearingAccuracyDegrees()>30)){s.turn=Double.NaN;s.bearing=Double.NaN;s.bearingAt=-1;return;}
    s.turn=NatSplitMath.turn(s.bearing,l.getBearing(),l.getSpeed()*3.6,t-s.bearingAt);s.bearing=l.getBearing();s.bearingAt=t;
   }public void onProviderDisabled(String x){s.bearingAt=-1;s.turn=Double.NaN;}public void onProviderEnabled(String x){}public void onStatusChanged(String x,int y,Bundle z){}};
   s.lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,500,0,s.listener,Looper.getMainLooper());
  }catch(RuntimeException e){s.listener=null;s.lm=null;}
 }
 private static String fmt(double v,String u){return Double.isFinite(v)?String.format(Locale.US,"%.1f %s",v,u):"—";}
 private static void update(Activity a,State s,View root){
  long now=SystemClock.elapsedRealtime();Motion m=sample;double speed=m==null||!BydBatteryPatch.isActive()?Double.NaN:NatSplitMath.speed(m.speed,now,m.at,15000);
  double turn=NatSplitMath.fresh(now,s.bearingAt,3000)&&Double.isFinite(speed)&&speed>=5?s.turn:Double.NaN;
  NatLiveEnergy.Reading live=NatPrediction.liveReading();NatPrediction.Snapshot f=NatPrediction.freshSnapshot();boolean match=NatNavigationCard.matchesForecast(root,f);
  s.status.setText(fmt(speed,"km/h")+" • "+(!Double.isFinite(turn)?"Heading —":turn<-3?"Left trend":turn>3?"Right trend":"Straight trend"));
  s.soc.setText("Current "+(Double.isFinite(live.soc)?Math.round(live.soc)+"%":"—"));
  s.arrival.setText("Arrival "+(match?f.google+"%":"—"));s.arrival.setTextColor(match&&f.google<0?0xffef5350:s.dark?0xffedf4f7:0xff263238);
  s.energy.setText("Battery power "+fmt(match?live.power:Double.NaN,"kW")+"\nRemaining "+fmt(match?f.remainingKwh:Double.NaN,"kWh")+"\nObserved "+fmt(match?live.rate:Double.NaN,"kWh/100km"));
  boolean dark=ColorScheme.nightColors(a);if(s.dark!=dark){restore(s);return;}
  s.road.speed=speed;s.road.turn=turn;s.road.running=true;s.road.dark=dark;s.road.invalidate();
 }
 private static final class Road extends View {
  final Paint p=new Paint(3);double speed=Double.NaN,turn=Double.NaN;boolean running,dark;long last;float phase;
  Road(Context c){super(c);setContentDescription("Illustrative road and BYD Seal, not ADAS");}
  void color(int c){p.setColor(c);p.setStyle(Paint.Style.FILL);}
  protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;long now=SystemClock.elapsedRealtime();if(running&&Double.isFinite(speed)&&speed>1)phase=(phase+Math.min(100,Math.max(0,now-last))*((float)speed/100))%30;last=now;
   c.drawColor(dark?0xff17242d:0xffdce7ea);float x=w/2,y=h*.65f,k=Float.isFinite(w)?Math.min(w/180,h/150):1;float angle=Double.isFinite(turn)?(float)turn:0;
   c.save();c.translate(x,y);c.scale(k,k);Path road=new Path();road.moveTo(-50,80);road.quadTo(-50,-40,-50+angle*2,-130);road.lineTo(50+angle*2,-130);road.quadTo(50,-40,50,80);road.close();color(0xff43515a);c.drawPath(road,p);
   color(0xffe7edf0);for(float yy=-120+phase;yy<80;yy+=30){float xx=angle*Math.max(0,-yy)/65;c.drawRoundRect(xx-1,yy,xx+1,yy+13,1,1,p);}
   c.rotate(angle*.6f);color(0xff101820);c.drawRoundRect(-25,-43,25,43,15,15,p);color(0xff637fae);c.drawRoundRect(-22,-46,22,46,15,15,p);color(0xff162836);c.drawRoundRect(-16,-30,16,-10,5,5,p);c.drawRoundRect(-15,12,15,30,5,5,p);color(0xffeaf5ff);c.drawRoundRect(-18,-41,-5,-37,2,2,p);c.drawRoundRect(5,-41,18,-37,2,2,p);color(0xffe83b51);c.drawRoundRect(-18,38,18,42,2,2,p);c.restore();
   if(running&&Double.isFinite(speed)&&speed>1&&isShown()&&hasWindowFocus())postInvalidateDelayed(100);
  }
 }
}
