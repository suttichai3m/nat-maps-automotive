package app.morphe.extension.mapsautomotive.patches;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Recorded intervals only, not ignition time. Charging log is explicitly user-started/ended. */
final class NatV36Daily {
 static final class Summary{final long moving,stopped;Summary(long m,long s){moving=m;stopped=s;}}
 static final class Energy{final long at;final double soc,kwh,speed;Energy(long at,double soc,double kwh,double speed){this.at=at;this.soc=soc;this.kwh=kwh;this.speed=speed;}}
 private static volatile Energy energy;
 private static long previous=-1;private static String previousDay="";private static double previousSpeed=Double.NaN;
 private static SharedPreferences pref(Context c){return c.getSharedPreferences("nat_v36_stats",0);}
 private static String day(long wall){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);f.setTimeZone(TimeZone.getTimeZone("Asia/Bangkok"));return f.format(new Date(wall));}
 static void observe(Context c,BydEnergyReader.Sample s){
  energy=new Energy(s.elapsed,s.soc,s.energy,s.speedKmh);
  SharedPreferences p=pref(c);String d=day(s.wall);boolean same=d.equals(p.getString("day",""));
  if(p.contains("charge_start")&&Double.isFinite(s.speedKmh)&&s.speedKmh>1&&s.speedKmh<=250&&!p.getBoolean("charge_moved",false))p.edit().putBoolean("charge_moved",true).apply();
  long m=same?p.getLong("moving",0):0,z=same?p.getLong("stopped",0):0;
  long dt=s.elapsed-previous;
  int kind=d.equals(previousDay)?NatV36Rules.interval(dt,previousSpeed,s.speedKmh):0;
  if(kind==1)m+=dt;else if(kind==2)z+=dt;
  p.edit().putString("day",d).putLong("moving",m).putLong("stopped",z).apply();
  previous=s.elapsed;previousDay=d;previousSpeed=s.speedKmh;
 }
 static Summary read(Context c){SharedPreferences p=pref(c);return day(System.currentTimeMillis()).equals(p.getString("day",""))?new Summary(p.getLong("moving",0),p.getLong("stopped",0)):null;}
 static String duration(long ms){long n=ms/60000;return (n/60)+" ชม. "+(n%60)+" นาที";}
 static Energy energy(){Energy e=energy;return BydBatteryPatch.isActive()&&e!=null&&NatSplitMath.fresh(SystemClock.elapsedRealtime(),e.at,15000)&&Double.isFinite(e.kwh)&&e.kwh>=0&&e.kwh<=150?e:null;}
 static String chargeStatus(Context c){SharedPreferences p=pref(c);return p.contains("charge_start")?"มีช่วงชาร์จที่เริ่มบันทึกไว้":"ยังไม่ได้เริ่มบันทึกช่วงชาร์จ";}
 static String beginCharge(Context c){
  SharedPreferences p=pref(c);Energy e=energy();if(p.contains("charge_start"))return "มีช่วงที่เปิดอยู่แล้ว";
  if(e==null||!NatV36Rules.stopped(e.speed))return "ต้องมีข้อมูลรถสดและรถหยุดนิ่ง";
  p.edit().putLong("charge_start",System.currentTimeMillis()).putFloat("charge_kwh",(float)e.kwh).putFloat("charge_rate",(float)NatV33Telemetry.tariff(c)).remove("charge_moved").apply();return "เริ่มบันทึกแล้ว (ด้วยตนเอง)";
 }
 static String endCharge(Context c){
  SharedPreferences p=pref(c);Energy e=energy();long start=p.getLong("charge_start",0),now=System.currentTimeMillis();
  if(start<=0)return "ยังไม่ได้เริ่มบันทึก";if(p.getBoolean("charge_moved",false))return "พบรถเคลื่อนที่ระหว่างช่วงที่บันทึก • ยกเลิกช่วงนี้ก่อน";if(e==null||!NatV36Rules.stopped(e.speed))return "ต้องมีข้อมูลรถสดและรถหยุดนิ่ง";
  double kwh=NatV36Rules.chargeDelta(p.getFloat("charge_kwh",Float.NaN),e.kwh,now-start);
  if(!Double.isFinite(kwh))return "ข้อมูลไม่สอดคล้อง • ไม่บันทึกค่าไฟ";
  double cost=NatDailyMath.cost(kwh,p.getFloat("charge_rate",7f));
  String record=String.format(Locale.US,"%s • %.1f kWh • ฿%.2f • %s",day(now),kwh,cost,duration(now-start));
  String history=record+"\n"+p.getString("history","");
  String[] lines=history.split("\n");StringBuilder retained=new StringBuilder();for(int i=0;i<Math.min(30,lines.length);i++)retained.append(lines[i]).append("\n");
  p.edit().remove("charge_start").remove("charge_kwh").remove("charge_rate").remove("charge_moved").putString("history",retained.toString()).apply();return "บันทึกแล้ว: "+record;
 }
 static void cancelCharge(Context c){pref(c).edit().remove("charge_start").remove("charge_kwh").remove("charge_rate").remove("charge_moved").apply();}
 static String history(Context c){String h=pref(c).getString("history","");return h.isEmpty()?"ยังไม่มีประวัติ":h;}
}
