package app.morphe.extension.mapsautomotive.patches;
final class NatStabilityMath {
 static boolean fresh(long now,long at){return at>=0&&now>=at&&now-at<=30000;}
 static int signedSoc(int wh,int cap,int positiveDisplay){if(cap<=0)throw new IllegalArgumentException();return wh<=0?(int)Math.floor(wh*100.0/cap):positiveDisplay;}
 static double remainingKwh(int current,int finalWh,long minimumAdded,boolean complete){if(!complete||current<0||minimumAdded<0)return Double.NaN;double k=((double)current-finalWh+minimumAdded)/1000;return k>=0?k:Double.NaN;}
 static double rate(double kwh,double km){return Double.isFinite(kwh)&&kwh>=0&&Double.isFinite(km)&&km>0?kwh*100/km:Double.NaN;}
 static double cost(double kwh,double price){return Double.isFinite(kwh)&&kwh>=0&&Double.isFinite(price)&&price>0&&price<=100?kwh*price:Double.NaN;}
 static boolean autoHide(boolean previousFresh,int previousStops,boolean fresh,int stops){return fresh&&(!previousFresh||previousStops!=stops);}
 static boolean moving(double speed){return Double.isFinite(speed)&&speed>1&&speed<=250;}
 static boolean pair(long ms,double a,double b){return ms>=1000&&ms<=30000&&moving(a)&&moving(b);}
 static boolean matches(int value,double km,int expected,double expectedKm){return value==expected&&Double.isFinite(km)&&Double.isFinite(expectedKm)&&km>=0&&Math.abs(km-expectedKm)<=Math.max(.2,expectedKm*.01);}
 static final class Observed {
  private int wh=-1;private long at=-1;private double speed=Double.NaN;double net;int pairs;
  void reset(){wh=-1;at=-1;speed=Double.NaN;net=0;pairs=0;}
  void observe(int next,long now,double nextSpeed){long dt=now-at;long delta=(long)wh-next;
   if(wh>=0&&next>=0&&pair(dt,speed,nextSpeed)&&Math.abs(delta)<=250000.0*dt/3600000.0+200){net+=delta/1000.0;pairs++;}
   wh=next;at=now;speed=nextSpeed;}
 }
}
