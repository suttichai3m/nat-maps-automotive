package app.morphe.extension.mapsautomotive.patches;
/** Pure calculations; unknown values remain unknown. */
final class NatDriverRules {
 static final int OFF=0,WAIT=1,PICKUP=2,RIDE=3,BREAK=4;
 static boolean can(int from,int to){return from==OFF?to==WAIT:from==WAIT?(to==PICKUP||to==BREAK||to==OFF):from==PICKUP?(to==RIDE||to==WAIT):from==RIDE?to==WAIT:from==BREAK?(to==WAIT||to==OFF):false;}
 static boolean stopped(double speed){return Double.isFinite(speed)&&speed>=0&&speed<=1;}
 static boolean interval(long ms){return ms>0&&ms<=30000;}
 static double km(int a,int b,long dt){if(!interval(dt)||a<0||b<a)return Double.NaN;double v=(b-a)/10.0;return v<=250*dt/3600000.0+.2?v:Double.NaN;}
 static boolean amount(double n){return Double.isFinite(n)&&n>=0&&n<=1000000;}
 static double energy(double km,double rate){return amount(km)&&Double.isFinite(rate)&&rate>0&&rate<=100?km*rate/100:Double.NaN;}
 static double profit(double fare,double fees,double kwh,double tariff,double fixed){return amount(fare)&&amount(fees)&&amount(kwh)&&amount(tariff)&&amount(fixed)?fare-fees-kwh*tariff-fixed:Double.NaN;}
 static double perHour(double value,long ms){return Double.isFinite(value)&&ms>0?value*3600000/ms:Double.NaN;}
 static double dead(double dead,double total){return amount(dead)&&amount(total)&&total>0&&dead<=total?100*dead/total:Double.NaN;}
}
