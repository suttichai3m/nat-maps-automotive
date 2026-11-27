package app.morphe.extension.mapsautomotive.patches;

import android.app.Activity;
import android.os.SystemClock;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/** Native EVCS decision bridge for the inspected Maps build. Never enables a native-ineligible layer. */
public final class NatNativeEvLayer {
 private static final long AGE=30000;
 private static final String TAG="NatNativeEvLayer";
 private static final Object LOCK=new Object();
 private static volatile Observation last;
 private static WeakReference<Object> hidden=new WeakReference<>(null);
 private static long requestedAt=-1,generation;
 private static final class Observation {
  final WeakReference<Object> producer;final long at;final boolean eligible,shown;
  Observation(Object p,boolean e,boolean s){producer=new WeakReference<>(p);at=SystemClock.elapsedRealtime();eligible=e;shown=s;}
 }
 private static Field field(Object p,String name)throws Exception{Field f=p.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
 private static boolean main(Object p)throws Exception{
  if(p==null||!p.getClass().getName().equals("ifd"))return false;
  Object surface=field(p,"n").get(p);
  return surface instanceof Enum&&"MAIN".equals(((Enum<?>)surface).name());
 }
 /** Injected at the existing Boolean.valueOf eligibility decision; original false cannot become true. */
 public static boolean gate(Object producer,boolean eligible){
  try{
   if(!main(producer))return eligible;
   synchronized(LOCK){
    boolean shown=eligible&&(!BydBatteryPatch.isActive()||hidden.get()!=producer);
    last=new Observation(producer,eligible,shown);return shown;
   }
  }catch(Throwable ignored){return eligible;}
 }
 private static Observation fresh(){
  Observation x=last;long now=SystemClock.elapsedRealtime();
  return x!=null&&x.producer.get()!=null&&now>=x.at&&now-x.at<=AGE?x:null;
 }
 static Boolean effective(){Observation x=fresh();return x==null?null:x.shown;}
 static String status(){
  Observation x=fresh();return x==null?"EVCS producer: waiting / unsupported / stale":
    "EVCS MAIN • native eligible="+x.eligible+" • renderer requested="+x.shown;
 }
 /** UI click only; preserve provider eligibility and refresh using the native producer's own executor. */
 static boolean request(Activity activity){
  if(activity==null||activity.isFinishing()||activity.isDestroyed()||!activity.hasWindowFocus()||!BydBatteryPatch.isActive())return false;
  final Observation x=fresh();
  if(x==null)return false;
  final Object producer=x.producer.get();if(producer==null)return false;
  final Executor executor;final Method refresh;
  try{
   if(!main(producer))return false;
   Object e=field(producer,"a").get(producer);
   if(!(e instanceof Executor))return false;
   executor=(Executor)e;refresh=producer.getClass().getDeclaredMethod("b");
   if(refresh.getReturnType()!=Void.TYPE)return false;
   refresh.setAccessible(true);
  }catch(Throwable error){ReportLog.i(TAG,"Native refresh unavailable: "+error.getClass().getSimpleName());return false;}
  final boolean wasHidden;final long ticket;final boolean nextHidden;
  synchronized(LOCK){
   if(last!=x)return false;
   wasHidden=hidden.get()==producer;
   if(!x.eligible&&!wasHidden)return false;
   long now=SystemClock.elapsedRealtime();if(requestedAt>=0&&now-requestedAt<1200)return true;
   requestedAt=now;ticket=++generation;nextHidden=!wasHidden&&x.shown;
   hidden=new WeakReference<>(nextHidden?producer:null);
  }
  try{
   executor.execute(()->{
    synchronized(LOCK){if(ticket!=generation)return;}
    Observation current=fresh();
    if(current==null||current.producer.get()!=producer){restore(producer,wasHidden,ticket);return;}
    try{refresh.invoke(producer);}
    catch(Throwable error){restore(producer,wasHidden,ticket);ReportLog.i(TAG,"Native refresh failed: "+error.getClass().getSimpleName());}
   });
   Toast.makeText(activity,nextHidden?"ขอซ่อน EV Layer":"ขอแสดง EV Layer หาก Maps รองรับ",Toast.LENGTH_SHORT).show();return true;
  }catch(Throwable error){restore(producer,wasHidden,ticket);ReportLog.i(TAG,"Native executor unavailable: "+error.getClass().getSimpleName());return false;}
 }
 private static void restore(Object p,boolean old,long ticket){
  synchronized(LOCK){if(ticket==generation)hidden=new WeakReference<>(old?p:null);}
 }
}
