package app.morphe.extension.mapsautomotive.patches;
public final class NatDrivePowerTest{
 static int n;static void check(boolean b){if(!b)throw new AssertionError("case "+(n+1));n++;}
 public static void main(String[] args){
 check(NatDrivePowerRules.valid(-40));check(NatDrivePowerRules.valid(-100));check(NatDrivePowerRules.valid(300));check(NatDrivePowerRules.valid(0));check(!NatDrivePowerRules.valid(-101));check(!NatDrivePowerRules.valid(301));check(!NatDrivePowerRules.valid(3095));check(!NatDrivePowerRules.valid(Double.NaN));check(!NatDrivePowerRules.valid(Double.POSITIVE_INFINITY));check(!NatDrivePowerRules.valid(Integer.MIN_VALUE));
 check(NatDrivePowerRules.fresh(1000,1000));check(NatDrivePowerRules.fresh(2800,1000));check(!NatDrivePowerRules.fresh(2801,1000));check(!NatDrivePowerRules.fresh(999,1000));check(!NatDrivePowerRules.fresh(0,-1));
 check(NatDrivePowerRules.regen(-40,60));check(!NatDrivePowerRules.regen(-40,0));check(!NatDrivePowerRules.regen(-40,Double.NaN));check(!NatDrivePowerRules.regen(65,60));check(!NatDrivePowerRules.regen(0,60));check(NatDrivePowerRules.label(-40,60).equals("REGEN • SDK"));check(NatDrivePowerRules.label(-40,0).equals("NEGATIVE • SDK"));check(NatDrivePowerRules.label(0,60).equals("0 • SDK"));
 System.out.println("PASS "+n+" direct power rules");
 }
}
