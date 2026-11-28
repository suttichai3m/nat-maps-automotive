package app.morphe.extension.mapsautomotive.patches;
public class NatReadableTest {
 static int n;static void check(boolean b){if(!b)throw new AssertionError("case "+(n+1));n++;}
 public static void main(String[] a){
  check(Math.abs(NatReadableRules.estimate(95,93,12,82.56,93,12,95)-1.6512)<.00001);
  check(Double.isFinite(NatReadableRules.estimate(95,93,12,82.56,94,12.1,95)));
  check(Double.isNaN(NatReadableRules.estimate(95,93,12,82.56,90,12,95)));
  check(Double.isNaN(NatReadableRules.estimate(95,93,12,82.56,93,30,95)));
  check(Double.isNaN(NatReadableRules.estimate(95,93,12,82.56,93,12,85)));
  check(Double.isNaN(NatReadableRules.estimate(95,96,12,82.56,96,12,95)));
  check(Double.isNaN(NatReadableRules.estimate(95,93,0,82.56,93,0,95)));
  check(Double.isNaN(NatReadableRules.estimate(95,93,12,0,93,12,95)));
  check(Double.isNaN(NatReadableRules.estimate(95,93,12,82.56,93,12,Double.NaN)));
  check(NatReadableRules.estimate(95,95,12,82.56,95,12,95)==0);
  check(NatReadableRules.estimate(10,-5,12,82.56,-5,12,10)>0);
  System.out.println("PASS "+n+" readable dashboard forecast cases");
 }
}
