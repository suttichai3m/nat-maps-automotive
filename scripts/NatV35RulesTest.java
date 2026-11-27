package app.morphe.extension.mapsautomotive.patches;
public final class NatV35RulesTest {
 static int n;static void check(boolean v){if(!v)throw new AssertionError("V3.5 case "+(n+1));n++;}
 public static void main(String[] args){
  check(NatV35Rules.recoveryDelay(0,100,100000)==0);
  check(NatV35Rules.recoveryDelay(1000,1001,100000)==99999);
  check(NatV35Rules.recoveryDelay(1000,100999,100000)==1);
  check(NatV35Rules.recoveryDelay(1000,101000,100000)==0);
  check(NatV35Rules.recoveryDelay(1000,999,100000)==0);
  check(NatV35Rules.climate(1,1,24).equals("24°C"));
  check(NatV35Rules.climate(1,1,27).equals("27°C"));
  check(NatV35Rules.climate(0,1,24).equals("OFF"));
  check(NatV35Rules.climate(1,2,75).equals("—"));
  check(NatV35Rules.climate(1,1,65535).equals("—"));
  check(NatV35Rules.climate(1,1,48).equals("—"));
  check(NatV35Rules.climate(-1,1,24).equals("—"));
  check(NatV35Rules.psi(235).equals("34.1"));
  check(NatV35Rules.psi(0).equals("—"));
  check(NatV35Rules.psi(65535).equals("—"));
  check(NatV35Rules.psi(-1).equals("—"));
  System.out.println("PASS: "+n+" V3.5 recovery and signal formatting cases");
 }
}
