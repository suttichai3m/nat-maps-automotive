package app.morphe.extension.mapsautomotive.patches;
final class NatV36Rules {
 private NatV36Rules(){}
 static boolean stopped(double s){return Double.isFinite(s)&&s>=0&&s<=1;}
 static int interval(long dt,double a,double b){
  if(dt<=0||dt>30000||!Double.isFinite(a)||!Double.isFinite(b)||a<0||b<0||a>250||b>250)return 0;
  return stopped(a)&&stopped(b)?2:a>1&&b>1?1:0;
 }
 static double chargeDelta(double start,double end,long dt){return Double.isFinite(start)&&Double.isFinite(end)&&start>=0&&end>=start&&end<=150&&dt>0&&dt<=24*3600000L?end-start:Double.NaN;}
}
