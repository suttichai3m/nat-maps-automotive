package app.morphe.extension.mapsautomotive.patches;
public class NatV34MathTest{
 static int n;
 static void check(boolean v){if(!v)throw new AssertionError("case "+(n+1));n++;}
 static boolean eq(double a,double b){return Math.abs(a-b)<1e-8;}
 public static void main(String[] args){
  check(eq(NatDailyMath.reserveEnergy(80,14,15),.8));
  check(eq(NatDailyMath.reserveEnergy(80,-5,15),16));
  check(eq(NatDailyMath.reserveEnergy(80,20,15),0));
  check(Double.isNaN(NatDailyMath.reserveEnergy(0,10,15)));
  check(Double.isNaN(NatDailyMath.reserveEnergy(80,101,15)));
  check(Double.isNaN(NatDailyMath.reserveEnergy(80,10,-1)));
  check(Double.isNaN(NatDailyMath.reserveEnergy(80,Double.NaN,15)));
  check(eq(NatDailyMath.chargeMinutes(15,50),18));
  check(Double.isNaN(NatDailyMath.chargeMinutes(15,0)));
  check(Double.isNaN(NatDailyMath.chargeMinutes(15,351)));
  check(eq(NatDailyMath.perKm(140,100),1.4));
  check(Double.isNaN(NatDailyMath.perKm(140,0)));
  check(Double.isNaN(NatDailyMath.perKm(-1,100)));
  System.out.println("PASS: "+n+" NAT V3.4 math cases");
 }
}
