package app.morphe.extension.mapsautomotive.patches;
import android.content.Context;
import android.content.SharedPreferences;
/** Observed signed battery change per speed band. Separate estimate, never modifies Google's model. */
final class NatEvLearning {
 private static long at=-1;private static int odo=-1,wh=-1;private static double speed=Double.NaN;
 private static final long MAX_AGE=90L*86400000;
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences("nat_ev_learn_v1",0);}
 static synchronized void observe(Context c,BydEnergyReader.Sample s){
  long dt=s.elapsed-at;int current=(int)Math.round(s.energy*1000);double km=NatDailyMath.distance(odo,s.mileage),delta=NatDailyMath.net(dt,wh,current,speed,s.speedKmh,km);int band=NatEvPlanRules.band(speed,s.speedKmh);
  if(at>=0&&band>=0&&Double.isFinite(delta)&&km>=0){
   SharedPreferences p=prefs(c);double d=p.getFloat("km"+band,0),e=p.getFloat("kwh"+band,0);int n=p.getInt("pairs"+band,0);long old=p.getLong("wall"+band,0);
   if(old<=0||s.wall<old||s.wall-old>MAX_AGE){d=e=0;n=0;}
   if(d>1000||n>100000){d*=.8;e*=.8;n=(int)(n*.8);}
   p.edit().putFloat("km"+band,(float)(d+km)).putFloat("kwh"+band,(float)(e+delta)).putInt("pairs"+band,n+1).putLong("wall"+band,s.wall).apply();
  }
  at=s.elapsed;odo=s.mileage;wh=current;speed=s.speedKmh;
 }
 static double rate(Context c,int band){
  if(band<0||band>2)return Double.NaN;SharedPreferences p=prefs(c);long old=p.getLong("wall"+band,0),now=System.currentTimeMillis();
  if(old<=0||now<old||now-old>MAX_AGE)return Double.NaN;
  return NatEvPlanRules.learned(p.getFloat("km"+band,0),p.getFloat("kwh"+band,0),p.getInt("pairs"+band,0));
 }
 static String info(Context c,int band){SharedPreferences p=prefs(c);double r=rate(c,band);return String.format(java.util.Locale.US,"ประวัติ %.1f km / %d คู่ข้อมูล • %s",p.getFloat("km"+band,0),p.getInt("pairs"+band,0),Double.isFinite(r)?String.format(java.util.Locale.US,"%.1f kWh/100 km",r):"ยังไม่พอสำหรับการเรียนรู้");}
}
