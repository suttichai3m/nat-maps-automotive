package app.morphe.extension.mapsautomotive.patches;
final class NatDailyMath {
 private NatDailyMath(){}
 static boolean validRate(double x){return Double.isFinite(x)&&x>0&&x<=100;}
 static double cost(double kwh,double rate){return Double.isFinite(kwh)&&kwh>=0&&validRate(rate)?kwh*rate:Double.NaN;}
 static double range(double energy,double rate){return Double.isFinite(energy)&&energy>=0&&Double.isFinite(rate)&&rate>0&&rate<=99.9?energy*100/rate:Double.NaN;}
 static double distance(int from,int to){return from>=0&&to>=from?(to-(long)from)/10.0:Double.NaN;}
 static double net(long dt,int oldWh,int wh,double oldSpeed,double speed,double km){
  if(dt<5000||dt>30000||oldWh<0||wh<0||!Double.isFinite(km)||km<0||km>250*dt/3600000.0+.2||!moving(oldSpeed)||!moving(speed))return Double.NaN;
  double delta=((long)oldWh-wh)/1000.0;
  return Math.abs(delta*3600000/dt)<=250?delta:Double.NaN;
 }
 static boolean moving(double s){return Double.isFinite(s)&&s>1&&s<=250;}
 static double forecastRate(double energy,double km){return Double.isFinite(energy)&&energy>=0&&Double.isFinite(km)&&km>0?energy*100/km:Double.NaN;}
}
