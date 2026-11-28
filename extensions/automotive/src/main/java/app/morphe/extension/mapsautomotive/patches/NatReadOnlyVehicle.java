package app.morphe.extension.mapsautomotive.patches;
import android.content.Context;
import android.os.SystemClock;
import java.lang.reflect.Method;
/** Named, documented read-only SDK calls. No wrappers overriding permission checks. */
final class NatReadOnlyVehicle {
 static final class Values{final long at;final String gear,front,rear,lowVoltage;Values(long a,String g,String f,String r,String l){at=a;gear=g;front=f;rear=r;lowVoltage=l;}}
 private static volatile Values values;private static volatile String diag="Vehicle SDK • waiting";private static long last=-1,gearTry=-60000,bodyTry=-60000;
 private static Object gearbox,body;private static Class<?> gc,bc;private static Method gear,door,voltage;private static final int NA=Integer.MIN_VALUE;
 static Values read(){Values v=values;return BydBatteryPatch.isActive()&&v!=null&&NatSplitMath.fresh(SystemClock.elapsedRealtime(),v.at,15000)?v:null;}
 static String status(){return diag;}
 private static int constant(Class<?> c,String key){try{return c.getField(key).getInt(null);}catch(Throwable e){return NA;}}
 private static int get(Method m,Object o,Object... a){try{Object v=m.invoke(o,a);return v instanceof Number?((Number)v).intValue():NA;}catch(Throwable e){return NA;}}
 private static String match(Class<?> c,int raw,String prefix,String[] keys,String[] labels){if(c==null||raw==NA)return "—";String out="—";int count=0;for(int i=0;i<keys.length;i++)if(constant(c,prefix+keys[i])==raw){out=labels[i];count++;}return count==1?out:"—";}
 private static String door(String area){int z=constant(bc,"BODYWORK_CMD_DOOR_"+area);return z==NA?"—":match(bc,get(door,body,z),"BODYWORK_STATE_",new String[]{"OPEN","CLOSED"},new String[]{"เปิด","ปิด"});}
 static void poll(Context c){
  long now=SystemClock.elapsedRealtime();if(last>=0&&now-last<5000)return;last=now;
  String g="—",f="—",r="—",v="—";
  if(gearbox==null&&now-gearTry>=30000){gearTry=now;try{gc=Class.forName("android.hardware.bydauto.gearbox.BYDAutoGearboxDevice");gear=gc.getMethod("getGearboxAutoModeType");gearbox=gc.getMethod("getInstance",Context.class).invoke(null,c.getApplicationContext());}catch(Throwable e){gearbox=null;}}
  if(body==null&&now-bodyTry>=30000){bodyTry=now;try{bc=Class.forName("android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice");try{door=bc.getMethod("getDoorState",int.class);}catch(NoSuchMethodException e){door=null;}try{voltage=bc.getMethod("getBatteryVoltageLevel");}catch(NoSuchMethodException e){voltage=null;}body=bc.getMethod("getInstance",Context.class).invoke(null,c.getApplicationContext());}catch(Throwable e){body=null;}}
  if(gearbox!=null)g=match(gc,get(gear,gearbox),"GEARBOX_AUTO_MODE_",new String[]{"P","R","N","D","S","M"},new String[]{"P","R","N","D","S","M"});
  if(body!=null){f=door("LEFT_FRONT")+" / "+door("RIGHT_FRONT");r=door("LEFT_REAR")+" / "+door("RIGHT_REAR");v=match(bc,get(voltage,body),"BODYWORK_BATTERY_VOLTAGE_LEVEL_",new String[]{"NORMAL","LOW"},new String[]{"Normal","Low"});}
  values=new Values(now,g,f,r,v);diag="Read-only SDK • gearbox="+(gearbox!=null)+" • bodywork="+(body!=null)+" • unknown values not inferred";
 }
}
