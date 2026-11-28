package app.morphe.extension.mapsautomotive.patches;
import android.content.Context;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.util.Locale;

/** Optional read-only SDK getters. No writes, no raw CAN feature scanning. */
final class NatVehicleExtras {
 static final class Values {
  final long at;final String driver,passenger,front,rear;
  Values(long at,String driver,String passenger,String front,String rear){this.at=at;this.driver=driver;this.passenger=passenger;this.front=front;this.rear=rear;}
 }
 private static volatile Values cached;
 private static long last=-1,initAc=-60000,initTyre=-60000;
 private static Object ac,tyre;
 private static Method temp,unit,state,pressure,signal,systemState,pressureByType,tyreTemperature;
 private static final String CAL="nat_tpms_calibration";
 private static Method optional(Class<?> t,String name){try{return t.getMethod(name,int.class);}catch(NoSuchMethodException e){return null;}}
 private static volatile String tyreDiag="TPMS: waiting for sample";
 static String tyreDiagnostics(){return tyreDiag;}
 static void configure(android.app.Activity a){
  android.content.SharedPreferences p=a.getSharedPreferences(CAL,0);int n=p.getInt("pressure_mode",0);if(n<0||n>2)n=0;
  new android.app.AlertDialog.Builder(a).setTitle("TPMS • เลือกหน่วยหลังเทียบ 4 ล้อกับ OverDrive ขณะจอดเท่านั้น").setSingleChoiceItems(new String[]{"มาตรฐาน kPa (เดิม)","ByType / 10 = PSI (ยืนยันแล้ว)","ByType = kPa (ยืนยันแล้ว)"},n,(d,w)->{p.edit().putInt("pressure_mode",w).apply();d.dismiss();}).setNegativeButton("ปิด",null).show();
 }
 private static int mainZone,deputyZone,normal;private static int[] zones;
 private static String front="—",rear="—";private static long tyreAt=-1;
 static Values read(){Values v=cached;return BydBatteryPatch.isActive()&&v!=null&&NatSplitMath.fresh(SystemClock.elapsedRealtime(),v.at,3500)?v:null;}
 private static String raw(int x){return x==Integer.MIN_VALUE?"unavailable":Integer.toString(x);}
 private static int get(Method m,Object target,Object...args){try{Object v=m.invoke(target,args);return v instanceof Number?((Number)v).intValue():Integer.MIN_VALUE;}catch(Throwable e){return Integer.MIN_VALUE;}}
 private static Object device(Class<?> type,Context c)throws Exception{return type.getMethod("getInstance",Context.class).invoke(null,c.getApplicationContext());}
 private static int constant(Class<?> type,String name)throws Exception{return type.getField(name).getInt(null);}
 static void poll(Context c){
  long now=SystemClock.elapsedRealtime();if(c==null||(last>=0&&now-last<1000))return;last=now;
  if(ac==null&&now-initAc>=30000){initAc=now;try{
   Class<?> t=Class.forName("android.hardware.bydauto.ac.BYDAutoAcDevice");
   temp=t.getMethod("getTemprature",int.class);unit=t.getMethod("getTemperatureUnit");state=t.getMethod("getAcStartState");
   mainZone=constant(t,"AC_TEMPERATURE_MAIN");deputyZone=constant(t,"AC_TEMPERATURE_DEPUTY");
   ac=device(t,c);
  }catch(Throwable e){ac=null;ReportLog.i("NatVehicleExtras","AC named read unavailable");}}
  String d="—",p="—";
  if(ac!=null){int on=get(state,ac),u=get(unit,ac);
   if(on==0){d=p=NatV35Rules.climate(on,u,0);}else if(on==1&&u==1){d=NatV35Rules.climate(on,u,get(temp,ac,mainZone));p=NatV35Rules.climate(on,u,get(temp,ac,deputyZone));}
   if(on!=0&&on!=1&&u==Integer.MIN_VALUE){ac=null;initAc=now;}
  }
  if(tyre==null&&now-initTyre>=30000){initTyre=now;try{
   Class<?> t=Class.forName("android.hardware.bydauto.tyre.BYDAutoTyreDevice");
   pressure=t.getMethod("getTyrePressureValue",int.class);signal=t.getMethod("getTyreSignalState",int.class);
   pressureByType=optional(t,"getTyrePressureValueByType");tyreTemperature=optional(t,"getTyreTemperatureValue");
   normal=constant(t,"TYRE_SIGNAL_STATE_NORMAL");
   zones=new int[]{constant(t,"TYRE_COMMAND_AREA_LEFT_FRONT"),constant(t,"TYRE_COMMAND_AREA_RIGHT_FRONT"),constant(t,"TYRE_COMMAND_AREA_LEFT_REAR"),constant(t,"TYRE_COMMAND_AREA_RIGHT_REAR")};
   tyre=device(t,c);try{systemState=t.getMethod("getTyreSystemState");}catch(NoSuchMethodException e){systemState=null;}
   tyreDiag=tyre==null?"TPMS: device unavailable":"TPMS: SDK connected";
  }catch(Throwable e){tyre=null;tyreDiag="TPMS init: "+e.getClass().getSimpleName()+(e.getCause()==null?"":" / "+e.getCause().getClass().getSimpleName());ReportLog.i("NatVehicleExtras",tyreDiag);}}
  if(tyreAt<0||now-tyreAt>=10000){
   tyreAt=now;String[] v={"—","—","—","—"};
   if(tyre!=null){
    StringBuilder info=new StringBuilder("TPMS • raw diagnostic (not confirmed PSI)");
    if(systemState!=null)info.append("\nSystem state: ").append(raw(get(systemState,tyre)));
    String[] labels={"FL","FR","RL","RR"};
    int selected=c.getSharedPreferences(CAL,0).getInt("pressure_mode",0);
    for(int i=0;i<4;i++){
     int st=get(signal,tyre,zones[i]),kpa=get(pressure,tyre,zones[i]),typed=get(pressureByType,tyre,zones[i]),temperature=get(tyreTemperature,tyre,zones[i]);
     info.append("\n").append(labels[i]).append(" signal=").append(raw(st)).append(" kPa getter=").append(raw(kpa)).append(" ByType raw=").append(raw(typed)).append(" temp raw=").append(raw(temperature));
     if(st==normal){double psi=NatEvPlanRules.pressure(selected,kpa,typed);v[i]=Double.isFinite(psi)?String.format(Locale.US,"%.1f",psi):"—";}
    }
    info.append("\nPressure mode: ").append(selected).append(" • Temperature raw unit not verified");
    info.append("\nNormal signal code: ").append(normal).append("\nรถจอดอาจพัก TPMS • ตรวจเทียบกับหน้าปัด");
    tyreDiag=info.toString();
   }
   front=v[0]+" / "+v[1];rear=v[2]+" / "+v[3];
  }
  cached=new Values(now,d,p,front,rear);
 }

}
