package app.morphe.extension.mapsautomotive.patches;
public final class NatV33MathTest {
 static int n;static void check(boolean ok){if(!ok)throw new AssertionError("case "+(n+1));n++;}
 static boolean eq(double a,double b){return Math.abs(a-b)<1e-8;}
 public static void main(String[] x){
 check(eq(NatDailyMath.cost(21.6,7),151.2));check(eq(NatDailyMath.cost(21.6,8),172.8));
 check(Double.isNaN(NatDailyMath.cost(21.6,0)));check(!NatDailyMath.validRate(Double.NaN));check(!NatDailyMath.validRate(101));
 check(eq(NatDailyMath.range(60,15),400));check(Double.isNaN(NatDailyMath.range(60,0)));
 check(eq(NatDailyMath.distance(584200,584215),1.5));check(Double.isNaN(NatDailyMath.distance(20,10)));
 check(eq(NatDailyMath.net(10000,50000,49900,30,30,.1),.1));check(eq(NatDailyMath.net(10000,50000,50100,30,30,.1),-.1));
 check(eq(NatDailyMath.net(10000,50000,49900,Double.NaN,Double.NaN,.1),.1));
 check(Double.isNaN(NatDailyMath.net(10000,50000,49900,Double.NaN,Double.NaN,0)));
 check(Double.isNaN(NatDailyMath.net(10000,50000,52000,0,0,0)));
 check(Double.isNaN(NatDailyMath.net(31000,50000,49900,30,30,.1)));
 check(Double.isNaN(NatDailyMath.net(10000,50000,49900,30,30,20)));
 check(eq(NatDailyMath.forecastRate(.4,1.8),22.22222222222222));check(Double.isNaN(NatDailyMath.forecastRate(.4,0)));
 System.out.println("PASS: "+n+" V3.3 math cases");
 }
}
