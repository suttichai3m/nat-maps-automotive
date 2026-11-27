package app.morphe.extension.mapsautomotive.patches;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Independent read-only display cache; the existing ten-second Maps model and log cadence is unchanged. */
final class NatV33Telemetry {
 static final class Fast {
  final long at; final double soc,speed;
  Fast(long at,double soc,double speed){this.at=at;this.soc=soc;this.speed=speed;}
 }
 static final class Slow {
  final long at;final double odo,range,kw,recent;
  Slow(long at,double odo,double range,double kw,double recent){this.at=at;this.odo=odo;this.range=range;this.kw=kw;this.recent=recent;}
 }
 static final class Today {
  final String day;final double km,kwh,observedKm,observedNet,positive,increase;final int pairs,detailPairs;final boolean partial;
  Today(String day,double km,double kwh,int pairs,boolean partial,double observedKm,double observedNet,double positive,double increase,int detailPairs){this.day=day;this.km=km;this.kwh=kwh;this.pairs=pairs;this.partial=partial;this.observedKm=observedKm;this.observedNet=observedNet;this.positive=positive;this.increase=increase;this.detailPairs=detailPairs;}
 }
 private static volatile long wanted=-1;
 private static volatile Fast fast;
 private static volatile Slow slow;
 private static volatile Today today;
 private static String day="";
 private static int odo=-1,wh=-1,pairs;
 private static long at=-1;
 private static double speed=Double.NaN,km,net,observedKm,observedNet,positive,increase;
 private static int detailPairs;
 private static boolean partial=true;
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences("nat_v33_daily",Context.MODE_PRIVATE);}
 static void request(){wanted=SystemClock.elapsedRealtime();}
 static void stop(){wanted=-1;}
 static boolean wanted(long now){return NatSplitMath.fresh(now,wanted,1800);}
 static void fast(Fast f){fast=f;}
 static Fast fast(){Fast f=fast;return f!=null&&NatSplitMath.fresh(SystemClock.elapsedRealtime(),f.at,1800)?f:null;}
 static Slow slow(){Slow s=slow;return s!=null&&NatSplitMath.fresh(SystemClock.elapsedRealtime(),s.at,15000)?s:null;}
 private static String day(long wall){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);f.setTimeZone(TimeZone.getTimeZone("Asia/Bangkok"));return f.format(new Date(wall));}
 static Today today(){Today t=today;return t!=null&&t.day.equals(day(System.currentTimeMillis()))?t:null;}
 static double tariff(Context c){double v=prefs(c).getFloat("tariff",7f);return NatDailyMath.validRate(v)?v:7;}
 static void tariff(Context c,double v){if(NatDailyMath.validRate(v))prefs(c).edit().putFloat("tariff",(float)v).apply();}
 // Worker thread only. Distance spans same-day restarts; unobserved energy is never fabricated.
 static void sample(Context c,BydEnergyReader.Sample s){
  String d=day(s.wall);SharedPreferences pref=prefs(c);
  if(!d.equals(day)){
   day=d;boolean same=d.equals(pref.getString("day",""));
   odo=same?pref.getInt("odo",-1):-1;km=same?pref.getFloat("km",0):0;
   net=same?pref.getFloat("net",0):0;pairs=same?pref.getInt("pairs",0):0;
   observedKm=same?pref.getFloat("v34_observed_km",0):0;observedNet=same?pref.getFloat("v34_observed_net",0):0;
   positive=same?pref.getFloat("v34_positive",0):0;increase=same?pref.getFloat("v34_increase",0):0;
   detailPairs=same?pref.getInt("v34_pairs",0):0;
   partial=true;at=-1;wh=-1;speed=Double.NaN;
  }
  int next=s.mileage;int nextWh=(int)Math.round(s.energy*1000);long dt=s.elapsed-at;
  double step=NatDailyMath.distance(odo,next), delta=Double.NaN;
  if(Double.isFinite(step)&&step<=2000){
   if(at<0||dt>30000){km+=step;if(step>0)partial=true;}
   else if(dt>0&&step<=250*dt/3600000.0+.2){
    km+=step;delta=NatDailyMath.net(dt,wh,nextWh,speed,s.speedKmh,step);
    if(Double.isFinite(delta)){net+=delta;pairs++;observedKm+=step;observedNet+=delta;positive+=Math.max(0,delta);increase+=Math.max(0,-delta);detailPairs++;}
    else if(step>0||NatDailyMath.moving(speed)||NatDailyMath.moving(s.speedKmh))partial=true;
   } else partial=true;
  } else if(odo>=0)partial=true;
  if(next>=0)odo=next;else odo=-1;
  at=s.elapsed;wh=nextWh;speed=s.speedKmh;
  today=new Today(day,km,pairs>0?Math.max(0,net):Double.NaN,pairs,partial,observedKm,observedNet,positive,increase,detailPairs);
  pref.edit().putString("day",day).putInt("odo",odo).putFloat("km",(float)km).putFloat("net",(float)net).putInt("pairs",pairs).putFloat("v34_observed_km",(float)observedKm).putFloat("v34_observed_net",(float)observedNet).putFloat("v34_positive",(float)positive).putFloat("v34_increase",(float)increase).putInt("v34_pairs",detailPairs).apply();
  double consumption=s.recentConsumptionKwhPer100Km;
  // BMS steps are coarse. A zero difference is not proof of zero instantaneous power.
  double kw=Double.isFinite(delta)&&delta!=0?delta*3600000/dt:Double.NaN;
  slow=new Slow(s.elapsed,next>0?next/10.0:Double.NaN,NatDailyMath.range(s.energy,consumption),kw,consumption);
 }
}
