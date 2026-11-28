package app.morphe.extension.mapsautomotive.patches;
import java.util.Locale;
final class NatEvPlanRules {
 static boolean bounded(double x,double lo,double hi){return Double.isFinite(x)&&x>=lo&&x<=hi;}
 static final class Result{final double outbound,back,oneKwh,totalKwh,needed;Result(double a,double b,double o,double t,double n){outbound=a;back=b;oneKwh=o;totalKwh=t;needed=n;}}
 static Result plan(double soc,double cap,double outKm,double outRate,double backKm,double backRate,double reserve){
  if(!bounded(soc,0,100)||!bounded(cap,5,150)||!bounded(outKm,0,10000)||!bounded(backKm,0,10000)||!bounded(outRate,5,50)||!bounded(backRate,5,50)||!bounded(reserve,0,100))throw new IllegalArgumentException("input");
  double one=outKm*outRate/100,total=one+backKm*backRate/100,a=soc-100*one/cap,b=soc-100*total/cap;
  return new Result(a,b,one,total,Math.max(0,cap*(reserve-b)/100));
 }
 static int band(double a,double b){if(!bounded(a,1.01,250)||!bounded(b,1.01,250))return -1;int x=a<40?0:a<80?1:2,y=b<40?0:b<80?1:2;return x==y?x:-1;}
 static double learned(double km,double kwh,int pairs){if(!bounded(km,20,2000)||pairs<100)return Double.NaN;double r=kwh*100/km;return bounded(r,5,50)?r:Double.NaN;}
 static double pressure(int mode,int original,int typed){double p=mode==0?original/6.894757293:mode==1?typed/10.0:mode==2?typed/6.894757293:Double.NaN;return bounded(p,15,90)?p:Double.NaN;}
 static String airport(String z){if(z==null)return null;String n=z.trim().toLowerCase(Locale.ROOT);if(n.equals("bkk")||n.equals("สุวรรณภูมิ"))return "BKK";if(n.equals("dmk")||n.equals("ดอนเมือง"))return "DMK";return null;}
}
