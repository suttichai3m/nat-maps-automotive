package app.morphe.extension.mapsautomotive.patches;
public class NatDriverRulesTest {
 static int n;static void check(boolean b){if(!b)throw new AssertionError("case "+(n+1));n++;}
 public static void main(String[] a){
 check(NatDriverRules.can(0,1));check(!NatDriverRules.can(0,3));check(NatDriverRules.can(1,2));check(NatDriverRules.can(2,3));check(NatDriverRules.can(3,1));check(!NatDriverRules.can(3,0));check(NatDriverRules.can(1,4));check(NatDriverRules.can(4,1));check(NatDriverRules.can(4,0));check(!NatDriverRules.can(2,0));check(NatDriverRules.stopped(0));check(!NatDriverRules.stopped(Double.NaN));check(!NatDriverRules.stopped(10));check(NatDriverRules.interval(30000));check(!NatDriverRules.interval(30001));check(NatDriverRules.km(100,101,10000)==.1);check(Double.isNaN(NatDriverRules.km(100,99,10000)));check(Double.isNaN(NatDriverRules.km(100,200,10000)));check(NatDriverRules.energy(30,16)==4.8);check(Math.abs(NatDriverRules.profit(450,0,4.8,7,0)-416.4)<.00001);check(Double.isNaN(NatDriverRules.profit(450,0,Double.NaN,7,0)));check(NatDriverRules.perHour(450,1800000)==900);check(Double.isNaN(NatDriverRules.perHour(450,0)));check(Math.abs(NatDriverRules.dead(5,30)-16.66666667)<.00001);check(Double.isNaN(NatDriverRules.dead(5,0)));check(!NatDriverRules.amount(-1));
 System.out.println("PASS "+n+" driver rules");
 }
}
