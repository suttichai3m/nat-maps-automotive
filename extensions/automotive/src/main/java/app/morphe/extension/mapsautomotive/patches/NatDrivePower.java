package app.morphe.extension.mapsautomotive.patches;
import android.content.Context;
import android.os.SystemClock;
import java.lang.reflect.Method;
import org.json.JSONObject;

/** Optional read-only engine signal. Worker-thread polling; no writes, no access elevation. */
final class NatDrivePower {
 private static final String SOURCE="BYDAutoEngineDevice.getEnginePower";
 static final class Reading {
  final long at;final double kw,speed;
  Reading(long at,double kw,double speed){this.at=at;this.kw=kw;this.speed=speed;}
 }
 private static volatile Reading reading;
 private static volatile String status="waiting for dashboard";
 private static volatile double lastRaw=Double.NaN,peakPower=Double.NaN,peakRegen=Double.NaN;
 private static volatile long rawAt=-1,lastChangeAt=-1;private static volatile long samples;
 private static volatile String returnType="unknown";
 private Object device;private Method getter;private long nextTry;
 void poll(Context context,double speed){
  long now=SystemClock.elapsedRealtime();
  if(now<nextTry)return;
  try{
   if(device==null){
    Class<?> c=Class.forName("android.hardware.bydauto.engine.BYDAutoEngineDevice");
    Method m=c.getMethod("getEnginePower");
    returnType=m.getReturnType().getName();
    Object d=c.getMethod("getInstance",Context.class).invoke(null,context.getApplicationContext());
    if(d==null)throw new IllegalStateException("SDK returned null");
    device=d;getter=m;
   }
   Object value=BydEnergyReader.invoke(getter,device);
   long at=SystemClock.elapsedRealtime();
   double raw=value instanceof Number?((Number)value).doubleValue():Double.NaN;
   if(Double.compare(raw,lastRaw)!=0)lastChangeAt=at;lastRaw=raw;rawAt=at;samples++;
   if(!NatDrivePowerRules.valid(raw)){reading=null;status="invalid SDK power";return;}
   reading=new Reading(at,raw,speed);status="SDK read OK (cluster comparison pending)";
   if(NatDrivePowerRules.moving(speed)){
    if(raw>0)peakPower=Double.isFinite(peakPower)?Math.max(peakPower,raw):raw;
    if(raw<0)peakRegen=Double.isFinite(peakRegen)?Math.min(peakRegen,raw):raw;
   }
  }catch(Throwable e){
   reading=null;status="SDK unavailable: "+BydEnergyReader.describe(e);device=null;getter=null;nextTry=SystemClock.elapsedRealtime()+3000;
  }
 }
 static Reading fresh(){Reading r=reading;return r!=null&&BydBatteryPatch.isActive()&&NatDrivePowerRules.fresh(SystemClock.elapsedRealtime(),r.at)?r:null;}
 private static Object number(double v){return Double.isFinite(v)?v:JSONObject.NULL;}
 static JSONObject diagnostics() throws org.json.JSONException {
  Reading r=fresh();long now=SystemClock.elapsedRealtime(),at=rawAt;
  return new JSONObject().put("source",SOURCE).put("sdk_return_type",returnType).put("status",status).put("fresh",r!=null)
   .put("last_raw_kw",number(lastRaw)).put("raw_age_ms",at>=0&&now>=at?now-at:JSONObject.NULL)
   .put("sample_count",samples).put("unchanged_ms",lastChangeAt>=0&&now>=lastChangeAt?now-lastChangeAt:JSONObject.NULL)
   .put("live_kw",number(r==null?Double.NaN:r.kw)).put("sample_speed_kmh",number(r==null?Double.NaN:r.speed))
   .put("peak_power_kw_process",number(peakPower)).put("peak_regen_kw_process",number(peakRegen));
 }
 static String summary(){try{return "Live POWER / REGEN • "+diagnostics().toString(1)+"\nPeak เฉพาะช่วงเคลื่อนที่ที่อ่านได้ใน process นี้ • เทียบหน้าปัดก่อนรับรอง";}catch(Exception e){return "Drive power diagnostics unavailable";}}
}
